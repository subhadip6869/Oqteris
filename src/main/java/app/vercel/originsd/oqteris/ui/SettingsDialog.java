package app.vercel.originsd.oqteris.ui;

import app.vercel.originsd.oqteris.config.*;
import app.vercel.originsd.oqteris.core.recovery.AutosaveService;
import app.vercel.originsd.oqteris.crypto.trust.CertificateManagerDialog;
import app.vercel.originsd.oqteris.platform.DefaultViewerService;
import app.vercel.originsd.oqteris.platform.DefaultViewerServiceFactory;
import app.vercel.originsd.oqteris.platform.DefaultViewerStatus;
import app.vercel.originsd.oqteris.platform.RegistrationResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Preferences dialog for application-wide PDF settings and system defaults,
 * adhering to the OriginSD Settings Management & Theme System Standard.
 */
public class SettingsDialog extends Stage {

    private final UserSettingsService settingsService = UserSettingsService.getInstance();
    private final DefaultViewerService defaultViewerService = DefaultViewerServiceFactory.getInstance();
    private final Label defaultStatusLabel = new Label();
    private final Button setDefaultBtn = new Button("Set Oqteris as Default PDF Viewer");

    public SettingsDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Settings - Oqteris");

        UserSettings current = settingsService.getSettings();

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setPrefWidth(560);

        Label title = new Label("PDF Reader Preferences");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // General settings grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        // 1. Author Name (for annotations and metadata)
        Label authorLbl = new Label("Default Author Name:");
        TextField authorField = new TextField(current.getDefaultAuthor());
        authorField.setPromptText("Your name or organization");
        grid.addRow(0, authorLbl, authorField);

        // 2. Autosave
        AutosaveService autosave = AutosaveService.getInstance();
        CheckBox autosaveCheck = new CheckBox("Enable periodic autosave");
        autosaveCheck.setSelected(current.isAutosaveEnabled());

        Spinner<Integer> intervalSpinner = new Spinner<>(1, 30, current.getAutosaveIntervalMinutes());
        intervalSpinner.setEditable(true);
        intervalSpinner.setPrefWidth(80);

        HBox intervalBox = new HBox(8, intervalSpinner, new Label("minutes"));
        intervalBox.setAlignment(Pos.CENTER_LEFT);
        intervalBox.disableProperty().bind(autosaveCheck.selectedProperty().not());

        grid.addRow(1, new Label("Autosave Recovery:"), autosaveCheck);
        grid.addRow(2, new Label("Autosave Interval:"), intervalBox);

        // 3. Theme
        ThemeManager themeMgr = ThemeManager.getInstance();
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("System", "Light", "Dark");
        String currentThemeName = current.getTheme();
        if ("DARK".equalsIgnoreCase(currentThemeName)) {
            themeCombo.setValue("Dark");
        } else if ("LIGHT".equalsIgnoreCase(currentThemeName)) {
            themeCombo.setValue("Light");
        } else {
            themeCombo.setValue("System");
        }
        grid.addRow(3, new Label("Interface Theme:"), themeCombo);

        // 4. Typography adjustment (fontSizeDelta)
        Spinner<Integer> fontDeltaSpinner = new Spinner<>(-4, 12, current.getFontSizeDelta());
        fontDeltaSpinner.setEditable(true);
        fontDeltaSpinner.setPrefWidth(80);
        Label fontDeltaNote = new Label("Scale font size (-4 to +12 px)");
        fontDeltaNote.getStyleClass().add("text-muted");
        HBox fontDeltaBox = new HBox(8, fontDeltaSpinner, fontDeltaNote);
        fontDeltaBox.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(4, new Label("Font Size Adjustment:"), fontDeltaBox);

        // 5. Document View Mode
        ComboBox<ViewMode> viewModeCombo = new ComboBox<>();
        viewModeCombo.getItems().addAll(ViewMode.FIT_WIDTH, ViewMode.FIT_PAGE, ViewMode.ACTUAL_SIZE);
        viewModeCombo.setValue(current.getViewMode());
        grid.addRow(5, new Label("Default View Mode:"), viewModeCombo);

        // 6. Page Layout
        ComboBox<PageLayoutMode> pageLayoutCombo = new ComboBox<>();
        pageLayoutCombo.getItems().addAll(PageLayoutMode.CONTINUOUS, PageLayoutMode.SINGLE_PAGE);
        pageLayoutCombo.setValue(current.getPageLayout());
        grid.addRow(6, new Label("Default Page Layout:"), pageLayoutCombo);

        // 7. Cursor Interaction Mode
        ComboBox<CursorMode> cursorCombo = new ComboBox<>();
        cursorCombo.getItems().addAll(CursorMode.SELECT, CursorMode.HAND);
        cursorCombo.setValue(current.getCursorMode());
        grid.addRow(7, new Label("Default Cursor Tool:"), cursorCombo);

        // 8. Navigation Sidebar
        CheckBox sidebarCheck = new CheckBox("Open navigation sidebar on document launch");
        sidebarCheck.setSelected(current.isSidebarOpen());
        grid.addRow(8, new Label("Navigation Sidebar:"), sidebarCheck);

        // 9. Default PDF Viewer
        updateDefaultViewerStatus();
        setDefaultBtn.setOnAction(e -> handleSetDefaultViewer());

        VBox defaultViewerBox = new VBox(6, defaultStatusLabel, setDefaultBtn);
        defaultViewerBox.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(9, new Label("Default PDF Viewer:"), defaultViewerBox);

        // 10. Certificate Management Button
        Button openCertMgrBtn = new Button("Manage Trusted Certificates...");
        openCertMgrBtn.setOnAction(e -> {
            CertificateManagerDialog certDialog = new CertificateManagerDialog(this);
            certDialog.sizeToScene();
            certDialog.showAndWait();
        });
        grid.addRow(10, new Label("Security & Trust:"), openCertMgrBtn);

        // Buttons
        Button saveBtn = new Button("Save Changes");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            String author = authorField.getText().trim();
            boolean autoEnabled = autosaveCheck.isSelected();
            int interval = intervalSpinner.getValue();
            String selectedTheme = themeCombo.getValue().toUpperCase();
            int fontDelta = fontDeltaSpinner.getValue();

            ViewMode selectedViewMode = viewModeCombo.getValue() != null ? viewModeCombo.getValue() : ViewMode.FIT_WIDTH;
            PageLayoutMode selectedPageLayout = pageLayoutCombo.getValue() != null ? pageLayoutCombo.getValue() : PageLayoutMode.CONTINUOUS;
            CursorMode selectedCursor = cursorCombo.getValue() != null ? cursorCombo.getValue() : CursorMode.SELECT;
            boolean selectedSidebarOpen = sidebarCheck.isSelected();

            // Persist to settings.json
            settingsService.update(s -> {
                s.setDefaultAuthor(author.isBlank() ? "Oqteris User" : author);
                s.setAutosaveEnabled(autoEnabled);
                s.setAutosaveIntervalMinutes(interval);
                s.setTheme(selectedTheme);
                s.setFontSizeDelta(fontDelta);
                s.setViewMode(selectedViewMode);
                s.setPageLayout(selectedPageLayout);
                s.setCursorMode(selectedCursor);
                s.setSidebarOpen(selectedSidebarOpen);
            });

            // Apply immediately to runtime services
            autosave.setEnabled(autoEnabled);
            autosave.setIntervalSeconds(interval * 60);
            themeMgr.setTheme(selectedTheme);
            themeMgr.setFontSizeDelta(fontDelta);

            close();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> close());

        HBox btnBox = new HBox(10, new Region(), cancelBtn, saveBtn);
        HBox.setHgrow(btnBox.getChildren().getFirst(), Priority.ALWAYS);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
        sizeToScene();

        // Refresh default viewer status when dialog regains focus
        focusedProperty().addListener((obs, wasF, isF) -> {
            if (isF) {
                updateDefaultViewerStatus();
            }
        });
    }

    private void updateDefaultViewerStatus() {
        DefaultViewerStatus status = defaultViewerService.checkStatus();
        switch (status) {
            case DEFAULT -> {
                defaultStatusLabel.setText("● Oqteris is your default PDF viewer");
                defaultStatusLabel.setStyle("-fx-text-fill: -color-success; -fx-font-weight: bold; -fx-font-size: 12px;");
                setDefaultBtn.setDisable(true);
                setDefaultBtn.setText("Already Set as Default");
            }
            case NOT_DEFAULT -> {
                defaultStatusLabel.setText("● Oqteris is not currently the default PDF viewer");
                defaultStatusLabel.setStyle("-fx-text-fill: -color-warning; -fx-font-weight: bold; -fx-font-size: 12px;");
                setDefaultBtn.setDisable(false);
                setDefaultBtn.setText("Set Oqteris as Default PDF Viewer");
            }
            case UNKNOWN -> {
                defaultStatusLabel.setText("● Default viewer status unconfirmed (" + defaultViewerService.getPlatformName() + ")");
                defaultStatusLabel.setStyle("-fx-text-fill: -color-text-muted; -fx-font-size: 12px;");
                setDefaultBtn.setDisable(false);
                setDefaultBtn.setText("Register as Default PDF Viewer");
            }
        }
    }

    private void handleSetDefaultViewer() {
        RegistrationResult result = defaultViewerService.registerAsDefault();
        updateDefaultViewerStatus();

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
        alert.initOwner(this);
        ThemeManager.getInstance().applyTheme(alert);
        alert.getDialogPane().getScene().getWindow().sizeToScene();
        alert.showAndWait();
        updateDefaultViewerStatus();
    }
}
