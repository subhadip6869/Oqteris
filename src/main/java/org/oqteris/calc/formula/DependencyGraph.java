package org.oqteris.calc.formula;

import org.oqteris.calc.model.CellAddress;

import java.util.*;

/**
 * Directed Acyclic Graph (DAG) tracking formula dependencies across spreadsheet cells.
 * Detects circular dependencies and calculates topological evaluation orders.
 */
public class DependencyGraph {
    // Cell -> Set of cells it depends on
    private final Map<CellAddress, Set<CellAddress>> dependencies = new HashMap<>();

    // Cell -> Set of cells that depend on it
    private final Map<CellAddress, Set<CellAddress>> dependents = new HashMap<>();

    public synchronized void setDependencies(CellAddress cell, Set<CellAddress> cellDependsOn) {
        if (cell == null) return;

        // Remove old dependencies
        Set<CellAddress> oldDeps = dependencies.remove(cell);
        if (oldDeps != null) {
            for (CellAddress dep : oldDeps) {
                Set<CellAddress> set = dependents.get(dep);
                if (set != null) {
                    set.remove(cell);
                    if (set.isEmpty()) dependents.remove(dep);
                }
            }
        }

        // Add new dependencies
        if (cellDependsOn != null && !cellDependsOn.isEmpty()) {
            dependencies.put(cell, new HashSet<>(cellDependsOn));
            for (CellAddress dep : cellDependsOn) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(cell);
            }
        }
    }

    public synchronized void removeCell(CellAddress cell) {
        setDependencies(cell, Collections.emptySet());
    }

    /**
     * Checks if adding dependencies from 'cell' introduces a circular dependency.
     */
    public synchronized boolean hasCycle(CellAddress startCell) {
        Set<CellAddress> visited = new HashSet<>();
        Set<CellAddress> recursionStack = new HashSet<>();
        return dfsCycleCheck(startCell, visited, recursionStack);
    }

    private boolean dfsCycleCheck(CellAddress current, Set<CellAddress> visited, Set<CellAddress> recursionStack) {
        visited.add(current);
        recursionStack.add(current);

        Set<CellAddress> deps = dependencies.get(current);
        if (deps != null) {
            for (CellAddress neighbor : deps) {
                if (!visited.contains(neighbor)) {
                    if (dfsCycleCheck(neighbor, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    return true;
                }
            }
        }

        recursionStack.remove(current);
        return false;
    }

    /**
     * Computes topological evaluation order of all cells affected by a change to startCell.
     */
    public synchronized List<CellAddress> getEvaluationOrder(CellAddress startCell) {
        Set<CellAddress> affected = new HashSet<>();
        collectDependents(startCell, affected);

        List<CellAddress> order = new ArrayList<>();
        Set<CellAddress> visited = new HashSet<>();

        for (CellAddress cell : affected) {
            if (!visited.contains(cell)) {
                topologicalSort(cell, visited, order, affected);
            }
        }

        Collections.reverse(order);
        return order;
    }

    private void collectDependents(CellAddress current, Set<CellAddress> affected) {
        affected.add(current);
        Set<CellAddress> deps = dependents.get(current);
        if (deps != null) {
            for (CellAddress d : deps) {
                if (!affected.contains(d)) {
                    collectDependents(d, affected);
                }
            }
        }
    }

    private void topologicalSort(CellAddress current, Set<CellAddress> visited, List<CellAddress> order, Set<CellAddress> allowed) {
        visited.add(current);
        Set<CellAddress> next = dependents.get(current);
        if (next != null) {
            for (CellAddress neighbor : next) {
                if (allowed.contains(neighbor) && !visited.contains(neighbor)) {
                    topologicalSort(neighbor, visited, order, allowed);
                }
            }
        }
        order.add(current);
    }
}
