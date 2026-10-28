package org.originsd.oqteris.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Main JavaFX Application class for the Oqteris Office Suite.
 */
public class OqterisApp extends Application {
    private MainWindow mainWindow;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Oqteris Office Suite");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);

        mainWindow = new MainWindow(primaryStage);

        // Factory and file handler wired dynamically by module initializer
        AppBootstrap.initialize(mainWindow);

        Scene scene = new Scene(mainWindow, 1280, 820);
        ThemeManager.getInstance().applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Process any command line arguments as file paths
        List<String> rawArgs = getParameters().getRaw();
        if (rawArgs != null && !rawArgs.isEmpty()) {
            for (String arg : rawArgs) {
                File f = new File(arg);
                if (f.exists()) {
                    mainWindow.openFile(f);
                }
            }
        }
    }
}
