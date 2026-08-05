package com.gmail.nossr50.config;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.text.StringUtils;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * {@code coreskills.yml} — the per-skill master switches (GitHub #10), ported onto
 * {@link ConfigLoader}.
 *
 * <p>The dataFolder is injected (matching {@link GeneralConfig}) so the load/merge flow is
 * unit-testable against a temp directory; the legacy static {@code getInstance()} singleton is
 * dropped in favour of the {@code McMMOMod} service-locator surface.
 *
 * <p>Read the file itself for what "disabled" is defined to mean; the enforcement lives in
 * {@link com.gmail.nossr50.util.skills.SkillGating}, which is the only thing that should be
 * calling {@link #isPrimarySkillEnabled}.
 */
public class CoreSkillsConfig extends ConfigLoader {

    /**
     * The disabled skills, resolved once at load.
     *
     * <p>⚠️ Cached deliberately. {@link #isPrimarySkillEnabled} sits on the XP path and behind every
     * sub-skill proc gate — several calls per player per tick — and {@code config.getBoolean} walks
     * the backing YAML map and re-parses the dotted path on every call. The same per-tick config-read
     * trap that Alchemy's Catalysis hit.
     *
     * <p>Stores the <em>disabled</em> set rather than the enabled one so the overwhelmingly common
     * case (nothing disabled) is an empty set, and so a skill constant added later without a
     * {@code coreskills.yml} entry defaults to enabled by construction rather than by a default
     * argument someone can forget.
     */
    private Set<PrimarySkillType> disabledSkills = EnumSet.noneOf(PrimarySkillType.class);

    public CoreSkillsConfig(Path dataFolder) {
        super("coreskills.yml", dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        final Set<PrimarySkillType> disabled = EnumSet.noneOf(PrimarySkillType.class);
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (!config.getBoolean(enabledPath(skill), true)) {
                disabled.add(skill);
                LOGGER.info("coreskills.yml: {} is DISABLED — it will earn no XP, proc no sub-skills,"
                        + " ready no super ability, show no XP bar, appear in no /mcstats listing and"
                        + " grant no milestone plaques. Your stored level is untouched.", skill);
            }
        }
        this.disabledSkills = disabled;
    }

    // PORT Phase 10: isSkillEnabled(AbstractSubSkill) — dropped. Needs AbstractSubSkill, which
    // drags in McMMOPlayer + the subskill interfaces; re-add when the subskill types port with
    // their skills. The faithful body read
    //   <PrimarySkill>.<ConfigKeyName>.Enabled  (default true)
    // i.e. the per-subskill sibling of isPrimarySkillEnabled below. GitHub #10 deliberately did NOT
    // revive it: the issue asks for a whole-skill switch, and a sub-skill switch is a second, finer
    // gating surface that every one of SkillGating's chokepoints would have to consult separately.

    /**
     * Whether this primary skill is enabled. Defaults true — an absent key, an unreadable file and a
     * skill constant with no entry at all all mean "on", because failing closed here would silently
     * switch the mod off.
     *
     * @param primarySkillType target primary skill
     * @return true if enabled
     */
    public boolean isPrimarySkillEnabled(@NotNull PrimarySkillType primarySkillType) {
        return !disabledSkills.contains(primarySkillType);
    }

    /** The dotted {@code coreskills.yml} path holding {@code skill}'s master switch. */
    public static @NotNull String enabledPath(@NotNull PrimarySkillType skill) {
        return StringUtils.getCapitalized(skill.toString()) + ".Enabled";
    }
}
