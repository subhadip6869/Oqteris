# Oqteris Build, Packaging & Verification Guide

This guide describes how to build, run, package, and verify Oqteris as a dedicated cross-platform desktop PDF reader and editor.

---

## 1. System Requirements

### Hardware Requirements
- **Processor**: 64-bit Dual-Core x86_64 or ARM64 processor (Quad-core recommended).
- **RAM**: Minimum 2 GB (4 GB recommended).
- **Disk Space**: 500 MB free disk space for build artifacts and dependencies.

### Software Prerequisites
- **Java Development Kit (JDK)**: OpenJDK 21 LTS or newer (Eclipse Temurin, Oracle OpenJDK, Amazon Corretto, or Microsoft Build of OpenJDK).
- **Apache Maven**: Version 3.9.0 or higher.
- **Operating Systems Supported**:
  - Windows: Windows 10, Windows 11, Windows Server 2019/2022/2025 (x86_64).
  - Linux: Ubuntu 20.04+, Debian 11+, Fedora 38+, RHEL 8/9, Arch Linux.
  - macOS: macOS 12 (Monterey), macOS 13 (Ventura), macOS 14 (Sonoma), macOS 15 (Sequoia) (Apple Silicon & Intel).

---

## 2. Compiling & Building the Project

### Clone the Repository
```bash
git clone https://github.com/subhadip6869/Oqteris.git
cd Oqteris
```

### Compile Codebase
```bash
mvn clean compile
```

### Run Automated Test Suite
To execute all unit, integration, and platform abstraction tests:
```bash
mvn test
```

Expected output:
```
[INFO] Running org.originsd.oqteris.crypto.CertificateTrustManagerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.crypto.PdfSignatureVerifierTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.pdf.PasswordProtectedPdfTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.platform.DefaultViewerServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.platform.PdfFileOpenHelperTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.platform.SingleInstanceManagerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.samples.SampleFilesVerificationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.ui.UiLibraryTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 3. Running Oqteris

### Development Mode
Launch Oqteris directly via the JavaFX Maven plugin:
```bash
mvn javafx:run
```

### Running with a Command-Line File Argument
To launch Oqteris and immediately open a PDF document:
```bash
mvn javafx:run -Djavafx.args="samples/sample_signed.pdf"
```

### Packaging & Running the Standalone Executable JAR
Build the standalone executable JAR:
```bash
mvn clean package -DskipTests
```

Run using the Java launcher:
```bash
java -jar target/oqteris-1.0.0-SNAPSHOT.jar
```

Or pass a PDF file path directly:
```bash
java -jar target/oqteris-1.0.0-SNAPSHOT.jar "samples/sample_signed.pdf"
```

---

## 4. Native OS Packaging & Platform Integration

### Staging Dependencies for Native Packaging
`jpackage` packages everything located inside a designated staging directory. Prepare the staging directory (`target/app-input`):

#### PowerShell (Windows):
```powershell
mvn clean package -DskipTests
mvn dependency:copy-dependencies -DoutputDirectory=target/app-input
Copy-Item target\oqteris-1.0.0-SNAPSHOT.jar target\app-input\
```

#### Bash (Linux / macOS):
```bash
mvn clean package -DskipTests
mvn dependency:copy-dependencies -DoutputDirectory=target/app-input
cp target/oqteris-1.0.0-SNAPSHOT.jar target/app-input/
```

---

### 4.1 Windows Integration & Packaging

#### Application & Document Icons
- **Application Icon**: `src/main/resources/icons/oqteris-logo.ico` (multi-resolution ICO for the executable and installer).
- **PDF Document Icon**: `src/main/resources/icons/oqteris-document.ico` (file association icon).

#### Portable App Image (No external tools required)
```powershell
jpackage `
  --type app-image `
  --name "Oqteris" `
  --app-version "1.0.0" `
  --vendor "Oqteris Project" `
  --description "Dedicated PDF Reader and Editor" `
  --icon "src/main/resources/icons/oqteris-logo.ico" `
  --input target/app-input `
  --main-jar oqteris-1.0.0-SNAPSHOT.jar `
  --main-class org.originsd.oqteris.ui.OqterisLauncher `
  --dest target/dist
```

#### Windows Installer (EXE / MSI)
*Requires WiX Toolset 3.11+ installed and on PATH.*
```powershell
jpackage `
  --type exe `
  --name "Oqteris" `
  --app-version "1.0.0" `
  --vendor "Oqteris Project" `
  --description "Dedicated PDF Reader and Editor" `
  --icon "src/main/resources/icons/oqteris-logo.ico" `
  --input target/app-input `
  --main-jar oqteris-1.0.0-SNAPSHOT.jar `
  --main-class org.originsd.oqteris.ui.OqterisLauncher `
  --file-associations packaging/windows/pdf-association.properties `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --dest target/installer
```

#### File Association Properties (`packaging/windows/pdf-association.properties`):
```properties
extension=pdf
mime-type=application/pdf
description=PDF Document
icon=src/main/resources/icons/oqteris-document.ico
```

#### In-App Default PDF Viewer Registration on Windows
Oqteris provides automated file association registration via `WindowsDefaultViewerService`:
1. Registers the ProgID `Oqteris.PDF` in `HKCU\Software\Classes\Oqteris.PDF`.
2. Sets the DefaultIcon to `oqteris-document.ico`.
3. Sets `shell\open\command` to the Oqteris launcher passing `"%1"`.
4. Registers `Oqteris.PDF` in `HKCU\Software\Classes\.pdf\OpenWithProgids`.
5. Declares application capabilities in `HKCU\Software\Oqteris\Capabilities` and registers with `HKCU\Software\RegisteredApplications`.
6. On Windows 10 and 11, Windows policy protects the `UserChoice` registry key to prevent silent hijacking. Oqteris automatically launches `ms-settings:defaultapps` and guides the user to confirm Oqteris as their default PDF viewer.

---

### 4.2 Linux Integration & Packaging

#### Debian / Ubuntu Package (.deb)
```bash
jpackage \
  --type deb \
  --name "oqteris" \
  --app-version "1.0.0" \
  --vendor "Oqteris Project" \
  --description "Dedicated PDF Reader and Editor" \
  --icon "src/main/resources/icons/oqteris-logo.png" \
  --input target/app-input \
  --main-jar oqteris-1.0.0-SNAPSHOT.jar \
  --main-class org.originsd.oqteris.ui.OqterisLauncher \
  --linux-shortcut \
  --linux-menu-group "Office;Viewer;Graphics;" \
  --dest target/installer
```

Install the package:
```bash
sudo dpkg -i target/installer/oqteris_1.0.0-1_amd64.deb
```

#### Fedora / RHEL Package (.rpm)
```bash
jpackage \
  --type rpm \
  --name "oqteris" \
  --app-version "1.0.0" \
  --vendor "Oqteris Project" \
  --description "Dedicated PDF Reader and Editor" \
  --icon "src/main/resources/icons/oqteris-logo.png" \
  --input target/app-input \
  --main-jar oqteris-1.0.0-SNAPSHOT.jar \
  --main-class org.originsd.oqteris.ui.OqterisLauncher \
  --linux-shortcut \
  --dest target/installer
```

#### Desktop Entry & MIME Type (`~/.local/share/applications/oqteris.desktop`)
```ini
[Desktop Entry]
Type=Application
Version=1.0
Name=Oqteris
GenericName=PDF Reader & Editor
Comment=View, annotate, and verify digital signatures on PDF documents
Icon=oqteris-logo
Exec=oqteris %f
Terminal=false
MimeType=application/pdf;
Categories=Office;Viewer;Graphics;
StartupNotify=true
```

Set Oqteris as default PDF handler via terminal:
```bash
xdg-mime default oqteris.desktop application/pdf
```

Verify default status:
```bash
xdg-mime query default application/pdf
```

---

### 4.3 macOS Integration & Packaging

#### Application Bundle (.dmg or .pkg)
```bash
jpackage \
  --type dmg \
  --name "Oqteris" \
  --app-version "1.0.0" \
  --vendor "Oqteris Project" \
  --description "Dedicated PDF Reader and Editor" \
  --icon "src/main/resources/icons/oqteris-logo.icns" \
  --input target/app-input \
  --main-jar oqteris-1.0.0-SNAPSHOT.jar \
  --main-class org.originsd.oqteris.ui.OqterisLauncher \
  --dest target/installer
```

#### Document Type Declaration (`Info.plist`)
In the macOS application bundle, the PDF document association is declared in `Contents/Info.plist`:
```xml
<key>CFBundleDocumentTypes</key>
<array>
    <dict>
        <key>CFBundleTypeName</key>
        <string>PDF Document</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleTypeExtensions</key>
        <array>
            <string>pdf</string>
        </array>
        <key>CFBundleTypeMIMETypes</key>
        <array>
            <string>application/pdf</string>
        </array>
        <key>CFBundleTypeIconFile</key>
        <string>oqteris-document.icns</string>
        <key>LSHandlerRank</key>
        <string>Default</string>
    </dict>
</array>
```

---

## 5. Manual Verification Test Procedures

### Test Case 1: Welcome Screen & Branding
1. Launch Oqteris: `mvn javafx:run` (or `java -jar target/oqteris-1.0.0-SNAPSHOT.jar`).
2. Verify that the window displays the **Oqteris** title and application logo (`oqteris-logo.png`).
3. Verify that the Welcome Screen presents PDF-focused actions:
   - "Open PDF Document..."
   - "Security & Trust Store"
   - "Application Settings"
   - Drag & drop PDF area
4. Verify that no references to Word, Calc, Impress, or office suites remain on the screen or in menus.
5. **Pass Criteria**: Clean PDF reader and editor interface with no obsolete office-suite elements.

### Test Case 2: Opening and Navigating PDF Documents
1. Click **Open PDF Document...** (or press `Ctrl+O`).
2. Select `samples/sample_signed.pdf`.
3. Verify that the document renders with continuous scrolling.
4. Test zoom slider and buttons (+ / -). Verify zoom ranges from 25% to 400%.
5. Click **Rotate Right** in the View menu or toolbar. Verify pages rotate 90&deg; clockwise.
6. Open the **Thumbnails** panel on the left sidebar. Verify thumbnail previews are generated.
7. Open the **Bookmarks** panel. Verify document outline items are displayed and clickable.
8. Press `Ctrl+F` and search for text within the document. Verify matches highlight correctly.
9. **Pass Criteria**: Document navigation, zoom, rotation, thumbnails, and search function smoothly.

### Test Case 3: Digital Signature & Trust Verification
1. Open `samples/sample_signed.pdf`.
2. Observe the signature status banner and the **Digital Signatures** tab in the sidebar.
3. Verify the initial state: **Valid Signature (Untrusted Signer)** with yellow badge.
4. Open **Tools &rarr; Certificate Trust Manager...**.
5. Click **Import Certificate**, select `samples/oqteris_demo_signer.cer`, specify alias `demo_root`, and select **Trusted Root**.
6. Switch back to the document view (or trigger re-verification).
7. Verify that the badge transitions to **:white_check_mark: Digitally Signed & Trusted** with green badge.
8. **Pass Criteria**: Signature verification correctly validates byte ranges and transitions to trusted status upon certificate import.

### Test Case 4: Command-Line File Argument & Single-Instance IPC
1. Ensure Oqteris is closed.
2. Launch Oqteris from terminal with a PDF argument:
   ```bash
   java -jar target/oqteris-1.0.0-SNAPSHOT.jar samples/sample_signed.pdf
   ```
3. Verify that the file opens immediately without stopping at the welcome screen.
4. While the application is running, open a second terminal and run:
   ```bash
   java -jar target/oqteris-1.0.0-SNAPSHOT.jar samples/sample_signed.pdf
   ```
5. Verify that the second command exits cleanly and routes the request to the existing application window.
6. **Pass Criteria**: Command-line arguments open PDF files reliably, and secondary invocations pass files to the running instance via IPC.

### Test Case 5: Setting Default PDF Viewer
1. Open **File &rarr; Settings...**.
2. Locate the **Default PDF Viewer** section.
3. Verify that the current status is accurately detected.
4. Click **Set Oqteris as Default PDF Viewer**.
5. On Windows: Verify registry keys are written and Windows Settings opens with clear user guidance.
6. On Linux: Verify `.desktop` file is installed in `~/.local/share/applications/` and `xdg-mime` is updated.
7. **Pass Criteria**: File association registration executes cleanly and provides clear platform-specific guidance.
