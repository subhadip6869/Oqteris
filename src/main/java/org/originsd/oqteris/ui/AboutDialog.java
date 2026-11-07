package org.originsd.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.originsd.oqteris.config.AppConfig;
import org.originsd.oqteris.ui.icon.AppResources;
import org.originsd.oqteris.update.AppVersion;

/**
 * About dialog featuring Oqteris PDF branding, OriginSD publisher identity, and system metadata.
 */
public class AboutDialog extends Stage {

    public AboutDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("About Oqteris - " + AppConfig.PUBLISHER);
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

        Label title = new Label(AppConfig.APP_NAME);
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: -color-primary;");

        Label subtitle = new Label("Dedicated PDF Reader and Editor");
        subtitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: -color-secondary-teal;");

        Label version = new Label("Version " + AppVersion.CURRENT_VERSION + " LTS (Java 21 LTS, JavaFX 21)");
        version.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-text-muted;");

        VBox publisherBox = new VBox(4);
        publisherBox.setAlignment(Pos.CENTER);
        publisherBox.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label pubLabel = new Label("Publisher: " + AppConfig.PUBLISHER);
        Label devLabel = new Label("Developer: " + AppConfig.DEVELOPER);
        Label locLabel = new Label("Location: " + AppConfig.LOCATION);
        Label cprLabel = new Label("Copyright: " + AppConfig.COPYRIGHT);
        for (Label lbl : new Label[]{pubLabel, devLabel, locLabel, cprLabel}) {
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-text-secondary;");
            lbl.setAlignment(Pos.CENTER);
            lbl.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        publisherBox.getChildren().addAll(pubLabel, devLabel, locLabel, cprLabel);

        Label desc = new Label(
                "• High-performance PDF viewing with continuous scrolling, rotation, and zooming\n" +
                "• Interactive page thumbnails, document bookmarks outline, and full-text search\n" +
                "• PDF annotation and document management capabilities\n" +
                "• CMS / PKCS#7 digital signature verification with live signature validity badges\n" +
                "• Built-in X.509 Certificate Trust Store and Certificate Manager\n" +
                "• Operating system file association and default PDF viewer registration"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px; -fx-line-spacing: 3px; -fx-text-fill: -color-text-secondary;");

        Label notice = new Label("100% Standalone: Operates with zero cloud dependencies.");
        notice.setWrapText(true);
        notice.setAlignment(Pos.CENTER);
        notice.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -color-success; -fx-text-alignment: center;");

        HBox linksBox = new HBox(16);
        linksBox.setAlignment(Pos.CENTER);

        Hyperlink websiteLink = new Hyperlink("Official Website (" + AppConfig.WEBSITE_URL + ")");
        websiteLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppConfig.WEBSITE_URL));
            } catch (Exception ignored) {}
        });

        Hyperlink repoLink = new Hyperlink("GitHub Repository");
        repoLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppVersion.GITHUB_REPO_URL));
            } catch (Exception ignored) {}
        });
        linksBox.getChildren().addAll(websiteLink, repoLink);

        Button closeBtn = new Button("Close");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> close());

        Button updateBtn = new Button("Check for Updates");
        updateBtn.setOnAction(e -> org.originsd.oqteris.update.UpdateCheckerService.getInstance().checkForUpdatesAsync(this, true));
        HBox buttonBar = new HBox(10, updateBtn, closeBtn);
        buttonBar.setAlignment(Pos.CENTER);

        root.getChildren().addAll(logoView, title, subtitle, version, new Separator(), publisherBox, new Separator(), desc, notice, linksBox, new Separator(), buttonBar);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
        sizeToScene();
    }
}
