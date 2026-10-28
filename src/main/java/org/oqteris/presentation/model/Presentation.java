package org.oqteris.presentation.model;

import org.oqteris.core.model.AbstractDocumentModel;
import org.oqteris.core.model.DocumentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-level presentation document model containing slides and slide masters.
 */
public class Presentation extends AbstractDocumentModel {
    private double slideWidth = 960.0;
    private double slideHeight = 540.0;
    private final List<Slide> slides = new ArrayList<>();
    private int activeSlideIndex = 0;

    public Presentation() {
        this("Presentation 1");
    }

    public Presentation(String defaultTitle) {
        super(DocumentType.IMPRESS, defaultTitle);
        // Default presentation has a title slide and a content slide
        slides.add(Slide.createTitleSlide("Oqteris Presentation", "Independent Cross-Platform Desktop Office Suite"));
        slides.add(Slide.createContentSlide("Key Architecture Highlights",
                "• 100% Standalone Java & JavaFX Implementation\n" +
                "• Word Processor with Independent Pagination & Layout\n" +
                "• Spreadsheet with DAG Formula Recalculation Engine\n" +
                "• Presentation Slide Canvas with Slideshow Mode\n" +
                "• PDF Viewer with Cryptographic Signature & Certificate Trust Verification"));
    }

    public double getSlideWidth() {
        return slideWidth;
    }

    public void setSlideWidth(double slideWidth) {
        this.slideWidth = Math.max(300.0, slideWidth);
    }

    public double getSlideHeight() {
        return slideHeight;
    }

    public void setSlideHeight(double slideHeight) {
        this.slideHeight = Math.max(200.0, slideHeight);
    }

    public List<Slide> getSlides() {
        return Collections.unmodifiableList(slides);
    }

    public int getSlideCount() {
        return slides.size();
    }

    public Slide getSlide(int index) {
        if (index >= 0 && index < slides.size()) {
            return slides.get(index);
        }
        return null;
    }

    public Slide getActiveSlide() {
        if (activeSlideIndex >= 0 && activeSlideIndex < slides.size()) {
            return slides.get(activeSlideIndex);
        }
        return slides.isEmpty() ? null : slides.get(0);
    }

    public int getActiveSlideIndex() {
        return activeSlideIndex;
    }

    public void setActiveSlideIndex(int activeSlideIndex) {
        if (activeSlideIndex >= 0 && activeSlideIndex < slides.size()) {
            this.activeSlideIndex = activeSlideIndex;
            notifyChanged();
        }
    }

    public Slide createSlide(String title) {
        Slide s = Slide.createContentSlide(title != null ? title : "New Slide", "• Bullet point 1\n• Bullet point 2");
        slides.add(s);
        setModified(true);
        notifyChanged();
        return s;
    }

    public void addSlide(Slide slide) {
        if (slide != null) {
            slides.add(slide);
            setModified(true);
            notifyChanged();
        }
    }

    public void duplicateSlide(int index) {
        if (index >= 0 && index < slides.size()) {
            Slide copy = slides.get(index).copy();
            slides.add(index + 1, copy);
            setModified(true);
            notifyChanged();
        }
    }

    public void clearSlides() {
        slides.clear();
        activeSlideIndex = 0;
        setModified(true);
        notifyChanged();
    }

    public void removeSlide(int index) {
        if (slides.size() > 1 && index >= 0 && index < slides.size()) {
            slides.remove(index);
            if (activeSlideIndex >= slides.size()) {
                activeSlideIndex = slides.size() - 1;
            }
            setModified(true);
            notifyChanged();
        }
    }

    public void reorderSlide(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < slides.size() && toIndex >= 0 && toIndex < slides.size() && fromIndex != toIndex) {
            Slide s = slides.remove(fromIndex);
            slides.add(toIndex, s);
            activeSlideIndex = toIndex;
            setModified(true);
            notifyChanged();
        }
    }
}
