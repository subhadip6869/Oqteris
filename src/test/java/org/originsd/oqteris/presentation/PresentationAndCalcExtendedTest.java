package org.originsd.oqteris.presentation;

import org.junit.jupiter.api.Test;
import org.originsd.oqteris.calc.chart.SpreadsheetChart;
import org.originsd.oqteris.calc.model.CellRangeAddress;
import org.originsd.oqteris.presentation.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PresentationAndCalcExtendedTest {

    @Test
    public void testPresentationShapeCloningAndZOrder() {
        Presentation pres = new Presentation();
        Slide slide = new Slide("Blank");
        pres.addSlide(slide);

        GeometricShape rect = new GeometricShape(GeometricShape.Geometry.RECTANGLE, 100, 100, 200, 150);
        TextBoxShape tb = new TextBoxShape(150, 150, 180, 50, "Sample Label", 18.0, false);

        slide.addShape(rect);
        slide.addShape(tb);

        assertEquals(2, slide.getShapes().size());
        assertEquals(rect, slide.getShapes().get(0));
        assertEquals(tb, slide.getShapes().get(1));

        // Bring rect to front
        slide.bringToFront(rect);
        assertEquals(rect, slide.getShapes().get(1));
        assertEquals(tb, slide.getShapes().get(0));

        // Send rect to back
        slide.sendToBack(rect);
        assertEquals(rect, slide.getShapes().get(0));

        // Copy shape
        SlideShape copy = tb.copy();
        assertNotNull(copy);
        assertTrue(copy instanceof TextBoxShape);
        assertEquals("Sample Label", ((TextBoxShape) copy).getText());
        assertEquals(18.0, ((TextBoxShape) copy).getFontSize());
    }

    @Test
    public void testCalcChartModel() {
        SpreadsheetChart chart = new SpreadsheetChart(
                SpreadsheetChart.ChartType.COLUMN,
                "Quarterly Sales",
                new CellRangeAddress(1, 4, 0, 0),
                List.of(new CellRangeAddress(1, 4, 1, 1))
        );

        assertEquals(SpreadsheetChart.ChartType.COLUMN, chart.getChartType());
        assertEquals("Quarterly Sales", chart.getTitle());
        assertNotNull(chart.getCategoryRange());
        assertEquals(1, chart.getSeriesRanges().size());
    }
}
