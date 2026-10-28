package org.oqteris.writer.model;

/**
 * Tab stop representation for word processor paragraphs.
 */
public class TabStop {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        DECIMAL
    }

    public enum Leader {
        NONE,
        DOT,
        HYPHEN,
        UNDERSCORE
    }

    private double position; // position in points from paragraph left margin
    private Alignment alignment = Alignment.LEFT;
    private Leader leader = Leader.NONE;

    public TabStop(double position) {
        this(position, Alignment.LEFT, Leader.NONE);
    }

    public TabStop(double position, Alignment alignment) {
        this(position, alignment, Leader.NONE);
    }

    public TabStop(double position, Alignment alignment, Leader leader) {
        this.position = Math.max(0.0, position);
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
        this.leader = leader != null ? leader : Leader.NONE;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = Math.max(0.0, position);
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
    }

    public Leader getLeader() {
        return leader;
    }

    public void setLeader(Leader leader) {
        this.leader = leader != null ? leader : Leader.NONE;
    }
}
