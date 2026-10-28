package org.oqteris.presentation.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.controlsfx.control.textfield.TextFields;
import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import org.kordamp.ikonli.materialdesign2.*;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.presentation.model.*;
import org.oqteris.presentation.render.SlideRenderer;
import org.oqteris.ui.DocumentView;
import org.oqteris.ui.icon.AppIcons;

import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;

/**
 * Main UI view and interactive slide canvas for Oqteris Impress.
 */
public class PresentationDocumentView extends BorderPane implements DocumentView {
    private enum ResizeHandle {
        NONE, NW, N, NE, E, SE, S, SW, W
    }

    private final Presentation presentation;

    // Left thumbnails
    private final VBox thumbnailsBox = new VBox(10);
    private final ScrollPane thumbnailsScroll = new ScrollPane();

    // Center canvas
    private final Canvas canvas = new Canvas();
    private final StackPane canvasContainer = new StackPane();
    private final ScrollPane canvasScroll = new ScrollPane();

    // Ribbon toolbar
    private final Ribbon ribbon = new Ribbon();
    private final Label statusSlideLabel = new Label("Slide 1 of 1");

    // Search bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = TextFields.createClearableTextField();
    private final Label searchResultLabel = new Label("");

    // Selection & interaction state
    private SlideShape selectedShape = null;
    private SlideShape clipboardShape = null;
    private ResizeHandle activeHandle = ResizeHandle.NONE;

    private double dragStartX;
    private double dragStartY;
    private double shapeStartX;
    private double shapeStartY;
    private double shapeStartW;
    private double shapeStartH;
    private boolean isDraggingShape = false;
    private double currentScale = 0.85;

    // In-place text editor for slide shapes
    private final TextArea inPlaceTextEditor = new TextArea();
    private TextBoxShape editingTextBox = null;

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
        thumbnailsScroll.getStyleClass().add("sidebar-panel");
        thumbnailsBox.setPadding(new Insets(12));
        thumbnailsBox.setAlignment(Pos.TOP_CENTER);
        setLeft(thumbnailsScroll);

        // 2. Center Slide Canvas
        canvasContainer.setAlignment(Pos.CENTER);
        canvasContainer.setPadding(new Insets(30));
        canvasContainer.getStyleClass().add("editor-canvas-container");
        canvasContainer.getChildren().addAll(canvas, inPlaceTextEditor);

        inPlaceTextEditor.setVisible(false);
        inPlaceTextEditor.setManaged(false);
        inPlaceTextEditor.setWrapText(true);
        inPlaceTextEditor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) commitInPlaceEditor();
        });
        inPlaceTextEditor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelInPlaceEditor();
            }
        });

        canvasScroll.setContent(canvasContainer);
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);
        canvasScroll.getStyleClass().add("editor-canvas-container");
        setCenter(canvasScroll);

        // Canvas mouse handlers
        canvas.setFocusTraversable(true);
        canvas.setOnMousePressed(this::handleCanvasPressed);
        canvas.setOnMouseDragged(this::handleCanvasDragged);
        canvas.setOnMouseReleased(e -> {
            isDraggingShape = false;
            activeHandle = ResizeHandle.NONE;
            refreshThumbnails();
        });
        canvas.setOnKeyPressed(this::handleKeyPressed);

        // 3. Search Bar setup
        setupSearchBar();

        // 4. Ribbon Toolbar
        setupRibbonToolbar();
    }

    private void setupSearchBar() {
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5, 12, 5, 12));
        searchBar.getStyleClass().add("editor-search-bar");
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField.setPromptText("Find text in slides...");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> performSearch(searchField.getText()));

        Button findNextBtn = AppIcons.button("Find Next", MaterialDesignC.CHEVRON_RIGHT, e -> performSearch(searchField.getText()));
        Button closeBtn = AppIcons.iconButton(MaterialDesignC.CLOSE, "Close Search", e -> toggleSearch(false));
        closeBtn.setStyle("-fx-background-color: transparent;");

        searchResultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 500;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchBar.getChildren().addAll(new Label("Find:"), searchField, findNextBtn, searchResultLabel, spacer, closeBtn);
    }

    private void toggleSearch(boolean visible) {
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible) {
            setTop(new VBox(searchBar));
            searchField.requestFocus();
        } else {
            setTop(null);
            searchResultLabel.setText("");
        }
    }

    private void performSearch(String query) {
        if (query == null || query.isBlank()) {
            searchResultLabel.setText("");
            return;
        }

        String lower = query.toLowerCase();
        int curIdx = presentation.getActiveSlideIndex();
        int total = presentation.getSlideCount();

        for (int i = 1; i <= total; i++) {
            int checkIdx = (curIdx + i) % total;
            Slide s = presentation.getSlide(checkIdx);
            for (SlideShape shape : s.getShapes()) {
                if (shape instanceof TextBoxShape tb && tb.getText().toLowerCase().contains(lower)) {
                    presentation.setActiveSlideIndex(checkIdx);
                    selectedShape = tb;
                    refreshThumbnails();
                    renderActiveSlide();
                    searchResultLabel.setText("Found on Slide " + (checkIdx + 1));
                    return;
                }
            }
        }
        searchResultLabel.setText("Not found");
    }

    private void setupRibbonToolbar() {
        ribbon.getTabs().clear();
        ribbon.setMaxWidth(Double.MAX_VALUE);

        // =========================================================================
        // Tab 1: HOME
        // =========================================================================
        RibbonTab homeTab = new RibbonTab("Home");

        // Slides Group
        RibbonGroup slidesGroup = new RibbonGroup();
        slidesGroup.setTitle("Slides");

        Button newSlideBtn = AppIcons.button("New Slide", MaterialDesignP.PLUS_BOX, e -> {
            presentation.createSlide("New Slide");
            presentation.setActiveSlideIndex(presentation.getSlideCount() - 1);
            refreshThumbnails();
            renderActiveSlide();
        });

        Button dupSlideBtn = AppIcons.button("Duplicate", MaterialDesignC.CONTENT_DUPLICATE, e -> {
            presentation.duplicateSlide(presentation.getActiveSlideIndex());
            refreshThumbnails();
            renderActiveSlide();
        });

        Button deleteSlideBtn = AppIcons.button("Delete", MaterialDesignT.TRASH_CAN_OUTLINE, e -> {
            if (presentation.getSlideCount() > 1) {
                presentation.removeSlide(presentation.getActiveSlideIndex());
                refreshThumbnails();
                renderActiveSlide();
            }
        });

        slidesGroup.getNodes().addAll(newSlideBtn, dupSlideBtn, deleteSlideBtn);

        // Clipboard Group
        RibbonGroup clipboardGroup = new RibbonGroup();
        clipboardGroup.setTitle("Clipboard");
        Button pasteBtn = AppIcons.button("Paste", MaterialDesignC.CONTENT_PASTE, e -> paste());
        Button cutBtn = AppIcons.button("Cut", MaterialDesignC.CONTENT_CUT, e -> cut());
        Button copyBtn = AppIcons.button("Copy", MaterialDesignC.CONTENT_COPY, e -> copy());
        clipboardGroup.getNodes().addAll(pasteBtn, cutBtn, copyBtn);

        // Text Group
        RibbonGroup textGroup = new RibbonGroup();
        textGroup.setTitle("Text Formatting");

        ComboBox<Double> fontSizeCombo = new ComboBox<>();
        fontSizeCombo.getItems().addAll(12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0, 40.0, 48.0);
        fontSizeCombo.setValue(18.0);
        fontSizeCombo.setPrefWidth(65);
        fontSizeCombo.setOnAction(e -> {
            if (selectedShape instanceof TextBoxShape tb) {
                tb.setFontSize(fontSizeCombo.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        ToggleButton boldBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_BOLD, "Bold");
        boldBtn.setOnAction(e -> {
            if (selectedShape instanceof TextBoxShape tb) {
                tb.setBold(boldBtn.isSelected());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        ToggleButton italicBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_ITALIC, "Italic");
        italicBtn.setOnAction(e -> {
            if (selectedShape instanceof TextBoxShape tb) {
                tb.setItalic(italicBtn.isSelected());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        ColorPicker textColorPicker = new ColorPicker(Color.BLACK);
        textColorPicker.setPrefWidth(42);
        textColorPicker.setTooltip(new Tooltip("Text Color"));
        textColorPicker.setOnAction(e -> {
            if (selectedShape instanceof TextBoxShape tb) {
                tb.setTextColor(textColorPicker.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        textGroup.getNodes().addAll(fontSizeCombo, boldBtn, italicBtn, textColorPicker);

        // Style Group
        RibbonGroup styleGroup = new RibbonGroup();
        styleGroup.setTitle("Shape Style");

        ColorPicker fillColorPicker = new ColorPicker(Color.rgb(79, 70, 229));
        fillColorPicker.setPrefWidth(42);
        fillColorPicker.setTooltip(new Tooltip("Fill Color"));
        fillColorPicker.setOnAction(e -> {
            if (selectedShape != null) {
                selectedShape.setFillColor(fillColorPicker.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        ColorPicker strokeColorPicker = new ColorPicker(Color.rgb(67, 56, 202));
        strokeColorPicker.setPrefWidth(42);
        strokeColorPicker.setTooltip(new Tooltip("Border Color"));
        strokeColorPicker.setOnAction(e -> {
            if (selectedShape != null) {
                selectedShape.setStrokeColor(strokeColorPicker.getValue());
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        styleGroup.getNodes().addAll(new Label("Fill:"), fillColorPicker, new Label("Border:"), strokeColorPicker);

        // Arrange Group
        RibbonGroup arrangeGroup = new RibbonGroup();
        arrangeGroup.setTitle("Arrange");

        Button bringFrontBtn = AppIcons.button("Bring Forward", MaterialDesignA.ARRANGE_BRING_TO_FRONT, e -> {
            if (selectedShape != null) {
                presentation.getActiveSlide().bringToFront(selectedShape);
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        Button sendBackBtn = AppIcons.button("Send Back", MaterialDesignA.ARRANGE_SEND_TO_BACK, e -> {
            if (selectedShape != null) {
                presentation.getActiveSlide().sendToBack(selectedShape);
                presentation.setModified(true);
                renderActiveSlide();
            }
        });

        arrangeGroup.getNodes().addAll(bringFrontBtn, sendBackBtn);

        homeTab.getRibbonGroups().addAll(slidesGroup, clipboardGroup, textGroup, styleGroup, arrangeGroup);

        // =========================================================================
        // Tab 2: INSERT
        // =========================================================================
        RibbonTab insertTab = new RibbonTab("Insert");

        RibbonGroup shapesGroup = new RibbonGroup();
        shapesGroup.setTitle("Shapes");

        MenuButton insertShapeBtn = new MenuButton("Insert Shape");
        insertShapeBtn.setGraphic(AppIcons.of(MaterialDesignS.SHAPE_PLUS, 16));
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

        insertShapeBtn.getItems().addAll(addRect, addRoundedRect, addEllipse, addTriangle, addArrow, addStar);
        shapesGroup.getNodes().add(insertShapeBtn);

        RibbonGroup textInsertGroup = new RibbonGroup();
        textInsertGroup.setTitle("Text");
        Button addTextBtn = AppIcons.button("Text Box", MaterialDesignT.TEXT_BOX, e -> addShapeToActiveSlide(new TextBoxShape(200, 200, 320, 70, "Click to edit text", 20.0, false)));
        textInsertGroup.getNodes().add(addTextBtn);

        RibbonGroup mediaGroup = new RibbonGroup();
        mediaGroup.setTitle("Media & Tables");
        Button insertImageBtn = AppIcons.button("Picture", MaterialDesignI.IMAGE, e -> handleInsertImage());
        Button addTableBtn = AppIcons.button("Table (3x3)", MaterialDesignT.TABLE, e -> addShapeToActiveSlide(new TableShape(3, 3, 200, 200, 420, 160)));
        mediaGroup.getNodes().addAll(insertImageBtn, addTableBtn);

        insertTab.getRibbonGroups().addAll(shapesGroup, textInsertGroup, mediaGroup);

        // =========================================================================
        // Tab 3: DESIGN
        // =========================================================================
        RibbonTab designTab = new RibbonTab("Design");

        RibbonGroup bgGroup = new RibbonGroup();
        bgGroup.setTitle("Background");
        ColorPicker slideBgPicker = new ColorPicker(presentation.getActiveSlide().getBackgroundColor());
        slideBgPicker.setPrefWidth(55);
        slideBgPicker.setTooltip(new Tooltip("Slide Background Color"));
        slideBgPicker.setOnAction(e -> {
            presentation.getActiveSlide().setBackgroundColor(slideBgPicker.getValue());
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        });
        bgGroup.getNodes().addAll(new Label("Background:"), slideBgPicker);

        RibbonGroup themeGroup = new RibbonGroup();
        themeGroup.setTitle("Themes");
        Button themeDark = new Button("Modern Obsidian");
        themeDark.setOnAction(e -> {
            presentation.getActiveSlide().setBackgroundColor(Color.rgb(15, 23, 42));
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        });

        Button themeLight = new Button("Executive Clean");
        themeLight.setOnAction(e -> {
            presentation.getActiveSlide().setBackgroundColor(Color.WHITE);
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        });

        Button themeIndigo = new Button("Indigo Slate");
        themeIndigo.setOnAction(e -> {
            presentation.getActiveSlide().setBackgroundColor(Color.rgb(241, 245, 249));
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        });

        themeGroup.getNodes().addAll(themeLight, themeDark, themeIndigo);

        designTab.getRibbonGroups().addAll(bgGroup, themeGroup);

        // =========================================================================
        // Tab 4: SLIDE SHOW
        // =========================================================================
        RibbonTab showTab = new RibbonTab("Slide Show");

        RibbonGroup presentGroup = new RibbonGroup();
        presentGroup.setTitle("Start Presentation");
        Button slideshowBtn = AppIcons.button("Present (F5)", MaterialDesignP.PRESENTATION_PLAY, e -> startSlideshow());
        slideshowBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px;");
        presentGroup.getNodes().add(slideshowBtn);

        RibbonGroup transGroup = new RibbonGroup();
        transGroup.setTitle("Transitions");
        ComboBox<Slide.Transition> transCombo = new ComboBox<>();
        transCombo.getItems().addAll(Slide.Transition.values());
        transCombo.setValue(presentation.getActiveSlide().getTransition());
        transCombo.setOnAction(e -> {
            presentation.getActiveSlide().setTransition(transCombo.getValue());
            presentation.setModified(true);
        });
        transGroup.getNodes().addAll(new Label("Effect:"), transCombo);

        showTab.getRibbonGroups().addAll(presentGroup, transGroup);

        ribbon.getTabs().addAll(homeTab, insertTab, designTab, showTab);
    }

    private void handleInsertImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Insert Slide Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Image img = new Image(fis);
                double w = Math.min(img.getWidth(), 400);
                double h = img.getHeight() * (w / Math.max(1, img.getWidth()));
                ImageShape imgShape = new ImageShape(img, 200, 150, w, h);
                addShapeToActiveSlide(imgShape);
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to load image: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        }
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
            card.setStyle("-fx-background-color: white; -fx-border-radius: 6px; -fx-background-radius: 6px; " +
                    (isActive ? "-fx-border-color: #6366f1; -fx-border-width: 2px; -fx-effect: dropshadow(gaussian, rgba(99,102,241,0.25), 8, 0, 0, 2);" : "-fx-border-color: #e2e8f0; -fx-border-width: 1px;"));

            Canvas thumbCanvas = new Canvas(thumbW, thumbH);
            GraphicsContext tgc = thumbCanvas.getGraphicsContext2D();
            double scale = thumbW / presentation.getSlideWidth();
            SlideRenderer.renderSlide(tgc, slide, presentation.getSlideWidth(), presentation.getSlideHeight(), scale, null);

            Label titleLabel = new Label((index + 1) + ". " + slide.getTitle());
            titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-weight: " + (isActive ? "bold" : "normal") + ";");

            card.getChildren().addAll(thumbCanvas, titleLabel);
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

        // Check if a handle on selectedShape was clicked
        if (selectedShape != null) {
            ResizeHandle handle = hitTestHandle(selectedShape, slideX, slideY);
            if (handle != ResizeHandle.NONE) {
                activeHandle = handle;
                isDraggingShape = true;
                dragStartX = slideX;
                dragStartY = slideY;
                shapeStartX = selectedShape.getX();
                shapeStartY = selectedShape.getY();
                shapeStartW = selectedShape.getWidth();
                shapeStartH = selectedShape.getHeight();
                return;
            }
        }

        SlideShape hit = slide.hitTest(slideX, slideY);
        selectedShape = hit;

        if (e.getClickCount() == 2) {
            if (selectedShape instanceof TextBoxShape tb) {
                showInPlaceEditor(tb);
                return;
            } else if (selectedShape instanceof TableShape ts) {
                TextInputDialog dialog = new TextInputDialog(ts.getCell(0, 0));
                dialog.setTitle("Edit Table Cell");
                dialog.setHeaderText("Enter text for cell (row 1, col 1):");
                dialog.showAndWait().ifPresent(newText -> {
                    ts.setCell(0, 0, newText);
                    presentation.setModified(true);
                    refreshThumbnails();
                    renderActiveSlide();
                });
                return;
            }
        }

        if (selectedShape != null) {
            isDraggingShape = true;
            activeHandle = ResizeHandle.NONE;
            dragStartX = slideX;
            dragStartY = slideY;
            shapeStartX = selectedShape.getX();
            shapeStartY = selectedShape.getY();
            shapeStartW = selectedShape.getWidth();
            shapeStartH = selectedShape.getHeight();
        }

        renderActiveSlide();
    }

    private ResizeHandle hitTestHandle(SlideShape shape, double px, double py) {
        double x = shape.getX();
        double y = shape.getY();
        double w = shape.getWidth();
        double h = shape.getHeight();
        double tol = 8.0 / currentScale;

        if (Math.abs(px - x) <= tol && Math.abs(py - y) <= tol) return ResizeHandle.NW;
        if (Math.abs(px - (x + w / 2)) <= tol && Math.abs(py - y) <= tol) return ResizeHandle.N;
        if (Math.abs(px - (x + w)) <= tol && Math.abs(py - y) <= tol) return ResizeHandle.NE;
        if (Math.abs(px - (x + w)) <= tol && Math.abs(py - (y + h / 2)) <= tol) return ResizeHandle.E;
        if (Math.abs(px - (x + w)) <= tol && Math.abs(py - (y + h)) <= tol) return ResizeHandle.SE;
        if (Math.abs(px - (x + w / 2)) <= tol && Math.abs(py - (y + h)) <= tol) return ResizeHandle.S;
        if (Math.abs(px - x) <= tol && Math.abs(py - (y + h)) <= tol) return ResizeHandle.SW;
        if (Math.abs(px - x) <= tol && Math.abs(py - (y + h / 2)) <= tol) return ResizeHandle.W;

        return ResizeHandle.NONE;
    }

    private void handleCanvasDragged(MouseEvent e) {
        if (!isDraggingShape || selectedShape == null) return;

        double slideX = e.getX() / currentScale;
        double slideY = e.getY() / currentScale;
        double dx = slideX - dragStartX;
        double dy = slideY - dragStartY;

        if (activeHandle != ResizeHandle.NONE) {
            // Resize shape
            switch (activeHandle) {
                case SE -> {
                    selectedShape.setWidth(Math.max(20, shapeStartW + dx));
                    selectedShape.setHeight(Math.max(20, shapeStartH + dy));
                }
                case E -> selectedShape.setWidth(Math.max(20, shapeStartW + dx));
                case S -> selectedShape.setHeight(Math.max(20, shapeStartH + dy));
                case SW -> {
                    double newW = Math.max(20, shapeStartW - dx);
                    selectedShape.setX(shapeStartX + (shapeStartW - newW));
                    selectedShape.setWidth(newW);
                    selectedShape.setHeight(Math.max(20, shapeStartH + dy));
                }
                case W -> {
                    double newW = Math.max(20, shapeStartW - dx);
                    selectedShape.setX(shapeStartX + (shapeStartW - newW));
                    selectedShape.setWidth(newW);
                }
                case NW -> {
                    double newW = Math.max(20, shapeStartW - dx);
                    double newH = Math.max(20, shapeStartH - dy);
                    selectedShape.setX(shapeStartX + (shapeStartW - newW));
                    selectedShape.setY(shapeStartY + (shapeStartH - newH));
                    selectedShape.setWidth(newW);
                    selectedShape.setHeight(newH);
                }
                case N -> {
                    double newH = Math.max(20, shapeStartH - dy);
                    selectedShape.setY(shapeStartY + (shapeStartH - newH));
                    selectedShape.setHeight(newH);
                }
                case NE -> {
                    double newH = Math.max(20, shapeStartH - dy);
                    selectedShape.setY(shapeStartY + (shapeStartH - newH));
                    selectedShape.setWidth(Math.max(20, shapeStartW + dx));
                    selectedShape.setHeight(newH);
                }
            }
        } else {
            // Move shape
            selectedShape.setX(Math.max(0, shapeStartX + dx));
            selectedShape.setY(Math.max(0, shapeStartY + dy));
        }

        presentation.setModified(true);
        renderActiveSlide();
    }

    private void showInPlaceEditor(TextBoxShape tb) {
        this.editingTextBox = tb;
        inPlaceTextEditor.setText(tb.getText());
        double x = tb.getX() * currentScale - (canvas.getWidth() / 2.0) + (tb.getWidth() * currentScale / 2.0);
        double y = tb.getY() * currentScale - (canvas.getHeight() / 2.0) + (tb.getHeight() * currentScale / 2.0);
        inPlaceTextEditor.setTranslateX(x);
        inPlaceTextEditor.setTranslateY(y);
        inPlaceTextEditor.setPrefWidth(Math.max(tb.getWidth() * currentScale, 100));
        inPlaceTextEditor.setPrefHeight(Math.max(tb.getHeight() * currentScale, 40));
        inPlaceTextEditor.setStyle(String.format(
                "-fx-background-color: white; -fx-border-color: #2563eb; -fx-border-width: 1.5px; -fx-font-family: '%s'; -fx-font-size: %.1fpx;",
                tb.getFontFamily(), Math.max(10, tb.getFontSize() * currentScale * 0.9)
        ));
        inPlaceTextEditor.setVisible(true);
        inPlaceTextEditor.setManaged(true);
        inPlaceTextEditor.requestFocus();
        inPlaceTextEditor.selectAll();
    }

    private void commitInPlaceEditor() {
        if (inPlaceTextEditor.isVisible() && editingTextBox != null) {
            editingTextBox.setText(inPlaceTextEditor.getText());
            inPlaceTextEditor.setVisible(false);
            inPlaceTextEditor.setManaged(false);
            editingTextBox = null;
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
            canvas.requestFocus();
        }
    }

    private void cancelInPlaceEditor() {
        if (inPlaceTextEditor.isVisible()) {
            inPlaceTextEditor.setVisible(false);
            inPlaceTextEditor.setManaged(false);
            editingTextBox = null;
            canvas.requestFocus();
        }
    }

    private void handleKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.F5) {
            startSlideshow();
        } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.F2) {
            if (selectedShape instanceof TextBoxShape tb) {
                showInPlaceEditor(tb);
                e.consume();
            }
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            if (selectedShape != null) {
                presentation.getActiveSlide().removeShape(selectedShape);
                selectedShape = null;
                presentation.setModified(true);
                refreshThumbnails();
                renderActiveSlide();
                e.consume();
            }
        } else if (selectedShape != null && (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN ||
                                             e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT)) {
            double step = e.isShiftDown() ? 10.0 : 1.0;
            if (e.getCode() == KeyCode.UP) selectedShape.setY(selectedShape.getY() - step);
            else if (e.getCode() == KeyCode.DOWN) selectedShape.setY(selectedShape.getY() + step);
            else if (e.getCode() == KeyCode.LEFT) selectedShape.setX(selectedShape.getX() - step);
            else if (e.getCode() == KeyCode.RIGHT) selectedShape.setX(selectedShape.getX() + step);
            presentation.setModified(true);
            renderActiveSlide();
            e.consume();
        }
    }

    @Override
    public DocumentModel getDocument() {
        return presentation;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbon;
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
    public void copy() {
        if (selectedShape != null) {
            clipboardShape = selectedShape.copy();
        }
    }

    @Override
    public void cut() {
        copy();
        if (selectedShape != null) {
            presentation.getActiveSlide().removeShape(selectedShape);
            selectedShape = null;
            presentation.setModified(true);
            refreshThumbnails();
            renderActiveSlide();
        }
    }

    @Override
    public void paste() {
        if (clipboardShape != null) {
            SlideShape clone = clipboardShape.copy();
            clone.setX(clone.getX() + 20);
            clone.setY(clone.getY() + 20);
            addShapeToActiveSlide(clone);
        }
    }

    @Override
    public void selectAll() {
        Slide s = presentation.getActiveSlide();
        if (s != null && !s.getShapes().isEmpty()) {
            selectedShape = s.getShapes().get(0);
            renderActiveSlide();
        }
    }

    @Override
    public void showSearch() {
        toggleSearch(true);
    }

    @Override
    public void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene() != null ? getScene().getWindow() : null)) {
            Printer printer = job.getPrinter();
            PageLayout layout = printer.createPageLayout(Paper.A4, PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT);
            job.getJobSettings().setPageLayout(layout);

            boolean success = true;
            for (Slide slide : presentation.getSlides()) {
                Canvas printCanvas = new Canvas(presentation.getSlideWidth(), presentation.getSlideHeight());
                GraphicsContext pgc = printCanvas.getGraphicsContext2D();
                SlideRenderer.renderSlide(pgc, slide, presentation.getSlideWidth(), presentation.getSlideHeight(), 1.0, null);

                if (!job.printPage(printCanvas)) {
                    success = false;
                    break;
                }
            }

            if (success) {
                job.endJob();
            }
        }
    }

    @Override
    public void close() {
    }
}
