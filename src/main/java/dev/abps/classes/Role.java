package dev.abps.classes;

/**
 * The two kinds of attributes. PvP attributes are built for fighting players. Gatherer attributes are built for
 * getting items and progressing: farming, mining, chopping, fishing and exploring. Their ultimates charge from
 * gathering and fighting mobs instead of from hitting players, and they deal less damage to players.
 */
public enum Role {
    PVP("pvp", "PvP", "Built for fighting other players.", 0xFF5252),
    GATHERER("gatherer", "Gatherer", "Built for getting items and progressing. Deals less damage to players.", 0x69F0AE);

    /** How much damage gatherers deal to players, compared to normal. */
    public static final double GATHERER_PVP_DAMAGE = 0.75;

    public final String id, label, blurb;
    public final int color;

    Role(String id, String label, String blurb, int color) {
        this.id = id;
        this.label = label;
        this.blurb = blurb;
        this.color = color;
    }

    public static Role of(String id) {
        for (Role r : values()) if (r.id.equalsIgnoreCase(id) || r.label.equalsIgnoreCase(id)) return r;
        return null;
    }

    public Role other() {
        return this == PVP ? GATHERER : PVP;
    }
}
