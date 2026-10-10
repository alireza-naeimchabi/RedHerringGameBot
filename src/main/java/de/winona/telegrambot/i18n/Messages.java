package de.winona.telegrambot.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * All texts of the bot, loaded once from src/main/resources/i18n/messages_&lt;code&gt;.properties.
 * To add a language: add it to {@link Language} and add one more properties file.
 */
public final class Messages {

    private static final Messages INSTANCE = new Messages();

    private final Map<Language, Properties> bundles = new EnumMap<>(Language.class);

    private Messages() {
        for (Language language : Language.values()) {
            bundles.put(language, load(language));
        }
    }

    public static Messages get() {
        return INSTANCE;
    }

    private static Properties load(Language language) {
        String path = "/i18n/messages_" + language.code() + ".properties";
        Properties properties = new Properties();
        try (InputStream stream = Messages.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing translation file " + path);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read translation file " + path, e);
        }
        return properties;
    }

    /**
     * The raw text for a key. A text missing in one language falls back to the default language,
     * and a key missing everywhere is returned itself, so a mistake is visible but never crashes.
     */
    public String text(Language language, String key) {
        String text = bundles.get(language).getProperty(key);
        if (text == null) {
            text = bundles.get(Language.DEFAULT).getProperty(key);
        }
        return text == null ? key : text;
    }

    public boolean has(Language language, String key) {
        return bundles.get(language).containsKey(key);
    }

    public Set<String> keys(Language language) {
        return bundles.get(language).stringPropertyNames();
    }
}
