# Oqteris Format Compatibility Matrix & Technical Specifications

## 1. Supported Formats Overview

Oqteris prioritizes modern Office Open XML (OOXML) formats (DOCX, XLSX, PPTX) and PDF as its first-class citizen document types, alongside industry-standard interchange formats (CSV, TSV, TXT, HTML, RTF).

| Extension | Format Name | Application | Import | Export | Fidelity Level |
|---|---|---|:---:|:---:|---|
| **.docx** | Office Open XML Document | Writer | Yes | Yes | High (Layout & Pagination Engine) |
| **.doc** | Word 97-2003 Binary Document | Writer | Yes | No | Text, Structure & Paragraphs |
| **.odt** | OpenDocument Text (ISO 26300) | Writer | Yes | Yes | High (Headings, Paragraphs & Tables) |
| **.rtf** | Rich Text Format | Writer | Yes | Yes | Complete (Styled Runs, Colors & Tables) |
| **.txt** | Plain Text Document | Writer | Yes | Yes | Complete |
| **.html** | HyperText Markup Language | Writer | Yes | Yes | Presentation-Ready Import & Clean Export |
| **.xlsx** | Office Open XML Spreadsheet | Calc | Yes | Yes | High (Formulas, Styles & Values) |
| **.xls** | Excel 97-2003 Binary Workbook | Calc | Yes | Yes | High (Formulas, Styles & Values) |
| **.ods** | OpenDocument Spreadsheet | Calc | Yes | No | Complete (Formulas, Values & Sheets) |
| **.csv** | Comma-Separated Values | Calc | Yes | Yes | Complete |
| **.tsv** | Tab-Separated Values | Calc | Yes | Yes | Complete |
| **.pptx** | Office Open XML Presentation | Impress | Yes | Yes | High (Vector Shapes, Text & Geometry) |
| **.ppt** | PowerPoint 97-2003 Binary Deck | Impress | Yes | No | High (Slide Titles & Body Shapes) |
| **.odp** | OpenDocument Presentation | Impress | Yes | No | High (Slide Frames & Vector Shapes) |
| **.pdf** | Portable Document Format (ISO 32000) | PDF Viewer | Yes | Yes (from Impress) | Full (Digital Signature Verification) |

---

## 2. Word Processing Formats (Oqteris Writer)

### 2.1 Microsoft Word (.docx)
- **Primary Standard**: ECMA-376 / ISO/IEC 29500 Office Open XML (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`).

#### Supported Features
- **Paragraphs & Headings**: Title, Subtitle, Heading 1, Heading 2, Heading 3, and Normal styles with paragraph alignment (`LEFT`, `CENTER`, `RIGHT`, `JUSTIFY`).
- **Character Formatting**: Font family, font size, bold, italic, underline, strikethrough, text foreground colors (hex/RGB), and highlight colors.
- **Lists**: Bulleted lists (`•`) and numbered lists (`1. `, `2. `) with automatic indentation.
- **Page Layout**: Paper sizes (A4, Letter, Legal), margins (top, bottom, left, right), orientation (Portrait, Landscape).
- **Tables**: Multi-row, multi-column tables with cell text, cell background colors, and border widths.
- **Headers & Footers**: Header and footer text with dynamic page counter tokens (`$PAGE`, `$PAGES`).
- **Page Breaks**: Explicit hard page breaks (`<w:br w:type="page"/>`) and automatic pagination.

#### Explicit Limitations
- **Macros**: Word macro-enabled files (`.docm`) are imported for content only; VBA code execution is disabled for enterprise security.
- **Complex Floating Shapes**: Floating text boxes with complex word-wrapping (e.g. *Tight* or *Through* shape contour wrapping) are approximated using inline top/bottom layout.
- **Track Changes**: Word revision marks (`<w:del>`, `<w:ins>`) are loaded as their accepted modern text representation.
- **Embedded OLE Objects**: Embedded Excel objects or Visio diagrams inside `.docx` are represented by their embedded preview image if present.

### 2.2 Plain Text (.txt)
- Full UTF-8, UTF-16, and ANSI text import and export.
- Paragraph boundaries preserved; clean continuous flow into paginated pages.

### 2.3 HTML Export (.html)
- Clean, semantic HTML5 output with embedded CSS styling for easy sharing, printing, or web publishing.

---

## 3. Spreadsheet Formats (Oqteris Calc)

### 3.1 Microsoft Excel (.xlsx)
- **Primary Standard**: ECMA-376 / ISO/IEC 29500 SpreadsheetML (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).

#### Supported Features
- **Workbook Structure**: Multiple worksheets with custom sheet tab names and sheet selection.
- **Cell Data Types**: Double-precision floating point numbers, text strings, booleans, formula strings, and errors.
- **Formula Recalculation Engine**:
  - Independent AST parser and DAG dependency calculation engine.
  - Recalculates over 30 Excel-compatible functions:
    - *Mathematical*: `SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`, `COUNTA`, `ABS`, `ROUND`, `SQRT`, `POWER`, `PRODUCT`, `MOD`, `INT`.
    - *Logical*: `IF`, `AND`, `OR`, `NOT`, `TRUE`, `FALSE`, `IFERROR`.
    - *Textual*: `CONCATENATE`, `LEFT`, `RIGHT`, `MID`, `LEN`, `UPPER`, `LOWER`, `TRIM`, `TEXT`.
    - *Lookup*: `VLOOKUP`, `INDEX`, `MATCH`, `CHOOSE`.
    - *Date/Time*: `TODAY`, `NOW`, `YEAR`, `MONTH`, `DAY`.
- **Topological Dependency Graph**: Resolves cell recalculation ordering topologically and detects circular references (`#CYCLE!`).
- **Cell Styling & Formatting**:
  - Font families, sizes, bold, italic, underline, and font colors.
  - Cell background fills (solid RGB colors).
  - Cell borders (thin, medium, thick, dashed) and border colors.
  - Horizontal alignment (`LEFT`, `CENTER`, `RIGHT`) and vertical alignment (`TOP`, `CENTER`, `BOTTOM`).
  - Number formats: Currency (`$#,##0.00`), Percentage (`0.00%`), Integers (`#,##0`), Decimals (`0.00`), General.
- **Sheet Metrics**: Custom column widths, custom row heights, and merged cell regions (`CellRangeAddress`).

#### Explicit Limitations
- **Array Formulas & Dynamic Spills**: Modern Excel spill formulas (e.g. `XLOOKUP`, `UNIQUE`, `FILTER`) are evaluated when written as standard single-cell lookups. Full multi-cell spill arrays are scheduled for an upcoming release.
- **VBA Macros**: Macro-enabled workbooks (`.xlsm`) are loaded for data, formulas, and visual formatting without macro execution.
- **Pivot Tables**: Pivot caches are imported as standard data ranges; interactive pivot recalculation is planned.

### 3.2 Delimiter-Separated Values (.csv, .tsv)
- Standard RFC 4180 CSV and TSV reader/writer.
- Auto-detects delimiters (comma vs tab), handles quoted fields containing commas and line breaks, and auto-types numeric and date entries.

---

## 4. Presentation Formats (Oqteris Impress)

### 4.1 Microsoft PowerPoint (.pptx)
- **Primary Standard**: ECMA-376 / ISO/IEC 29500 PresentationML (`application/vnd.openxmlformats-officedocument.presentationml.presentation`).

#### Supported Features
- **Presentation Deck**: Slide dimensions (widescreen 16:9, standard 4:3), ordered slide list.
- **Slide Shapes**:
  - `TextBoxShape`: Typography, font size, bold, italic, alignment, text color, multi-line paragraph layout.
  - `GeometricShape`: Vector rectangles, rounded rectangles, ellipses, and triangles with fill colors and border strokes.
  - `ImageShape`: Embedded images with positional geometry and aspect-ratio scaling.
  - `TableShape`: Slide tables with defined rows and columns.
- **Slide Management**: Slide creation, duplication, reordering, and deletion.
- **Presentation Mode**: Full-screen slide transitions (Fade, Slide Left, Slide Right, Zoom) and presentation keyboard navigation.
- **PDF Export**: Vector PDF export generating high-resolution presentation decks directly.

#### Explicit Limitations
- **Complex SmartArt**: Embedded SmartArt node hierarchies are loaded as their grouped shape components.
- **Media Video & Audio**: Audio and video clips embedded inside slides display placeholder visual representations during edit mode.

---

## 5. PDF & Digital Signature Formats (Oqteris PDF)

### 5.1 Portable Document Format (PDF 1.4 - 2.0 / ISO 32000-1)
- **High-Performance Page Rendering**: Page rasterization via PDFBox with zoom scaling (50% to 400%), page rotation (90&deg;, 180&deg;, 270&deg;), and thumbnail previews.
- **Navigation & Document Outline**: Hierarchical bookmark outline tree and search across document text streams.

### 5.2 Cryptographic Digital Signatures
- **Signature Formats**: Adobe PPKLite detached signatures, CMS / PKCS#7 (`adbe.pkcs7.detached`, `ETSI.CAdES.detached`).
- **Signature Digest Algorithms**: SHA-256, SHA-384, SHA-512, SHA-1 (with deprecation warning).
- **Public Key Cryptosystems**: RSA (1024, 2048, 3072, 4096-bit), ECDSA (P-256, P-384, P-521).
- **Byte Range Integrity Verification**: Verification that signature covers whole document (`/ByteRange` offsets spanning start-to-end), detecting appended modifications or incremental tampering.
- **X.509 Certificate Chain Validation**: Certificate path construction, expiration validation, and trust anchor comparison against the application PKCS#12 keystore.
