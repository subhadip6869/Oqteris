package org.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.oqteris.core.recovery.AutosaveService;
import org.oqteris.crypto.trust.CertificateManagerDialog;

import java.util.prefs.Preferences;

/**
 * Preferences dialog for application-wide settings.
 */
public class SettingsDialog extends Stage {
    private static final String PREF_AUTHOR = "pref_default_author";
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsDialog.class);

    public SettingsDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Settings - Oqteris");

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setPrefWidth(480);

        Label title = new Label("Application Preferences");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // General settings grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        // Author
        Label authorLbl = new Label("Default Author Name:");
        TextField authorField = new TextField(prefs.get(PREF_AUTHOR, System.getProperty("user.name", "Oqteris User")));
        grid.addRow(0, authorLbl, authorField);

        // Autosave
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

        // Theme
        ThemeManager themeMgr = ThemeManager.getInstance();
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark");
        themeCombo.setValue(themeMgr.isDarkTheme() ? "Dark" : "Light");
        grid.addRow(3, new Label("Interface Theme:"), themeCombo);

        // Certificate Management Button
        Button openCertMgrBtn = new Button("Manage Trusted Certificates...");
        openCertMgrBtn.setOnAction(e -> new CertificateManagerDialog(this).showAndWait());
        grid.addRow(4, new Label("Security & Trust:"), openCertMgrBtn);

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
}
