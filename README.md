# Oqteris

<div align="center">
  <img src="src/main/resources/icons/oqteris-logo.png" alt="Oqteris Logo" width="160"/>
  <h3>Dedicated Cross-Platform Desktop PDF Reader and Editor</h3>
  <p><strong>Java 21 LTS &bull; JavaFX 21 &bull; Apache PDFBox 3.0.5 &bull; Bouncy Castle 1.86</strong></p>
</div>

---

## 1. Overview & Vision

**Oqteris** is a modern, high-performance desktop PDF reader and editor engineered in Java 21 LTS and JavaFX. It provides a reliable, secure environment for viewing, annotating, searching, and cryptographically validating Portable Document Format (PDF) files.

### Core Value Proposition

- **Dedicated PDF Experience**: Exclusively focused on PDF workflows with continuous scrolling, page thumbnails, bookmarks outline, full-text search, and page rotation.
- **Enterprise-Grade Digital Signature Verification**: Built-in verification for PPKLite / CMS / PKCS#7 detached digital signatures with full byte-range tamper detection.
- **Built-in Trust Store & Certificate Manager**: Application-managed X.509 PKCS#12 certificate trust store with granular trust levels (Trusted Root, Trusted Signer, Blacklisted).
- **Deep Operating System Integration**:
  - Can register as the system default PDF viewer on Windows, Linux, and macOS.
  - Native file-type association with dedicated PDF document icon.
  - Single-instance IPC mechanism routing newly opened files to the active window without opening duplicate instances.
  - Opens files via command-line arguments, desktop file managers, or drag-and-drop.
- **100% Standalone Desktop Application**: Operates completely offline with zero cloud dependencies or external office runtimes.

---

## 2. Application Icons & Branding

Oqteris provides two distinct, purpose-built icons:

| Icon Asset | Visual Identity | Logical Role & Usage |
|---|---|---|
| **Icon A: Application Logo**<br/>`oqteris-logo.png`<br/>`oqteris-logo.ico`<br/>`oqteris-app-icon` | Stylized blue/cyan **Q** surrounding an open document and fountain pen | Application branding, window title bar, desktop launcher, About dialog, installer branding |
| **Icon B: PDF Document Icon**<br/>`oqteris-document.png`<br/>`oqteris-document.ico`<br/>`oqteris-pdf-file-icon` | Blue document sheet with prominent PDF indicator and preview eye | Operating system `.pdf` file association icon, explorer/file-manager document icon |

---

## 3. Technology Stack

| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Runtime** | Java LTS | 21+ | Core runtime, Virtual Threads, Records, Sealed types |
| **UI Framework** | JavaFX | 21.0.6 | Hardware-accelerated desktop UI shell, Canvas rendering |
| **PDF Engine** | Apache PDFBox | 3.0.5 | PDF document structure, font extraction, page rasterization |
| **Cryptography** | Bouncy Castle | 1.86 | CMS / PKCS#7 signature verification, X.509 certificate path validation |
| **UI Components** | ControlsFX & FXRibbon | 11.2.1 / 1.2.2 | Clearable text fields, ribbon controls |
| **Vector Icons** | Ikonli (Material Design) | 12.3.1 | High-DPI resolution-independent vector icons |
| **Build System** | Apache Maven | 3.9+ | Dependency management, multi-platform compilation, testing |
| **Test Runner** | JUnit Jupiter | 5.11.4 | Automated unit, regression, and acceptance testing |

---

## 4. Key Features

```
+---------------------------------------------------------------------------------------------------------+
| [File] [Edit] [View] [Tools] [Help]                                                  [Light/Dark Theme] |
+---------------------------------------------------------------------------------------------------------+
| [Tab: Contract_Signed.pdf]  |  [Tab: Technical_Manual.pdf]                                              |
+---------------------------------------------------------------------------------------------------------+
| HOME | VIEW | EDIT | COMMENT | ORGANIZE | FILL & SIGN | PROTECT | SHARE | HELP | [SIGNATURES (1)]         |
+---------------------------------------------------------------------------------------------------------+
| [Open] [Save]  [Undo] [Redo] | [Select] [Hand] | [|<] [<] [ 1 / 12 ] [>] [>|] | [100%] [Fit] | [Rotate] |
|  FILE    |     CLIPBOARD     |      TOOLS      |          NAVIGATION          |     ZOOM     |  PAGES   |
+---------------------------------------------------------------------------------------------------------+
| [Thumbnails] |                                                                                          |
| [Bookmarks ] |        Page 1                                                                            |
| [Signatures] |   +----------------------------------------------------------------------------------+   |
|              |   |                                                                                  |   |
|   ✓ Trusted  |   |                   OQTERIS SPECIFICATION DOCUMENT                                 |   |
|   John Doe   |   |                                                                                  |   |
|   Valid CMS  |   |   Continuous high-fidelity page rendering with PDFBox 3.x                        |   |
|              |   |                                                                                  |   |
|              |   |   [✓ Valid Signature: CN=Certified Signer]                                       |   |
|              |   +----------------------------------------------------------------------------------+   |
+---------------------------------------------------------------------------------------------------------+
| Ready | Page 1 of 12 | Zoom: 100% | Trust Store Active                                                  |
+---------------------------------------------------------------------------------------------------------+
```

### Professional Desktop Ribbon Interface
- **3-Level Desktop Hierarchy**: Top tab strip, middle command group body, and crisp bottom group captions.
- **Logically Grouped Ribbon Tabs**:
  - **Home**: Quick document operations, clipboard, text selection, hand/pan tool, page jumps, zoom, rotation, search.
  - **View**: Continuous vs single-page reading, full-screen mode (F11), thumbnails, bookmarks, signatures drawer switches.
  - **Edit**: Text selection, page text copying, select all, document properties inspector.
  - **Comment**: Annotation markup tools including Highlights, Underlines, Sticky Notes, Text Boxes, Pencil, and Standard Stamp presets (APPROVED, CONFIDENTIAL, DRAFT, FINAL, VOID).
  - **Organize**: Page rotation (90&deg; CW, 90&deg; CCW, 180&deg;), page deletion, and page dimension inspection.
  - **Fill & Sign**: Form field filling, clear form fields, visual signatures & initials, and digital certificate validation.
  - **Protect**: Password encryption detection, document permissions, and Certificate Trust Store manager.
  - **Share**: Email PDF, copy document path, open containing folder in system explorer, print, and default viewer configuration.
  - **Help**: Documentation guide, keyboard shortcuts cheatsheet, settings dialog, and about dialog.
- **Contextual Tabs**: Conditionally presents the **Digital Signatures** contextual tab with direct certificate trust verification when signed documents are opened.
- **Responsive Layout**: Horizontal scroll handling ensures seamless operation across different window widths and screen resolutions without control clipping.
- **Oqteris Aurora Design System**: Refined blue/cyan aesthetics with clear active indicators in both light and dark desktop themes.

### High-Fidelity PDF Viewing
- Continuous vertical page rendering with smooth scrolling.
- Zoom range from 25% to 400%, with quick presets and responsive zoom slider.
- Page rotation (90&deg;, 180&deg;, 270&deg; clockwise).
- Interactive page thumbnail sidebar for rapid navigation.
- PDF outline / bookmarks tree navigation.
- Live full-text search with match highlighting and next/previous navigation.
- Tabbed workspace allowing multiple PDF files to be open simultaneously.

### Digital Signature Verification & Trust Center
- Validates embedded PKCS#7 / CMS detached signatures (`/ByteRange`, `/Contents`).
- Full byte-range verification detecting any appended or modified bytes after signing.
- Live visual signature appearance rewriting updating in-document signature stamps to reflect real cryptographic validity.
- Built-in X.509 certificate manager allowing users to import root and intermediate certificates to establish chain-of-trust.
- Clear visual status badges:
  - **Green**: Valid mathematical signature, full byte-range coverage, unexpired certificate, chained to an explicitly trusted root.
  - **Yellow**: Valid cryptographic signature, but certificate is not yet in the trusted certificates store.
  - **Red**: Cryptographic failure, byte range mismatch, or document modified after signing.

### Operating System Integration & Default Viewer
- Detects whether Oqteris is the operating system's default PDF viewer.
- Provides a one-click **Set Oqteris as default PDF viewer** action in Settings and on the Welcome Screen.
- Windows file association registration under `HKCU\Software\Classes\Oqteris.PDF` with `RegisteredApplications` support.
- Freedesktop.org Linux desktop entry (`oqteris.desktop`) with `application/pdf` MIME type.
- macOS LaunchServices document handler support.
- Single-instance IPC handling ensuring subsequent double-clicked files open in new tabs of the running instance.

---

## 5. Quick Start

### Prerequisites
- **Java Development Kit (JDK)**: OpenJDK 21 LTS or newer on `PATH`.
- **Apache Maven**: Version 3.9+ on `PATH`.

Verify your installation:
```bash
java -version
mvn -version
```

### Build & Run Tests
To compile the codebase and run the automated test suite:
```bash
mvn clean test
```

### Run in Development Mode
Launch Oqteris directly via the JavaFX Maven plugin:
```bash
mvn javafx:run
```

### Package Standalone Application JAR
Package the application JAR:
```bash
mvn clean package
```

Run the compiled JAR:
```bash
java -jar target/oqteris-1.0.0-SNAPSHOT.jar
```

Or pass a PDF file path directly to open on launch:
```bash
java -jar target/oqteris-1.0.0-SNAPSHOT.jar samples/sample_signed.pdf
```

---

## 6. Sample Documents

The project includes verified sample PDF assets in `samples/`:

| File | Description | Purpose |
|---|---|---|
| `samples/sample_signed.pdf` | Digitally signed PDF sealed with an embedded RSA PKCS#7 signature | Verifying signature parsing, byte-range integrity, and visual appearance rewriting |
| `samples/oqteris_demo_signer.cer` | Exported X.509 certificate of the demo signer | Testing certificate import and trust validation transitions |

---

## 7. Documentation Directory

Detailed architectural specifications, security policies, format compatibility matrices, and build guides are available:

- **[ARCHITECTURE.md](file:///d:/Study/Programming/Projects/Oqteris/ARCHITECTURE.md)**: System design, PDF rendering pipeline, signature verification pipeline, platform abstraction, and threading model.
- **[BUILD.md](file:///d:/Study/Programming/Projects/Oqteris/BUILD.md)**: Build instructions, native OS packaging (`jpackage`), icon setup, and platform-specific default viewer registration.
- **[FORMAT_SUPPORT.md](file:///d:/Study/Programming/Projects/Oqteris/FORMAT_SUPPORT.md)**: Technical specifications for PDF standards (ISO 32000-1 / ISO 32000-2) and digital signature formats.
- **[SECURITY.md](file:///d:/Study/Programming/Projects/Oqteris/SECURITY.md)**: Cryptographic signature verification guidelines, trust store architecture, and trust decision policies.

---

## 8. License

Oqteris is licensed under the Apache License, Version 2.0. All third-party dependencies (Apache PDFBox, Bouncy Castle, OpenJFX, ControlsFX) are permissive open-source libraries permitting desktop and commercial distribution.