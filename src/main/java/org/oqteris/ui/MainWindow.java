package org.oqteris.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.core.model.DocumentType;
import org.oqteris.core.recovery.AutosaveService;
import org.oqteris.crypto.trust.CertificateManagerDialog;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Primary application window for Oqteris Office Suite.
 */
public class MainWindow extends BorderPane {
    private final Stage primaryStage;
    private final TabPane documentTabPane = new TabPane();
    private final VBox topContainer = new VBox();
    private final StackPane centerContainer = new StackPane();
    private final HBox ribbonContainer = new HBox();

    // Status bar components
    private final HBox statusBar = new HBox(12);
    private final Label statusMessage = new Label("Ready");
    private final StackPane contextualStatusContainer = new StackPane();
    private final Slider zoomSlider = new Slider(25, 400, 100);
    private final Label zoomLabel = new Label("100%");
    private final ProgressBar backgroundProgress = new ProgressBar();

    private WelcomeScreen welcomeScreen;
    private final List<DocumentView> activeViews = new ArrayList<>();

    // Document creation/open factories
    private Function<DocumentType, DocumentView> documentFactory;
    private Function<File, DocumentView> fileOpenHandler;

    public MainWindow(Stage primaryStage) {
        this.primaryStage = primaryStage;
        AutosaveService.getInstance().setDocumentsSupplier(this::getAllOpenDocuments);

        buildUI();
        setupDragAndDrop();
        setupWindowCloseHandler();
    }

    public void setDocumentFactory(Function<DocumentType, DocumentView> factory) {
        this.documentFactory = factory;
    }

    public void setFileOpenHandler(Function<File, DocumentView> handler) {
        this.fileOpenHandler = handler;
    }

    private void buildUI() {
        // App icon
        try (InputStream in = getClass().getResourceAsStream("/icons/oqteris-logo.png")) {
            if (in != null) {
                primaryStage.getIcons().add(new Image(in));
            }
        } catch (Exception ignored) {
        }

        // Top Area: Menu Bar + Quick Access / Ribbon
        MenuBar menuBar = createMenuBar();
        ribbonContainer.getStyleClass().add("ribbon-bar");
        ribbonContainer.setAlignment(Pos.CENTER_LEFT);
        ribbonContainer.setPadding(Insets.EMPTY);

        topContainer.getChildren().addAll(menuBar, ribbonContainer);
        setTop(topContainer);

        // Center Area: Welcome Screen or TabPane
        documentTabPane.getStyleClass().add("document-tab-pane");
        documentTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        documentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateActiveView();
        });

        welcomeScreen = new WelcomeScreen(this::createNewDocument, this::showOpenDialog, this::openFile);
        centerContainer.getChildren().addAll(welcomeScreen, documentTabPane);
        documentTabPane.setVisible(false);

        setCenter(centerContainer);

        // Status Bar
        setupStatusBar();
        setBottom(statusBar);
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // --- File Menu ---
        Menu fileMenu = new Menu("File");
        Menu newSubMenu = new Menu("New");
        MenuItem newDoc = new MenuItem("Text Document (Writer)");
        newDoc.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newDoc.setOnAction(e -> createNewDocument(DocumentType.WRITER));

        MenuItem newSheet = new MenuItem("Spreadsheet (Calc)");
        newSheet.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        newSheet.setOnAction(e -> createNewDocument(DocumentType.CALC));

        MenuItem newPresentation = new MenuItem("Presentation (Impress)");
        newPresentation.setOnAction(e -> createNewDocument(DocumentType.IMPRESS));

        newSubMenu.getItems().addAll(newDoc, newSheet, newPresentation);

        MenuItem openItem = new MenuItem("Open...");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> showOpenDialog());

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> saveActiveDocument(false));

        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAsItem.setOnAction(e -> saveActiveDocument(true));

        MenuItem printItem = new MenuItem("Print...");
        printItem.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        printItem.setOnAction(e -> {
            DocumentView view = getActiveView();
            if (view != null) view.print();
        });

        MenuItem closeItem = new MenuItem("Close Document");
        closeItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        closeItem.setOnAction(e -> closeActiveTab());

        MenuItem settingsItem = new MenuItem("Settings...");
        settingsItem.setOnAction(e -> new SettingsDialog(primaryStage).showAndWait());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        fileMenu.getItems().addAll(newSubMenu, openItem, new SeparatorMenuItem(),
                saveItem, saveAsItem, new SeparatorMenuItem(),
                printItem, closeItem, new SeparatorMenuItem(),
                settingsItem, exitItem);

        // --- Edit Menu ---
        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null && v.getDocument().getUndoRedoManager().canUndo()) {
                v.getDocument().getUndoRedoManager().undo();
            }
        });

        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null && v.getDocument().getUndoRedoManager().canRedo()) {
                v.getDocument().getUndoRedoManager().redo();
            }
        });

        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cutItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null) v.cut();
        });

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copyItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null) v.copy();
        });

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        pasteItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null) v.paste();
        });

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selectAllItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null) v.selectAll();
        });

        MenuItem findItem = new MenuItem("Find...");
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        findItem.setOnAction(e -> {
            DocumentView v = getActiveView();
            if (v != null) v.showSearch();
        });

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(),
                cutItem, copyItem, pasteItem, new SeparatorMenuItem(),
                selectAllItem, findItem);

        // --- View Menu ---
        Menu viewMenu = new Menu("View");
        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN));
        zoomIn.setOnAction(e -> setZoom(zoomSlider.getValue() + 15));

        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOut.setOnAction(e -> setZoom(zoomSlider.getValue() - 15));

        MenuItem zoomReset = new MenuItem("Actual Size (100%)");
        zoomReset.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        zoomReset.setOnAction(e -> setZoom(100));

        CheckMenuItem toggleDark = new CheckMenuItem("Dark Theme");
        toggleDark.selectedProperty().bindBidirectional(ThemeManager.getInstance().darkThemeProperty());
        toggleDark.setOnAction(e -> {
            Scene s = primaryStage.getScene();
            if (s != null) ThemeManager.getInstance().applyTheme(s);
        });

        viewMenu.getItems().addAll(zoomIn, zoomOut, zoomReset, new SeparatorMenuItem(), toggleDark);

        // --- Security & Tools Menu ---
        Menu toolsMenu = new Menu("Tools");
        MenuItem certManager = new MenuItem("Certificate Trust Manager...");
        certManager.setOnAction(e -> new CertificateManagerDialog(primaryStage).showAndWait());

        toolsMenu.getItems().addAll(certManager);

        // --- Help Menu ---
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About Oqteris...");
        aboutItem.setOnAction(e -> new AboutDialog(primaryStage).showAndWait());
        helpMenu.getItems().addAll(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private void setupStatusBar() {
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(3, 10, 3, 10));

        backgroundProgress.setVisible(false);
        backgroundProgress.setPrefWidth(90);
        backgroundProgress.setPrefHeight(10);

        Label autosaveChip = new Label("● Autosaved");
        autosaveChip.setStyle("-fx-font-size: 11px; -fx-text-fill: #10b981; -fx-padding: 2 6; -fx-background-radius: 4;");

        HBox leftStatus = new HBox(8, statusMessage, backgroundProgress, autosaveChip);
        leftStatus.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Zoom controls
        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");
        zoomOutBtn.setOnAction(e -> setZoom(zoomSlider.getValue() - 15));

        Button zoomInBtn = new Button("+");
        zoomInBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");
        zoomInBtn.setOnAction(e -> setZoom(zoomSlider.getValue() + 15));

        zoomSlider.setPrefWidth(110);
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int rounded = (int) Math.round(newVal.doubleValue());
            zoomLabel.setText(rounded + "%");
            DocumentView view = getActiveView();
            if (view != null) {
                view.setZoom(newVal.doubleValue() / 100.0);
            }
        });

        zoomLabel.setStyle("-fx-font-size: 11px; -fx-min-width: 38px; -fx-alignment: center-right;");
        zoomLabel.setOnMouseClicked(e -> setZoom(100));
        zoomLabel.setCursor(javafx.scene.Cursor.HAND);

        HBox zoomControls = new HBox(5, zoomOutBtn, zoomSlider, zoomInBtn, zoomLabel);
        zoomControls.setAlignment(Pos.CENTER_RIGHT);

        statusBar.getChildren().addAll(leftStatus, spacer, contextualStatusContainer, zoomControls);
    }

    public void setStatus(String message) {
        Platform.runLater(() -> statusMessage.setText(message));
    }

    public void setBusy(boolean busy, String message) {
        Platform.runLater(() -> {
            statusMessage.setText(message);
            backgroundProgress.setVisible(busy);
        });
    }

    public void setZoom(double percentage) {
        zoomSlider.setValue(Math.max(25, Math.min(400, percentage)));
    }

    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    openFile(file);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupWindowCloseHandler() {
        primaryStage.setOnCloseRequest(event -> {
            for (Tab tab : new ArrayList<>(documentTabPane.getTabs())) {
                DocumentView view = (DocumentView) tab.getUserData();
                if (view != null && view.getDocument().isModified()) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            "Document '" + view.getDocument().getTitle() + "' has unsaved changes. Save before closing?",
                            ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                    alert.initOwner(primaryStage);
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent()) {
                        if (result.get() == ButtonType.YES) {
                            saveActiveDocument(false);
                        } else if (result.get() == ButtonType.CANCEL) {
                            event.consume();
                            return;
                        }
                    }
                }
            }
        });
    }

    public void createNewDocument(DocumentType type) {
        if (documentFactory != null) {
            DocumentView view = documentFactory.apply(type);
            if (view != null) {
                addDocumentView(view);
            }
        }
    }

    public void showOpenDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Document - Oqteris");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported Office & PDF Files", "*.docx", "*.xlsx", "*.pptx", "*.pdf", "*.doc", "*.xls", "*.ppt", "*.odt", "*.ods", "*.odp", "*.rtf", "*.txt", "*.csv"),
                new FileChooser.ExtensionFilter("Word Documents (*.docx, *.doc, *.rtf, *.txt)", "*.docx", "*.doc", "*.rtf", "*.txt"),
                new FileChooser.ExtensionFilter("Spreadsheets (*.xlsx, *.xls, *.csv)", "*.xlsx", "*.xls", "*.csv"),
                new FileChooser.ExtensionFilter("Presentations (*.pptx, *.ppt)", "*.pptx", "*.ppt"),
                new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            openFile(file);
        }
    }

    public void openFile(File file) {
        if (file == null || !file.exists()) return;

        // Check if already open
        for (Tab tab : documentTabPane.getTabs()) {
            DocumentView v = (DocumentView) tab.getUserData();
            if (v != null && v.getDocument().getFile() != null &&
                    v.getDocument().getFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                documentTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        setBusy(true, "Opening " + file.getName() + "...");
        AsyncExecutor.execute(
                () -> {
                    if (fileOpenHandler != null) {
                        return fileOpenHandler.apply(file);
                    }
                    return null;
                },
                view -> {
                    setBusy(false, "Ready");
                    if (view != null) {
                        addDocumentView(view);
                        RecentFilesManager.getInstance().addFile(file, view.getDocument().getType());
                        welcomeScreen.refreshRecentFiles();
                    } else {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to open document: " + file.getName(), ButtonType.OK);
                        alert.initOwner(primaryStage);
                        alert.showAndWait();
                    }
                },
                err -> {
                    setBusy(false, "Error");
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error opening file: " + err.getMessage(), ButtonType.OK);
                    alert.initOwner(primaryStage);
                    alert.showAndWait();
                }
        );
    }

    public void addDocumentView(DocumentView view) {
        if (view == null) return;
        activeViews.add(view);

        Tab tab = new Tab();
        tab.setUserData(view);
        updateTabTitle(tab, view.getDocument());

        view.getDocument().addDocumentListener(doc -> Platform.runLater(() -> updateTabTitle(tab, doc)));

        if (view instanceof Node node) {
            tab.setContent(node);
        }

        tab.setOnCloseRequest(e -> {
            if (view.getDocument().isModified()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Save changes to '" + view.getDocument().getTitle() + "' before closing?",
                        ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                alert.initOwner(primaryStage);
                Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent()) {
                    if (res.get() == ButtonType.YES) {
                        saveDocument(view, false);
                    } else if (res.get() == ButtonType.CANCEL) {
                        e.consume();
                        return;
                    }
                }
            }
            view.close();
            activeViews.remove(view);
            AutosaveService.getInstance().clearRecovery(view.getDocument());
        });

        documentTabPane.getTabs().add(tab);
        documentTabPane.getSelectionModel().select(tab);

        updateTabVisibility();
    }

    private void updateTabTitle(Tab tab, DocumentModel doc) {
        String title = doc.getTitle();
        if (doc.isModified()) {
            title += " ●";
        }
        tab.setText(title);

        Label typeBadge = new Label(doc.getType().name());
        switch (doc.getType()) {
            case WRITER -> typeBadge.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-padding: 1 5; -fx-background-radius: 4; -fx-font-size: 9px; -fx-font-weight: bold;");
            case CALC -> typeBadge.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-padding: 1 5; -fx-background-radius: 4; -fx-font-size: 9px; -fx-font-weight: bold;");
            case IMPRESS -> typeBadge.setStyle("-fx-background-color: #ea580c; -fx-text-fill: white; -fx-padding: 1 5; -fx-background-radius: 4; -fx-font-size: 9px; -fx-font-weight: bold;");
            case PDF -> typeBadge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-padding: 1 5; -fx-background-radius: 4; -fx-font-size: 9px; -fx-font-weight: bold;");
        }
        tab.setGraphic(typeBadge);

        primaryStage.setTitle(title + " - Oqteris Office Suite");
    }

    private void updateTabVisibility() {
        boolean hasTabs = !documentTabPane.getTabs().isEmpty();
        documentTabPane.setVisible(hasTabs);
        welcomeScreen.setVisible(!hasTabs);
        if (!hasTabs) {
            primaryStage.setTitle("Oqteris Office Suite");
            ribbonContainer.getChildren().clear();
            contextualStatusContainer.getChildren().clear();
            welcomeScreen.checkRecovery();
        } else {
            updateActiveView();
        }
    }

    private void updateActiveView() {
        DocumentView view = getActiveView();
        ribbonContainer.getChildren().clear();
        contextualStatusContainer.getChildren().clear();

        if (view != null) {
            Node toolbar = view.getRibbonToolbar();
            if (toolbar != null) {
                if (toolbar instanceof Control c) {
                    c.setMaxWidth(Double.MAX_VALUE);
                }
                HBox.setHgrow(toolbar, Priority.ALWAYS);
                ribbonContainer.getChildren().add(toolbar);
            }
            Node statusNode = view.getStatusBarNode();
            if (statusNode != null) {
                contextualStatusContainer.getChildren().add(statusNode);
            }
            zoomSlider.setValue(view.getZoom() * 100);
            primaryStage.setTitle(view.getDocument().getTitle() + (view.getDocument().isModified() ? " *" : "") + " - Oqteris Office Suite");
        }
    }

    public DocumentView getActiveView() {
        Tab tab = documentTabPane.getSelectionModel().getSelectedItem();
        return tab != null ? (DocumentView) tab.getUserData() : null;
    }

    public void saveActiveDocument(boolean saveAs) {
        DocumentView view = getActiveView();
        if (view != null) {
            saveDocument(view, saveAs);
        }
    }

    public void saveDocument(DocumentView view, boolean saveAs) {
        if (view == null) return;
        DocumentModel doc = view.getDocument();
        File target = doc.getFile();

        if (target == null || saveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Document - Oqteris");
            switch (doc.getType()) {
                case WRITER -> chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx"),
                        new FileChooser.ExtensionFilter("Rich Text Format (*.rtf)", "*.rtf"),
                        new FileChooser.ExtensionFilter("OpenDocument Text (*.odt)", "*.odt"),
                        new FileChooser.ExtensionFilter("Plain Text Document (*.txt)", "*.txt"),
                        new FileChooser.ExtensionFilter("HTML Document (*.html)", "*.html")
                );
                case CALC -> chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"),
                        new FileChooser.ExtensionFilter("Excel 97-2003 Workbook (*.xls)", "*.xls"),
                        new FileChooser.ExtensionFilter("Comma-Separated Values (*.csv)", "*.csv")
                );
                case IMPRESS -> chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("PowerPoint Presentation (*.pptx)", "*.pptx")
                );
                default -> {
                    String ext = doc.getType().getDefaultExtension();
                    chooser.getExtensionFilters().add(
                            new FileChooser.ExtensionFilter(doc.getType().getDisplayName() + " (*." + ext + ")", "*." + ext)
                    );
                }
            }

            if (target != null) {
                chooser.setInitialDirectory(target.getParentFile());
                chooser.setInitialFileName(target.getName());
            } else {
                String defExt = doc.getType().getDefaultExtension();
                chooser.setInitialFileName(doc.getTitle().contains(".") ? doc.getTitle() : doc.getTitle() + "." + defExt);
            }
            target = chooser.showSaveDialog(primaryStage);
            if (target == null) return;
        }

        final File saveTarget = target;
        setBusy(true, "Saving " + saveTarget.getName() + "...");

        AsyncExecutor.execute(
                () -> {
                    try {
                        Optional<DocumentExporter<DocumentModel>> exporter = DocumentRegistry.getInstance().findExporter(saveTarget, doc.getType());
                        if (exporter.isPresent()) {
                            exporter.get().exportDocument(doc, saveTarget);
                            return true;
                        } else {
                            throw new IllegalStateException("No registered exporter for " + saveTarget.getName());
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                success -> {
                    doc.setFile(saveTarget);
                    doc.setModified(false);
                    AutosaveService.getInstance().clearRecovery(doc);
                    RecentFilesManager.getInstance().addFile(saveTarget, doc.getType());
                    setBusy(false, "Document saved");
                },
                err -> {
                    setBusy(false, "Save failed");
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save document: " + err.getMessage(), ButtonType.OK);
                    alert.initOwner(primaryStage);
                    alert.showAndWait();
                }
        );
    }

    public void closeActiveTab() {
        Tab tab = documentTabPane.getSelectionModel().getSelectedItem();
        if (tab != null) {
            documentTabPane.getTabs().remove(tab);
            updateTabVisibility();
        }
    }

    public List<DocumentModel> getAllOpenDocuments() {
        List<DocumentModel> list = new ArrayList<>();
        for (DocumentView v : activeViews) {
            list.add(v.getDocument());
        }
        return list;
    }
}
