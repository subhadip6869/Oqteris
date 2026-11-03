package org.originsd.oqteris.ui;

import javafx.application.Application;
import org.originsd.oqteris.platform.SingleInstanceManager;

import java.util.Arrays;

/**
 * Standard entry point for launching Oqteris from command-line or executable jar.
 * Routes file-open arguments to an already-running instance if one exists.
 */
public class OqterisLauncher {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            boolean handled = SingleInstanceManager.notifyRunningInstance(Arrays.asList(args));
            if (handled) {
                return;
            }
        }
        Application.launch(OqterisApp.class, args);
    }
}
