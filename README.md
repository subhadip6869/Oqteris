# Oqteris

<div align="center">
  <img src="src/main/resources/icons/oqteris-logo.png" alt="Oqteris Logo" width="160"/>
  <h3>Dedicated Cross-Platform Desktop PDF Reader and Editor</h3>
  <p><strong>Java 21 LTS &bull; JavaFX 21 &bull; Apache PDFBox 3.0.5 &bull; Bouncy Castle 1.86 &bull; OriginSD Architecture</strong></p>
</div>

---

## 1. Project & Publisher Identity

Oqteris adheres to the **OriginSD Desktop Application Master Architecture Standard**:

- **Publisher / Organization:** OriginSD
- **Developer:** Subhadip Dutta
- **Location:** Bankura, West Bengal, India
- **Official Website:** [https://originsd.org](https://originsd.org)
- **GitHub Organization / Profile:** [https://github.com/subhadip6869](https://github.com/subhadip6869)
- **GitHub Repository:** [https://github.com/subhadip6869/Oqteris.git](https://github.com/subhadip6869/Oqteris.git)
- **Authoritative Maven Group ID:** `org.originsd`
- **Application Package Namespace:** `org.originsd.oqteris`

---

## 2. OS-Specific Storage & User Directory Management

All writable runtime data, user preferences, crash recovery files, and security trust stores are kept strictly out of the installation directory and stored in standard operating-system user directories:

- **Windows:** `%LOCALAPPDATA%\OriginSD\Oqteris\` (e.g. `C:\Users\<user>\AppData\Local\OriginSD\Oqteris\`)
- **macOS:** `~/Library/Application Support/OriginSD/Oqteris/`
- **Linux:** `~/.local/share/originsd/oqteris/` (respects `$XDG_DATA_HOME`)

### Persistent Files
- `settings.json`: User-configurable preferences managed with Jackson (`UserSettingsService`).
- `recovery/`: Automated periodic document recovery backups (`AutosaveService`).
- `truststore.p12`: PKCS#12 application certificate trust store (`TrustStoreManager`).
- `recent_files.dat`: Persisted history of recently opened documents.

---

## 3. Master Design System & Theme Engine

Oqteris implements the uniform **OriginSD Master Design System & Theme Engine**:

- **Active Palettes (`src/main/resources/styles/`):**
  - `light-theme.css`: High-contrast light palette tokens.
  - `dark-theme.css`: Executive dark palette tokens (7.5:1+ contrast).
  - `oqteris-theme.css`: The master stylesheet referencing color tokens for all components and controls.
- **Dynamic Switching:** Real-time toggling between **System**, **Light**, and **Dark** modes without restarting.
- **Typography Adjustment:** Dynamic font size scaling (`-fx-font-size: <baseSize>px`) from `-4px` to `+12px` delta.
- **Accessible Controls & High-DPI Icons:** Form controls, tables, dialogs, and Ikonli vector icons automatically adapt to the active theme palette.

---

## 4. Technology Stack

| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Runtime** | Java LTS | 21+ | Core runtime, Virtual Threads, Records, Pattern matching |
| **UI Framework** | JavaFX | 21.0.6 | Hardware-accelerated desktop UI shell, Canvas rendering |
| **PDF Engine** | Apache PDFBox | 3.0.5 | PDF document structure, font extraction, page rasterization |
| **Cryptography** | Bouncy Castle | 1.86 | CMS / PKCS#7 signature verification, X.509 certificate path validation |
| **JSON Serialization** | Jackson Databind | 2.18.2 | Persistent settings serialization to `settings.json` |
| **UI Components** | ControlsFX & FXRibbon | 11.2.1 / 1.2.2 | Clearable text fields, ribbon controls |
| **Vector Icons** | Ikonli (Material Design & Fluent) | 12.3.1 | High-DPI resolution-independent vector icons |
| **Build & Packaging** | Maven Shade & jpackage | 3.6.0 | Standalone shaded executable JAR and native platform installers |
| **Test Runner** | JUnit Jupiter | 5.11.4 | Automated unit, regression, and acceptance testing |

---

## 5. Key Features

- **Professional Desktop Ribbon**: Modern Office-style ribbon navigation with dynamic content-driven height, grouped tabs (`Home`, `View`, `Edit`, `Comment`, `Organize`, `Fill & Sign`, `Protect`, `Share`, `Help`), and contextual `Signatures` tab.
- **High-Fidelity PDF Viewing**: Continuous vertical page rendering, smooth scrolling, thumbnails drawer, bookmarks tree, and responsive zoom (25%–400%).
- **CMS / PKCS#7 Digital Signatures**: Complete byte-range verification, tamper detection, and live visual stamp rewriting based on cryptographic validity.
- **Trust Store & Certificate Manager**: In-app X.509 certificate management with trust level assignments (Trusted Root, Trusted Signer, Blacklisted).
- **Single-Instance Lifecycle**: Zero-network file-based IPC queue routing newly opened files to the active window.
- **Default PDF Viewer Integration**: One-click registration as system default viewer on Windows, Linux, and macOS.

---

## 6. Quick Start & Development

### Prerequisites
- **Java Development Kit (JDK)**: OpenJDK 21 LTS or newer on `PATH`.
- **Apache Maven**: Version 3.9+ on `PATH`.

### Build & Run Tests
```bash
mvn clean test
```

### Run in Development Mode
```bash
mvn javafx:run
```

### Build Standalone Executable Fat JAR
```bash
mvn clean package -DskipTests
```
The shaded executable JAR will be located at:
```bash
target/oqteris-1.0.0-SNAPSHOT-all.jar
```

Run directly:
```bash
java -jar target/oqteris-1.0.0-SNAPSHOT-all.jar [optional-pdf-path]
```

---

## 7. Native Distribution Packaging (`scripts/`)

Production-ready packaging scripts bundle a stripped minimal JRE runtime (~35–45 MB) via `jlink` (`java.base,java.compiler,java.desktop,java.instrument,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.xml,jdk.crypto.ec,jdk.localedata,jdk.unsupported`) and produce native OS installers with zero host JRE dependency:

### Windows Native Installer (`.exe`)
Produces strictly an `.exe` installer using WiX Toolset and `jpackage --type exe`:
```powershell
# Using PowerShell
.\scripts\package-windows.ps1

# Or using CMD
.\scripts\package-windows.bat
```
Output: `dist\Oqteris-1.0.0.exe`

### Linux Native Packages (`.deb` & `.rpm`)
Produces native Debian and RPM packages with desktop shortcuts and MIME associations:
```bash
chmod +x scripts/package-linux.sh
./scripts/package-linux.sh
```
Output: `dist/Oqteris-1.0.0.deb` and `dist/Oqteris-1.0.0.rpm`

### macOS Native Installer (`.dmg`)
Produces a `.dmg` installer compatible with Apple Silicon and Intel:
```bash
chmod +x scripts/package-mac.sh
./scripts/package-mac.sh
```
Output: `dist/Oqteris-1.0.0.dmg`

---

## 8. Sample Documents

Sample documents for verification are located in `samples/`:
- `samples/sample_signed.pdf`: Digitally signed PDF with embedded RSA PKCS#7 signature.
- `samples/oqteris_demo_signer.cer`: Exported X.509 certificate for testing trust store import and validity transitions.

---

## 9. Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)**: System design, rendering pipeline, digital signature verification, and platform abstraction.
- **[BUILD.md](BUILD.md)**: Build instructions, native OS packaging, and file associations.
- **[FORMAT_SUPPORT.md](FORMAT_SUPPORT.md)**: ISO 32000-1 / ISO 32000-2 compliance and signature standards.
- **[SECURITY.md](SECURITY.md)**: Cryptographic signature policies and trust anchor management.

---

## 10. License & Publisher

- **Publisher:** OriginSD
- **Developer:** Subhadip Dutta &bull; Bankura, West Bengal, India
- **License:** Apache License, Version 2.0
- **Official Website:** [https://originsd.org](https://originsd.org)
- **Copyright:** &copy; 2026 OriginSD. All rights reserved.
