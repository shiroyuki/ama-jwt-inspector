package com.jwt.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class App extends Application {

    // ── theme selection ──────────────────────────────────────────────────────
    private enum ThemeMode {
        LIGHT, DARK, SYSTEM;

        String displayName() {
            return switch (this) {
                case LIGHT  -> "Light";
                case DARK   -> "Dark";
                case SYSTEM -> "Auto";
            };
        }
    }

    /**
     * One full colour palette. Strictly monotone (grayscale): meaning is conveyed through
     * text, icons and weight rather than hue, and every pairing below is chosen to clear
     * WCAG AA contrast for its role.
     */
    private record Palette(String bgDark, String bgSurface, String bgCard,
                            String accent, String accentHover, String accentText,
                            String textPrimary, String textSecondary, String textMuted,
                            String border) {}

    private static final Palette DARK = new Palette(
            "#121212", "#171717", "#1e1e1e",
            "#f5f5f5", "#d6d6d6", "#121212",
            "#f5f5f5", "#b0b0b0", "#8a8a8a", "#333333");

    private static final Palette LIGHT = new Palette(
            "#ffffff", "#ffffff", "#f2f2f2",
            "#111111", "#2e2e2e", "#ffffff",
            "#111111", "#4d4d4d", "#5c5c5c", "#dcdcdc");

    private static final Preferences PREFS = Preferences.userNodeForPackage(App.class);
    private static final String PREF_THEME_MODE = "themeMode";

    private ThemeMode themeMode = ThemeMode.SYSTEM;

    // ── colour fields (re-assigned whenever the active theme changes) ─────────
    private String BG_DARK;
    private String BG_SURFACE;
    private String BG_CARD;
    private String ACCENT;
    private String ACCENT_HOVER;
    private String ACCENT_TEXT;
    private String TEXT_PRIMARY;
    private String TEXT_SECONDARY;
    private String TEXT_MUTED;
    private String BORDER;

// A single Key/Value row rendered in the output tables.
    private record Field(String key, String value) {}

     // ── JSON helpers for building table rows ──────────────────────────────────
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SNAKE =
            Pattern.compile("([a-z0-9])([A-Z])");
    private static final DateTimeFormatter CLAIM_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

     // ── UI fields ─────────────────────────────────────────────────────────────
    private TextArea  tokenInput;
    private TextField secretField;
    private CheckBox  verifyCheck;
    private TableView<Field> headerTable;
    private TableView<Field> payloadTable;
    private TableView<Field> signatureTable;
    private TableView<Field> findingTable;
    private Label     typeLabel;
    private Label     qualityLabel;
    private Label     impersonationLabel;
    private Label     statusLabel;
    private Label     expLabel;
    private Label     iatLabel;
    private Label     nbfLabel;
    private TextFlow  tokenPreview;
    private VBox      metaBox;
    private VBox      analysisBox;

    private Stage      stage;
    private BorderPane root;
    private Scene       scene;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        themeMode = parseThemeMode(PREFS.get(PREF_THEME_MODE, ThemeMode.SYSTEM.name()));
        applyPalette(resolvePalette(themeMode));

        // Follow OS theme changes live while in SYSTEM mode.
        Platform.getPreferences().colorSchemeProperty().addListener((obs, oldScheme, newScheme) -> {
            if (themeMode == ThemeMode.SYSTEM) {
                applyPalette(paletteFor(newScheme));
                rebuildUi();
            }
        });

        root = new BorderPane();
        root.getStyleClass().add(resolvePalette(themeMode) == LIGHT ? "theme-light" : "theme-dark");
        root.setStyle("-fx-background-color: " + BG_DARK + ";");
        root.setTop(buildHeader());
        root.setCenter(buildContent());

        scene = new Scene(root, 1000, 760);
        scene.getStylesheets().add(
                getClass().getResource("/jwt-inspector.css").toExternalForm());
        stage.setTitle("JWT Inspector");
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(580);
        stage.show();
    }

    // ── theme helpers ─────────────────────────────────────────────────────────

    private ThemeMode parseThemeMode(String raw) {
        try {
            return ThemeMode.valueOf(raw);
        } catch (Exception e) {
            return ThemeMode.SYSTEM;
        }
    }

    private Palette paletteFor(ColorScheme scheme) {
        return scheme == ColorScheme.DARK ? DARK : LIGHT;
    }

    private Palette resolvePalette(ThemeMode mode) {
        return switch (mode) {
            case DARK   -> DARK;
            case LIGHT  -> LIGHT;
            case SYSTEM -> paletteFor(Platform.getPreferences().getColorScheme());
        };
    }

    private void applyPalette(Palette p) {
        BG_DARK        = p.bgDark();
        BG_SURFACE     = p.bgSurface();
        BG_CARD        = p.bgCard();
        ACCENT         = p.accent();
        ACCENT_HOVER   = p.accentHover();
        ACCENT_TEXT    = p.accentText();
        TEXT_PRIMARY   = p.textPrimary();
        TEXT_SECONDARY = p.textSecondary();
        TEXT_MUTED     = p.textMuted();
        BORDER         = p.border();

        if (root != null) {
            root.getStyleClass().removeAll("theme-dark", "theme-light");
            root.getStyleClass().add(p == LIGHT ? "theme-light" : "theme-dark");
        }
    }

    /** Called when the user picks a theme, or the OS theme changes while following it. */
    private void switchTheme(ThemeMode mode) {
        themeMode = mode;
        PREFS.put(PREF_THEME_MODE, mode.name());
        applyPalette(resolvePalette(mode));
        rebuildUi();
    }

    /** Rebuilds the whole UI against the current palette, preserving the user's input and results. */
    private void rebuildUi() {
        String tokenText   = tokenInput != null ? tokenInput.getText() : "";
        String secretText  = secretField != null ? secretField.getText() : "";
        boolean verify     = verifyCheck != null && verifyCheck.isSelected();
        boolean hadResults = analysisBox != null && analysisBox.isVisible();

        root.setStyle("-fx-background-color: " + BG_DARK + ";");
        root.setTop(buildHeader());
        root.setCenter(buildContent());

        tokenInput.setText(tokenText);
        secretField.setText(secretText);
        verifyCheck.setSelected(verify);

        if (hadResults && !tokenText.isBlank()) {
            inspect();
        }
    }

    // ── top header bar ────────────────────────────────────────────────────────
    private HBox buildHeader() {
        Label logo = new Label("JWT Inspector");
        logo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");

        ComboBox<ThemeMode> themeSwitch = buildThemeSwitch();

        Button infoBtn = new Button("ⓘ"); // circled Latin small letter i
        infoBtn.setTooltip(new Tooltip("About"));
        infoBtn.setStyle("-fx-background-color: transparent; "
                 + "-fx-text-fill: " + TEXT_MUTED + "; "
                 + "-fx-font-size: 18px; -fx-padding: 0 6; -fx-background-radius: 50%; -fx-cursor: hand;");
        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle("-fx-background-color: transparent; "
                 + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                 + "-fx-font-size: 18px; -fx-padding: 0 6; -fx-background-radius: 50%; -fx-cursor: hand;"));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle("-fx-background-color: transparent; "
                 + "-fx-text-fill: " + TEXT_MUTED + "; "
                 + "-fx-font-size: 18px; -fx-padding: 0 6; -fx-background-radius: 50%; -fx-cursor: hand;"));
        infoBtn.setOnAction(e -> showAboutDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, logo, spacer, themeSwitch, infoBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color: " + BG_SURFACE + "; "
                 + "-fx-border-color: " + BORDER + "; "
                 + "-fx-border-width: 0 0 1 0;");
        return header;
    }

    /**
     * Builds a Light / Dark / Auto theme selector as a dropdown. The popup's list cells are
     * styled explicitly (rather than left to the default list-view skin) so the option text
     * always keeps strong contrast against its background, in dark mode as well as light.
     */
    private ComboBox<ThemeMode> buildThemeSwitch() {
        ComboBox<ThemeMode> combo = new ComboBox<>();
        combo.getItems().addAll(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM);
        combo.setValue(themeMode);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(ThemeMode mode) { return mode == null ? "" : mode.displayName(); }
            @Override public ThemeMode fromString(String s) { return null; }
        });
        combo.setButtonCell(themeListCell());
        combo.setCellFactory(list -> themeListCell());
        combo.setStyle("-fx-background-color: " + BG_CARD + "; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-border-color: " + BORDER + "; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-font-size: 12px; -fx-font-weight: normal; -fx-mark-color: " + TEXT_MUTED + ";");
        combo.valueProperty().addListener((obs, oldMode, newMode) -> {
            if (newMode != null && newMode != themeMode) {
                switchTheme(newMode);
            }
        });
        return combo;
    }

    /** A dropdown row for the theme selector, colored explicitly for readability against BG_CARD. */
    private ListCell<ThemeMode> themeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ThemeMode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item.displayName());
                boolean selected = item == themeMode;
                setStyle("-fx-background-color: " + (selected ? ACCENT : BG_CARD) + "; "
                        + "-fx-text-fill: " + (selected ? ACCENT_TEXT : TEXT_PRIMARY) + "; "
                        + "-fx-font-size: 12px; -fx-font-weight: normal;");
            }
        };
    }

    /** Shows a modal with the application's identity, version, license, and copyright. */
    private void showAboutDialog() {
        Dialog<Void> dialog = new Dialog<>();

        String title   = manifest("Implementation-Title", "JWT Inspector");
        String version = manifest("Implementation-Version", "0.1");
        String license = manifest("Implementation-License", "Apache-2.0");
        String licenseUrl = manifest("Implementation-License-Url",
                "https://www.apache.org/licenses/LICENSE-2.0");
        String copyright = manifest("Implementation-Vendor", "Juti Noppornpitak");

        ImageView icon = new ImageView();
        var iconUrl = getClass().getResource("/app-icon.png");
        if (iconUrl != null) {
            icon.setImage(new Image(iconUrl.toExternalForm()));
        }
        icon.setFitWidth(72);
        icon.setFitHeight(72);
        icon.setPreserveRatio(true);
        icon.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 2);");

        Label nameLbl = new Label(title);
        nameLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");

        Label versionBadge = new Label("v" + version);
        versionBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ACCENT_TEXT + "; "
                + "-fx-background-color: " + ACCENT + "; "
                + "-fx-padding: 2 10; -fx-background-radius: 12;");

        VBox heading = new VBox(8, icon, nameLbl, versionBadge);
        heading.setAlignment(Pos.CENTER);

        Separator sep1 = new Separator();

        Hyperlink licenseLink = new Hyperlink(license);
        licenseLink.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px; -fx-underline: true;");
        licenseLink.setOnAction(e -> openUrl(licenseUrl));

        HBox licenseRow = new HBox(6, mutedText("License"), licenseLink);
        licenseRow.setAlignment(Pos.CENTER);

        Label copyrightLbl = new Label("© " + copyright);
        copyrightLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_MUTED + ";");

        VBox details = new VBox(6, licenseRow, copyrightLbl);
        details.setAlignment(Pos.CENTER);

        VBox content = new VBox(16, heading, sep1, details);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24, 32, 20, 32));
        content.setStyle("-fx-background-color: " + BG_SURFACE + ";");

        dialog.setTitle("About " + title);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.getDialogPane().setStyle("-fx-background-color: " + BG_SURFACE + ";");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE)
                .setStyle(buttonStyle(ACCENT, ACCENT_TEXT));
        dialog.showAndWait();
    }

    /** A small muted caption label, used inline before a value. */
    private Label mutedText(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");
        return lbl;
    }

    /** Opens a URL in the user's default browser, ignoring failures on unsupported platforms. */
    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    /** Reads a manifest attribute, falling back to a default when absent (e.g. under Gradle run). */
    private String manifest(String key, String fallback) {
        try {
            var url = getClass().getResource("/META-INF/MANIFEST.MF");
            if (url == null) return fallback;
            String result = fallback;
            try (var in = url.openStream();
                 var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(key + ":")) {
                        result = line.substring(key.length() + 1).trim();
                        break;
                    }
                }
            }
            return result;
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // ── main content split ────────────────────────────────────────────────────
    private HBox buildContent() {
        VBox left  = buildInputPanel();
        VBox right = buildOutputPanel();

        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox content = new HBox(0, left, right);
        content.setFillHeight(true);
        return content;
    }

    // ── left: input ───────────────────────────────────────────────────────────
    private VBox buildInputPanel() {
        // Token textarea with live colour preview below it
        Label tokenLbl = sectionLabel("JWT Token");

        tokenInput = new TextArea();
        tokenInput.setPromptText("Paste your JWT here…  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");
        tokenInput.setWrapText(true);
        tokenInput.setPrefRowCount(6);
        tokenInput.setStyle(textAreaStyle());
        tokenInput.textProperty().addListener((obs, o, n) -> updatePreview(n));
        VBox.setVgrow(tokenInput, Priority.ALWAYS);

        // Colour-coded preview of the token split by dot
        tokenPreview = new TextFlow();
        tokenPreview.setStyle("-fx-background-color: " + BG_CARD + "; "
                + "-fx-padding: 8; -fx-background-radius: 6;");
        tokenPreview.setPrefHeight(36);

        // Secret + verify row
        Label secretLbl = sectionLabel("Secret (HMAC only — RS/ES/PS use the issuer's JWKS)");

        secretField = new TextField();
        secretField.setPromptText("your-256-bit-secret");
        secretField.setStyle(textFieldStyle());

        verifyCheck = new CheckBox("Verify signature");
        verifyCheck.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px;");
        verifyCheck.setSelected(false);
        verifyCheck.selectedProperty().addListener((obs, o, n) -> secretField.setDisable(!n));
        secretField.setDisable(true);

        // Inspect button
        Button inspectBtn = new Button("Inspect Token");
        inspectBtn.setStyle(buttonStyle(ACCENT, ACCENT_TEXT));
        inspectBtn.setMaxWidth(Double.MAX_VALUE);
        inspectBtn.setOnMouseEntered(e -> inspectBtn.setStyle(buttonStyle(ACCENT_HOVER, ACCENT_TEXT)));
        inspectBtn.setOnMouseExited(e  -> inspectBtn.setStyle(buttonStyle(ACCENT, ACCENT_TEXT)));
        inspectBtn.setOnAction(e -> inspect());

        // Clear button
        Button clearBtn = new Button("Clear");
        clearBtn.setStyle(buttonStyle(BG_CARD, TEXT_PRIMARY));
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> clearAll());

        HBox buttons = new HBox(8, inspectBtn, clearBtn);
        HBox.setHgrow(inspectBtn, Priority.ALWAYS);
        HBox.setHgrow(clearBtn, Priority.SOMETIMES);

        // Status banner
        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");

        // Claims metadata grid
        metaBox = buildMetaBox();
        metaBox.setVisible(false);
        metaBox.setManaged(false);

        VBox panel = new VBox(12,
                tokenLbl, tokenInput, tokenPreview,
                secretLbl, secretField, verifyCheck,
                buttons, statusLabel,
                metaBox
        );
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + BG_DARK + "; "
                + "-fx-border-color: " + BORDER + "; "
                + "-fx-border-width: 0 1 0 0;");
        panel.setPrefWidth(420);

        return panel;
    }

    private VBox buildMetaBox() {
        Label metaLbl = sectionLabel("Claims Info");

        expLabel = metaValueLabel("—");
        iatLabel = metaValueLabel("—");
        nbfLabel = metaValueLabel("—");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setStyle("-fx-background-color: " + BG_CARD + "; -fx-padding: 10; -fx-background-radius: 8;");

        grid.add(metaKeyLabel("Expires (exp)"),  0, 0); grid.add(expLabel, 1, 0);
        grid.add(metaKeyLabel("Issued At (iat)"), 0, 1); grid.add(iatLabel, 1, 1);
        grid.add(metaKeyLabel("Not Before (nbf)"),0, 2); grid.add(nbfLabel, 1, 2);

        return new VBox(6, metaLbl, grid);
    }

    // ── right: output ─────────────────────────────────────────────────────────
    private VBox buildOutputPanel() {
        headerTable    = keyValueTable(TEXT_PRIMARY);
        payloadTable   = keyValueTable(TEXT_SECONDARY);
        signatureTable = keyValueTable(TEXT_MUTED);
        findingTable   = findingTable();

        VBox headerBox    = outputSection("Header",    TEXT_PRIMARY,   headerTable);
        VBox payloadBox    = outputSection("Payload",   TEXT_SECONDARY, payloadTable);
        VBox signatureBox = outputSection("Signature", TEXT_MUTED,     signatureTable);
        analysisBox    = buildAnalysisCard();
        analysisBox.setVisible(false);
        analysisBox.setManaged(false);

          // Let each output card stretch to fill the available vertical space
        VBox.setVgrow(headerBox,    Priority.ALWAYS);
        VBox.setVgrow(payloadBox,   Priority.ALWAYS);
        VBox.setVgrow(signatureBox, Priority.ALWAYS);
        VBox.setVgrow(analysisBox,  Priority.ALWAYS);

        VBox panel = new VBox(12, headerBox, payloadBox, signatureBox, analysisBox);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + BG_DARK + ";");

        return panel;
       }

     /** Builds the Analysis card: type / quality / impersonation summary plus a findings table. */
    private VBox buildAnalysisCard() {
        Label title = new Label("Analysis");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + ACCENT + ";");

        typeLabel = new Label("—");
        qualityLabel = new Label("—");
        impersonationLabel = new Label("—");
        for (var lbl : new Label[]{typeLabel, qualityLabel, impersonationLabel}) {
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_PRIMARY + ";");
         }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        grid.setStyle("-fx-background-color: " + BG_CARD + "; -fx-padding: 10; -fx-background-radius: 8;");
        grid.add(new Label("Type"), 0, 0);            grid.add(typeLabel, 1, 0);
        grid.add(new Label("Quality"), 0, 1);          grid.add(qualityLabel, 1, 1);
        grid.add(new Label("Impersonation"), 0, 2);    grid.add(impersonationLabel, 1, 2);

        Label findingsLbl = new Label("Findings");
        findingsLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MUTED + ";");

        VBox box = new VBox(6, title, grid, findingsLbl, findingTable);
        VBox.setVgrow(findingTable, Priority.ALWAYS);
        box.setStyle("-fx-background-color: " + BG_CARD + "; "
                  + "-fx-padding: 12; -fx-background-radius: 10; "
                  + "-fx-border-color: " + BORDER + "; -fx-border-width: 1; -fx-border-radius: 10;");
        return box;
     }

     /** Builds a findings table with a severity badge in the first column. */
    private TableView<Field> findingTable() {
        TableView<Field> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setTableMenuButtonVisible(false);
        table.setStyle("-fx-accent: " + ACCENT + ";");

        TableColumn<Field, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().key()));
        sevCol.setPrefWidth(90);
        sevCol.setCellFactory(col -> new TableCell<>() {
               @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                  }
                setText(severityBadge(value));
                setStyle("-fx-text-fill: " + severityColor(value) + "; -fx-font-weight: bold;");
               }
           });

        TableColumn<Field, String> detailCol = new TableColumn<>("Finding");
        detailCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().value()));
        detailCol.setCellFactory(col -> new TableCell<>() {
               @Override
             protected void updateItem(String value, boolean empty) {
                 super.updateItem(value, empty);
                 setText(empty ? null : value);
                 setWrapText(!empty);
                 setMaxWidth(Double.MAX_VALUE);
                 setPrefHeight(Region.USE_COMPUTED_SIZE);
               }
           });

        table.getColumns().add(sevCol);
        table.getColumns().add(detailCol);
        return table;
     }

    /** Builds a Key/Value table using the system font, with a muted key column. */
    private TableView<Field> keyValueTable(String accentColor) {
        TableView<Field> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setTableMenuButtonVisible(false);
        table.setStyle("-fx-accent: " + accentColor + ";");

        TableColumn<Field, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().key()));
        keyCol.setPrefWidth(150);
        keyCol.setMaxWidth(240);
        keyCol.setCellFactory(col -> new TableCell<>() {
             @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setStyle("-fx-text-fill: " + TEXT_MUTED + ";");
              }
          });

        TableColumn<Field, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().value()));

         // Wrap long values instead of truncating them
        valueCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setWrapText(!empty);
                setMaxWidth(Double.MAX_VALUE);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
             }
         });

        table.getColumns().add(keyCol);
        table.getColumns().add(valueCol);
        return table;
      }

    private VBox outputSection(String title, String color, TableView<Field> table) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-background-color: " + BG_CARD + "; "
                 + "-fx-text-fill: " + TEXT_MUTED + "; "
                 + "-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(tableText(table));
            Clipboard.getSystemClipboard().setContent(cc);
            copyBtn.setText("Copied!");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> copyBtn.setText("Copy"));
              }).start();
          });

        HBox header = new HBox(6, lbl, copyBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(lbl, Priority.ALWAYS);

        VBox box = new VBox(6, header, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setStyle("-fx-background-color: " + BG_CARD + "; "
                 + "-fx-padding: 12; -fx-background-radius: 10; "
                 + "-fx-border-color: " + BORDER + "; -fx-border-width: 1; -fx-border-radius: 10;");
        return box;
       }

      // ── table data helpers ────────────────────────────────────────────────────

      /** Serialises a table's rows back to "key\tvalue" lines for the Copy button. */
    private String tableText(TableView<Field> table) {
        StringBuilder sb = new StringBuilder();
        for (Field f : table.getItems()) {
            sb.append(f.key()).append("\t").append(f.value()).append('\n');
         }
        return sb.toString().stripTrailing();
      }

      /** Flattens a decoded JSON object into a list of Key/Value table rows. */
    private List<Field> toFieldRows(String json) {
        List<Field> rows = new ArrayList<>();
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            flatten("", parsed, rows);
         } catch (Exception e) {
            rows.add(new Field("error", "Could not parse: " + e.getMessage()));
         }
        return rows;
      }

    /**
     * Recursively flattens a parsed JSON value into Key/Value rows.
     * Objects and arrays are expanded; scalars are rendered with a hint of their type.
     */
    private void flatten(String prefix, Object node, List<Field> out) {
        if (node instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.add(new Field(prefix, "{}"));
                return;
             }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String child = prefix.isEmpty()
                        ? humanize(String.valueOf(e.getKey()))
                        : prefix + "." + humanize(String.valueOf(e.getKey()));
                flatten(child, e.getValue(), out);
             }
        } else if (node instanceof List<?> list) {
            if (list.isEmpty()) {
                out.add(new Field(prefix, "[]"));
                return;
             }
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "]", list.get(i), out);
             }
        } else {
            out.add(new Field(prefix, renderScalar(node)));
         }
      }

    private String humanize(String key) {
        String camel = SNAKE.matcher(key).replaceAll("$1 $2");
        return camel.substring(0, 1).toUpperCase(Locale.ROOT)
                + camel.substring(1).toLowerCase(Locale.ROOT);
      }

    /** Formats a JSON scalar for display: numbers, booleans, null, strings. */
    private String renderScalar(Object node) {
        if (node == null) {
            return "null";
         }
        if (node instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                long l = n.longValue();
                return (n.doubleValue() == (double) l) ? Long.toString(l) : n.toString();
             }
            return n.toString();
         }
        if (node instanceof Boolean b) {
            return b ? "true" : "false";
         }
        return node.toString();
      }

    /** Builds the signature table: the signing algorithm plus the raw signature. */
    private List<Field> signatureRows(String signature) {
        List<Field> rows = new ArrayList<>();
        rows.add(new Field("Algorithm", "HMAC-SHA256 (inspect)"));
        rows.add(new Field("Signature", signature == null || signature.isBlank() ? "—" : signature));
        return rows;
       }

      /** Turns an Analysis into finding rows (severity badge + "title — detail"). */
    private List<Field> toFindingRows(JwtInspector.Analysis analysis) {
        List<Field> rows = new ArrayList<>();
        if (analysis == null) return rows;
        for (JwtInspector.Finding f : analysis.findings()) {
            String detail = f.title() != null && f.detail() != null
                     ? f.title() + " — " + f.detail()
                     : (f.detail() != null ? f.detail() : f.title());
            rows.add(new Field(f.severity(), detail));
         }
        return rows;
      }

      private String severityBadge(String severity) {
        return switch (severity == null ? "" : severity.toLowerCase()) {
            case "error"   -> "● error";
            case "warning" -> "▲ warning";
            case "info", "ok" -> "● info";
            default        -> severity;
         };
       }

      private String severityColor(String severity) {
        return switch (severity == null ? "" : severity.toLowerCase()) {
            case "error"   -> TEXT_PRIMARY;
            case "warning" -> TEXT_SECONDARY;
            case "info", "ok" -> TEXT_MUTED;
            default        -> TEXT_MUTED;
         };
       }
    private void inspect() {
        String token = tokenInput.getText().trim();
        if (token.isEmpty()) {
            setStatus("Please paste a JWT token first.", TEXT_PRIMARY);
            return;
          }

        try {
            JwtInspector.InspectionResult result;

            if (verifyCheck.isSelected()) {
                String secret = secretField.getText();
                result = JwtInspector.decodeAndVerify(token, secret == null ? "" : secret);
              } else {
                result = JwtInspector.decode(token);
              }

            headerTable.getItems().setAll(toFieldRows(result.headerJson()));
            payloadTable.getItems().setAll(toFieldRows(result.payloadJson()));
            signatureTable.getItems().setAll(signatureRows(result.signature()));

            expLabel.setText(result.expiresAt());
            iatLabel.setText(result.issuedAt());
            nbfLabel.setText(result.notBefore());

            var analysis = result.analysis();
            updateAnalysis(analysis);

            revealResults();

             // Status
            String statusMsg = result.verificationMessage();
            if (result.isExpired()) {
                statusMsg += "   ⚠ Token is EXPIRED";
                setStatus(statusMsg, TEXT_PRIMARY);
            } else if (result.signatureVerified()) {
                setStatus(statusMsg, TEXT_PRIMARY);
            } else {
                setStatus(statusMsg, TEXT_MUTED);
            }

          } catch (IllegalArgumentException ex) {
             setStatus("Error: " + ex.getMessage(), TEXT_PRIMARY);
             headerTable.getItems().clear();
             payloadTable.getItems().clear();
             signatureTable.getItems().clear();
             expLabel.setText("—");
             iatLabel.setText("—");
             nbfLabel.setText("—");
             resetAnalysis();
             hideResults();
             }
        }

    private void clearAll() {
        tokenInput.clear();
        secretField.clear();
        headerTable.getItems().clear();
        payloadTable.getItems().clear();
        signatureTable.getItems().clear();
        findingTable.getItems().clear();
        nbfLabel.setText("—");
        statusLabel.setText("");
        tokenPreview.getChildren().clear();
        resetAnalysis();
        hideResults();
        }

      /** Reveals the claims and analysis cards after a successful inspection. */
     private void revealResults() {
         metaBox.setVisible(true);
         metaBox.setManaged(true);
         analysisBox.setVisible(true);
         analysisBox.setManaged(true);
        }

      /** Hides the claims and analysis cards so results stay out of view until inspected. */
     private void hideResults() {
         metaBox.setVisible(false);
         metaBox.setManaged(false);
         analysisBox.setVisible(false);
         analysisBox.setManaged(false);
        }

    /** Colour-codes the token in three segments separated by dots. */
    private void updatePreview(String token) {
        tokenPreview.getChildren().clear();
        if (token == null || token.isBlank()) return;

        String[] colors = { TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED };
        String[] parts  = token.trim().split("\\.", -1);

        for (int i = 0; i < parts.length && i < 3; i++) {
            if (i > 0) {
                Text dot = new Text(".");
                dot.setFill(Color.web(TEXT_MUTED));
                tokenPreview.getChildren().add(dot);
            }
            Text segment = new Text(parts[i]);
            segment.setFill(Color.web(colors[i]));
            segment.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 11px;");
            tokenPreview.getChildren().add(segment);
        }
    }

     private void setStatus(String message, String color) {
        statusLabel.setText(message);
        boolean emphasize = !color.equals(TEXT_MUTED);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";"
                + (emphasize ? " -fx-font-weight: bold;" : ""));
      }

      /** Populates the Analysis card (type / quality / impersonation + findings) from an Analysis. */
    private void updateAnalysis(JwtInspector.Analysis analysis) {
        if (analysis == null) {
            resetAnalysis();
            return;
         }
        typeLabel.setText(analysis.tokenType());
        qualityLabel.setText(analysis.quality());
        impersonationLabel.setText(analysis.hasImpersonation() ? "yes" : "no");
        impersonationLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: "
                 + (analysis.hasImpersonation() ? "bold" : "normal") + "; "
                 + "-fx-text-fill: " + (analysis.hasImpersonation() ? TEXT_PRIMARY : TEXT_MUTED) + ";");
        findingTable.getItems().setAll(toFindingRows(analysis));
      }

      private void resetAnalysis() {
        typeLabel.setText("—");
        qualityLabel.setText("—");
        impersonationLabel.setText("—");
        findingTable.getItems().clear();
      }

    // ── style helpers ─────────────────────────────────────────────────────────
    private Label sectionLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; "
                + "-fx-text-fill: " + TEXT_MUTED + "; -fx-text-transform: uppercase;");
        return lbl;
    }

    private Label metaKeyLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");
        return lbl;
    }

    private Label metaValueLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_PRIMARY + ";");
        return lbl;
    }


    private String textAreaStyle() {
        return "-fx-control-inner-background: " + BG_CARD + "; "
                + "-fx-background-color: transparent; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-prompt-text-fill: " + TEXT_MUTED + "; "
                + "-fx-font-family: 'Menlo', 'Consolas', monospace; "
                + "-fx-font-size: 12px; "
                + "-fx-border-color: " + BORDER + "; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6; "
                + "-fx-focus-color: " + ACCENT + "; "
                + "-fx-faint-focus-color: transparent;";
    }

    private String textFieldStyle() {
        return "-fx-control-inner-background: " + BG_CARD + "; "
                + "-fx-background-color: transparent; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-prompt-text-fill: " + TEXT_MUTED + "; "
                + "-fx-font-size: 12px; "
                + "-fx-border-color: " + BORDER + "; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6; "
                + "-fx-focus-color: " + ACCENT + "; "
                + "-fx-faint-focus-color: transparent; "
                + "-fx-padding: 6 10;";
    }

    private String buttonStyle(String bg, String textFill) {
        return "-fx-background-color: " + bg + "; "
                + "-fx-text-fill: " + textFill + "; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; "
                + "-fx-padding: 8 16; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
