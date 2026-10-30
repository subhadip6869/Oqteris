# Oqteris Build, Packaging & Verification Guide

## 1. System Requirements

### Hardware Requirements
- **Processor**: 64-bit Dual-Core x86_64 or ARM64 processor (Quad-core recommended).
- **RAM**: Minimum 2 GB (4 GB recommended).
- **Disk Space**: 500 MB free disk space for build artifacts and dependencies.

### Software Prerequisites
- **Java Development Kit (JDK)**: OpenJDK 21 LTS or newer (Eclipse Temurin, Oracle OpenJDK, or Amazon Corretto).
- **Apache Maven**: Version 3.9.0 or higher.
- **Operating Systems Supported**:
  - Windows 10, Windows 11, Windows Server 2019/2022 (x86_64).
  - Linux: Ubuntu 20.04+, Debian 11+, Fedora 38+, RHEL 8/9, Arch Linux.

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
To execute all 25 unit, regression, round-trip, and sample verification tests:
```bash
mvn test
```

Expected output:
```
[INFO] Running org.originsd.oqteris.calc.SpreadsheetFormulaTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.calc.SpreadsheetModelTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.crypto.CertificateTrustManagerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.crypto.PdfSignatureVerifierTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.presentation.PresentationModelTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.samples.SampleDataGeneratorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.samples.SampleFilesVerificationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.writer.DocxRoundTripTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.originsd.oqteris.writer.WordProcessorLayoutTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 3. Running Oqteris

### Running in Development Mode
Launch the application directly via the JavaFX Maven plugin:
```bash
mvn javafx:run
```

### Packaging & Running the Standalone JAR
Build the application JAR and stage runtime dependencies:
```bash
mvn package
mvn dependency:copy-dependencies -DoutputDirectory=target/app-input
cp target/oqteris-1.0.0-SNAPSHOT.jar target/app-input/
```

Run using the standard Java launcher:
```bash
java -jar target/app-input/oqteris-1.0.0-SNAPSHOT.jar
```

Or pass a document file path directly to open it on startup:
```bash
java -jar target/app-input/oqteris-1.0.0-SNAPSHOT.jar samples/sample_document.docx
```

---

## 4. Native OS Packaging (Windows & Linux)

Using the JDK `jpackage` utility, you can package Oqteris into a self-contained native desktop application that bundles a minimal Java runtime (created via `jlink`) without requiring users to have Java pre-installed.

> **Excluding Build Artifacts & Loose Class Files:**
> `jpackage` packages everything inside the specified `--input` directory. Do not point `--input` directly at `target`, as this bundles Maven's intermediate `classes/`, `test-classes/`, and metadata files into your application package. Instead, stage only the required JARs into a clean input directory (`target/app-input`).

### Step 1: Prepare Clean Packaging Staging Directory

Run in **PowerShell (Windows)**:
```powershell
# 1. Compile and package the project
mvn clean package -DskipTests

# 2. Stage all dependency JARs into a clean input directory
mvn dependency:copy-dependencies -DoutputDirectory=target/app-input

# 3. Copy only the main JAR into the staging directory
Copy-Item target\oqteris-1.0.0-SNAPSHOT.jar target\app-input\
```

Run in **Bash (Linux / macOS)**:
```bash
# 1. Compile and package the project
mvn clean package -DskipTests

# 2. Stage all dependency JARs into a clean input directory
mvn dependency:copy-dependencies -DoutputDirectory=target/app-input

# 3. Copy only the main JAR into the staging directory
cp target/oqteris-1.0.0-SNAPSHOT.jar target/app-input/
```

### Step 2A: Windows Packaging (MSI / EXE Installer)
Run in PowerShell (requires WiX Toolset 3.11+ on PATH for MSI/EXE installers; Windows icons must be `.ico` format):
```powershell
jpackage `
  --type exe `
  --name "Oqteris Office" `
  --app-version "1.0.0" `
  --vendor "Oqteris Open Source Project" `
  --description "Modern Independent Desktop Office Suite" `
  --icon "src/main/resources/icons/oqteris-logo.ico" `
  --input target/app-input `
  --main-jar oqteris-1.0.0-SNAPSHOT.jar `
  --main-class org.originsd.oqteris.ui.OqterisLauncher `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --dest target/installer
```

> **Tip (Portable App Image without WiX):** If WiX Toolset is not installed, you can create a standalone portable directory without an installer by using `--type app-image` instead of `--type exe`.

### Step 2B: Linux Packaging (DEB & RPM Installers)
On Ubuntu / Debian:
```bash
jpackage \
  --type deb \
  --name "oqteris" \
  --app-version "1.0.0" \
  --vendor "Oqteris" \
  --description "Modern Independent Desktop Office Suite" \
  --icon "src/main/resources/icons/oqteris-logo.png" \
  --input target/app-input \
  --main-jar oqteris-1.0.0-SNAPSHOT.jar \
  --main-class org.originsd.oqteris.ui.OqterisLauncher \
  --linux-shortcut \
  --linux-menu-group "Office" \
  --dest target/installer
```

Install via `dpkg`:
```bash
sudo dpkg -i target/installer/oqteris_1.0.0-1_amd64.deb
```

On Fedora / RHEL / CentOS:
```bash
jpackage \
  --type rpm \
  --name "oqteris" \
  --app-version "1.0.0" \
  --input target/app-input \
  --main-jar oqteris-1.0.0-SNAPSHOT.jar \
  --main-class org.originsd.oqteris.ui.OqterisLauncher \
  --linux-shortcut \
  --dest target/installer
```

---

## 5. Manual Verification Test Procedures

To perform manual user-acceptance testing across all modules, follow these step-by-step procedures:

### Test Case 1: Word Processor (Oqteris Writer)
1. Launch Oqteris: `java -jar target/app-input/oqteris-1.0.0-SNAPSHOT.jar` (or `mvn javafx:run`).
2. On the Welcome Screen, click **New Document** (or press `Ctrl+N`).
3. Type text: `Oqteris Word Processor Verification`.
4. Select text and click **Bold**, change font size to **20**, and choose a color.
5. In the toolbar, click **+ Table**. Verify a 3x3 table is inserted into the document.
6. Click **File &rarr; Save As** (`Ctrl+S`), name it `test_doc.docx`.
7. Close the tab (`Ctrl+W`) and reopen via **File &rarr; Open** (`Ctrl+O`).
8. **Pass Criteria**: Document opens with formatting, typography, and table intact.

### Test Case 2: Spreadsheet Editor (Oqteris Calc)
1. On the Welcome Screen, click **New Spreadsheet**.
2. In cell `A1`, enter `100`. In cell `A2`, enter `250`.
3. In cell `A3`, type `=SUM(A1:A2)` and press Enter.
4. Verify cell `A3` displays `350`.
5. Select cell `A3` and set number format to **Currency** (`$#,##0.00`). Verify it renders as `$350.00`.
6. Click **File &rarr; Open** and select `samples/sample_sheet.xlsx`.
7. **Pass Criteria**: Q1 Financials sheet displays with departments, formulas, variance computations, and totals.

### Test Case 3: Presentation Designer (Oqteris Impress)
1. On the Welcome Screen, click **New Presentation**.
2. Click **Add Slide**.
3. In the toolbar, click **+ Rectangle**. Click and drag the shape across the slide canvas.
4. Press **F5** to start presentation mode.
5. Use the **Right Arrow** to advance slides, and **Escape** to exit.
6. Click **File &rarr; Export as PDF**.
7. **Pass Criteria**: Slideshow runs full-screen and PDF exports accurately.

### Test Case 4: PDF Viewer & Signature Verification
1. Click **File &rarr; Open** and open `samples/sample_signed.pdf`.
2. Notice the top banner: **Valid Signature (Untrusted Signer)** with yellow alert icon.
3. Click **View Details**. Verify signer name: `CN=Oqteris Certified Signer, O=Oqteris Office Project, C=US`.
4. Open **Security &rarr; Certificate Manager** (`Ctrl+Shift+C`).
5. Click **Import Certificate**, select `samples/oqteris_demo_signer.cer`, name alias `demo_root`, and select **Trusted Root**.
6. Switch back to `sample_signed.pdf` (or close and reopen it).
7. Notice the top banner now displays: **:white_check_mark: Digitally Signed & Trusted** with green badge.
8. **Pass Criteria**: Signature verification correctly transitions from untrusted to fully trusted upon certificate import.
