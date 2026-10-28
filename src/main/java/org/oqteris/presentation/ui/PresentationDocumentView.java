package org.oqteris.presentation.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.presentation.model.*;
import org.oqteris.presentation.render.SlideRenderer;
import org.oqteris.ui.DocumentView;

import java.util.Optional;

/**
 * Main UI view and interactive slide canvas for the Oqteris Impress presentation editor.
 */
public class PresentationDocumentView extends BorderPane implements DocumentView {
    private final Presentation presentation;

    // Left thumbnails
    private final VBox thumbnailsBox = new VBox(10);
    private final ScrollPane thumbnailsScroll = new ScrollPane();

    // Center canvas
    private final Canvas canvas = new Canvas();
    private final StackPane canvasContainer = new StackPane();
    private final ScrollPane canvasScroll = new ScrollPane();

    // Ribbon toolbar
    private final HBox ribbonBar = new HBox(6);
    private final Label statusSlideLabel = new Label("Slide 1 of 1");

    // Selection & interaction state
    private SlideShape selectedShape = null;
    private double dragStartX;
    private double dragStartY;
    private double shapeStartX;
    private double shapeStartY;
    private boolean isDraggingShape = false;
    private double currentScale = 0.85;

    public PresentationDocumentView() {
        this(new Presentation());
    }

    public PresentationDocumentView(Presentation presentation) {
        this.presentation = presentation;
        buildUI();
        refreshThumbnails();
        renderActiveSlide();
    }

    private void buildUI() {
        // 1. Left Thumbnails Sidebar
        thumbnailsScroll.setContent(thumbnailsBox);
        thumbnailsScroll.setFitToWidth(true);
        thumbnailsScroll.setPrefWidth(210);
        thumbnailsScroll.setStyle("-fx-background-color: #f3f3f3; -fx-background: #f3f3f3;");
        thumbnailsBox.setPadding(new Insets(12));
        thumbnailsBox.setAlignment(Pos.TOP_CENTER);
        setLeft(thumbnailsScroll);

        // 2. Center Slide Canvas
        canvasContainer.setAlignment(Pos.CENTER);
        canvasContainer.setPadding(new Insets(30));
        canvasContainer.setStyle("-fx-background-color: #e8eaed;");
        canvasContainer.getChildren().add(canvas);

        canvasScroll.setContent(canvasContainer);
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);
        canvasScroll.setStyle("-fx-background-color: #e8eaed; -fx-background: #e8eaed;");
        setCenter(canvasScroll);

        // Canvas mouse handlers
        canvas.setFocusTraversable(true);
        canvas.setOnMousePressed(this::handleCanvasPressed);
        canvas.setOnMouseDragged(this::handleCanvasDragged);
        canvas.setOnMouseReleased(e -> isDraggingShape = false);
        canvas.setOnKeyPressed(this::handleKeyPressed);

        // 3. Ribbon Toolbar
        setupRibbonToolbar();
    }

    private void setupRibbonToolbar() {
        ribbonBar.setAlignment(Pos.CENTER_LEFT);
        ribbonBar.setSpacing(6);

        // Slides Group
        Button newSlideBtn = new Button("+ New Slide");
        newSlideBtn.setOnAction(e -> {
            presentation.createSlide("New Slide");
            presentation.setActiveSlideIndex(presentation.getSlideCount() - 1);
            refreshThumbnails();
            renderActiveSlide();
        });

        Button dupSlideBtn = new Button("Duplicate");
        dupSlideBtn.setOnAction(e -> {
            presentation.duplicateSlide(presentation.getActiveSlideIndex());
            refreshThumbnails();
            renderActiveSlide();
        });

        Button deleteSlideBtn = new Button("Delete");
        deleteSlideBtn.setOnAction(e -> {
            if (presentation.getSlideCount() > 1) {
                presentation.removeSlide(presentation.getActiveSlideIndex());
                refreshThumbnails();
                renderActiveSlide();
            }
        });

        // Insert Shapes Group
        MenuButton insertShapeBtn = new MenuButton("Insert Shape");
        MenuItem addText = new MenuItem("Text Box");
        addText.setOnAction(e -> addShapeToActiveSlide(new TextBoxShape(200, 200, 300, 60, "Enter text", 20.0, false)));

        MenuItem addRect = new MenuItem("Rectangle");
        addRect.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.RECTANGLE, 200, 200, 200, 120)));

        MenuItem addRoundedRect = new MenuItem("Rounded Rectangle");
        addRoundedRect.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.ROUNDED_RECTANGLE, 200, 200, 200, 120)));

        MenuItem addEllipse = new MenuItem("Circle / Ellipse");
        addEllipse.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.ELLIPSE, 200, 200, 150, 150)));

        MenuItem addTriangle = new MenuItem("Triangle");
        addTriangle.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.TRIANGLE, 200, 200, 160, 140)));

        MenuItem addArrow = new MenuItem("Arrow");
        addArrow.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.ARROW, 200, 200, 220, 80)));

        MenuItem addStar = new MenuItem("Star");
        addStar.setOnAction(e -> addShapeToActiveSlide(new GeometricShape(GeometricShape.Geometry.STAR, 200, 200, 150, 150)));

        MenuItem addTable = new MenuItem("Table (3x3)");
        addTable.setOnAction(e -> addShapeToActiveSlide(new TableShape(3, 3, 200, 200, 400, 150)));

        insertShapeBtn.getItems().addAll(addText, new SeparatorMenuItem(), addRect, addRoundedRect, addEllipse, addTriangle, addArrow, addStar, new SeparatorMenuItem(), addTable);

        // Styling
        ColorPicker fillColorPicker = new ColorPicker(Color.rgb(0, 120, 215));
        fillColorPicker.setPrefWidth(40);
        fillColorPicker.setOnAction(e -> {
            if (selectedShape != null) {
                selectedShape.setFillColor(fillColorPicker.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        ColorPicker strokeColorPicker = new ColorPicker(Color.rgb(0, 90, 160));
        strokeColorPicker.setPrefWidth(40);
        strokeColorPicker.setOnAction(e -> {
            if (selectedShape != null) {
                selectedShape.setStrokeColor(strokeColorPicker.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        // Arrange
        Button bringFrontBtn = new Button("Bring to Front");
        bringFrontBtn.setOnAction(e -> {
            if (selectedShape != null) {
                presentation.getActiveSlide().bringToFront(selectedShape);
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        Button sendBackBtn = new Button("Send to Back");
        sendBackBtn.setOnAction(e -> {
            if (selectedShape != null) {
                presentation.getActiveSlide().sendToBack(selectedShape);
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        // Slideshow presentation launcher (F5)
        Button slideshowBtn = new Button("▶ Present (F5)");
        slideshowBtn.setStyle("-fx-background-color: #107c41; -fx-text-fill: white; -fx-font-weight: bold;");
        slideshowBtn.setOnAction(e -> startSlideshow());

        ribbonBar.getChildren().addAll(
                newSlideBtn, dupSlideBtn, deleteSlideBtn, new Separator(Orientation.VERTICAL),
                insertShapeBtn, new Separator(Orientation.VERTICAL),
                new Label("Fill:"), fillColorPicker, new Label("Line:"), strokeColorPicker, new Separator(Orientation.VERTICAL),
                bringFrontBtn, sendBackBtn, new Separator(Orientation.VERTICAL),
                slideshowBtn
        );
    }

    private void addShapeToActiveSlide(SlideShape shape) {
        Slide s = presentation.getActiveSlide();
        if (s != null && shape != null) {
            s.addShape(shape);
            selectedShape = shape;
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        }
    }

    public void startSlideshow() {
        SlideshowStage stage = new SlideshowStage(presentation, presentation.getActiveSlideIndex());
        stage.show();
    }

    private void refreshThumbnails() {
        thumbnailsBox.getChildren().clear();
        double thumbW = 160;
        double thumbH = 90;

        for (int i = 0; i < presentation.getSlideCount(); i++) {
            final int index = i;
            Slide slide = presentation.getSlide(index);

            VBox card = new VBox(4);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(6));
            card.setCursor(javafx.scene.Cursor.HAND);

            boolean isActive = (index == presentation.getActiveSlideIndex());
            card.setStyle("-fx-background-color: white; -fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    (isActive ? "-fx-border-color: #0078d4; -fx-border-width: 2px;" : "-fx-border-color: #dddddd; -fx-border-width: 1px;"));

            Canvas thumbCanvas = new Canvas(thumbW, thumbH);
            GraphicsContext gc = thumbCanvas.getGraphicsContext2D();
            double scale = thumbW / presentation.getSlideWidth();
            SlideRenderer.renderSlide(gc, slide, presentation.getSlideWidth(), presentation.getSlideHeight(), scale, null);

            Label title = new Label((index + 1) + ". " + slide.getTitle());
            title.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444;");

            card.getChildren().addAll(thumbCanvas, title);
            card.setOnMouseClicked(e -> {
                presentation.setActiveSlideIndex(index);
                selectedShape = null;
                refreshThumbnails();
                renderActiveSlide();
            });

            thumbnailsBox.getChildren().add(card);
        }
    }

    public void renderActiveSlide() {
        double slideW = presentation.getSlideWidth();
        double slideH = presentation.getSlideHeight();

        double canvasW = slideW * currentScale;
        double canvasH = slideH * currentScale;

        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasW, canvasH);

        Slide slide = presentation.getActiveSlide();
        if (slide != null) {
            SlideRenderer.renderSlide(gc, slide, slideW, slideH, currentScale, selectedShape);
        }

        statusSlideLabel.setText("Slide " + (presentation.getActiveSlideIndex() + 1) + " of " + presentation.getSlideCount());
    }

    private void handleCanvasPressed(MouseEvent e) {
        canvas.requestFocus();
        double slideX = e.getX() / currentScale;
        double slideY = e.getY() / currentScale;

        Slide slide = presentation.getActiveSlide();
        if (slide == null) return;

        SlideShape hit = slide.hitTest(slideX, slideY);
        selectedShape = hit;

        if (e.getClickCount() == 2 && selectedShape instanceof TextBoxShape tb) {
            // Edit text box text via quick prompt
            TextInputDialog dialog = new TextInputDialog(tb.getText());
            dialog.setTitle("Edit Text");
            dialog.setHeaderText("Modify slide text box contents:");
            dialog.showAndWait().ifPresent(newText -> {
                tb.setText(newText);
                presentation.setModified(true);
                refreshThumbnails();
                renderActiveSlide();
            });
            return;
        }

        if (selectedShape != null) {
            isDraggingShape = true;
            dragStartX = slideX;
            dragStartY = slideY;
            shapeStartX = selectedShape.getX();
            shapeStartY = selectedShape.getY();
        }

        renderActiveSlide();
    }

    private void handleCanvasDragged(MouseEvent e) {
        if (isDraggingShape && selectedShape != null) {
            double slideX = e.getX() / currentScale;
            double slideY = e.getY() / currentScale;

            double dx = slideX - dragStartX;
            double dy = slideY - dragStartY;

            selectedShape.setX(Math.max(0, shapeStartX + dx));
            selectedShape.setY(Math.max(0, shapeStartY + dy));
            presentation.setModified(true);

            renderActiveSlide();
        }
    }

    private void handleKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.F5) {
            startSlideshow();
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            if (selectedShape != null) {
                presentation.getActiveSlide().removeShape(selectedShape);
                selectedShape = null;
                presentation.setModified(true);
                refreshThumbnails();
                renderActiveSlide();
            }
        }
    }

    @Override
    public DocumentModel getDocument() {
        return presentation;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbonBar;
    }

    @Override
    public Node getStatusBarNode() {
        return statusSlideLabel;
    }

    @Override
    public double getZoom() {
        return currentScale;
    }

    @Override
    public void setZoom(double zoom) {
        this.currentScale = Math.max(0.3, Math.min(2.5, zoom));
        renderActiveSlide();
    }

    @Override
    public void close() {
    }
}
