package de.winona.telegrambot.i18n;

import de.winona.telegrambot.ui.Banner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the translation files complete and consistent with each other.
 */
class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d}");
    private static final Pattern TAG = Pattern.compile("</?[a-z]+>");
    private static final List<String> PLAIN_TEXT_KEYS = List.of("btn.", "nav.", "err.", "toast.", "cmd.");
    /**
     * Texts that are rightly the same in every language.
     */
    private static final Set<String> NOT_TRANSLATED = Set.of("home.rank_line");

    private final Messages messages = Messages.get();

    @Test
    void everyLanguageHasExactlyTheSameKeys() {
        Set<String> expected = new TreeSet<>(messages.keys(Language.DEFAULT));
        assertTrue(expected.size() > 400, "the default language is loaded");
        for (Language language : Language.values()) {
            assertEquals(expected, new TreeSet<>(messages.keys(language)), "keys of " + language);
        }
    }

    @Test
    void everyTranslationKeepsThePlaceholdersAndTheFormatting() {
        List<String> problems = new ArrayList<>();
        for (String key : messages.keys(Language.DEFAULT)) {
            String original = messages.text(Language.DEFAULT, key);
            for (Language language : Language.values()) {
                String translated = messages.text(language, key);
                if (!found(PLACEHOLDER, original).equals(found(PLACEHOLDER, translated))) {
                    problems.add(language + " " + key + ": different placeholders");
                }
                if (!found(TAG, original).equals(found(TAG, translated))) {
                    problems.add(language + " " + key + ": different HTML tags");
                }
                if (translated.isBlank()) {
                    problems.add(language + " " + key + ": empty");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void textsForButtonsAndPopUpsContainNoHtml() {
        for (Language language : Language.values()) {
            for (String key : messages.keys(language)) {
                boolean plain = PLAIN_TEXT_KEYS.stream().anyMatch(key::startsWith);
                if (plain) {
                    assertTrue(found(TAG, messages.text(language, key)).isEmpty(), language + " " + key);
                }
            }
        }
    }

    @Test
    void persianIsReallyTranslated() {
        List<String> untranslated = new ArrayList<>();
        for (String key : messages.keys(Language.DEFAULT)) {
            boolean same = messages.text(Language.DEFAULT, key).equals(messages.text(Language.PERSIAN, key));
            if (same && !NOT_TRANSLATED.contains(key)) {
                untranslated.add(key);
            }
        }
        assertEquals(List.of(), untranslated);
    }

    @Test
    void everyBannerExistsInEveryLanguage() {
        for (Language language : Language.values()) {
            for (Banner banner : Banner.values()) {
                String path = banner.resourcePath(language);
                assertNotNull(getClass().getResource(path), path);
            }
        }
        assertEquals("/ui/banner-home.jpg", Banner.HOME.resourcePath(Language.ENGLISH));
        assertEquals("/ui/fa/banner-home.jpg", Banner.HOME.resourcePath(Language.PERSIAN));
    }

    private List<String> found(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        matches.sort(null);
        return matches;
    }
}
