package org.oqteris.calc.formula;

import org.oqteris.calc.model.Cell;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Standard Excel function implementations.
 */
public class FunctionRegistry {
    @FunctionalInterface
    public interface ExcelFunction {
        Object execute(List<Object> args, EvaluationContext context);
    }

    private static final Map<String, ExcelFunction> FUNCTIONS = new HashMap<>();

    static {
        registerMathFunctions();
        registerLogicFunctions();
        registerLookupFunctions();
        registerTextFunctions();
        registerDateFunctions();
    }

    public static Object call(String functionName, List<Object> args, EvaluationContext context) {
        if (functionName == null) return "#NAME?";
        ExcelFunction fn = FUNCTIONS.get(functionName.toUpperCase(Locale.ROOT));
        if (fn != null) {
            try {
                return fn.execute(args, context);
            } catch (Exception e) {
                return "#VALUE!";
            }
        }
        return "#NAME?";
    }

    public static boolean hasFunction(String name) {
        return name != null && FUNCTIONS.containsKey(name.toUpperCase(Locale.ROOT));
    }

    private static void registerMathFunctions() {
        // SUM
        FUNCTIONS.put("SUM", (args, ctx) -> {
            double sum = 0;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) sum += n.doubleValue();
                else if (obj instanceof String s) {
                    try { sum += Double.parseDouble(s); } catch (Exception ignored) {}
                }
            }
            return sum;
        });

        // AVERAGE
        FUNCTIONS.put("AVERAGE", (args, ctx) -> {
            double sum = 0;
            int count = 0;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) {
                    sum += n.doubleValue();
                    count++;
                }
            }
            return count > 0 ? (sum / count) : "#DIV/0!";
        });

        // COUNT
        FUNCTIONS.put("COUNT", (args, ctx) -> {
            int count = 0;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number) count++;
            }
            return (double) count;
        });

        // COUNTA
        FUNCTIONS.put("COUNTA", (args, ctx) -> {
            int count = 0;
            for (Object obj : flatten(args)) {
                if (obj != null && !obj.toString().isEmpty()) count++;
            }
            return (double) count;
        });

        // MIN / MAX
        FUNCTIONS.put("MIN", (args, ctx) -> {
            double min = Double.MAX_VALUE;
            boolean found = false;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) {
                    min = Math.min(min, n.doubleValue());
                    found = true;
                }
            }
            return found ? min : 0.0;
        });

        FUNCTIONS.put("MAX", (args, ctx) -> {
            double max = -Double.MAX_VALUE;
            boolean found = false;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) {
                    max = Math.max(max, n.doubleValue());
                    found = true;
                }
            }
            return found ? max : 0.0;
        });

        // PRODUCT
        FUNCTIONS.put("PRODUCT", (args, ctx) -> {
            double p = 1.0;
            boolean found = false;
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) {
                    p *= n.doubleValue();
                    found = true;
                }
            }
            return found ? p : 0.0;
        });

        // ABS, SQRT, POWER, MOD
        FUNCTIONS.put("ABS", (args, ctx) -> Math.abs(toDouble(args.get(0))));
        FUNCTIONS.put("SQRT", (args, ctx) -> {
            double d = toDouble(args.get(0));
            return d >= 0 ? Math.sqrt(d) : "#NUM!";
        });
        FUNCTIONS.put("POWER", (args, ctx) -> Math.pow(toDouble(args.get(0)), toDouble(args.get(1))));
        FUNCTIONS.put("MOD", (args, ctx) -> {
            double d = toDouble(args.get(1));
            return d != 0 ? toDouble(args.get(0)) % d : "#DIV/0!";
        });

        // ROUND, ROUNDUP, ROUNDDOWN
        FUNCTIONS.put("ROUND", (args, ctx) -> {
            double val = toDouble(args.get(0));
            int digits = args.size() > 1 ? (int) toDouble(args.get(1)) : 0;
            double factor = Math.pow(10, digits);
            return Math.round(val * factor) / factor;
        });
        FUNCTIONS.put("ROUNDUP", (args, ctx) -> {
            double val = toDouble(args.get(0));
            int digits = args.size() > 1 ? (int) toDouble(args.get(1)) : 0;
            double factor = Math.pow(10, digits);
            return Math.ceil(val * factor) / factor;
        });
        FUNCTIONS.put("ROUNDDOWN", (args, ctx) -> {
            double val = toDouble(args.get(0));
            int digits = args.size() > 1 ? (int) toDouble(args.get(1)) : 0;
            double factor = Math.pow(10, digits);
            return Math.floor(val * factor) / factor;
        });

        // MEDIAN
        FUNCTIONS.put("MEDIAN", (args, ctx) -> {
            List<Double> list = new ArrayList<>();
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) list.add(n.doubleValue());
            }
            if (list.isEmpty()) return "#NUM!";
            Collections.sort(list);
            int sz = list.size();
            if (sz % 2 == 1) return list.get(sz / 2);
            return (list.get(sz / 2 - 1) + list.get(sz / 2)) / 2.0;
        });

        // STDEV
        FUNCTIONS.put("STDEV", (args, ctx) -> {
            List<Double> list = new ArrayList<>();
            for (Object obj : flatten(args)) {
                if (obj instanceof Number n) list.add(n.doubleValue());
            }
            if (list.size() < 2) return "#DIV/0!";
            double sum = 0;
            for (double d : list) sum += d;
            double mean = sum / list.size();
            double sumSq = 0;
            for (double d : list) sumSq += Math.pow(d - mean, 2);
            return Math.sqrt(sumSq / (list.size() - 1));
        });

        // SUMIF(range, criteria, [sum_range])
        FUNCTIONS.put("SUMIF", (args, ctx) -> {
            if (args.size() < 2) return "#VALUE!";
            List<Object> range = toList(args.get(0));
            String criteria = args.get(1).toString();
            List<Object> sumRange = args.size() > 2 ? toList(args.get(2)) : range;

            double sum = 0;
            for (int i = 0; i < range.size(); i++) {
                Object val = range.get(i);
                if (matchesCriteria(val, criteria)) {
                    if (i < sumRange.size() && sumRange.get(i) instanceof Number n) {
                        sum += n.doubleValue();
                    }
                }
            }
            return sum;
        });

        // COUNTIF(range, criteria)
        FUNCTIONS.put("COUNTIF", (args, ctx) -> {
            if (args.size() < 2) return "#VALUE!";
            List<Object> range = toList(args.get(0));
            String criteria = args.get(1).toString();
            int count = 0;
            for (Object val : range) {
                if (matchesCriteria(val, criteria)) {
                    count++;
                }
            }
            return (double) count;
        });
    }

    private static void registerLogicFunctions() {
        // IF(cond, trueVal, [falseVal])
        FUNCTIONS.put("IF", (args, ctx) -> {
            if (args.isEmpty()) return "#VALUE!";
            boolean cond = toBoolean(args.get(0));
            if (cond) {
                return args.size() > 1 ? args.get(1) : true;
            } else {
                return args.size() > 2 ? args.get(2) : false;
            }
        });

        // IFS(c1, v1, c2, v2, ...)
        FUNCTIONS.put("IFS", (args, ctx) -> {
            for (int i = 0; i < args.size() - 1; i += 2) {
                if (toBoolean(args.get(i))) {
                    return args.get(i + 1);
                }
            }
            return "#N/A";
        });

        // AND
        FUNCTIONS.put("AND", (args, ctx) -> {
            for (Object obj : flatten(args)) {
                if (!toBoolean(obj)) return false;
            }
            return true;
        });

        // OR
        FUNCTIONS.put("OR", (args, ctx) -> {
            for (Object obj : flatten(args)) {
                if (toBoolean(obj)) return true;
            }
            return false;
        });

        // NOT
        FUNCTIONS.put("NOT", (args, ctx) -> !toBoolean(args.get(0)));

        // XOR
        FUNCTIONS.put("XOR", (args, ctx) -> {
            int trueCount = 0;
            for (Object obj : flatten(args)) {
                if (toBoolean(obj)) trueCount++;
            }
            return (trueCount % 2) == 1;
        });

        // IFERROR(val, fallback)
        FUNCTIONS.put("IFERROR", (args, ctx) -> {
            if (args.isEmpty()) return "#VALUE!";
            Object val = args.get(0);
            if (val instanceof String s && s.startsWith("#")) {
                return args.size() > 1 ? args.get(1) : "";
            }
            return val;
        });
    }

    private static void registerLookupFunctions() {
        // VLOOKUP(lookup_val, table_2d_list, col_index, [range_lookup])
        FUNCTIONS.put("VLOOKUP", (args, ctx) -> {
            if (args.size() < 3) return "#VALUE!";
            Object lookupVal = args.get(0);
            Object tableObj = args.get(1);
            int colIndex = (int) toDouble(args.get(2));
            boolean exactMatch = args.size() > 3 ? !toBoolean(args.get(3)) : true;

            if (!(tableObj instanceof List<?> table)) return "#VALUE!";
            if (table.isEmpty()) return "#N/A";

            for (Object rowObj : table) {
                if (rowObj instanceof List<?> row) {
                    if (row.isEmpty()) continue;
                    Object firstCell = row.get(0);
                    if (firstCell != null && compareValues(firstCell, lookupVal) == 0) {
                        int targetIdx = colIndex - 1;
                        if (targetIdx >= 0 && targetIdx < row.size()) {
                            return row.get(targetIdx);
                        }
                        return "#REF!";
                    }
                }
            }
            return "#N/A";
        });

        // INDEX(array, row_num, [col_num])
        FUNCTIONS.put("INDEX", (args, ctx) -> {
            if (args.size() < 2) return "#VALUE!";
            Object arrObj = args.get(0);
            int rowNum = (int) toDouble(args.get(1));
            int colNum = args.size() > 2 ? (int) toDouble(args.get(2)) : 1;

            if (!(arrObj instanceof List<?> list)) return "#VALUE!";
            if (list.isEmpty()) return "#REF!";

            if (list.get(0) instanceof List<?> matrix) {
                int rIdx = rowNum - 1;
                if (rIdx < 0 || rIdx >= list.size()) return "#REF!";
                List<?> row = (List<?>) list.get(rIdx);
                int cIdx = colNum - 1;
                if (cIdx < 0 || cIdx >= row.size()) return "#REF!";
                return row.get(cIdx);
            } else {
                int idx = rowNum - 1;
                if (idx < 0 || idx >= list.size()) return "#REF!";
                return list.get(idx);
            }
        });

        // MATCH(lookup_value, lookup_array, [match_type])
        FUNCTIONS.put("MATCH", (args, ctx) -> {
            if (args.size() < 2) return "#VALUE!";
            Object lookupVal = args.get(0);
            List<Object> array = toList(args.get(1));

            for (int i = 0; i < array.size(); i++) {
                if (compareValues(array.get(i), lookupVal) == 0) {
                    return (double) (i + 1);
                }
            }
            return "#N/A";
        });

        // CHOOSE(index, val1, val2, ...)
        FUNCTIONS.put("CHOOSE", (args, ctx) -> {
            if (args.isEmpty()) return "#VALUE!";
            int idx = (int) toDouble(args.get(0));
            if (idx >= 1 && idx < args.size()) {
                return args.get(idx);
            }
            return "#VALUE!";
        });
    }

    private static void registerTextFunctions() {
        // CONCATENATE / CONCAT
        ExcelFunction concatFn = (args, ctx) -> {
            StringBuilder sb = new StringBuilder();
            for (Object obj : flatten(args)) {
                if (obj != null) sb.append(obj.toString());
            }
            return sb.toString();
        };
        FUNCTIONS.put("CONCATENATE", concatFn);
        FUNCTIONS.put("CONCAT", concatFn);

        // TEXTJOIN(delimiter, ignore_empty, text1, ...)
        FUNCTIONS.put("TEXTJOIN", (args, ctx) -> {
            if (args.size() < 3) return "#VALUE!";
            String delim = args.get(0).toString();
            boolean ignoreEmpty = toBoolean(args.get(1));

            List<Object> items = new ArrayList<>();
            for (int i = 2; i < args.size(); i++) {
                items.addAll(flatten(Collections.singletonList(args.get(i))));
            }

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Object obj : items) {
                if (obj == null) continue;
                String s = obj.toString();
                if (ignoreEmpty && s.isEmpty()) continue;
                if (!first) sb.append(delim);
                sb.append(s);
                first = false;
            }
            return sb.toString();
        });

        // LEFT, RIGHT, MID, LEN, TRIM, UPPER, LOWER, PROPER
        FUNCTIONS.put("LEFT", (args, ctx) -> {
            String s = args.get(0).toString();
            int n = args.size() > 1 ? (int) toDouble(args.get(1)) : 1;
            return s.substring(0, Math.min(s.length(), Math.max(0, n)));
        });

        FUNCTIONS.put("RIGHT", (args, ctx) -> {
            String s = args.get(0).toString();
            int n = args.size() > 1 ? (int) toDouble(args.get(1)) : 1;
            int start = Math.max(0, s.length() - n);
            return s.substring(start);
        });

        FUNCTIONS.put("MID", (args, ctx) -> {
            String s = args.get(0).toString();
            int start = (int) toDouble(args.get(1)) - 1;
            int len = (int) toDouble(args.get(2));
            if (start < 0 || start >= s.length() || len <= 0) return "";
            return s.substring(start, Math.min(s.length(), start + len));
        });

        FUNCTIONS.put("LEN", (args, ctx) -> (double) args.get(0).toString().length());
        FUNCTIONS.put("TRIM", (args, ctx) -> args.get(0).toString().trim());
        FUNCTIONS.put("UPPER", (args, ctx) -> args.get(0).toString().toUpperCase());
        FUNCTIONS.put("LOWER", (args, ctx) -> args.get(0).toString().toLowerCase());

        FUNCTIONS.put("PROPER", (args, ctx) -> {
            String s = args.get(0).toString().toLowerCase();
            StringBuilder sb = new StringBuilder();
            boolean nextUpper = true;
            for (char c : s.toCharArray()) {
                if (Character.isWhitespace(c)) {
                    nextUpper = true;
                    sb.append(c);
                } else if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    private static void registerDateFunctions() {
        // TODAY
        FUNCTIONS.put("TODAY", (args, ctx) -> {
            LocalDate today = LocalDate.now();
            return (double) today.toEpochDay();
        });

        // NOW
        FUNCTIONS.put("NOW", (args, ctx) -> {
            LocalDateTime now = LocalDateTime.now();
            return (double) now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 86400000.0;
        });

        // DATE(y, m, d)
        FUNCTIONS.put("DATE", (args, ctx) -> {
            int y = (int) toDouble(args.get(0));
            int m = (int) toDouble(args.get(1));
            int d = (int) toDouble(args.get(2));
            return (double) LocalDate.of(y, m, d).toEpochDay();
        });

        // YEAR, MONTH, DAY
        FUNCTIONS.put("YEAR", (args, ctx) -> {
            LocalDate ld = LocalDate.ofEpochDay((long) toDouble(args.get(0)));
            return (double) ld.getYear();
        });
        FUNCTIONS.put("MONTH", (args, ctx) -> {
            LocalDate ld = LocalDate.ofEpochDay((long) toDouble(args.get(0)));
            return (double) ld.getMonthValue();
        });
        FUNCTIONS.put("DAY", (args, ctx) -> {
            LocalDate ld = LocalDate.ofEpochDay((long) toDouble(args.get(0)));
            return (double) ld.getDayOfMonth();
        });
    }

    // Helper functions
    private static List<Object> flatten(List<Object> args) {
        List<Object> flat = new ArrayList<>();
        for (Object item : args) {
            if (item instanceof List<?> l) {
                for (Object sub : l) {
                    if (sub instanceof List<?> subL) {
                        for (Object s : subL) {
                            if (s instanceof Cell c) flat.add(c.getEvaluatedValue());
                            else flat.add(s);
                        }
                    } else if (sub instanceof Cell c) {
                        flat.add(c.getEvaluatedValue());
                    } else {
                        flat.add(sub);
                    }
                }
            } else if (item instanceof Cell c) {
                flat.add(c.getEvaluatedValue());
            } else {
                flat.add(item);
            }
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toList(Object obj) {
        if (obj instanceof List<?> l) {
            return (List<Object>) l;
        }
        return Collections.singletonList(obj);
    }

    public static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof Cell c) return toDouble(c.getEvaluatedValue());
        if (obj != null) {
            try {
                return Double.parseDouble(obj.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return 0.0;
    }

    public static boolean toBoolean(Object obj) {
        if (obj instanceof Boolean b) return b;
        if (obj instanceof Number n) return n.doubleValue() != 0;
        if (obj instanceof Cell c) return toBoolean(c.getEvaluatedValue());
        if (obj != null) {
            String s = obj.toString().trim();
            if (s.equalsIgnoreCase("TRUE")) return true;
            if (s.equalsIgnoreCase("FALSE")) return false;
            try {
                return Double.parseDouble(s) != 0;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof Cell ca) a = ca.getEvaluatedValue();
        if (b instanceof Cell cb) b = cb.getEvaluatedValue();

        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static boolean matchesCriteria(Object val, String criteria) {
        if (criteria.startsWith(">=")) return toDouble(val) >= toDouble(criteria.substring(2));
        if (criteria.startsWith("<=")) return toDouble(val) <= toDouble(criteria.substring(2));
        if (criteria.startsWith("<>")) return compareValues(val, criteria.substring(2)) != 0;
        if (criteria.startsWith(">")) return toDouble(val) > toDouble(criteria.substring(1));
        if (criteria.startsWith("<")) return toDouble(val) < toDouble(criteria.substring(1));
        if (criteria.startsWith("=")) return compareValues(val, criteria.substring(1)) == 0;
        return compareValues(val, criteria) == 0;
    }
}
