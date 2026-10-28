package org.originsd.oqteris.calc.formula;

import org.originsd.oqteris.calc.model.CellAddress;

import java.util.Set;

/**
 * Node in the abstract syntax tree of a parsed spreadsheet formula.
 */
public interface ExpressionNode {
    /**
     * Evaluates this node within the given context.
     * Returns Double, String, Boolean, or Excel error string (e.g. #VALUE!, #REF!, #DIV/0!).
     */
    Object evaluate(EvaluationContext context);

    /**
     * Collects all cell addresses referenced by this expression for dependency tracking.
     */
    void collectReferences(String defaultSheet, Set<CellAddress> references);
}
