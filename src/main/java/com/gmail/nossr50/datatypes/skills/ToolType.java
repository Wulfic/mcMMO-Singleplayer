package com.gmail.nossr50.datatypes.skills;

import com.gmail.nossr50.util.ItemUtils;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public enum ToolType {
    AXE("Axes.Ability.Lower", "Axes.Ability.Ready"),
    FISTS("Unarmed.Ability.Lower", "Unarmed.Ability.Ready"),
    HOE("Herbalism.Ability.Lower", "Herbalism.Ability.Ready"),
    PICKAXE("Mining.Ability.Lower", "Mining.Ability.Ready"),
    SHOVEL("Excavation.Ability.Lower", "Excavation.Ability.Ready"),
    SWORD("Swords.Ability.Lower", "Swords.Ability.Ready"),
    CROSSBOW("Crossbows.Ability.Lower", "Crossbows.Ability.Ready"),
    BOW("Archery.Ability.Lower", "Archery.Ability.Ready"),
    TRIDENTS("Tridents.Ability.Lower", "Tridents.Ability.Ready"),
    MACES("Maces.Ability.Lower", "Maces.Ability.Ready");

    private final String lowerTool;
    private final String raiseTool;

    ToolType(String lowerTool, String raiseTool) {
        this.lowerTool = lowerTool;
        this.raiseTool = raiseTool;
    }

    public String getLowerTool() {
        return lowerTool;
    }

    public String getRaiseTool() {
        return raiseTool;
    }

    /**
     * Whether the given held stack is the tool this super-ability tool-type prep uses — step 1 of the
     * 2-step super-ability activation (raise the right tool, then interact). Delegates to the
     * MC-typed {@link ItemUtils} classifiers over the tested {@link com.gmail.nossr50.util.MaterialMapStore}.
     *
     * <p>Faithful to upstream mcMMO: {@link #FISTS} is a bare empty hand ({@link ItemStack#isEmpty()},
     * the {@code Material.AIR} check), and {@link #BOW} has no tool-raise (upstream's switch had no BOW
     * case → default {@code false}); Archery has no super ability to prime this way.
     */
    public boolean inHand(@NotNull ItemStack itemStack) {
        return switch (this) {
            case AXE -> ItemUtils.isAxe(itemStack);
            case FISTS -> itemStack.isEmpty();
            case HOE -> ItemUtils.isHoe(itemStack);
            case PICKAXE -> ItemUtils.isPickaxe(itemStack);
            case SHOVEL -> ItemUtils.isShovel(itemStack);
            case SWORD -> ItemUtils.isSword(itemStack);
            case CROSSBOW -> ItemUtils.isCrossbow(itemStack);
            case TRIDENTS -> ItemUtils.isTrident(itemStack);
            case MACES -> ItemUtils.isMace(itemStack);
            case BOW -> false;
        };
    }
}
