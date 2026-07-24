package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.fabric.McMMOMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

/**
 * ModMenu integration for mcMMO — provides the config-cog screen shown next to the mod in ModMenu's
 * list. Registered via the {@code modmenu} entrypoint in {@code fabric.mod.json}, so this class is
 * loaded <em>only</em> when ModMenu is installed; the mod runs untouched otherwise.
 *
 * <p>The editable options UI needs the Cloth Config API. When it is present the cog opens the full
 * {@link ClothConfigScreenBuilder} editor; when it is absent the reference to any Cloth class is
 * skipped (so nothing fails to link) and the cog opens the {@link McMMOInfoScreen} fallback, which
 * points the player at the on-disk config files instead.
 */
public final class McMMOModMenuIntegration implements ModMenuApi {

    private static final String CLOTH_CONFIG_MOD_ID = "cloth-config";

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> createConfigScreen(parent);
    }

    private static Screen createConfigScreen(Screen parent) {
        final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(McMMOMod.MOD_ID);
        if (FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_MOD_ID)) {
            return ClothConfigScreenBuilder.build(parent, configDir);
        }
        return new McMMOInfoScreen(parent, configDir);
    }
}
