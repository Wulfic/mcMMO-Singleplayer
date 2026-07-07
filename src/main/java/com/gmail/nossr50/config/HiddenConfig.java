package com.gmail.nossr50.config;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code hidden.yml} — undocumented tuning knobs shipped only inside the jar (no user-editable disk
 * copy). Read-only bundled data, so this keeps the legacy lazy-singleton rather than the injected
 * dataFolder pattern of {@link ConfigLoader}.
 *
 * <p>Ported off Bukkit's {@code YamlConfiguration}/{@code getResourceAsReader} onto the port's own
 * {@link YamlConfiguration} loaded from the classpath resource.
 */
public class HiddenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/Config");

    private static HiddenConfig instance;
    private final String fileName;
    private int conversionRate = 1;
    private boolean useEnchantmentBuffs = true;

    public HiddenConfig(String fileName) {
        this.fileName = fileName;
        load();
    }

    public static HiddenConfig getInstance() {
        if (instance == null) {
            instance = new HiddenConfig("hidden.yml");
        }

        return instance;
    }

    public void load() {
        final InputStream in = HiddenConfig.class.getResourceAsStream("/" + fileName);
        if (in == null) {
            LOGGER.error("Missing bundled config resource: {}", fileName);
            return;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(in);
            conversionRate = config.getInt("Options.ConversionRate", 1);
            useEnchantmentBuffs = config.getBoolean("Options.EnchantmentBuffs", true);
        } catch (IOException e) {
            LOGGER.error("Failed to read bundled config: {}", fileName, e);
        }
    }

    public int getConversionRate() {
        return conversionRate;
    }

    public boolean useEnchantmentBuffs() {
        return useEnchantmentBuffs;
    }
}
