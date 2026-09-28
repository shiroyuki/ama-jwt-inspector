# JWT Inspector

A desktop application to decode and inspect JWT tokens — inspired by [jwt.io](https://jwt.io).
Built with Java 25, JavaFX 24, and JJWT 0.12.

## Features

- **Decode any JWT** without needing a secret — paste and inspect instantly
- **Colour-coded token preview** — header (blue) · payload (yellow) · signature (green)
- **Pretty-printed JSON** for the header and payload sections
- **Claims metadata** — `exp`, `iat`, and `nbf` shown as human-readable timestamps
- **Optional HMAC signature verification** — provide a secret to validate the signature
- **Expired token detection** — clearly flagged in the status bar
- **Copy buttons** for each decoded section
- Dark theme, responsive layout

## Requirements

| Dependency | Version |
|------------|---------|
| JDK        | 25+     |
| Gradle     | 9.x (or use the wrapper once added) |

No other installation is required — Gradle fetches all library dependencies automatically.

## Dev Setup

1. **Clone the repository**

   ```bash
   git clone <repo-url>
   cd ama-jwt-inspector
   ```

2. **Verify your Java version**

   ```bash
   java -version
   # Expected: openjdk version "25.x.x" ...
   ```

3. **Verify Gradle is available**

   ```bash
   gradle --version
   # Expected: Gradle 9.x
   ```

   > If you don't have Gradle installed, install it via [sdkman](https://sdkman.io/):
   > ```bash
   > sdk install gradle
   > ```

4. **Resolve dependencies** (optional — happens automatically on first build)

   ```bash
   gradle dependencies
   ```

## Compile

Compile the Java source only, without packaging:

```bash
gradle compileJava
```

Class files are written to `build/classes/java/main/`.

## Build

Package the project into a JAR and a runnable distribution:

```bash
gradle build
```

Artifacts produced in `build/`:

```
build/
├── libs/
│   └── jwt-inspector-1.0-SNAPSHOT.jar   ← application JAR
└── distributions/
    ├── jwt-inspector-1.0-SNAPSHOT.zip   ← self-contained zip
    └── jwt-inspector-1.0-SNAPSHOT.tar   ← self-contained tar
```

To clean previous build output before rebuilding:

```bash
gradle clean build
```

## Run

### Via Gradle (recommended during development)

```bash
gradle run
```

### Via the distribution archive

Unzip and use the generated launch script:

```bash
unzip build/distributions/jwt-inspector-1.0-SNAPSHOT.zip -d build/distributions/
./build/distributions/jwt-inspector-1.0-SNAPSHOT/bin/jwt-inspector
```

## Usage

1. Paste a JWT token into the **JWT Token** text area.
2. The token is colour-coded live as you type — blue (header), yellow (payload), green (signature).
3. Click **Inspect Token** to decode and display the header, payload, and signature.
4. Review **Claims Info** for expiry (`exp`), issued-at (`iat`), and not-before (`nbf`) timestamps.
5. To verify the signature, check **Verify signature**, enter your HMAC secret, and click **Inspect Token** again.

## Project Structure

```
src/main/java/com/jwt/inspector/
├── App.java           ← JavaFX application entry point and UI
└── JwtInspector.java  ← JWT decode / verify logic
build.gradle           ← Gradle build configuration
settings.gradle        ← Project name
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [JavaFX](https://openjfx.io) | 24.0.1 | Desktop UI framework |
| [JJWT](https://github.com/jwtk/jjwt) | 0.12.7 | JWT parsing and verification |
| [Jackson Databind](https://github.com/FasterXML/jackson-databind) | 2.18.3 | JSON pretty-printing |
