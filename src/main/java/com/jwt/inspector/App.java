package com.jwt.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class App extends Application {

    // ── colour constants (dark theme) ────────────────────────────────────────
    private static final String BG_DARK       = "#1e1e2e";
    private static final String BG_SURFACE    = "#2a2a3e";
    private static final String BG_CARD       = "#313149";
    private static final String ACCENT        = "#7c6af7";
    private static final String ACCENT_HOVER  = "#9d8fff";
    private static final String SUCCESS       = "#4ec9b0";
    private static final String ERROR         = "#f28779";
    private static final String TEXT_PRIMARY  = "#cdd6f4";
    private static final String TEXT_MUTED    = "#7f849c";
    private static final String BORDER        = "#45475a";

    // JWT segment colours
    private static final String COLOR_HEADER    = "#61afef";
    private static final String COLOR_PAYLOAD   = "#e5c07b";
    private static final String COLOR_SIGNATURE = "#98c379";

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

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        root.setTop(buildHeader());
        root.setCenter(buildContent());

        Scene scene = new Scene(root, 1000, 760);
        scene.getStylesheets().add(
                getClass().getResource("/jwt-inspector.css").toExternalForm());
        stage.setTitle("JWT Inspector");
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(580);
        stage.show();
    }

    // ── top header bar ────────────────────────────────────────────────────────
    private HBox buildHeader() {
        Label logo = new Label("🔍 JWT Inspector");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");

        Label sub = new Label("Decode · Inspect · Verify");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");

        VBox titles = new VBox(2, logo, sub);
        titles.setAlignment(Pos.CENTER_LEFT);

        Button aboutBtn = new Button("About");
        aboutBtn.setStyle("-fx-background-color: " + BG_CARD + "; "
                 + "-fx-text-fill: " + TEXT_MUTED + "; "
                 + "-fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 6; -fx-cursor: hand;");
        aboutBtn.setOnAction(e -> showAboutDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(titles, spacer, aboutBtn);
        header.setPadding(new Insets(18, 24, 18, 24));
        header.setStyle("-fx-background-color: " + BG_SURFACE + "; "
                 + "-fx-border-color: " + BORDER + "; "
                 + "-fx-border-width: 0 0 1 0;");
        return header;
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

        VBox content = new VBox(8,
                label("Name", title),
                label("Version", version),
                label("License", license + "  (" + licenseUrl + ")"),
                label("Copyright", "© " + copyright)
        );
        content.setPadding(new Insets(16, 20, 16, 20));
        content.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace;");

        dialog.setTitle("About " + title);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.getDialogPane().setStyle(dialogPaneStyle());
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE)
                .setStyle(buttonStyle(ACCENT));
        dialog.showAndWait();
    }

    /** A labelled "key  value" row for the About modal. */
    private Text label(String key, String value) {
        Text text = new Text(key + "  :  " + value);
        text.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; "
                 + "-fx-font-size: 12px; -fx-fill: " + TEXT_PRIMARY + ";");
        return text;
    }

    private String dialogPaneStyle() {
        return "-fx-background-color: " + BG_SURFACE + ";";
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
        Label secretLbl = sectionLabel("HMAC Secret (optional)");

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
        inspectBtn.setStyle(buttonStyle(ACCENT));
        inspectBtn.setMaxWidth(Double.MAX_VALUE);
        inspectBtn.setOnMouseEntered(e -> inspectBtn.setStyle(buttonStyle(ACCENT_HOVER)));
        inspectBtn.setOnMouseExited(e  -> inspectBtn.setStyle(buttonStyle(ACCENT)));
        inspectBtn.setOnAction(e -> inspect());

        // Clear button
        Button clearBtn = new Button("Clear");
        clearBtn.setStyle(buttonStyle(BG_CARD));
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
        VBox metaBox = buildMetaBox();

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
        headerTable    = keyValueTable(COLOR_HEADER);
        payloadTable   = keyValueTable(COLOR_PAYLOAD);
        signatureTable = keyValueTable(COLOR_SIGNATURE);
        findingTable   = findingTable();

        VBox headerBox    = outputSection("Header",    COLOR_HEADER,    headerTable);
        VBox payloadBox    = outputSection("Payload",   COLOR_PAYLOAD,   payloadTable);
        VBox signatureBox = outputSection("Signature", COLOR_SIGNATURE, signatureTable);
        VBox analysisBox   = buildAnalysisCard();

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
            lbl.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: " + TEXT_PRIMARY + ";");
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
        sevCol.setCellValueFactory(new PropertyValueFactory<>("key"));
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
        detailCol.setCellValueFactory(new PropertyValueFactory<>("value"));
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

    /** Builds a Key/Value table with a monospace body and a coloured header. */
    private TableView<Field> keyValueTable(String accentColor) {
        TableView<Field> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setTableMenuButtonVisible(false);
        table.setStyle("-fx-accent: " + accentColor + ";");

        TableColumn<Field, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(new PropertyValueFactory<>("key"));
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
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

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
            case "error"   -> ERROR;
            case "warning" -> "#e5c07b";
            case "info", "ok" -> SUCCESS;
            default        -> TEXT_MUTED;
         };
       }
    private void inspect() {
        String token = tokenInput.getText().trim();
        if (token.isEmpty()) {
            setStatus("Please paste a JWT token first.", ERROR);
            return;
          }

        try {
            JwtInspector.InspectionResult result;

            if (verifyCheck.isSelected() && !secretField.getText().isBlank()) {
                result = JwtInspector.decodeAndVerify(token, secretField.getText());
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

             // Status
            String statusMsg = result.verificationMessage();
            if (result.isExpired()) {
                statusMsg += "   ⚠ Token is EXPIRED";
                setStatus(statusMsg, ERROR);
            } else if (result.signatureVerified()) {
                setStatus(statusMsg, SUCCESS);
            } else {
                setStatus(statusMsg, TEXT_MUTED);
            }

          } catch (IllegalArgumentException ex) {
             setStatus("Error: " + ex.getMessage(), ERROR);
             headerTable.getItems().clear();
             payloadTable.getItems().clear();
             signatureTable.getItems().clear();
             expLabel.setText("—");
             iatLabel.setText("—");
             nbfLabel.setText("—");
             resetAnalysis();
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
       }

    /** Colour-codes the token in three segments separated by dots. */
    private void updatePreview(String token) {
        tokenPreview.getChildren().clear();
        if (token == null || token.isBlank()) return;

        String[] colors = { COLOR_HEADER, COLOR_PAYLOAD, COLOR_SIGNATURE };
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
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";");
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
        impersonationLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; "
                 + "-fx-text-fill: " + (analysis.hasImpersonation() ? "#e5c07b" : SUCCESS) + ";");
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
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-family: monospace;");
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
                + "-fx-font-family: 'Menlo', 'Consolas', monospace; "
                + "-fx-font-size: 12px; "
                + "-fx-border-color: " + BORDER + "; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6; "
                + "-fx-focus-color: " + ACCENT + "; "
                + "-fx-faint-focus-color: transparent; "
                + "-fx-padding: 6 10;";
    }

    private String buttonStyle(String bg) {
        return "-fx-background-color: " + bg + "; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; "
                + "-fx-padding: 8 16; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
