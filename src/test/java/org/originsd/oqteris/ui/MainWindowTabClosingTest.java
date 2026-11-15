package org.originsd.oqteris.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.originsd.oqteris.core.model.AbstractDocumentModel;
import org.originsd.oqteris.core.model.DocumentModel;
import org.originsd.oqteris.core.model.DocumentType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class MainWindowTabClosingTest {

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    private static class DummyDocumentModel extends AbstractDocumentModel {
        DummyDocumentModel(String title) {
            super(DocumentType.PDF, title);
        }
    }

    private static class DummyDocumentView extends Region implements DocumentView {
        private final DocumentModel model;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        DummyDocumentView(String title) {
            this.model = new DummyDocumentModel(title);
        }

        @Override public DocumentModel getDocument() { return model; }
        @Override public Node getRibbonToolbar() { return new Label("Toolbar"); }
        @Override public Node getStatusBarNode() { return new Label("Status"); }
        @Override public double getZoom() { return 1.0; }
        @Override public void setZoom(double zoom) {}
        @Override public void close() { closed.set(true); }
    }

    @Test
    public void testWelcomeScreenShownWhenAllTabsClosed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                MainWindow window = new MainWindow(stage);

                // 1. Initial State: No tabs open -> WelcomeScreen must be visible, TabPane hidden
                assertFalse(window.getDocumentTabPane().isVisible(), "DocumentTabPane should be hidden initially");
                assertTrue(window.getWelcomeScreen().isVisible(), "WelcomeScreen should be visible initially");

                // 2. Open first tab -> TabPane must be visible, WelcomeScreen hidden
                DummyDocumentView view1 = new DummyDocumentView("Doc 1");
                window.addDocumentView(view1);

                assertEquals(1, window.getDocumentTabPane().getTabs().size());
                assertTrue(window.getDocumentTabPane().isVisible(), "DocumentTabPane should be visible when tab is open");
                assertFalse(window.getWelcomeScreen().isVisible(), "WelcomeScreen should be hidden when tab is open");

                // 3. Open second tab
                DummyDocumentView view2 = new DummyDocumentView("Doc 2");
                window.addDocumentView(view2);
                assertEquals(2, window.getDocumentTabPane().getTabs().size());

                // 4. Close first tab -> 1 tab remains -> TabPane still visible, WelcomeScreen still hidden
                window.closeActiveTab();
                assertEquals(1, window.getDocumentTabPane().getTabs().size());
                assertTrue(window.getDocumentTabPane().isVisible(), "DocumentTabPane should remain visible with 1 tab open");
                assertFalse(window.getWelcomeScreen().isVisible(), "WelcomeScreen should remain hidden with 1 tab open");

                // 5. Close remaining tab -> 0 tabs remain -> WelcomeScreen MUST be visible, TabPane hidden
                window.closeActiveTab();
                assertEquals(0, window.getDocumentTabPane().getTabs().size());
                assertFalse(window.getDocumentTabPane().isVisible(), "DocumentTabPane must be hidden when all tabs are closed");
                assertTrue(window.getWelcomeScreen().isVisible(), "WelcomeScreen MUST be shown when all tabs are closed");

                // Also verify view.close() was properly triggered on closed views
                assertTrue(view1.closed.get(), "View 1 should have had close() called");
                assertTrue(view2.closed.get(), "View 2 should have had close() called");
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Test timed out");
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
