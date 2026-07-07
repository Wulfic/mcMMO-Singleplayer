package com.gmail.nossr50.datatypes.treasure;

import com.gmail.nossr50.util.random.Probability;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Base type for a rollable treasure drop.
 *
 * <p><b>Port note:</b> the legacy field was a live {@code org.bukkit.inventory.ItemStack drop};
 * it now holds an MC-free {@link ItemSpec} blueprint that a post-bootstrap builder turns into a real
 * item at spawn time (see {@link ItemSpec} for why). Everything else — XP, drop chance, the
 * {@link Probability} (already ported, MC-free), and level requirement — is unchanged.
 */
public abstract class Treasure {
    private int xp;
    private double dropChance;
    private @NotNull Probability dropProbability;
    private int dropLevel;
    private @NotNull ItemSpec drop;

    public Treasure(@NotNull ItemSpec drop, int xp, double dropChance, int dropLevel) {
        this.drop = drop;
        this.xp = xp;
        this.dropChance = dropChance;
        this.dropProbability = Probability.ofPercent(dropChance);
        this.dropLevel = dropLevel;
    }

    public @NotNull Probability getDropProbability() {
        return dropProbability;
    }

    public @NotNull ItemSpec getDrop() {
        return drop;
    }

    public void setDrop(@NotNull ItemSpec drop) {
        this.drop = drop;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public double getDropChance() {
        return dropChance;
    }

    public void setDropChance(double dropChance) {
        this.dropChance = dropChance;
        this.dropProbability = Probability.ofPercent(dropChance);
    }

    public int getDropLevel() {
        return dropLevel;
    }

    public void setDropLevel(int dropLevel) {
        this.dropLevel = dropLevel;
    }

    @Override
    public String toString() {
        return "Treasure{" +
                "xp=" + xp +
                ", dropChance=" + dropChance +
                ", dropProbability=" + dropProbability +
                ", dropLevel=" + dropLevel +
                ", drop=" + drop +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Treasure treasure = (Treasure) o;
        return xp == treasure.xp && Double.compare(treasure.dropChance, dropChance) == 0
                && dropLevel == treasure.dropLevel && Objects.equals(dropProbability,
                treasure.dropProbability) && Objects.equals(drop, treasure.drop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xp, dropChance, dropProbability, dropLevel, drop);
    }
}
