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
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: 800; -fx-text-fill: #6366f1;");

        Label version = new Label("Version 1.0.0 LTS (Java 21 LTS, JavaFX 21)");
        version.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        Label desc = new Label(
                "Oqteris is a completely independent, cross-platform desktop office suite featuring:\n" +
                " • Oqteris Writer: Full word processor supporting DOCX, DOC, RTF, ODT, TXT, and HTML\n" +
                " • Oqteris Calc: High-performance spreadsheet supporting XLSX, XLS, ODS, CSV, and TSV\n" +
                " • Oqteris Impress: Presentation designer supporting PPTX, PPT, ODP, and Vector PDF export\n" +
                " • Oqteris PDF & Trust Center: PDF viewer with CMS / PKCS#7 signature validation & trust store"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-line-spacing: 4px;");

        Label notice = new Label("100% Standalone: Operates with zero external office suite or cloud dependencies.");
        notice.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        Button closeBtn = new Button("Close");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> close());

        root.getChildren().addAll(logoView, title, version, new Separator(), desc, notice, new Separator(), closeBtn);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
    }
}
