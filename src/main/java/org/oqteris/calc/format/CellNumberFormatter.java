package org.oqteris.calc.format;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Formats spreadsheet cell values according to Excel number format patterns.
 */
public class CellNumberFormatter {

    public static String format(Object value, String pattern) {
        if (value == null) {
            return "";
        }

        if (pattern == null || pattern.isBlank() || pattern.equalsIgnoreCase("General")) {
            if (value instanceof Double d) {
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e12) {
                    return String.valueOf(d.longValue());
                }
                return String.valueOf(d);
            }
            return value.toString();
        }

        if (value instanceof Number num) {
            double d = num.doubleValue();
            try {
                // Currency shortcut
                if (pattern.contains("$")) {
                    DecimalFormat df = new DecimalFormat("$#,##0.00");
                    return df.format(d);
                }
                // Percentage shortcut
                if (pattern.contains("%")) {
                    DecimalFormat df = new DecimalFormat("0.00%");
                    return df.format(d);
                }
                // Custom DecimalFormat
                DecimalFormat df = new DecimalFormat(pattern);
                return df.format(d);
            } catch (Exception e) {
                return String.valueOf(d);
            }
        }

        if (value instanceof Date date) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                return sdf.format(date);
            } catch (Exception e) {
                return date.toString();
            }
        }

        return value.toString();
    }
}
