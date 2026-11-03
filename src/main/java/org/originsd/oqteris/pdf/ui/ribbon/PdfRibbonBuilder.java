package org.originsd.oqteris.pdf.ui.ribbon;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.*;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import org.originsd.oqteris.pdf.model.PdfDocumentModel;
import org.originsd.oqteris.pdf.ui.PdfDocumentView;
import org.originsd.oqteris.ui.icon.AppIcons;
import org.originsd.oqteris.ui.ribbon.MainWindowHost;
import org.originsd.oqteris.ui.ribbon.RibbonControlFactory;
import org.originsd.oqteris.ui.ThemeManager;

import java.util.List;

/**
 * Builds the professional desktop ribbon for Oqteris PDF editor.
 * Features 3-level visual hierarchy (tabs, groups, captions), consistent Oqteris blue/cyan
 * branding, full functional integration, responsive overflow handling, and contextual tabs.
 */
public class PdfRibbonBuilder {

    private final PdfDocumentView view;
    private final PdfDocumentModel model;
    private final Ribbon ribbon;

    // Core tabs
    private RibbonTab homeTab;
    private RibbonTab viewTab;
    private RibbonTab editTab;
    private RibbonTab commentTab;
    private RibbonTab organizeTab;
    private RibbonTab fillSignTab;
    private RibbonTab protectTab;
    private RibbonTab shareTab;
    private RibbonTab helpTab;

    // Contextual Tab
    private RibbonTab signatureContextualTab;

    // Dynamic Nodes & Badges
    private final RibbonGroup homeSigGroup = new RibbonGroup();
    private final RibbonGroup fillSignSigGroup = new RibbonGroup();
    private final RibbonGroup contextualStatusGroup = new RibbonGroup();

    // Tool toggle groups
    private final ToggleGroup selectionToolGroup = new ToggleGroup();
    private final ToggleGroup viewModeGroup = new ToggleGroup();

    // Navigation input controls
    private final TextField pageField = new TextField("1");
    private final Label pageCountLabel = new Label("/ 1");

    public PdfRibbonBuilder(PdfDocumentView view, PdfDocumentModel model, Ribbon ribbon) {
        this.view = view;
        this.model = model;
        this.ribbon = ribbon;
    }

    public void buildRibbon() {
        ribbon.getTabs().clear();
        ribbon.setMaxWidth(Double.MAX_VALUE);

        homeTab = buildHomeTab();
        viewTab = buildViewTab();
        editTab = buildEditTab();
        commentTab = buildCommentTab();
        organizeTab = buildOrganizeTab();
        fillSignTab = buildFillSignTab();
        protectTab = buildProtectTab();
        shareTab = buildShareTab();
        helpTab = buildHelpTab();

        ribbon.getTabs().addAll(
                homeTab, viewTab, editTab, commentTab, organizeTab,
                fillSignTab, protectTab, shareTab, helpTab
        );

        // Setup contextual tabs based on document features
        updateContextualTabs();
        updateSignatureBadges();
    }

    public TextField getPageField() {
        return pageField;
    }

    public Label getPageCountLabel() {
        return pageCountLabel;
    }

    public void updateContextualTabs() {
        boolean hasSignatures = !model.getSignatures().isEmpty();
        if (hasSignatures) {
            if (signatureContextualTab == null) {
                signatureContextualTab = buildSignatureContextualTab();
            }
            if (!ribbon.getTabs().contains(signatureContextualTab)) {
                ribbon.getTabs().add(signatureContextualTab);
            }
        } else {
            if (signatureContextualTab != null) {
                ribbon.getTabs().remove(signatureContextualTab);
            }
        }
    }

    public void updateSignatureBadges() {
        Node badge1 = createSignatureBadgeNode();
        Node badge2 = createSignatureBadgeNode();

        homeSigGroup.getNodes().clear();
        homeSigGroup.getNodes().add(badge1);

        fillSignSigGroup.getNodes().clear();
        fillSignSigGroup.getNodes().add(badge2);

        if (signatureContextualTab != null) {
            contextualStatusGroup.getNodes().clear();
            contextualStatusGroup.getNodes().add(createSignatureBadgeNode());
        }
    }

    // =========================================================================
    // TAB 1: HOME
    // =========================================================================
    private RibbonTab buildHomeTab() {
        RibbonTab tab = new RibbonTab("Home");

        // 1. File Group
        Button openBtn = RibbonControlFactory.createLargeButton(
                "Open\nPDF", MaterialDesignF.FOLDER_OPEN_OUTLINE,
                "Open an existing PDF document (Ctrl+O)",
                e -> getWindowHost().showOpenDialog()
        );
        Button saveBtn = RibbonControlFactory.createSmallButton(
                "Save", MaterialDesignC.CONTENT_SAVE_OUTLINE,
                "Save changes to document (Ctrl+S)",
                e -> getWindowHost().saveActiveDocument(false)
        );
        Button saveAsBtn = RibbonControlFactory.createSmallButton(
                "Save As", MaterialDesignC.CONTENT_SAVE_EDIT_OUTLINE,
                "Save document under a new filename (Ctrl+Shift+S)",
                e -> getWindowHost().saveActiveDocument(true)
        );
        Button printBtn = RibbonControlFactory.createLargeButton(
                "Print", MaterialDesignP.PRINTER,
                "Print the current PDF document (Ctrl+P)",
                e -> view.print()
        );
        Column saveCol = RibbonControlFactory.createColumn(saveBtn, saveAsBtn);
        RibbonGroup fileGroup = RibbonControlFactory.createGroup("File", openBtn, saveCol, printBtn);

        // 2. Clipboard & History Group
        Button undoBtn = RibbonControlFactory.createSmallButton(
                "Undo", MaterialDesignU.UNDO,
                "Undo the last action (Ctrl+Z)",
                e -> {
                    if (model.getUndoRedoManager().canUndo()) model.getUndoRedoManager().undo();
                }
        );
        Button redoBtn = RibbonControlFactory.createSmallButton(
                "Redo", MaterialDesignR.REDO,
                "Redo the previously undone action (Ctrl+Y)",
                e -> {
                    if (model.getUndoRedoManager().canRedo()) model.getUndoRedoManager().redo();
                }
        );
        Button copyBtn = RibbonControlFactory.createSmallButton(
                "Copy", MaterialDesignC.CONTENT_COPY,
                "Copy text from current page to clipboard (Ctrl+C)",
                e -> view.copy()
        );
        Button selectAllBtn = RibbonControlFactory.createSmallButton(
                "Select All", MaterialDesignS.SELECT_ALL,
                "Select all document contents (Ctrl+A)",
                e -> view.selectAll()
        );
        Column undoRedoCol = RibbonControlFactory.createColumn(undoBtn, redoBtn);
        Column copyCol = RibbonControlFactory.createColumn(copyBtn, selectAllBtn);
        RibbonGroup clipboardGroup = RibbonControlFactory.createGroup("Clipboard", undoRedoCol, copyCol);

        // 3. Selection & Interaction Tools Group
        ToggleButton selectToolBtn = RibbonControlFactory.createLargeToggleButton(
                "Select\nText", MaterialDesignC.CURSOR_TEXT,
                "Select text for copying or annotation",
                selectionToolGroup, true,
                e -> view.setHandToolActive(false)
        );
        ToggleButton handToolBtn = RibbonControlFactory.createLargeToggleButton(
                "Hand\nTool", MaterialDesignH.HAND,
                "Pan and drag the document pages",
                selectionToolGroup, false,
                e -> view.setHandToolActive(true)
        );
        RibbonGroup toolsGroup = RibbonControlFactory.createGroup("Tools", selectToolBtn, handToolBtn);

        // 4. Page Navigation Group
        Button firstBtn = RibbonControlFactory.createIconButton(
                MaterialDesignP.PAGE_FIRST, "First Page (Home)",
                e -> view.jumpToPage(0)
        );
        Button prevBtn = RibbonControlFactory.createIconButton(
                MaterialDesignC.CHEVRON_LEFT, "Previous Page (Page Up)",
                e -> view.jumpToPage(view.getCurrentPage() - 1)
        );

        pageField.setPrefWidth(46);
        pageField.setAlignment(Pos.CENTER);
        pageField.setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-padding: 3 4;");
        pageField.setOnAction(e -> {
            try {
                int p = Integer.parseInt(pageField.getText().trim()) - 1;
                view.jumpToPage(p);
            } catch (Exception ignored) {}
        });

        pageCountLabel.setText("/ " + model.getPageCount());
        pageCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 500;");

        Button nextBtn = RibbonControlFactory.createIconButton(
                MaterialDesignC.CHEVRON_RIGHT, "Next Page (Page Down)",
                e -> view.jumpToPage(view.getCurrentPage() + 1)
        );
        Button lastBtn = RibbonControlFactory.createIconButton(
                MaterialDesignP.PAGE_LAST, "Last Page (End)",
                e -> view.jumpToPage(model.getPageCount() - 1)
        );

        HBox navRow = new HBox(3, firstBtn, prevBtn, pageField, pageCountLabel, nextBtn, lastBtn);
        navRow.setAlignment(Pos.CENTER);
        RibbonGroup navGroup = RibbonControlFactory.createGroup("Navigation", navRow);

        // 5. Zoom & Display Group
        Button actualSizeBtn = RibbonControlFactory.createLargeButton(
                "Actual\nSize", MaterialDesignM.MAGNIFY_SCAN,
                "View at 100% scale (Ctrl+0)",
                e -> view.setZoom(1.0)
        );
        Button fitPageBtn = RibbonControlFactory.createLargeButton(
                "Fit\nPage", MaterialDesignF.FIT_TO_PAGE_OUTLINE,
                "Fit entire page in window",
                e -> view.setZoom(1.0)
        );
        Button fitWidthBtn = RibbonControlFactory.createLargeButton(
                "Fit\nWidth", MaterialDesignA.ARROW_EXPAND_HORIZONTAL,
                "Fit page width to window width",
                e -> view.setZoom(1.3)
        );
        Button zoomInBtn = RibbonControlFactory.createSmallButton(
                "Zoom In", MaterialDesignM.MAGNIFY_PLUS_OUTLINE,
                "Increase zoom level (Ctrl++)",
                e -> view.setZoom(view.getZoom() + 0.2)
        );
        Button zoomOutBtn = RibbonControlFactory.createSmallButton(
                "Zoom Out", MaterialDesignM.MAGNIFY_MINUS_OUTLINE,
                "Decrease zoom level (Ctrl+-)",
                e -> view.setZoom(view.getZoom() - 0.2)
        );
        Column zoomCol = RibbonControlFactory.createColumn(zoomInBtn, zoomOutBtn);

        MenuButton zoomPresetsBtn = createZoomPresetsMenu("Presets");
        RibbonGroup zoomGroup = RibbonControlFactory.createGroup("Zoom", actualSizeBtn, fitPageBtn, fitWidthBtn, zoomCol, zoomPresetsBtn);

        // 6. Page Tools Group
        SplitMenuButton rotateBtn = RibbonControlFactory.createSplitMenuButton(
                "Rotate", MaterialDesignR.ROTATE_RIGHT,
                "Rotate page clockwise 90°",
                e -> view.rotateClockwise(),
                true,
                createMenuItem("Rotate Clockwise 90°", MaterialDesignR.ROTATE_RIGHT, e -> view.rotateClockwise()),
                createMenuItem("Rotate Counter-Clockwise 90°", MaterialDesignR.ROTATE_LEFT, e -> view.rotateCounterClockwise()),
                createMenuItem("Rotate 180°", MaterialDesignR.ROTATE_3D_VARIANT, e -> view.rotate180())
        );

        ToggleButton sideToggle = RibbonControlFactory.createLargeToggleButton(
                "Sidebar", MaterialDesignV.VIEW_SPLIT_VERTICAL,
                "Toggle navigation and bookmarks sidebar",
                null, true,
                e -> view.toggleSidebar()
        );
        RibbonGroup pageToolsGroup = RibbonControlFactory.createGroup("Page Tools", rotateBtn, sideToggle);

        // 7. Find Group
        Button findBtn = RibbonControlFactory.createLargeButton(
                "Find", MaterialDesignM.MAGNIFY,
                "Find text in PDF document (Ctrl+F)",
                e -> view.showSearch()
        );
        RibbonGroup findGroup = RibbonControlFactory.createGroup("Find", findBtn);

        // 8. Signature status group
        homeSigGroup.setTitle("Security");

        tab.getRibbonGroups().addAll(
                fileGroup, clipboardGroup, toolsGroup, navGroup, zoomGroup, pageToolsGroup, findGroup, homeSigGroup
        );
        return tab;
    }

    // =========================================================================
    // TAB 2: VIEW
    // =========================================================================
    private RibbonTab buildViewTab() {
        RibbonTab tab = new RibbonTab("View");

        // 1. Page Display Group
        ToggleButton continuousBtn = RibbonControlFactory.createLargeToggleButton(
                "Continuous", MaterialDesignV.VIEW_STREAM_OUTLINE,
                "Continuous vertical scrolling mode",
                viewModeGroup, true,
                e -> view.setSinglePageView(false)
        );
        ToggleButton singleBtn = RibbonControlFactory.createLargeToggleButton(
                "Single\nPage", MaterialDesignF.FILE_DOCUMENT_OUTLINE,
                "View one page at a time",
                viewModeGroup, false,
                e -> view.setSinglePageView(true)
        );
        MenuButton layoutMenu = RibbonControlFactory.createMenuButton(
                "Page\nLayout", MaterialDesignV.VIEW_DASHBOARD_OUTLINE,
                "Configure document page view layouts",
                true,
                createMenuItem("Continuous Vertical", MaterialDesignV.VIEW_STREAM_OUTLINE, e -> view.setSinglePageView(false)),
                createMenuItem("Single Page View", MaterialDesignF.FILE_DOCUMENT_OUTLINE, e -> view.setSinglePageView(true)),
                createDisabledMenuItem("Facing Pages (Two-Up)", "Facing dual-page layout will be enabled in a future release"),
                createDisabledMenuItem("Facing Continuous", "Continuous two-up scrolling will be enabled in a future release")
        );
        RibbonGroup displayGroup = RibbonControlFactory.createGroup("Page Display", continuousBtn, singleBtn, layoutMenu);

        // 2. Window / Presentation Group
        Button fullScreenBtn = RibbonControlFactory.createLargeButton(
                "Full\nScreen", MaterialDesignF.FULLSCREEN,
                "Toggle full screen reading mode (F11)",
                e -> getWindowHost().toggleFullScreen()
        );
        RibbonGroup windowGroup = RibbonControlFactory.createGroup("Window", fullScreenBtn);

        // 3. Navigation Panels Group
        Button thumbBtn = RibbonControlFactory.createLargeButton(
                "Thumbnails", MaterialDesignV.VIEW_GRID_OUTLINE,
                "Open page thumbnails pane",
                e -> view.selectSidebarTab(0)
        );
        Button bookmarksBtn = RibbonControlFactory.createLargeButton(
                "Bookmarks", MaterialDesignB.BOOKMARK_MULTIPLE_OUTLINE,
                "Open document outline & bookmarks pane",
                e -> view.selectSidebarTab(1)
        );
        Button signaturesBtn = RibbonControlFactory.createLargeButton(
                "Signatures", MaterialDesignS.SHIELD_CHECK_OUTLINE,
                "Open digital signatures validation pane",
                e -> view.selectSidebarTab(2)
        );
        Button attachmentsBtn = RibbonControlFactory.createDisabledButton(
                "Attachments", MaterialDesignP.PAPERCLIP,
                "Document contains no embedded file attachments",
                true
        );
        RibbonGroup panelsGroup = RibbonControlFactory.createGroup("Panels", thumbBtn, bookmarksBtn, signaturesBtn, attachmentsBtn);

        // 4. Zoom Controls Group
        Button actualBtn = RibbonControlFactory.createLargeButton(
                "100%", MaterialDesignM.MAGNIFY_SCAN,
                "Actual size (100%)",
                e -> view.setZoom(1.0)
        );
        Button fitWidthBtn = RibbonControlFactory.createLargeButton(
                "Fit\nWidth", MaterialDesignA.ARROW_EXPAND_HORIZONTAL,
                "Fit width to window",
                e -> view.setZoom(1.3)
        );
        Button fitPageBtn = RibbonControlFactory.createLargeButton(
                "Fit\nPage", MaterialDesignF.FIT_TO_PAGE_OUTLINE,
                "Fit entire page",
                e -> view.setZoom(1.0)
        );
        MenuButton zoomMenu = createZoomPresetsMenu("Presets");
        RibbonGroup zoomGroup = RibbonControlFactory.createGroup("Zoom", actualBtn, fitWidthBtn, fitPageBtn, zoomMenu);

        // 5. Search Group
        Button searchBtn = RibbonControlFactory.createLargeButton(
                "Search", MaterialDesignM.MAGNIFY,
                "Search for keywords in document text (Ctrl+F)",
                e -> view.showSearch()
        );
        RibbonGroup searchGroup = RibbonControlFactory.createGroup("Search", searchBtn);

        // 6. Theme Group
        ToggleButton darkThemeBtn = RibbonControlFactory.createLargeToggleButton(
                "Dark\nTheme", MaterialDesignW.WEATHER_NIGHT,
                "Toggle between dark and light application theme",
                null, ThemeManager.getInstance().isDarkTheme(),
                e -> {
                    boolean isDark = !ThemeManager.getInstance().isDarkTheme();
                    ThemeManager.getInstance().setDarkTheme(isDark);
                    Scene s = view.getScene();
                    if (s != null) {
                        ThemeManager.getInstance().applyTheme(s);
                    }
                }
        );
        ThemeManager.getInstance().darkThemeProperty().addListener((obs, oldV, newV) -> {
            darkThemeBtn.setSelected(newV);
        });
        RibbonGroup themeGroup = RibbonControlFactory.createGroup("Theme", darkThemeBtn);

        tab.getRibbonGroups().addAll(displayGroup, windowGroup, panelsGroup, zoomGroup, searchGroup, themeGroup);
        return tab;
    }

    // =========================================================================
    // TAB 3: EDIT
    // =========================================================================
    private RibbonTab buildEditTab() {
        RibbonTab tab = new RibbonTab("Edit");

        // 1. Selection & Clipboard Group
        Button selectBtn = RibbonControlFactory.createLargeButton(
                "Select Text", MaterialDesignC.CURSOR_TEXT,
                "Select text for copying or annotation",
                e -> view.setHandToolActive(false)
        );
        Button copyBtn = RibbonControlFactory.createLargeButton(
                "Copy Text", MaterialDesignC.CONTENT_COPY,
                "Copy selected text (Ctrl+C)",
                e -> view.copy()
        );
        Button selectAllBtn = RibbonControlFactory.createLargeButton(
                "Select All", MaterialDesignS.SELECT_ALL,
                "Select all document text (Ctrl+A)",
                e -> view.selectAll()
        );
        RibbonGroup selectGroup = RibbonControlFactory.createGroup("Selection", selectBtn, copyBtn, selectAllBtn);

        // 2. Content Tools Group
        Button editTextBtn = RibbonControlFactory.createDisabledButton(
                "Edit Text", MaterialDesignF.FORMAT_TEXT,
                "Direct content stream text modification requires decompressed editable font streams. Use 'Add Text' or 'Sticky Note' in Comment tab.",
                true
        );
        Button editImagesBtn = RibbonControlFactory.createDisabledButton(
                "Edit Images", MaterialDesignI.IMAGE_EDIT_OUTLINE,
                "Inline raster replacement is not supported in the current document stream.",
                true
        );
        Button addTextBtn = RibbonControlFactory.createLargeButton(
                "Add Text", MaterialDesignF.FORMAT_TITLE,
                "Place text annotation box on current page",
                e -> view.addTextAnnotation()
        );
        Button addImageBtn = RibbonControlFactory.createDisabledButton(
                "Add Image", MaterialDesignI.IMAGE_PLUS,
                "Image placement is supported via Signature & Stamp tools.",
                true
        );
        Button addLinkBtn = RibbonControlFactory.createDisabledButton(
                "Add Link", MaterialDesignL.LINK_VARIANT_PLUS,
                "Hyperlink annotations will be supported in an upcoming release.",
                true
        );
        Button propertiesBtn = RibbonControlFactory.createLargeButton(
                "Properties", MaterialDesignI.INFORMATION_OUTLINE,
                "Inspect document properties and metadata",
                e -> view.showDocumentProperties()
        );

        RibbonGroup contentGroup = RibbonControlFactory.createGroup(
                "Content Editing", editTextBtn, editImagesBtn, addTextBtn, addImageBtn, addLinkBtn, propertiesBtn
        );

        tab.getRibbonGroups().addAll(selectGroup, contentGroup);
        return tab;
    }

    // =========================================================================
    // TAB 4: COMMENT
    // =========================================================================
    private RibbonTab buildCommentTab() {
        RibbonTab tab = new RibbonTab("Comment");

        // 1. Text Markup Group
        Button highlightBtn = RibbonControlFactory.createLargeButton(
                "Highlight", MaterialDesignF.FORMAT_COLOR_HIGHLIGHT,
                "Highlight text on current page",
                e -> view.highlightCurrentPage()
        );
        Button underlineBtn = RibbonControlFactory.createLargeButton(
                "Underline", MaterialDesignF.FORMAT_UNDERLINE,
                "Underline text on current page",
                e -> view.underlineCurrentPage()
        );
        Button strikethroughBtn = RibbonControlFactory.createDisabledButton(
                "Strikethrough", MaterialDesignF.FORMAT_STRIKETHROUGH_VARIANT,
                "Strikethrough markup is being finalized.",
                true
        );
        RibbonGroup markupGroup = RibbonControlFactory.createGroup("Text Markup", highlightBtn, underlineBtn, strikethroughBtn);

        // 2. Notes & Comments Group
        Button noteBtn = RibbonControlFactory.createLargeButton(
                "Sticky Note", MaterialDesignN.NOTE_TEXT_OUTLINE,
                "Place a sticky comment note on current page",
                e -> view.addStickyNote()
        );
        Button textBoxBtn = RibbonControlFactory.createLargeButton(
                "Text Box", MaterialDesignC.COMMENT_TEXT_OUTLINE,
                "Insert an editable text comment callout",
                e -> view.addTextAnnotation()
        );
        RibbonGroup notesGroup = RibbonControlFactory.createGroup("Notes & Comments", noteBtn, textBoxBtn);

        // 3. Drawing & Shapes Group
        Button pencilBtn = RibbonControlFactory.createLargeButton(
                "Pencil", MaterialDesignL.LEAD_PENCIL,
                "Freehand drawing markup tool",
                e -> view.addFreehandDrawing()
        );
        MenuButton shapesMenu = RibbonControlFactory.createMenuButton(
                "Shapes", MaterialDesignS.SHAPE_OUTLINE,
                "Insert geometric shape annotation",
                true,
                createMenuItem("Rectangle", MaterialDesignR.RECTANGLE_OUTLINE, e -> view.addFreehandDrawing()),
                createDisabledMenuItem("Line", "Line shape markup in development"),
                createDisabledMenuItem("Oval", "Oval markup in development")
        );
        RibbonGroup drawingGroup = RibbonControlFactory.createGroup("Drawing", pencilBtn, shapesMenu);

        // 4. Stamps Group
        SplitMenuButton stampBtn = RibbonControlFactory.createSplitMenuButton(
                "Stamp", MaterialDesignS.STAMPER,
                "Apply an APPROVED stamp to the current page",
                e -> view.addStamp("APPROVED"),
                true,
                createMenuItem("APPROVED", MaterialDesignC.CHECK_CIRCLE_OUTLINE, e -> view.addStamp("APPROVED")),
                createMenuItem("CONFIDENTIAL", MaterialDesignL.LOCK_OUTLINE, e -> view.addStamp("CONFIDENTIAL")),
                createMenuItem("DRAFT", MaterialDesignF.FILE_DOCUMENT_EDIT_OUTLINE, e -> view.addStamp("DRAFT")),
                createMenuItem("FINAL", MaterialDesignC.CERTIFICATE_OUTLINE, e -> view.addStamp("FINAL")),
                createMenuItem("VOID", MaterialDesignC.CLOSE_OCTAGON_OUTLINE, e -> view.addStamp("VOID"))
        );
        RibbonGroup stampGroup = RibbonControlFactory.createGroup("Stamps", stampBtn);

        // 5. Review & Management Group
        Button manageCommentsBtn = RibbonControlFactory.createLargeButton(
                "Manage", MaterialDesignF.FORMAT_LIST_BULLETED,
                "View all comments and annotations in the sidebar",
                e -> view.selectSidebarTab(0)
        );
        RibbonGroup manageGroup = RibbonControlFactory.createGroup("Review", manageCommentsBtn);

        tab.getRibbonGroups().addAll(markupGroup, notesGroup, drawingGroup, stampGroup, manageGroup);
        return tab;
    }

    // =========================================================================
    // TAB 5: ORGANIZE
    // =========================================================================
    private RibbonTab buildOrganizeTab() {
        RibbonTab tab = new RibbonTab("Organize");

        // 1. Rotate Pages Group
        Button rotateCwBtn = RibbonControlFactory.createLargeButton(
                "Rotate\nCW", MaterialDesignR.ROTATE_RIGHT,
                "Rotate current view 90 degrees clockwise",
                e -> view.rotateClockwise()
        );
        Button rotateCcwBtn = RibbonControlFactory.createLargeButton(
                "Rotate\nCCW", MaterialDesignR.ROTATE_LEFT,
                "Rotate current view 90 degrees counter-clockwise",
                e -> view.rotateCounterClockwise()
        );
        Button rotate180Btn = RibbonControlFactory.createLargeButton(
                "Rotate\n180°", MaterialDesignR.ROTATE_3D_VARIANT,
                "Rotate current view 180 degrees",
                e -> view.rotate180()
        );
        RibbonGroup rotateGroup = RibbonControlFactory.createGroup("Rotate Pages", rotateCwBtn, rotateCcwBtn, rotate180Btn);

        // 2. Page Operations Group
        Button deletePageBtn = RibbonControlFactory.createLargeButton(
                "Delete\nPage", MaterialDesignF.FILE_REMOVE_OUTLINE,
                "Delete current page from document",
                e -> view.deleteCurrentPage()
        );

        Button insertBtn = RibbonControlFactory.createSmallButton(
                "Insert Pages...", MaterialDesignF.FILE_PLUS,
                "Insert external PDF pages",
                e -> view.showDisabledFeatureNotice("Insert Pages", "Batch page insertion will be available in the next release.")
        );
        insertBtn.setDisable(true);

        Button extractBtn = RibbonControlFactory.createSmallButton(
                "Extract Pages...", MaterialDesignC.CALL_SPLIT,
                "Extract selected pages to a separate PDF",
                e -> view.showDisabledFeatureNotice("Extract Pages", "Page extraction is in development.")
        );
        extractBtn.setDisable(true);

        Column opCol1 = RibbonControlFactory.createColumn(insertBtn, extractBtn);

        Button splitBtn = RibbonControlFactory.createSmallButton(
                "Split PDF...", MaterialDesignF.FILE_DOCUMENT_MULTIPLE_OUTLINE,
                "Split document by page ranges",
                e -> view.showDisabledFeatureNotice("Split PDF", "Document splitting will be supported via batch tools.")
        );
        splitBtn.setDisable(true);

        Button mergeBtn = RibbonControlFactory.createSmallButton(
                "Merge PDFs...", MaterialDesignC.CALL_MERGE,
                "Merge multiple PDF documents",
                e -> view.showDisabledFeatureNotice("Merge PDFs", "Document merging will be supported via batch tools.")
        );
        mergeBtn.setDisable(true);

        Column opCol2 = RibbonControlFactory.createColumn(splitBtn, mergeBtn);
        RibbonGroup operationsGroup = RibbonControlFactory.createGroup("Page Operations", deletePageBtn, opCol1, opCol2);

        // 3. Document Structure Info
        Button pagePropsBtn = RibbonControlFactory.createLargeButton(
                "Page\nInfo", MaterialDesignI.INFORMATION_OUTLINE,
                "Inspect dimensions, rotation, and media box of current page",
                e -> view.showPageProperties()
        );
        RibbonGroup infoGroup = RibbonControlFactory.createGroup("Structure", pagePropsBtn);

        tab.getRibbonGroups().addAll(rotateGroup, operationsGroup, infoGroup);
        return tab;
    }

    // =========================================================================
    // TAB 6: FILL & SIGN
    // =========================================================================
    private RibbonTab buildFillSignTab() {
        RibbonTab tab = new RibbonTab("Fill & Sign");

        // 1. Form Fields Group
        Button fillFieldsBtn = RibbonControlFactory.createLargeButton(
                "Fill\nForm", MaterialDesignF.FORM_SELECT,
                "AcroForm fields are interactive directly on the page",
                e -> view.showFormNotice()
        );
        Button clearFieldsBtn = RibbonControlFactory.createLargeButton(
                "Clear\nFields", MaterialDesignB.BROOM,
                "Reset all interactive form fields to empty values",
                e -> view.clearFormFields()
        );
        RibbonGroup formsGroup = RibbonControlFactory.createGroup("Forms", fillFieldsBtn, clearFieldsBtn);

        // 2. Visual Signatures & Stamps Group
        Button addVisualSigBtn = RibbonControlFactory.createLargeButton(
                "Add\nSignature", MaterialDesignP.PEN,
                "Place visual signature mark or stamp on current page",
                e -> view.addVisualSignature()
        );
        Button addInitialsBtn = RibbonControlFactory.createLargeButton(
                "Add\nInitials", MaterialDesignA.ALPHA_I_BOX_OUTLINE,
                "Place initials mark on current page",
                e -> view.addStamp("INITIALS")
        );
        RibbonGroup visualSigGroup = RibbonControlFactory.createGroup("Visual Signature", addVisualSigBtn, addInitialsBtn);

        // 3. Digital Signatures & Validation Group
        Button validateBtn = RibbonControlFactory.createLargeButton(
                "Validate", MaterialDesignS.SHIELD_CHECK_OUTLINE,
                "Re-verify all digital signatures against TrustStore",
                e -> view.reverifySignatures()
        );
        Button trustManagerBtn = RibbonControlFactory.createLargeButton(
                "Trust\nManager", MaterialDesignC.CERTIFICATE_OUTLINE,
                "Open Certificate Trust Manager dialog",
                e -> getWindowHost().showCertificateManager()
        );
        fillSignSigGroup.setTitle("Digital Security");
        RibbonGroup digitalSigGroup = RibbonControlFactory.createGroup("Digital Signatures", validateBtn, trustManagerBtn);

        tab.getRibbonGroups().addAll(formsGroup, visualSigGroup, digitalSigGroup, fillSignSigGroup);
        return tab;
    }

    // =========================================================================
    // TAB 7: PROTECT
    // =========================================================================
    private RibbonTab buildProtectTab() {
        RibbonTab tab = new RibbonTab("Protect");

        // 1. Document Protection Group
        Button encryptBtn = RibbonControlFactory.createLargeButton(
                "Encryption", MaterialDesignL.LOCK_OUTLINE,
                "Inspect document encryption state (Standard / AES)",
                e -> view.showEncryptionStatus()
        );
        Button permissionsBtn = RibbonControlFactory.createLargeButton(
                "Permissions", MaterialDesignS.SHIELD_LOCK_OUTLINE,
                "Inspect document printing, copying, and modification permissions",
                e -> view.showPermissionsStatus()
        );
        RibbonGroup protectionGroup = RibbonControlFactory.createGroup("Document Security", encryptBtn, permissionsBtn);

        // 2. Digital Certificates Group
        Button certMgrBtn = RibbonControlFactory.createLargeButton(
                "Trust\nStore", MaterialDesignC.CERTIFICATE_OUTLINE,
                "Open Certificate Trust Manager dialog",
                e -> getWindowHost().showCertificateManager()
        );
        Button validateAllBtn = RibbonControlFactory.createLargeButton(
                "Validate\nAll", MaterialDesignS.SHIELD_CHECK_OUTLINE,
                "Cryptographically verify signatures against trust anchors",
                e -> view.reverifySignatures()
        );
        Button secPropsBtn = RibbonControlFactory.createLargeButton(
                "Security\nInfo", MaterialDesignI.INFORMATION_OUTLINE,
                "Inspect signature cryptographic algorithms and digest algorithms",
                e -> view.showSignatureDetails()
        );
        RibbonGroup certGroup = RibbonControlFactory.createGroup("Certificates & Trust", certMgrBtn, validateAllBtn, secPropsBtn);

        tab.getRibbonGroups().addAll(protectionGroup, certGroup);
        return tab;
    }

    // =========================================================================
    // TAB 8: SHARE
    // =========================================================================
    private RibbonTab buildShareTab() {
        RibbonTab tab = new RibbonTab("Share");

        // 1. Share & Send Group
        Button emailBtn = RibbonControlFactory.createLargeButton(
                "Email\nPDF", MaterialDesignE.EMAIL_OUTLINE,
                "Compose an email with PDF document attached",
                e -> view.emailDocument()
        );
        Button copyPathBtn = RibbonControlFactory.createLargeButton(
                "Copy\nPath", MaterialDesignC.CLIPBOARD_TEXT_OUTLINE,
                "Copy full file path to system clipboard",
                e -> view.copyFilePath()
        );
        Button openFolderBtn = RibbonControlFactory.createLargeButton(
                "Open\nFolder", MaterialDesignF.FOLDER_OPEN_OUTLINE,
                "Open containing folder in system file manager",
                e -> view.openContainingFolder()
        );
        RibbonGroup sendGroup = RibbonControlFactory.createGroup("Share & Send", emailBtn, copyPathBtn, openFolderBtn);

        // 2. Output Group
        Button printBtn = RibbonControlFactory.createLargeButton(
                "Print", MaterialDesignP.PRINTER,
                "Print document (Ctrl+P)",
                e -> view.print()
        );
        Button defaultViewerBtn = RibbonControlFactory.createLargeButton(
                "Default\nViewer", MaterialDesignC.COG_OUTLINE,
                "Set Oqteris as default system PDF viewer",
                e -> getWindowHost().setDefaultViewer()
        );
        RibbonGroup outputGroup = RibbonControlFactory.createGroup("Output", printBtn, defaultViewerBtn);

        tab.getRibbonGroups().addAll(sendGroup, outputGroup);
        return tab;
    }

    // =========================================================================
    // TAB 9: HELP
    // =========================================================================
    private RibbonTab buildHelpTab() {
        RibbonTab tab = new RibbonTab("Help");

        Button docBtn = RibbonControlFactory.createLargeButton(
                "Documentation", MaterialDesignH.HELP_CIRCLE_OUTLINE,
                "View Oqteris user documentation and feature guides",
                e -> view.showDocumentation()
        );
        Button shortcutsBtn = RibbonControlFactory.createLargeButton(
                "Shortcuts", MaterialDesignK.KEYBOARD_OUTLINE,
                "View keyboard shortcuts cheatsheet",
                e -> view.showKeyboardShortcuts()
        );
        Button settingsBtn = RibbonControlFactory.createLargeButton(
                "Settings", MaterialDesignC.COG_OUTLINE,
                "Configure application and viewer preferences",
                e -> getWindowHost().showSettings()
        );
        Button updateBtn = RibbonControlFactory.createLargeButton(
                "Check\nUpdates", MaterialDesignU.UPDATE,
                "Check for Oqteris updates",
                e -> view.showUpdateNotice()
        );
        Button aboutBtn = RibbonControlFactory.createLargeButton(
                "About\nOqteris", MaterialDesignI.INFORMATION_VARIANT,
                "View application version, build info, and credits",
                e -> getWindowHost().showAbout()
        );

        RibbonGroup helpGroup = RibbonControlFactory.createGroup("Help & Support", docBtn, shortcutsBtn, settingsBtn, updateBtn, aboutBtn);
        tab.getRibbonGroups().addAll(helpGroup);
        return tab;
    }

    // =========================================================================
    // CONTEXTUAL TAB: DIGITAL SIGNATURES
    // =========================================================================
    private RibbonTab buildSignatureContextualTab() {
        RibbonTab tab = new RibbonTab("Signatures (" + model.getSignatures().size() + ")");
        tab.setContextualColor("blue");
        tab.getStyleClass().add("contextual-signatures-tab");

        // 1. Status Group
        contextualStatusGroup.setTitle("Signature Status");

        // 2. Inspection & Trust Group
        Button inspectBtn = RibbonControlFactory.createLargeButton(
                "Certificate", MaterialDesignC.CERTIFICATE_OUTLINE,
                "Inspect X.509 certificate trust path and cryptographic parameters",
                e -> view.showSignatureDetails()
        );
        Button reverifyBtn = RibbonControlFactory.createLargeButton(
                "Re-Verify", MaterialDesignR.REFRESH,
                "Re-run verification against user trust store",
                e -> view.reverifySignatures()
        );
        Button certMgrBtn = RibbonControlFactory.createLargeButton(
                "Trust Store", MaterialDesignS.SHIELD_CHECK,
                "Manage trusted root certificates",
                e -> getWindowHost().showCertificateManager()
        );
        RibbonGroup actionsGroup = RibbonControlFactory.createGroup("Certificate & Trust", inspectBtn, reverifyBtn, certMgrBtn);

        // 3. Navigation Group
        Button panelBtn = RibbonControlFactory.createLargeButton(
                "View Panel", MaterialDesignV.VIEW_SPLIT_VERTICAL,
                "Open digital signatures panel in sidebar",
                e -> view.selectSidebarTab(2)
        );
        RibbonGroup navGroup = RibbonControlFactory.createGroup("Navigation", panelBtn);

        tab.getRibbonGroups().addAll(contextualStatusGroup, actionsGroup, navGroup);
        return tab;
    }

    // =========================================================================
    // HELPERS & BADGE CREATION
    // =========================================================================
    private Node createSignatureBadgeNode() {
        List<PdfSignatureInfo> sigs = model.getSignatures();
        if (sigs.isEmpty()) {
            Label unsigned = new Label("Unsigned PDF");
            unsigned.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-padding: 8 12;");
            return unsigned;
        }

        boolean allTrusted = true;
        boolean hasInvalid = false;
        boolean hasUntrusted = false;

        for (PdfSignatureInfo s : sigs) {
            if (s.getStatus() == SignatureVerificationStatus.VALID_AND_TRUSTED) {
                // ok
            } else if (s.getStatus() == SignatureVerificationStatus.VALID_BUT_UNTRUSTED) {
                hasUntrusted = true;
                allTrusted = false;
            } else {
                hasInvalid = true;
                allTrusted = false;
            }
        }

        Label badge = new Label();
        badge.setContentDisplay(ContentDisplay.LEFT);
        badge.setCursor(javafx.scene.Cursor.HAND);
        badge.setOnMouseClicked(e -> view.selectSidebarTab(2));

        if (hasInvalid) {
            badge.setText(" Signature Invalid / Modified");
            badge.setGraphic(AppIcons.of(MaterialDesignS.SHIELD_OFF, 18, javafx.scene.paint.Color.WHITE));
            badge.getStyleClass().setAll("signature-badge-invalid");
            RibbonControlFactory.attachTooltip(badge, "Document has been modified or signature digest does not match.");
        } else if (hasUntrusted) {
            badge.setText(" Valid (Untrusted Root CA)");
            badge.setGraphic(AppIcons.of(MaterialDesignS.SHIELD_ALERT, 18, javafx.scene.paint.Color.rgb(30, 41, 59)));
            badge.getStyleClass().setAll("signature-badge-warning");
            RibbonControlFactory.attachTooltip(badge, "Document integrity is valid, but the certificate root CA is not in your Trust Store.");
        } else if (allTrusted) {
            badge.setText(" Digitally Signed & Trusted");
            badge.setGraphic(AppIcons.of(MaterialDesignS.SHIELD_CHECK, 18, javafx.scene.paint.Color.WHITE));
            badge.getStyleClass().setAll("signature-badge-trusted");
            RibbonControlFactory.attachTooltip(badge, "All digital signatures are valid and issued by trusted certificates.");
        }

        return badge;
    }

    private MenuButton createZoomPresetsMenu(String title) {
        MenuButton menu = RibbonControlFactory.createMenuButton(
                title, MaterialDesignM.MAGNIFY, "Choose zoom percentage preset", true,
                createMenuItem("25%", null, e -> view.setZoom(0.25)),
                createMenuItem("50%", null, e -> view.setZoom(0.50)),
                createMenuItem("75%", null, e -> view.setZoom(0.75)),
                createMenuItem("100% (Actual Size)", null, e -> view.setZoom(1.00)),
                createMenuItem("125%", null, e -> view.setZoom(1.25)),
                createMenuItem("150%", null, e -> view.setZoom(1.50)),
                createMenuItem("200%", null, e -> view.setZoom(2.00)),
                createMenuItem("300%", null, e -> view.setZoom(3.00)),
                createMenuItem("400%", null, e -> view.setZoom(4.00)),
                new SeparatorMenuItem(),
                createMenuItem("Fit Page", null, e -> view.setZoom(1.00)),
                createMenuItem("Fit Width", null, e -> view.setZoom(1.30))
        );
        return menu;
    }

    private MenuItem createMenuItem(String text, MaterialDesignR icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        MenuItem item = new MenuItem(text);
        if (icon != null) {
            item.setGraphic(AppIcons.of(icon, 14));
        }
        if (handler != null) {
            item.setOnAction(handler);
        }
        return item;
    }

    private MenuItem createMenuItem(String text, org.kordamp.ikonli.Ikon icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        MenuItem item = new MenuItem(text);
        if (icon != null) {
            item.setGraphic(AppIcons.of(icon, 14));
        }
        if (handler != null) {
            item.setOnAction(handler);
        }
        return item;
    }

    private MenuItem createDisabledMenuItem(String text, String tooltipReason) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    private MainWindowHost getWindowHost() {
        return view.getWindowHost();
    }
}