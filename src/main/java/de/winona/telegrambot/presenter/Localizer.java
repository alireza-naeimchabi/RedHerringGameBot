package de.winona.telegrambot.presenter;

import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.service.LanguageService;
import de.winona.telegrambot.view.Screen;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides in which language something is shown.
 * A private message uses the language of its reader. A group message is one message for
 * everybody, so it is written in every language the players of that room have chosen.
 */
public class Localizer {

    private final LanguageService languages;

    public Localizer(LanguageService languages) {
        this.languages = languages;
    }

    public void setLanguage(long userId, Language language) {
        languages.setLanguage(userId, language);
    }

    public Language languageOf(long userId) {
        return languages.languageOf(userId);
    }

    public Texts textsFor(long userId) {
        return Texts.of(languages.languageOf(userId));
    }

    /**
     * A screen for one reader.
     */
    public Screen forUser(long userId, Function<Texts, Screen> view) {
        return view.apply(textsFor(userId));
    }

    /**
     * A screen for a group: once per language its players use, joined into one message.
     */
    public Screen forPlayers(Collection<Player> players, Function<Texts, Screen> view) {
        List<Screen> versions = languagesOf(players).stream()
                .map(language -> view.apply(Texts.of(language)))
                .toList();
        return Screen.merge(versions);
    }

    /**
     * The languages of the real players, in a fixed order. Test players have no language.
     */
    private Set<Language> languagesOf(Collection<Player> players) {
        Set<Language> used = EnumSet.noneOf(Language.class);
        players.stream()
                .filter(player -> player.userId() > 0)
                .forEach(player -> used.add(languages.languageOf(player.userId())));
        if (used.isEmpty()) {
            used.add(Language.DEFAULT);
        }
        return used;
    }
}
