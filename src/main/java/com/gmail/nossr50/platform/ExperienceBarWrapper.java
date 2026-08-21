package com.gmail.nossr50.platform;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.experience.ExperienceBar;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.text.TextUtils;
import com.gmail.nossr50.util.player.PlayerLevelUtils;
import com.gmail.nossr50.util.text.StringUtils;
import java.util.Locale;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * The concrete, {@code ServerBossBar}-backed {@link ExperienceBar}: mcMMO's on-screen XP progress
 * bar for one skill of one player.
 *
 * <p><b>Port note.</b> Legacy mcMMO built this on Bukkit's {@code BossBar}, whose player set was
 * keyed on the session-stable Bukkit {@code Player}. Fabric's integrated server drives boss bars to
 * the client through the same {@code ServerBossBar} the vanilla {@code /bossbar} command uses, but
 * the {@link ServerPlayerEntity} is <b>recreated on respawn / End-exit</b> (see
 * {@link com.gmail.nossr50.platform.PlatformPlayer#rebind}). So this wrapper never captures the
 * entity at construction; {@link #show()} re-subscribes the live handle from
 * {@link McMMOPlayer#getPlayer()} whenever the bar is shown, which keeps it rendering across a death.
 */
public final class ExperienceBarWrapper implements ExperienceBar {

    private final PrimarySkillType primarySkillType;
    private final McMMOPlayer mmoPlayer;
    private final ServerBossEvent bossBar;

    /** Capitalised skill name used for the {@code XPBar.<Skill>} locale key (e.g. "Mining"). */
    private final String niceSkillName;

    /** The skill level the title currently reflects, so it is only re-rendered on a level change. */
    private int lastLevelRendered;

    public ExperienceBarWrapper(PrimarySkillType primarySkillType, McMMOPlayer mmoPlayer) {
        this.primarySkillType = primarySkillType;
        this.mmoPlayer = mmoPlayer;
        this.niceSkillName = StringUtils.getCapitalized(primarySkillType.toString());
        this.lastLevelRendered = mmoPlayer.getSkillLevel(primarySkillType);

        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        this.bossBar = new ServerBossEvent(
                renderTitle(),
                mapColor(config.getExperienceBarColorName(primarySkillType)),
                mapStyle(config.getExperienceBarStyleName(primarySkillType)));
        // Built hidden and with no subscriber; show() adds the live player entity on demand.
        this.bossBar.setVisible(false);
    }

    @Override
    public void setProgress(double progress) {
        this.bossBar.setPercent((float) clamp01(progress));

        // Legacy recoloured the bar on every progress update so the early-game boost is visible
        // rather than merely configured. Unconditional because ServerBossBar#setColor is a no-op
        // when the colour is unchanged (bytecode-verified: it compares before sending a packet), so
        // the steady state costs nothing.
        this.bossBar.setColor(resolveColor());

        // The bar title carries the level; refresh it only when the level actually changed (or when
        // the always-update knob is on), matching legacy's title-update throttle.
        final int level = mmoPlayer.getSkillLevel(primarySkillType);
        if (level != lastLevelRendered
                || McMMOMod.getExperienceConfig().getDoExperienceBarsAlwaysUpdateTitle()) {
            this.bossBar.setName(renderTitle());
            lastLevelRendered = level;
        }
    }

    @Override
    public void show() {
        final ServerPlayer current = mmoPlayer.getPlayer().unwrap();
        // Re-point at the live handle: after a respawn/End-exit the entity captured previously is
        // stale, and the bar must follow the current one (see the class javadoc). In the steady
        // state the handle is already subscribed and this is a no-op.
        if (!bossBar.getPlayers().contains(current)) {
            bossBar.clearPlayers();
            bossBar.addPlayer(current);
        }
        bossBar.setVisible(true);
    }

    @Override
    public void hide() {
        bossBar.setVisible(false);
    }

    // --- title ---------------------------------------------------------------

    private Component renderTitle() {
        return TextUtils.toText(renderTitleText());
    }

    /**
     * The bar's title as a legacy section-coded ({@code §}) string — mcMMO's own message
     * representation. Split out from {@link #renderTitle} so the title-selection logic (early-game
     * boost outranking the extra-details template, the nested complex template) carries no Minecraft
     * type; only the one-line wrapper above does.
     */
    private String renderTitleText() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        final int level = mmoPlayer.getSkillLevel(primarySkillType);

        // Legacy's first branch, and it outranks the extra-details template: while the boost is
        // running the bar says so instead of showing numbers. The XPBar.Template.EarlyGameBoost
        // string has shipped in the locale file since the port began and nothing rendered it.
        if (isEarlyGameBoosted()) {
            return LocaleLoader.getString("XPBar.Template.EarlyGameBoost");
        }

        if (config.getAddExtraDetails()) {
            // {0} = the plain skill+level string, {1}=current XP, {2}=XP to level, {3}=power level,
            // {4}=percent. Legacy nested one locale lookup inside the other; getString returns the
            // raw §-coded string so it drops straight into the {0} slot of the complex template.
            final String skillTitle = LocaleLoader.getString("XPBar." + niceSkillName, level);
            return LocaleLoader.getString("XPBar.Complex.Template",
                    skillTitle,
                    mmoPlayer.getProfile().getSkillXpLevel(primarySkillType),
                    mmoPlayer.getProfile().getXpToLevel(primarySkillType),
                    mmoPlayer.getPowerLevel(),
                    percentOfLevel());
        }

        return LocaleLoader.getString("XPBar." + niceSkillName, level);
    }

    private int percentOfLevel() {
        return (int) (mmoPlayer.getProgressInCurrentSkillLevel(primarySkillType) * 100);
    }

    /**
     * Whether this skill is currently earning the early-game boost — both the
     * {@code EarlyGameBoost.Enabled} switch and the level cutoff, the same pair
     * {@code McMMOPlayer#applySelfListenerModifiers} asks before topping the gain up. The bar must
     * not advertise a boost that the XP pipeline is not paying.
     */
    private boolean isEarlyGameBoosted() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        return config != null && config.isEarlyGameBoostEnabled()
                && PlayerLevelUtils.qualifiesForEarlyGameBoost(
                        mmoPlayer.getSkillLevel(primarySkillType));
    }

    private BossEvent.BossBarColor resolveColor() {
        return resolveColor(isEarlyGameBoosted(),
                McMMOMod.getExperienceConfig().getExperienceBarColorName(primarySkillType));
    }

    /**
     * The bar's colour: legacy overrode the configured per-skill colour with {@code YELLOW} while the
     * early-game boost applied, which is the only visual cue the mechanic has.
     *
     * <p>Static and package-private so both branches are testable without a live boss bar — building
     * a real wrapper needs a bound config, a tracked player and a server.
     */
    static BossEvent.BossBarColor resolveColor(boolean earlyGameBoosted, String configuredColorName) {
        if (earlyGameBoosted) {
            return BossEvent.BossBarColor.YELLOW;
        }
        return mapColor(configuredColorName);
    }

    // --- Bukkit-name -> vanilla-enum mapping ---------------------------------

    /**
     * Map a legacy {@code BarColor} name to the vanilla {@link BossBar.Color}. Bukkit's colour names
     * (PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE) are identical to vanilla's, so this is a direct
     * {@code valueOf} with a PINK fallback for an unrecognised config value.
     */
    static BossEvent.BossBarColor mapColor(String barColorName) {
        try {
            return BossEvent.BossBarColor.valueOf(barColorName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            McMMOMod.LOGGER.warn("Unknown XP bar color '{}', defaulting to PINK", barColorName);
            return BossEvent.BossBarColor.PINK;
        }
    }

    /**
     * Map a legacy {@code BarStyle} name to the vanilla {@link BossBar.Style}: {@code SOLID} becomes
     * {@code PROGRESS} and {@code SEGMENTED_n} becomes {@code NOTCHED_n}. Falls back to
     * {@code NOTCHED_6} (the bundled default) for an unrecognised value.
     */
    static BossEvent.BossBarOverlay mapStyle(String barStyleName) {
        return switch (barStyleName.trim().toUpperCase(Locale.ROOT)) {
            case "SOLID", "PROGRESS" -> BossEvent.BossBarOverlay.PROGRESS;
            case "SEGMENTED_6", "NOTCHED_6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "SEGMENTED_10", "NOTCHED_10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "SEGMENTED_12", "NOTCHED_12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "SEGMENTED_20", "NOTCHED_20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> {
                McMMOMod.LOGGER.warn("Unknown XP bar style '{}', defaulting to NOTCHED_6",
                        barStyleName);
                yield BossEvent.BossBarOverlay.NOTCHED_6;
            }
        };
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }
}
