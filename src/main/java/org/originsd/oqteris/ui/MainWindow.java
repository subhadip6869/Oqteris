package org.originsd.oqteris.ui;

import javafx.application.Platform;
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
import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.model.DocumentModel;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.core.recovery.AutosaveService;
import org.originsd.oqteris.crypto.trust.CertificateManagerDialog;
import org.originsd.oqteris.platform.DefaultViewerService;
import org.originsd.oqteris.platform.DefaultViewerServiceFactory;
import org.originsd.oqteris.platform.PdfFileOpenHelper;
import org.originsd.oqteris.platform.RegistrationResult;
import org.originsd.oqteris.ui.icon.AppResources;
import org.originsd.oqteris.ui.ribbon.MainWindowHost;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Primary application window for Oqteris PDF Reader and Editor.
 */
public class MainWindow extends BorderPane implements MainWindowHost {
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
        Image appIcon = AppResources.getAppLogo();
        if (appIcon != null) {
            primaryStage.getIcons().add(appIcon);
        }

        // Top Area: Dedicated Ribbon Container (MenuBar eliminated in favour of professional desktop ribbon)
        topContainer.getStyleClass().add("top-container");
        ribbonContainer.getStyleClass().add("ribbon-bar");
        ribbonContainer.setAlignment(Pos.CENTER_LEFT);
        ribbonContainer.setPadding(Insets.EMPTY);

        topContainer.getChildren().setAll(ribbonContainer);
        setTop(topContainer);
        setupSceneAccelerators();

        // Center Area: Welcome Screen or TabPane
        documentTabPane.getStyleClass().add("document-tab-pane");
        documentTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        documentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateActiveView();
        });

        welcomeScreen = new WelcomeScreen(
                this::showOpenDialog,
                this::openFile,
                () -> new SettingsDialog(primaryStage).showAndWait(),
                () -> new CertificateManagerDialog(primaryStage).showAndWait()
        );
        centerContainer.getChildren().addAll(welcomeScreen, documentTabPane);
        documentTabPane.setVisible(false);

        setCenter(centerContainer);

        // Status Bar
        setupStatusBar();
        setBottom(statusBar);
    }

    private void setupSceneAccelerators() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                // Ctrl+O: Open PDF
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                        this::showOpenDialog
                );
                // Ctrl+S: Save
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                        () -> saveActiveDocument(false)
                );
                // Ctrl+Shift+S: Save As
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> saveActiveDocument(true)
                );
                // Ctrl+P: Print
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView view = getActiveView();
                            if (view != null) view.print();
                        }
                );
                // Ctrl+W: Close Tab
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                        this::closeActiveTab
                );
                // Ctrl+Z: Undo
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null && v.getDocument().getUndoRedoManager().canUndo()) {
                                v.getDocument().getUndoRedoManager().undo();
                            }
                        }
                );
                // Ctrl+Y: Redo
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null && v.getDocument().getUndoRedoManager().canRedo()) {
                                v.getDocument().getUndoRedoManager().redo();
                            }
                        }
                );
                // Ctrl+X: Cut
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null) v.cut();
                        }
                );
                // Ctrl+C: Copy
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null) v.copy();
                        }
                );
                // Ctrl+V: Paste
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null) v.paste();
                        }
                );
                // Ctrl+A: Select All
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null) v.selectAll();
                        }
                );
                // Ctrl+F: Find
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                        () -> {
                            DocumentView v = getActiveView();
                            if (v != null) v.showSearch();
                        }
                );
                // Ctrl+Plus / Ctrl+=: Zoom In
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN),
                        () -> setZoom(zoomSlider.getValue() + 15)
                );
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN),
                        () -> setZoom(zoomSlider.getValue() + 15)
                );
                // Ctrl+Minus: Zoom Out
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                        () -> setZoom(zoomSlider.getValue() - 15)
                );
                // Ctrl+0: Actual Size
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                        () -> setZoom(100)
                );
                // F11: Full Screen
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F11),
                        this::toggleFullScreen
                );
            }
        });
    }

    private void handleSetDefaultViewer() {
        DefaultViewerService service = DefaultViewerServiceFactory.getInstance();
        RegistrationResult result = service.registerAsDefault();

        Alert alert;
        if (result.isSuccess()) {
            alert = new Alert(Alert.AlertType.INFORMATION, result.getMessage(), ButtonType.OK);
            alert.setHeaderText("Default PDF Viewer Configured");
        } else if (result.isGuidanceRequired()) {
            alert = new Alert(Alert.AlertType.INFORMATION, result.getMessage() + "\n\n" + result.getInstructions(), ButtonType.OK);
            alert.setHeaderText("Action Required in System Settings");
        } else if (result.getOutcome() == RegistrationResult.Outcome.UNSUPPORTED) {
            alert = new Alert(Alert.AlertType.WARNING, result.getMessage(), ButtonType.OK);
            alert.setHeaderText("Platform Limitation");
        } else {
            alert = new Alert(Alert.AlertType.ERROR, result.getMessage(), ButtonType.OK);
            alert.setHeaderText("Registration Error");
        }
        alert.initOwner(primaryStage);
        alert.showAndWait();
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
                    File validPdf = PdfFileOpenHelper.validatePdfFile(file.getAbsolutePath());
                    if (validPdf != null) {
                        openFile(validPdf);
                    }
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
        chooser.setTitle("Open PDF Document - Oqteris");
        chooser.getExtensionFilters().addAll(
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
                    setBusy(false, "Ready");
                    if (!(err instanceof UserCancelledException || (err.getCause() instanceof UserCancelledException))) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Error opening file: " + err.getMessage(), ButtonType.OK);
                        alert.initOwner(primaryStage);
                        alert.showAndWait();
                    }
                }
        );
    }

    public void addDocumentView(DocumentView view) {
        if (view == null) return;
        activeViews.add(view);

        if (view instanceof org.originsd.oqteris.pdf.ui.PdfDocumentView pdfView) {
            pdfView.setWindowHost(this);
        }

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

        Label typeBadge = new Label("PDF");
        typeBadge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-padding: 1 5; -fx-background-radius: 4; -fx-font-size: 9px; -fx-font-weight: bold;");
        tab.setGraphic(typeBadge);

        primaryStage.setTitle(title + " - Oqteris");
    }

    private void updateTabVisibility() {
        boolean hasTabs = !documentTabPane.getTabs().isEmpty();
        documentTabPane.setVisible(hasTabs);
        welcomeScreen.setVisible(!hasTabs);
        if (!hasTabs) {
            primaryStage.setTitle("Oqteris");
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
            primaryStage.setTitle(view.getDocument().getTitle() + (view.getDocument().isModified() ? " *" : "") + " - Oqteris");
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
            chooser.setTitle("Save PDF Document - Oqteris");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf")
            );

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

    @Override
    public void printActiveDocument() {
        DocumentView view = getActiveView();
        if (view != null) {
            view.print();
        }
    }

    @Override
    public void closeActiveDocument() {
        closeActiveTab();
    }

    @Override
    public void showSettings() {
        new SettingsDialog(primaryStage).showAndWait();
    }

    @Override
    public void showAbout() {
        new AboutDialog(primaryStage).showAndWait();
    }

    @Override
    public void showCertificateManager() {
        new CertificateManagerDialog(primaryStage).showAndWait();
    }

    @Override
    public void setDefaultViewer() {
        handleSetDefaultViewer();
    }

    @Override
    public void toggleFullScreen() {
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }
}