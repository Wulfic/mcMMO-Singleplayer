package com.gmail.nossr50.datatypes.skills;

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

    // PORT Phase 10: inHand(ItemStack) — dropped here because it needs the Bukkit
    // ItemStack + ItemUtils.isAxe/isPickaxe/... type checks. Re-add against the
    // platform/ item adapter (or Minecraft ItemStack) when the skills that call it port.
}
