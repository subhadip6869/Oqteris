package org.originsd.oqteris.ui.ribbon;

/**
 * Interface through which document views and ribbons interact with top-level window actions.
 */
public interface MainWindowHost {
    void showOpenDialog();
    void saveActiveDocument(boolean saveAs);
    void printActiveDocument();
    void closeActiveDocument();
    void showSettings();
    void showAbout();
    void showCertificateManager();
    void setDefaultViewer();
    void toggleFullScreen();
}
