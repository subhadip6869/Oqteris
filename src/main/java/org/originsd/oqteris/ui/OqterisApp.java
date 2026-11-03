package org.originsd.oqteris.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.originsd.oqteris.platform.PdfFileOpenHelper;
import org.originsd.oqteris.platform.SingleInstanceManager;

import java.io.File;
import java.util.List;

/**
 * Main JavaFX Application class for Oqteris PDF Reader and Editor.
 */
public class OqterisApp extends Application {
    private MainWindow mainWindow;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Oqteris");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);

        mainWindow = new MainWindow(primaryStage);

        // Factory and file handler wired dynamically by module initializer
        AppBootstrap.initialize(mainWindow);

        Scene scene = new Scene(mainWindow, 1280, 820);
        ThemeManager.getInstance().applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start single-instance listener to accept future file opens from OS / Explorer / CLI
        SingleInstanceManager.startServer(files -> {
            Platform.runLater(() -> {
                for (File f : files) {
                    mainWindow.openFile(f);
                }
                primaryStage.setIconified(false);
                primaryStage.toFront();
                primaryStage.requestFocus();
            });
        });

        // Process any command line arguments as file paths passed at launch
        List<String> rawArgs = getParameters().getRaw();
        if (rawArgs != null && !rawArgs.isEmpty()) {
            for (String arg : rawArgs) {
                File f = PdfFileOpenHelper.validatePdfFile(arg);
                if (f != null) {
                    mainWindow.openFile(f);
                }
            }
        }
    }

    @Override
    public void stop() {
        SingleInstanceManager.stopServer();
    }
}
