package com.gmail.nossr50.config;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.text.StringUtils;
import java.nio.file.Path;

/**
 * {@code coreskills.yml} — per-skill / per-subskill enable toggles, ported onto {@link ConfigLoader}.
 *
 * <p>The dataFolder is injected (matching {@link GeneralConfig}) so the load/merge flow is
 * unit-testable against a temp directory; the legacy static {@code getInstance()} singleton is
 * dropped in favour of the {@code McMMOMod} service-locator surface.
 */
public class CoreSkillsConfig extends ConfigLoader {

    public CoreSkillsConfig(Path dataFolder) {
        super("coreskills.yml", dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        // Values are read lazily through the getters; nothing to pre-compute.
    }

    // PORT Phase 10: isSkillEnabled(AbstractSubSkill) — dropped. Needs AbstractSubSkill, which
    // drags in McMMOPlayer + the subskill interfaces; re-add when the subskill types port with
    // their skills. The faithful body read
    //   <PrimarySkill>.<ConfigKeyName>.Enabled  (default true)
    // i.e. the per-subskill sibling of isPrimarySkillEnabled below.

    /**
     * Whether this primary skill is enabled. Defaults true.
     *
     * @param primarySkillType target primary skill
     * @return true if enabled
     */
    public boolean isPrimarySkillEnabled(PrimarySkillType primarySkillType) {
        return config.getBoolean(
                StringUtils.getCapitalized(primarySkillType.toString()) + ".Enabled", true);
    }
}
