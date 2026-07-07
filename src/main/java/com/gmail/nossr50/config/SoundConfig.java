package com.gmail.nossr50.config;

import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.sounds.SoundType;
import java.nio.file.Path;

/**
 * {@code sounds.yml} — per-{@link SoundType} volume/pitch/enable + custom sound ids, ported onto
 * {@link ConfigLoader}. MC-free: sound playback (which needs the Fabric sound registry) lives in
 * {@code util/sounds/SoundManager}, deferred to Phase 11; this class only reads the tuning values.
 */
public class SoundConfig extends ConfigLoader {

    public SoundConfig(Path dataFolder) {
        super("sounds.yml", dataFolder);
        loadKeys();
        validateKeys();
    }

    @Override
    protected void loadKeys() {
        // Values are read lazily through the getters; nothing to pre-compute.
    }

    protected boolean validateKeys() {
        for (SoundType soundType : SoundType.values()) {
            if (config.getDouble("Sounds." + soundType + ".Volume") < 0) {
                LogUtils.debug("[mcMMO] Sound volume cannot be below 0 for " + soundType);
                return false;
            }

            //Sounds with custom pitching don't use pitch values
            if (!soundType.usesCustomPitch()) {
                if (config.getDouble("Sounds." + soundType + ".Pitch") < 0) {
                    LogUtils.debug("[mcMMO] Sound pitch cannot be below 0 for " + soundType);
                    return false;
                }
            }
        }
        return true;
    }

    public float getMasterVolume() {
        return (float) config.getDouble("Sounds.MasterVolume", 1.0);
    }

    public float getVolume(SoundType soundType) {
        String key = "Sounds." + soundType + ".Volume";
        return (float) config.getDouble(key, 1.0);
    }

    public float getPitch(SoundType soundType) {
        String key = "Sounds." + soundType + ".Pitch";
        return (float) config.getDouble(key, 1.0);
    }

    public String getSound(SoundType soundType) {
        final String key = "Sounds." + soundType + ".CustomSoundId";
        return config.getString(key);
    }

    public boolean getIsEnabled(SoundType soundType) {
        String key = "Sounds." + soundType + ".Enable";
        return config.getBoolean(key, true);
    }
}
