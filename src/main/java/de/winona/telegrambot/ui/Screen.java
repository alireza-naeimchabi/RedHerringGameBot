package de.winona.telegrambot.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * One view of the interface: optional artwork, HTML text and optional buttons.
 * A screen only describes what to show. Sending it is the job of the Messenger.
 */
public record Screen(Banner banner, String html, InlineKeyboardMarkup keyboard) {

    /** Telegram's limit for the text under a photo. */
    public static final int MAX_CAPTION_LENGTH = 1024;
    /** Telegram's limit for a text message. */
    public static final int MAX_TEXT_LENGTH = 4096;

    public static Screen text(String html) {
        return new Screen(null, html, null);
    }

    public static Screen text(String html, InlineKeyboardMarkup keyboard) {
        return new Screen(null, html, keyboard);
    }

    public static Screen withBanner(Banner banner, String html, InlineKeyboardMarkup keyboard) {
        return new Screen(banner, html, keyboard);
    }

    public boolean hasBanner() {
        return banner != null;
    }

    /** Number of characters Telegram counts: the text without HTML tags, entities counted once. */
    public int visibleLength() {
        return html.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .length();
    }

    /** True if the text is short enough to be shown as the caption of the banner. */
    public boolean fitsCaption() {
        return visibleLength() <= MAX_CAPTION_LENGTH;
    }
}
