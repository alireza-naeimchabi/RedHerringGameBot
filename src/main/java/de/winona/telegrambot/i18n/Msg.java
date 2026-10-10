package de.winona.telegrambot.i18n;

/**
 * A message that is not translated yet: only its key and its values.
 * Game code returns this when it does not know who will read the message.
 */
public record Msg(String key, Object... args) {

    public static Msg of(String key, Object... args) {
        return new Msg(key, args);
    }
}
