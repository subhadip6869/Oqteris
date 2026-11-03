package org.originsd.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.originsd.oqteris.core.recovery.AutosaveService;
import org.originsd.oqteris.crypto.trust.CertificateManagerDialog;
import org.originsd.oqteris.platform.DefaultViewerService;
import org.originsd.oqteris.platform.DefaultViewerServiceFactory;
import org.originsd.oqteris.platform.DefaultViewerStatus;
import org.originsd.oqteris.platform.RegistrationResult;

import java.util.prefs.Preferences;

/**
 * Preferences dialog for application-wide PDF settings and system defaults.
 */
public class SettingsDialog extends Stage {
    private static final String PREF_AUTHOR = "pref_default_author";
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsDialog.class);

    private final DefaultViewerService defaultViewerService = DefaultViewerServiceFactory.getInstance();
    private final Label defaultStatusLabel = new Label();
    private final Button setDefaultBtn = new Button("Set Oqteris as Default PDF Viewer");

    public SettingsDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Settings - Oqteris");

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setPrefWidth(540);

        Label title = new Label("PDF Reader Preferences");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // General settings grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        // 1. Author Name (for annotations and metadata)
        Label authorLbl = new Label("Default Author Name:");
        TextField authorField = new TextField(prefs.get(PREF_AUTHOR, System.getProperty("user.name", "Oqteris User")));
        grid.addRow(0, authorLbl, authorField);

        // 2. Autosave
        AutosaveService autosave = AutosaveService.getInstance();
        CheckBox autosaveCheck = new CheckBox("Enable periodic autosave");
        autosaveCheck.setSelected(autosave.isEnabled());

        Spinner<Integer> intervalSpinner = new Spinner<>(1, 30, autosave.getIntervalSeconds() / 60);
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
        themeCombo.getItems().addAll("Light", "Dark");
        themeCombo.setValue(themeMgr.isDarkTheme() ? "Dark" : "Light");
        grid.addRow(3, new Label("Interface Theme:"), themeCombo);

        // 4. Default PDF Viewer
        updateDefaultViewerStatus();
        setDefaultBtn.setOnAction(e -> handleSetDefaultViewer());

        VBox defaultViewerBox = new VBox(6, defaultStatusLabel, setDefaultBtn);
        defaultViewerBox.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(4, new Label("Default PDF Viewer:"), defaultViewerBox);

        // 5. Certificate Management Button
        Button openCertMgrBtn = new Button("Manage Trusted Certificates...");
        openCertMgrBtn.setOnAction(e -> new CertificateManagerDialog(this).showAndWait());
        grid.addRow(5, new Label("Security & Trust:"), openCertMgrBtn);

        // Buttons
        Button saveBtn = new Button("Save Changes");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            prefs.put(PREF_AUTHOR, authorField.getText().trim());
            autosave.setEnabled(autosaveCheck.isSelected());
            autosave.setIntervalSeconds(intervalSpinner.getValue() * 60);
            boolean makeDark = "Dark".equals(themeCombo.getValue());
            themeMgr.setDarkTheme(makeDark);
            close();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> close());

        HBox btnBox = new HBox(10, new Region(), cancelBtn, saveBtn);
        HBox.setHgrow(btnBox.getChildren().get(0), Priority.ALWAYS);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
    }

    private void updateDefaultViewerStatus() {
        DefaultViewerStatus status = defaultViewerService.checkStatus();
        switch (status) {
            case DEFAULT -> {
                defaultStatusLabel.setText("● Oqteris is your default PDF viewer");
                defaultStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 12px;");
                setDefaultBtn.setDisable(true);
                setDefaultBtn.setText("Already Set as Default");
            }
            case NOT_DEFAULT -> {
                defaultStatusLabel.setText("● Oqteris is not currently the default PDF viewer");
                defaultStatusLabel.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold; -fx-font-size: 12px;");
                setDefaultBtn.setDisable(false);
                setDefaultBtn.setText("Set Oqteris as Default PDF Viewer");
            }
            case UNKNOWN -> {
                defaultStatusLabel.setText("● Default viewer status unconfirmed (" + defaultViewerService.getPlatformName() + ")");
                defaultStatusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
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
        alert.showAndWait();
        updateDefaultViewerStatus();
    }
}
