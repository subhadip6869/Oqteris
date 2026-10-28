package org.oqteris.presentation.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model representing a single slide within a presentation.
 */
public class Slide {
    public enum Transition {
        NONE,
        FADE,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        ZOOM
    }

    private String title = "Untitled Slide";
    private Color backgroundColor = Color.WHITE;
    private String notes = "";
    private Transition transition = Transition.FADE;
    private final List<SlideShape> shapes = new ArrayList<>();

    public Slide() {
    }

    public Slide(String title) {
        this.title = title != null ? title : "Slide";
    }

    public static Slide createTitleSlide(String titleText, String subtitleText) {
        Slide s = new Slide(titleText);
        // Title text box
        TextBoxShape titleBox = new TextBoxShape(100, 160, 760, 90, titleText, 38.0, true);
        titleBox.setAlignment(TextBoxShape.Alignment.CENTER);
        titleBox.setTextColor(Color.rgb(16, 68, 115));
        s.addShape(titleBox);

        // Subtitle text box
        TextBoxShape subBox = new TextBoxShape(150, 270, 660, 60, subtitleText, 20.0, false);
        subBox.setAlignment(TextBoxShape.Alignment.CENTER);
        subBox.setTextColor(Color.rgb(100, 100, 100));
        s.addShape(subBox);

        return s;
    }

    public static Slide createContentSlide(String heading, String bulletContent) {
        Slide s = new Slide(heading);
        // Header title
        TextBoxShape headerBox = new TextBoxShape(60, 40, 840, 60, heading, 28.0, true);
        headerBox.setTextColor(Color.rgb(16, 68, 115));
        s.addShape(headerBox);

        // Content
        TextBoxShape contentBox = new TextBoxShape(60, 120, 840, 360, bulletContent, 18.0, false);
        s.addShape(contentBox);

        return s;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "Slide";
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor != null ? backgroundColor : Color.WHITE;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
    }

    public Transition getTransition() {
        return transition;
    }

    public void setTransition(Transition transition) {
        this.transition = transition != null ? transition : Transition.NONE;
    }

    public List<SlideShape> getShapes() {
        return shapes;
    }

    public void addShape(SlideShape shape) {
        if (shape != null) {
            shapes.add(shape);
        }
    }

    public void removeShape(SlideShape shape) {
        shapes.remove(shape);
    }

    public void bringToFront(SlideShape shape) {
        if (shapes.remove(shape)) {
            shapes.add(shape);
        }
    }

    public void sendToBack(SlideShape shape) {
        if (shapes.remove(shape)) {
            shapes.add(0, shape);
        }
    }

    public SlideShape hitTest(double px, double py) {
        // Iterate from top-most z-order to bottom
        for (int i = shapes.size() - 1; i >= 0; i--) {
            SlideShape s = shapes.get(i);
            if (s.contains(px, py)) {
                return s;
            }
        }
        return null;
    }

    public Slide copy() {
        Slide s = new Slide(this.title);
        s.backgroundColor = this.backgroundColor;
        s.notes = this.notes;
        s.transition = this.transition;
        for (SlideShape shape : shapes) {
            s.addShape(shape.copy());
        }
        return s;
    }
}
