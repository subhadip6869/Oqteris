package org.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * About dialog featuring Oqteris branding, version information, and format support overview.
 */
public class AboutDialog extends Stage {

    public AboutDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("About Oqteris");
        setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setPrefWidth(540);
        root.setAlignment(Pos.CENTER);

        // Logo
        ImageView logoView = new ImageView();
        try (InputStream in = getClass().getResourceAsStream("/icons/oqteris-logo.png")) {
            if (in != null) {
                Image logo = new Image(in, 96, 96, true, true);
                logoView.setImage(logo);
            }
        } catch (Exception ignored) {
        }

        Label title = new Label("Oqteris Office Suite");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0078d4;");

        Label version = new Label("Version 1.0.0 (Java 21 LTS, JavaFX 21)");
        version.setStyle("-fx-font-size: 12px; -fx-text-fill: #777777;");

        Label desc = new Label(
                "Oqteris is a completely independent, cross-platform desktop office suite featuring:\n" +
                " • Oqteris Writer: Full DOCX word processor with custom layout & pagination\n" +
                " • Oqteris Calc: High-performance spreadsheet with DAG recalculation & Excel functions\n" +
                " • Oqteris Impress: Presentation slide designer and presentation slideshow mode\n" +
                " • Oqteris PDF: PDF viewer with cryptographic signature & certificate trust management"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px;");

        Label notice = new Label("100% Standalone: Operates with zero external office suite dependencies.");
        notice.setStyle("-fx-font-size: 11px; -fx-font-style: italic; -fx-text-fill: #28a745;");

        Button closeBtn = new Button("Close");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> close());

        root.getChildren().addAll(logoView, title, version, new Separator(), desc, notice, new Separator(), closeBtn);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
    }
}
