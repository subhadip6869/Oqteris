package org.oqteris.presentation.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.oqteris.presentation.model.Presentation;
import org.oqteris.presentation.model.Slide;
import org.oqteris.presentation.render.SlideRenderer;

/**
 * Full-screen slideshow presentation viewer with transitions and keyboard navigation.
 */
public class SlideshowStage extends Stage {
    private final Presentation presentation;
    private int currentSlideIndex;

    private final StackPane root = new StackPane();
    private final Canvas canvas = new Canvas();
    private final Label pageIndicator = new Label("Slide 1 of 1");
    private final HBox controlOverlay = new HBox(12);

    public SlideshowStage(Presentation presentation, int startSlideIndex) {
        this.presentation = presentation;
        this.currentSlideIndex = Math.max(0, Math.min(presentation.getSlideCount() - 1, startSlideIndex));

        initStyle(StageStyle.UNDECORATED);
        setTitle("Slideshow - " + presentation.getTitle());

        root.setStyle("-fx-background-color: black;");
        root.getChildren().add(canvas);

        setupControlsOverlay();
        root.getChildren().add(controlOverlay);
        StackPane.setAlignment(controlOverlay, Pos.BOTTOM_CENTER);

        Scene scene = new Scene(root);
        scene.setFill(Color.BLACK);
        scene.setOnKeyPressed(this::handleKeyPressed);
        scene.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                nextSlide();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                prevSlide();
            }
        });

        setScene(scene);
        setFullScreen(true);
        setFullScreenExitHint("Press ESC to exit presentation mode");

        scene.widthProperty().addListener(e -> renderCurrentSlide());
        scene.heightProperty().addListener(e -> renderCurrentSlide());

        renderCurrentSlide();
    }

    private void setupControlsOverlay() {
        controlOverlay.setAlignment(Pos.CENTER);
        controlOverlay.setPadding(new Insets(8, 16, 8, 16));
        controlOverlay.setStyle("-fx-background-color: rgba(30, 30, 30, 0.75); -fx-background-radius: 8px;");
        controlOverlay.setMaxWidth(340);
        controlOverlay.setOpacity(0.0);

        root.setOnMouseMoved(e -> {
            controlOverlay.setOpacity(1.0);
            FadeTransition ft = new FadeTransition(Duration.seconds(3), controlOverlay);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.play();
        });

        Button prevBtn = new Button("◀");
        prevBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px;");
        prevBtn.setOnAction(e -> prevSlide());

        pageIndicator.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");

        Button nextBtn = new Button("▶");
        nextBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px;");
        nextBtn.setOnAction(e -> nextSlide());

        Button closeBtn = new Button("✕ Exit");
        closeBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 11px;");
        closeBtn.setOnAction(e -> close());

        Region r1 = new Region();
        Region r2 = new Region();
        HBox.setHgrow(r1, Priority.ALWAYS);
        HBox.setHgrow(r2, Priority.ALWAYS);

        controlOverlay.getChildren().addAll(prevBtn, r1, pageIndicator, r2, nextBtn, closeBtn);
    }

    private void handleKeyPressed(KeyEvent e) {
        KeyCode code = e.getCode();
        if (code == KeyCode.ESCAPE) {
            close();
        } else if (code == KeyCode.RIGHT || code == KeyCode.DOWN || code == KeyCode.SPACE || code == KeyCode.PAGE_DOWN) {
            nextSlide();
        } else if (code == KeyCode.LEFT || code == KeyCode.UP || code == KeyCode.PAGE_UP || code == KeyCode.BACK_SPACE) {
            prevSlide();
        } else if (code == KeyCode.HOME) {
            currentSlideIndex = 0;
            renderCurrentSlide();
        } else if (code == KeyCode.END) {
            currentSlideIndex = presentation.getSlideCount() - 1;
            renderCurrentSlide();
        }
    }

    public void nextSlide() {
        if (currentSlideIndex < presentation.getSlideCount() - 1) {
            currentSlideIndex++;
            renderCurrentSlide();
        }
    }

    public void prevSlide() {
        if (currentSlideIndex > 0) {
            currentSlideIndex--;
            renderCurrentSlide();
        }
    }

    private void renderCurrentSlide() {
        double screenW = root.getWidth() > 0 ? root.getWidth() : 1280;
        double screenH = root.getHeight() > 0 ? root.getHeight() : 720;

        double slideW = presentation.getSlideWidth();
        double slideH = presentation.getSlideHeight();

        // Calculate aspect ratio scale to fit screen
        double scale = Math.min(screenW / slideW, screenH / slideH);
        double targetW = slideW * scale;
        double targetH = slideH * scale;

        canvas.setWidth(targetW);
        canvas.setHeight(targetH);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, targetW, targetH);

        Slide slide = presentation.getSlide(currentSlideIndex);
        if (slide != null) {
            SlideRenderer.renderSlide(gc, slide, slideW, slideH, scale, null);
        }

        pageIndicator.setText("Slide " + (currentSlideIndex + 1) + " of " + presentation.getSlideCount());

        // Transition fade
        FadeTransition ft = new FadeTransition(Duration.millis(300), canvas);
        ft.setFromValue(0.4);
        ft.setToValue(1.0);
        ft.play();
    }
}
