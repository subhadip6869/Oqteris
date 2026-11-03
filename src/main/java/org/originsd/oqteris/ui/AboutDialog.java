package org.originsd.oqteris.ui;

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
import org.originsd.oqteris.ui.icon.AppResources;

/**
 * About dialog featuring Oqteris PDF branding, version information, and capability overview.
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
        Image logo = AppResources.getAppLogo(96, 96);
        if (logo != null) {
            logoView.setImage(logo);
        }

        Label title = new Label("Oqteris");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #2563eb;");

        Label subtitle = new Label("Dedicated PDF Reader and Editor");
        subtitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #475569;");

        Label version = new Label("Version 1.0.0 LTS (Java 21 LTS, JavaFX 21)");
        version.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        Label desc = new Label(
                "Oqteris is a dedicated, cross-platform desktop PDF application featuring:\n" +
                " • High-performance PDF viewing with continuous scrolling, rotation, and zooming\n" +
                " • Interactive page thumbnails, document bookmarks outline, and full-text search\n" +
                " • PDF annotation and document management capabilities\n" +
                " • CMS / PKCS#7 digital signature verification with live signature validity badges\n" +
                " • Built-in X.509 Certificate Trust Store and Certificate Manager\n" +
                " • Operating system file association and default PDF viewer registration"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-line-spacing: 4px;");

        Label notice = new Label("100% Standalone: Operates with zero cloud dependencies.");
        notice.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        Button closeBtn = new Button("Close");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> close());

        root.getChildren().addAll(logoView, title, subtitle, version, new Separator(), desc, notice, new Separator(), closeBtn);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
    }
}
