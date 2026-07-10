package com.gmail.nossr50.util.text;

/**
 * Where a notification is rendered for the player. The legacy enum carried an Adventure
 * {@code BiConsumer<Audience, Component>} sender; in the singleplayer port there is no Adventure
 * {@code Audience}, so this collapses to a plain marker — {@code NotificationManager} does the
 * routing directly against {@code PlatformPlayer} ({@code sendActionBar} vs {@code sendMessage}).
 */
public enum McMMOMessageType {
    /** Rendered on the action bar / overlay above the hotbar. */
    ACTION_BAR,
    /** Rendered in the standard system chat feed. */
    SYSTEM
}
