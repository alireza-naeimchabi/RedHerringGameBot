package de.winona.telegrambot.service;

import de.winona.telegrambot.i18n.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Remembers which language each player chose and stores it in a small properties file,
 * so the choice survives a restart. Players without a choice get the default language.
 */
public class LanguageService {

    public static final Path DEFAULT_FILE = Path.of("data", "player-languages.properties");

    private static final Logger log = LoggerFactory.getLogger(LanguageService.class);

    private final Map<Long, Language> languageByUser = new HashMap<>();
    private final Path file;

    public LanguageService() {
        this(DEFAULT_FILE);
    }

    /**
     * @param file where the choices are stored, or null to keep them in memory only
     */
    public LanguageService(Path file) {
        this.file = file;
        load();
    }

    public static LanguageService inMemory() {
        return new LanguageService(null);
    }

    public Language languageOf(long userId) {
        return languageByUser.getOrDefault(userId, Language.DEFAULT);
    }

    public void setLanguage(long userId, Language language) {
        languageByUser.put(userId, language);
        save();
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            log.warn("Could not read language choices from {}. Everyone starts in the default language.", file, e);
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            try {
                languageByUser.put(Long.parseLong(key.trim()), Language.fromCode(properties.getProperty(key).trim()));
            } catch (NumberFormatException e) {
                log.warn("Ignoring unreadable language entry '{}'.", key);
            }
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        Properties properties = new Properties();
        languageByUser.forEach((userId, language) -> properties.setProperty(String.valueOf(userId), language.code()));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Language of each player - written by the bot");
            }
        } catch (IOException e) {
            log.warn("Could not save language choices to {}. They stay in memory until the bot stops.", file, e);
        }
    }
}
