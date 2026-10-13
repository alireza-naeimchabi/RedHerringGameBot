package de.winona.telegrambot.bot;

import de.winona.telegrambot.bot.FakeTelegram.Shown;
import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.service.LanguageService;
import de.winona.telegrambot.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * English and Persian side by side: choosing a language, keeping it, and playing together
 * in one room while every player reads their own language.
 */
class BilingualBotTest {

    private static final long GROUP = -1001234567890L;
    private static final String GROUP_TITLE = "Friday <Night> & Friends";
    private static final String DIVIDER = "┄┄┄┄┄┄┄┄┄┄";
    private static final char RIGHT_TO_LEFT_MARK = '‏';
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern PERSIAN_LETTER = Pattern.compile("[؀-ۿ]");
    private static final Pattern WESTERN_DIGIT = Pattern.compile("[0-9]");

    private static final Texts EN = Texts.of(Language.ENGLISH);
    private static final Texts FA = Texts.of(Language.PERSIAN);

    private final User alireza = FakeTelegram.user(11, "Alireza");
    private final User sara = FakeTelegram.user(12, "Sara");
    private final User ben = FakeTelegram.user(13, "Ben");
    private final User clara = FakeTelegram.user(14, "Clara");
    private final User dana = FakeTelegram.user(15, "Dana");
    private final List<User> table = List.of(alireza, sara, ben, clara);

    @TempDir
    Path tempDir;

    private FakeTelegram telegram;
    private StatsService stats;
    private LanguageService languages;
    private RedHerringBot bot;

    @BeforeEach
    void setUp() {
        telegram = new FakeTelegram();
        stats = new StatsService(tempDir.resolve("stats.properties"));
        languages = new LanguageService(tempDir.resolve("languages.properties"));
        bot = new RedHerringBot(telegram.client(), stats, languages);
    }

    @AfterEach
    void everyMessageRespectsTelegramsLimits() {
        assertEquals(List.of(), telegram.problems());
    }

    // ---------------------------------------------------------------- Choosing a language

    @Test
    void aNewPlayerGetsEnglishAndALanguageButtonReadableInBothLanguages() {
        say(alireza, "/start");

        Shown home = telegram.last(alireza.getId());
        assertEquals("banner-home.jpg", home.banner);
        assertTrue(home.html.contains(EN.get("brand.headline")));
        assertFalse(PERSIAN_LETTER.matcher(home.html).find(), "no Persian for a player who never chose it");
        InlineKeyboardButton language = buttonFor(home, "m:lang");
        assertTrue(language.getText().contains("Language"));
        assertTrue(language.getText().contains("زبان"));
    }

    @Test
    void theSelectorOffersEnglishAndPersianAndMarksTheCurrentOne() {
        say(alireza, "/start");
        Shown screen = telegram.last(alireza.getId());
        press(alireza, screen, "m:lang");

        assertTrue(screen.html.contains(EN.get("lang.title")));
        assertEquals("🇬🇧 English ✓", buttonFor(screen, "m:setlang:en").getText());
        assertEquals("\uD83E\uDD81☀\uFE0F فارسی", buttonFor(screen, "m:setlang:fa").getText());
    }

    @Test
    void choosingPersianTurnsTheDashboardPersianAtOnce() {
        say(alireza, "/start");
        Shown screen = telegram.last(alireza.getId());
        int messages = telegram.chat(alireza.getId()).size();
        press(alireza, screen, "m:lang");
        press(alireza, screen, "m:setlang:fa");

        assertEquals(FA.get("lang.changed"), telegram.lastAnswer().text());
        assertFalse(telegram.lastAnswer().alert());
        assertTrue(screen.html.contains(FA.get("brand.headline")), screen.html);
        assertTrue(screen.banner.contains("fa-banner-home.jpg"), "Persian artwork, got " + screen.banner);
        assertTrue(buttonFor(screen, "m:create").getText().contains(FA.get("btn.create_room")));
        assertEquals(messages, telegram.chat(alireza.getId()).size(), "the same message changes its language");
        assertEquals(Language.PERSIAN, languages.languageOf(alireza.getId()));

        // Every later screen and command answers in Persian too.
        say(alireza, "/stats");
        assertTrue(telegram.last(alireza.getId()).html.contains(FA.get("stats.title")));
        say(alireza, "hello");
        assertFalse(LATIN_WORD.matcher(visible(telegram.last(alireza.getId()))).find());
    }

    @Test
    void theLanguageCanBeSwitchedBackFromTheProfile() {
        languages.setLanguage(alireza.getId(), Language.PERSIAN);
        say(alireza, "/profile");
        Shown screen = telegram.last(alireza.getId());
        assertTrue(screen.html.contains(FA.get("profile.title")));
        assertTrue(screen.html.contains("\uD83E\uDD81☀\uFE0F فارسی"));

        press(alireza, screen, "m:lang");
        assertEquals("\uD83E\uDD81☀\uFE0F فارسی ✓", buttonFor(screen, "m:setlang:fa").getText());
        press(alireza, screen, "m:setlang:en");

        assertEquals(EN.get("lang.changed"), telegram.lastAnswer().text());
        assertTrue(screen.html.contains(EN.get("brand.headline")));
        assertFalse(PERSIAN_LETTER.matcher(screen.html).find());
        assertEquals(Language.ENGLISH, languages.languageOf(alireza.getId()));
    }

    @Test
    void theLanguageCommandOpensTheSelector() {
        say(alireza, "/language");
        assertTrue(telegram.last(alireza.getId()).html.contains(EN.get("lang.title")));
        assertNotNull(buttonFor(telegram.last(alireza.getId()), "m:setlang:fa"));
    }

    @Test
    void anUnknownLanguageIsRefusedAndChangesNothing() {
        say(alireza, "/start");
        Shown screen = telegram.last(alireza.getId());
        press(alireza, screen, "m:setlang:xx");

        assertTrue(telegram.lastAnswer().alert());
        assertEquals(Language.ENGLISH, languages.languageOf(alireza.getId()));
    }

    @Test
    void everyPlayerChoosesIndependently() {
        say(alireza, "/start");
        press(alireza, telegram.last(alireza.getId()), "m:setlang:fa");
        say(sara, "/start");

        assertTrue(telegram.last(alireza.getId()).html.contains(FA.get("brand.headline")));
        assertTrue(telegram.last(sara.getId()).html.contains(EN.get("brand.headline")));
        assertFalse(PERSIAN_LETTER.matcher(telegram.last(sara.getId()).html).find());
    }

    // ---------------------------------------------------------------- Persistence

    @Test
    void theChoiceSurvivesARestartAndOthersKeepEnglish() {
        say(alireza, "/start");
        press(alireza, telegram.last(alireza.getId()), "m:setlang:fa");

        // A new bot with new services reads the same file, like after a restart.
        FakeTelegram afterRestart = new FakeTelegram();
        RedHerringBot restarted = new RedHerringBot(afterRestart.client(),
                new StatsService(tempDir.resolve("stats.properties")),
                new LanguageService(tempDir.resolve("languages.properties")));
        restarted.consume(FakeTelegram.text(alireza, alireza.getId(), null, "/start"));
        restarted.consume(FakeTelegram.text(sara, sara.getId(), null, "/start"));

        assertTrue(afterRestart.last(alireza.getId()).html.contains(FA.get("brand.headline")));
        assertTrue(afterRestart.last(sara.getId()).html.contains(EN.get("brand.headline")),
                "players without a stored choice get English");
        assertEquals(List.of(), afterRestart.problems());
    }

    // ---------------------------------------------------------------- Persian navigation

    @Test
    void everyPersianDashboardScreenIsTranslatedAndRightToLeft() {
        languages.setLanguage(alireza.getId(), Language.PERSIAN);
        say(alireza, "/start");
        int messagesBefore = telegram.chat(alireza.getId()).size();

        Set<String> visited = new HashSet<>();
        Deque<String> open = new ArrayDeque<>();
        telegram.last(alireza.getId()).buttons().forEach(button -> addCallback(open, button));
        while (!open.isEmpty()) {
            String data = open.pop();
            if (data.startsWith("m:setlang") || !visited.add(data)) {
                continue;
            }
            press(alireza, telegram.last(alireza.getId()), data);
            assertFalse(telegram.lastAnswer().alert(), "button " + data + " answered with an alert");
            Shown screen = telegram.last(alireza.getId());
            assertPersianOnly(screen, "after " + data);
            if (screen.photo) {
                assertTrue(screen.banner.contains("fa-banner-"), data + " shows " + screen.banner);
            }
            screen.buttons().forEach(button -> addCallback(open, button));
        }

        assertTrue(visited.containsAll(Set.of("m:home", "m:rooms", "m:create", "m:join", "m:stats", "m:top",
                "m:me", "m:lang", "m:how:5", "m:roles:5")), "visited " + visited);
        assertEquals(messagesBefore, telegram.chat(alireza.getId()).size());
    }

    @Test
    void persianUsesPersianDigitsButKeepsRoomCodesAsTheyAre() {
        languages.setLanguage(alireza.getId(), Language.PERSIAN);
        String code = createRoom();

        Shown card = roomCard();
        assertTrue(card.html.contains("<code>" + code + "</code>"), "codes stay Latin so everyone can type them");
        assertTrue(card.html.contains(FA.get("room.seats", 1, 7)));
        assertTrue(card.html.contains("۱") && card.html.contains("۷"));
        assertFalse(WESTERN_DIGIT.matcher(visible(card).replace(code, "")).find(), visible(card));

        // A code typed with Persian text around it or in lower case still works.
        languages.setLanguage(sara.getId(), Language.PERSIAN);
        say(sara, "/start");
        press(sara, telegram.last(sara.getId()), "m:join");
        say(sara, code.toLowerCase(Locale.ROOT));
        assertTrue(telegram.last(sara.getId()).html.contains(FA.get("room.title", code)));
        assertTrue(roomCard().html.contains(FA.get("room.seats", 2, 7)));

        // So does a code copied with an invisible direction mark and typed with Persian digits.
        languages.setLanguage(ben.getId(), Language.PERSIAN);
        say(ben, "/join " + RIGHT_TO_LEFT_MARK + persianDigits(code));
        assertTrue(telegram.last(ben.getId()).html.contains(FA.get("room.title", code)),
                telegram.last(ben.getId()).html);
        assertTrue(roomCard().html.contains(FA.get("room.seats", 3, 7)));
    }

    @Test
    void persianErrorsAndJoinFailuresAreTranslated() {
        languages.setLanguage(sara.getId(), Language.PERSIAN);
        say(sara, "/join ZZZZ");
        Shown failed = telegram.last(sara.getId());
        assertTrue(failed.html.contains(FA.get("join.unknown", "ZZZZ")), failed.html);
        assertPersianOnly(failed, "join failure");

        say(sara, "/accuse");
        assertTrue(telegram.last(sara.getId()).html.contains(FA.get("private.group_command")));
    }

    // ---------------------------------------------------------------- Rooms and languages

    @Test
    void aPlayerWhoJoinsReadsTheLanguageOfTheRoom() {
        String code = createRoom();
        languages.setLanguage(sara.getId(), Language.PERSIAN);
        press(sara, roomCard(), "r:join:" + code);

        // Sara chose Persian, but the room is English: she now plays in English.
        assertEquals(Language.ENGLISH, languages.languageOf(sara.getId()));
        assertEquals(EN.get("toast.seated", code), telegram.lastAnswer().text());
        Shown card = roomCard();
        assertFalse(card.html.contains(DIVIDER), "the room keeps one language");
        assertFalse(PERSIAN_LETTER.matcher(card.html).find());
        assertTrue(card.html.contains(EN.get("room.seats", 2, 7)));
        assertTrue(telegram.find(sara.getId(), EN.get("seat.title", code)).isPresent(),
                "her private confirmation is in the room language");
    }

    @Test
    void popUpsFollowThePersonWhoPressedNotTheRoom() {
        String code = createRoom();

        // Dana reads Persian and is not seated, so her pop-up stays Persian.
        languages.setLanguage(dana.getId(), Language.PERSIAN);
        press(dana, roomCard(), "r:start:" + code);
        assertTrue(PERSIAN_LETTER.matcher(telegram.lastAnswer().text()).find(), telegram.lastAnswer().text());
        press(alireza, roomCard(), "r:start:" + code);
        assertEquals(EN.plural("err.need_players", 3, 4), telegram.lastAnswer().text());

        // Someone who is not in the room and reads Persian gets a Persian notice in the group.
        group(dana, "/accuse");
        assertTrue(telegram.last(GROUP).html.contains(FA.get("err.no_case")));
        assertFalse(telegram.last(GROUP).html.contains(DIVIDER));
    }

    @Test
    void anAllPersianRoomPlaysACaseWithoutAnyEnglish() {
        table.forEach(user -> languages.setLanguage(user.getId(), Language.PERSIAN));
        String code = openCaseWithFourPlayers();
        Cast cast = playToTheReport();
        User first = cast.investigators.get(0);
        User second = cast.investigators.get(1);

        // A lab test on the player's own turn, with its pop-up for a second try.
        User tester = currentTurn();
        press(tester, cast.report, "LT:" + GROUP);
        Shown lab = telegram.last(GROUP);
        assertTrue(lab.html.contains(FA.get("lab.who.ask")));
        press(tester, lab, lab.buttons().get(0).getCallbackData());
        press(tester, lab, lab.buttons().get(0).getCallbackData());
        assertTrue(lab.html.contains(FA.get("lab.result.title")), lab.html);
        press(tester, cast.report, "LT:" + GROUP);
        assertEquals(FA.get("err.lab.used"), telegram.lastAnswer().text());
        skipAllTurns();

        // A wrong accusation: the right player and means, the wrong clue card.
        press(second, cast.report, "AC:" + GROUP);
        Shown wrong = telegram.last(GROUP);
        press(second, wrong, wrong.button(cast.murderer.getFirstName()).orElseThrow().getCallbackData());
        press(second, wrong, wrong.buttons().get(0).getCallbackData());
        press(second, wrong, wrong.buttons().get(1).getCallbackData());
        assertTrue(wrong.html.contains(FA.get("accuse.wrong")), wrong.html);
        press(second, cast.report, "AC:" + GROUP);
        assertEquals(FA.get("err.accuse.used"), telegram.lastAnswer().text());

        // Round 2: the Forensic Scientist picks a new tile, replaces an answer and answers it.
        Shown tile = telegram.chat(cast.scientist.getId()).stream()
                .filter(shown -> shown.buttons().stream().anyMatch(button ->
                        button.getCallbackData().startsWith("NR:")))
                .findFirst().orElseThrow();
        press(cast.scientist, tile, tile.buttons().get(0).getCallbackData());
        assertTrue(tile.html.contains(FA.get("newtile.sub")), tile.html);
        press(cast.scientist, tile, tile.buttons().get(1).getCallbackData());
        assertTrue(tile.html.contains(FA.get("replace.ask")), tile.html);
        press(cast.scientist, tile, tile.buttons().get(0).getCallbackData());
        say(cast.scientist, FA.option("Wiped away"));
        assertTrue(tile.html.contains(FA.get("newtile.title")), tile.html);
        assertTrue(telegram.find(GROUP, FA.get("report.sub", code, 2, 3)).isPresent());
        assertTrue(telegram.find(GROUP, FA.get("report.lab")).isPresent());

        // The case file and the room list in the private chat.
        say(second, "/rooms");
        Shown rooms = telegram.last(second.getId());
        press(second, rooms, "m:case:" + code);
        assertTrue(telegram.last(second.getId()).html.contains(FA.get("file.attempt.used")));

        skipAllTurns();
        accuseCorrectly(first, cast);

        Shown verdict = telegram.find(GROUP, FA.get("verdict.solved.title", code)).orElseThrow();
        assertTrue(telegram.chat(GROUP).stream().anyMatch(shown -> shown.photo
                && shown.banner.contains("fa-banner-case-solved.jpg")), "Persian verdict artwork");
        assertFalse(verdict.html.contains(DIVIDER));
        for (Shown shown : telegram.chat(GROUP)) {
            assertPersianOnly(shown, "group message " + shown.messageId);
        }
        for (User user : table) {
            for (Shown shown : telegram.chat(user.getId())) {
                assertPersianOnly(shown, "private message of " + user.getFirstName());
            }
        }
        assertEquals(1, stats.statsOf(cast.investigators.get(0).getId()).orElseThrow().getCasesSolved());
    }

    @Test
    void playersWithOtherLanguagesPlayInTheHostsLanguage() {
        languages.setLanguage(alireza.getId(), Language.PERSIAN);
        String code = openCaseWithFourPlayers();

        for (User user : table) {
            assertEquals(Language.PERSIAN, languages.languageOf(user.getId()), user.getFirstName());
        }
        Shown opened = telegram.find(GROUP, FA.get("case.open.title", code)).orElseThrow();
        assertFalse(opened.html.contains(DIVIDER), "one language for the whole room");
        assertPersianOnly(opened, "case opening");
    }

    @Test
    void aPlayerWhoSwitchesLanguageDuringACaseKeepsPlaying() {
        String code = openCaseWithFourPlayers();
        Cast cast = playToTheReport();
        assertFalse(cast.report.html.contains(DIVIDER));

        User switcher = cast.suspects.get(0);
        say(switcher, "/language");
        press(switcher, telegram.last(switcher.getId()), "m:setlang:fa");

        // Old English buttons still work, and from now on the room is written in both languages.
        press(switcher, cast.report, "EB:" + GROUP);
        Shown board = telegram.last(GROUP);
        assertTrue(board.html.contains(EN.get("board.title")) && board.html.contains(FA.get("board.title")));
        assertTrue(board.html.contains(DIVIDER));

        skipAllTurns();
        accuseCorrectly(cast.investigators.get(0), cast);
        assertTrue(telegram.last(GROUP).html.contains(FA.get("verdict.solved.title", code)));
    }

    @Test
    void aPersianPracticeCaseWithTestPlayersIsTranslatedToo() {
        languages.setLanguage(alireza.getId(), Language.PERSIAN);
        createRoom();
        group(alireza, "/addtestplayers");
        group(alireza, "/testinvestigator");

        Texts own = FA;
        assertTrue(telegram.find(alireza.getId(),
                own.get("brief.title", own.role(Role.INVESTIGATOR))).isPresent());
        assertTrue(telegram.find(GROUP, FA.get("report.title")).isPresent(), "the test scientist has answered");
        assertTrue(telegram.find(GROUP, FA.get("case.practice")).isPresent());
        for (Shown shown : telegram.chat(GROUP)) {
            assertPersianOnly(shown, "practice group message");
        }
        for (Shown shown : telegram.chat(alireza.getId())) {
            assertPersianOnly(shown, "practice private message");
        }
    }

    // ---------------------------------------------------------------- Helpers

    /**
     * The newest group message with a Skip Turn button, if there is one.
     */
    private Optional<Shown> newestTurnMessage() {
        List<Shown> shown = telegram.chat(GROUP);
        for (int i = shown.size() - 1; i >= 0; i--) {
            if (shown.get(i).buttons().stream()
                    .anyMatch(button -> String.valueOf(button.getCallbackData()).startsWith("ST:"))) {
                return Optional.of(shown.get(i));
            }
        }
        return Optional.empty();
    }

    private Shown turnMessage() {
        return newestTurnMessage().orElseThrow(() -> new AssertionError("no turn announcement in the group"));
    }
    private User currentTurn() {
        Shown turn = turnMessage();
        return table.stream().filter(user -> turn.html.contains(user.getFirstName())).findFirst().orElseThrow();
    }

    /**
     * The host skips every remaining turn, so the round reaches the point where the crime can be solved.
     */
    private void skipAllTurns() {
        while (true) {
            Optional<Shown> turn = newestTurnMessage();
            if (turn.isEmpty()) {
                return;
            }
            String skip = turn.get().buttons().stream().map(InlineKeyboardButton::getCallbackData)
                    .filter(data -> data != null && data.startsWith("ST:")).findFirst().orElseThrow();
            press(alireza, turn.get(), skip);
            if (telegram.lastAnswer().alert()) {
                return; // an old announcement: no turn is open any more
            }
        }
    }

    private Texts t(User user) {
        return languages.languageOf(user.getId()) == Language.PERSIAN ? FA : EN;
    }

    /**
     * The first idea the open scene tile suggests, as the scientist reads it.
     */
    private String firstIdea(User scientist) {
        String html = telegram.last(scientist.getId()).html;
        String ideas = html.lines().filter(line -> line.contains(" · ")).findFirst().orElseThrow()
                .replaceAll("<[^>]+>", "")
                .replaceAll("[\u200e\u200f\u2066-\u2069]", "");
        return ideas.substring(ideas.indexOf(": ") + 2, ideas.indexOf(" · "));
    }

    private void say(User user, String text) {
        bot.consume(FakeTelegram.text(user, user.getId(), null, text));
    }

    private void group(User user, String text) {
        bot.consume(FakeTelegram.text(user, GROUP, GROUP_TITLE, text));
    }

    private void press(User user, Shown message, String data) {
        assertNotNull(data, "button without callback data");
        bot.consume(telegram.press(user, message, message.chatId < 0 ? GROUP_TITLE : null, data));
        assertNotNull(telegram.lastAnswer(), "button press " + data + " was not answered");
    }

    private void addCallback(Deque<String> open, InlineKeyboardButton button) {
        if (button.getCallbackData() != null) {
            open.add(button.getCallbackData());
        }
    }

    private InlineKeyboardButton buttonFor(Shown shown, String callbackData) {
        return shown.buttons().stream()
                .filter(button -> callbackData.equals(button.getCallbackData()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button " + callbackData + " under: " + shown.html));
    }

    /**
     * The room card is the group message with the join button, whatever language it is written in.
     */
    private Shown roomCard() {
        List<Shown> shown = telegram.chat(GROUP);
        for (int i = shown.size() - 1; i >= 0; i--) {
            if (shown.get(i).buttons().stream().anyMatch(button ->
                    String.valueOf(button.getCallbackData()).startsWith("r:join:"))) {
                return shown.get(i);
            }
        }
        throw new AssertionError("no room card in the group");
    }

    private String createRoom() {
        group(alireza, "/newgame");
        // The host first picks the room language: the one they already use.
        String language = languages.languageOf(alireza.getId()).code();
        press(alireza, telegram.last(GROUP), "r:lang:" + language + ":" + alireza.getId());
        Matcher matcher = Pattern.compile("r:join:([A-Z0-9]{4})").matcher(
                roomCard().buttons().stream().map(InlineKeyboardButton::getCallbackData)
                        .filter(data -> data != null).reduce("", (a, b) -> a + " " + b));
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private String openCaseWithFourPlayers() {
        String code = createRoom();
        for (User user : List.of(sara, ben, clara)) {
            press(user, roomCard(), "r:join:" + code);
            assertEquals(t(user).get("toast.seated", code), telegram.lastAnswer().text());
        }
        press(alireza, roomCard(), "r:start:" + code);
        assertEquals(t(alireza).get("toast.case_open"), telegram.lastAnswer().text());
        return code;
    }

    /**
     * Who plays what, read from each player's private briefing in their own language.
     */
    private record Cast(User scientist, User murderer, List<User> investigators, List<User> suspects, Shown report) {
    }

    private User playerWithRole(Role role) {
        return table.stream()
                .filter(user -> {
                    Texts own = t(user);
                    String title = own.get("brief.title", own.role(role).toUpperCase(Locale.ROOT));
                    return telegram.find(user.getId(), title).isPresent();
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("nobody is " + role));
    }

    /**
     * The murderer commits the crime and the Forensic Scientist answers all six tiles.
     */
    private Cast playToTheReport() {
        User scientist = playerWithRole(Role.FORENSIC_SCIENTIST);
        User murderer = playerWithRole(Role.MURDERER);

        List<User> investigators = table.stream()
                .filter(user -> user != scientist && user != murderer)
                .toList();

        List<User> suspects = table.stream()
                .filter(user -> user != scientist)
                .toList();

        Texts own = t(murderer);
        Shown choice = telegram.find(murderer.getId(), own.get("murder.means.title"))
                .orElseThrow();

        assertEquals(4, choice.buttons().size());

        for (int step = 0; step < 5; step++) {
            press(murderer, choice, choice.buttons().get(0).getCallbackData());
        }

        assertTrue(choice.html.contains(own.get("sealed.title")), choice.html);
        assertFalse(choice.html.contains(DIVIDER));

        Texts scientistTexts = t(scientist);

        assertTrue(telegram.find(
                scientist.getId(),
                scientistTexts.get("tile.heading", 1, 6)
        ).isPresent());

        assertTrue(telegram.find(
                scientist.getId(),
                scientistTexts.get("sci.solution.title")
        ).isPresent());

        // The scientist types one of the suggested ideas, in their own language.
        for (int i = 0; i < 6; i++) {
            say(scientist, firstIdea(scientist));
        }

        assertTrue(telegram.find(
                scientist.getId(),
                scientistTexts.get("placed.title", 1)
        ).isPresent());

        Shown report = telegram.chat(GROUP).stream()
                .filter(shown -> shown.buttons().stream()
                        .anyMatch(button -> ("LT:" + GROUP).equals(button.getCallbackData()))
                        && shown.buttons().stream()
                        .anyMatch(button -> ("AC:" + GROUP).equals(button.getCallbackData())))
                .reduce((first, second) -> second)
                .orElseThrow(() ->
                        new AssertionError("no scene report in the group"));

        return new Cast(scientist, murderer, investigators, suspects, report);
    }

    /**
     * The murderer chose their first means card and first clue card, so the solution is known.
     */
    private void accuseCorrectly(User accuser, Cast cast) {
        press(accuser, cast.report, "AC:" + GROUP);
        Shown flow = telegram.last(GROUP);
        InlineKeyboardButton suspect = flow.button(cast.murderer.getFirstName()).orElseThrow();
        press(accuser, flow, suspect.getCallbackData());
        press(accuser, flow, flow.buttons().get(0).getCallbackData());
        press(accuser, flow, flow.buttons().get(0).getCallbackData());
    }

    /**
     * The text a reader sees, without tags and without the things that are Latin by nature.
     */
    private String visible(Shown shown) {
        String text = shown.html
                .replaceAll("<code>[^<]*</code>", "")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace(GROUP_TITLE, "")
                .replace("Deception: Murder in Hong Kong", "")
                .replaceAll("/[a-z]+", "")
                .replaceAll("@[A-Za-z0-9_]+", "")
                .replaceAll("Test [A-Z][a-z]+", "") // the names of the practice players
                .replaceAll("\\b[A-Z0-9]{4}\\b", "");
        for (User user : List.of(alireza, sara, ben, clara, dana)) {
            text = text.replace(user.getFirstName(), "");
        }
        return text;
    }

    /**
     * No English words, Persian digits, and every line running right to left.
     */
    private void assertPersianOnly(Shown shown, String where) {
        if (shown.html.isEmpty()) {
            return; // artwork without a caption
        }
        String text = visible(shown);
        Matcher latin = LATIN_WORD.matcher(text);
        if (latin.find()) {
            throw new AssertionError(where + ": English word \"" + latin.group() + "\" in a Persian message: "
                    + shown.html);
        }
        assertTrue(PERSIAN_LETTER.matcher(text).find(), where + ": no Persian text in " + shown.html);
        for (String line : shown.html.split("\n")) {
            String withoutLeadingTags = line.replaceFirst("^(<(?!code)[^>]+>)+", "");
            if (!line.isBlank()) {
                assertEquals(RIGHT_TO_LEFT_MARK, withoutLeadingTags.charAt(0),
                        where + ": line does not start right-to-left: " + line);
            }
        }
        for (InlineKeyboardButton button : shown.buttons()) {
            String label = button.getText().replace("Language", "").replace("Sprache", "");
            for (Language language : Language.values()) {
                label = label.replace(language.nativeName(), "");
            }
            for (User user : List.of(alireza, sara, ben, clara, dana)) {
                label = label.replace(user.getFirstName(), "");
            }
            label = label.replaceAll("\\b[A-Z0-9]{4}\\b", "");
            label = label.replaceAll("Test [A-Z][a-z]+", "");
            assertFalse(LATIN_WORD.matcher(label).find(), where + ": English button \"" + button.getText() + "\"");
        }
    }

    private String persianDigits(String text) {
        StringBuilder converted = new StringBuilder();
        for (char c : text.toCharArray()) {
            converted.append(c >= '0' && c <= '9' ? (char) ('\u06F0' + (c - '0')) : c);
        }
        return converted.toString();
    }

    private int countOccurrences(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
