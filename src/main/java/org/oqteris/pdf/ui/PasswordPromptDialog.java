package org.oqteris.pdf.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.oqteris.ui.icon.AppIcons;

import java.util.Optional;

/**
 * Modern dialog prompting for the decryption password of encrypted PDF documents.
 */
public class PasswordPromptDialog {

    public static String promptForPassword(String documentName, boolean isRetry) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Password Protected PDF");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        pane.setStyle("-fx-font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16, 20, 16, 20));
        content.setPrefWidth(380);

        Label titleLabel = new Label("Password Required");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        titleLabel.setGraphic(AppIcons.of(MaterialDesignL.LOCK_OUTLINE, 18, javafx.scene.paint.Color.rgb(37, 99, 235)));

        Label descLabel = new Label("'" + documentName + "' is protected with a password. Please enter the password to open this document.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-line-spacing: 2px;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter document password");
        passwordField.setStyle("-fx-font-size: 13px; -fx-padding: 7px 10px; -fx-border-color: #cbd5e1; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        TextField plainTextField = new TextField();
        plainTextField.setPromptText("Enter document password");
        plainTextField.setStyle("-fx-font-size: 13px; -fx-padding: 7px 10px; -fx-border-color: #cbd5e1; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        plainTextField.setVisible(false);
        plainTextField.setManaged(false);

        // Keep masked and unmasked fields synchronized
        passwordField.textProperty().bindBidirectional(plainTextField.textProperty());

        CheckBox showPasswordBox = new CheckBox("Show password");
        showPasswordBox.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        showPasswordBox.selectedProperty().addListener((obs, oldV, isSelected) -> {
            if (isSelected) {
                plainTextField.setVisible(true);
                plainTextField.setManaged(true);
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                plainTextField.requestFocus();
                plainTextField.positionCaret(plainTextField.getText().length());
            } else {
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                plainTextField.setVisible(false);
                plainTextField.setManaged(false);
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        });

        Label errorLabel = new Label("⚠ Incorrect password. Please try again.");
        errorLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
        errorLabel.setVisible(isRetry);
        errorLabel.setManaged(isRetry);

        content.getChildren().addAll(titleLabel, descLabel, passwordField, plainTextField, showPasswordBox, errorLabel);
        pane.setContent(content);

        // Auto-focus the password field
        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }
}
