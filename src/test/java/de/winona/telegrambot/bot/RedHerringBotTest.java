package de.winona.telegrambot.bot;

import de.winona.telegrambot.bot.FakeTelegram.Shown;
import de.winona.telegrambot.model.PlayerStats;
import de.winona.telegrambot.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays the bot from the outside, the way Telegram users would: with commands and button presses.
 */
class RedHerringBotTest {

    private static final long GROUP = -1001234567890L;
    private static final String GROUP_TITLE = "Friday <Night> & Friends";

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
    private RedHerringBot bot;

    @BeforeEach
    void setUp() {
        telegram = new FakeTelegram();
        stats = new StatsService(tempDir.resolve("stats.properties"));
        bot = new RedHerringBot(telegram.client(), stats);
    }

    @AfterEach
    void everyMessageRespectsTelegramsLimits() {
        assertEquals(List.of(), telegram.problems());
    }

    // ---------------------------------------------------------------- Dashboard

    @Test
    void startShowsTheHomeScreenWithBannerAndMainActions() {
        say(alireza, "/start");

        Shown home = telegram.last(alireza.getId());
        assertTrue(home.photo, "home is a banner with caption");
        assertEquals("banner-home.jpg", home.banner);
        assertTrue(home.html.contains("Every clue hides a secret."));
        assertTrue(home.button("Create Game Room").isPresent());
        assertTrue(home.button("Join Game Room").isPresent());
        assertTrue(home.button("How to Play").isPresent());
        assertTrue(home.button("Game Roles").isPresent());
        assertTrue(home.button("My Statistics").isPresent());
        assertTrue(home.button("Home").isPresent());
        assertTrue(home.button("Rooms").isPresent());
        assertTrue(home.button("Leaders").isPresent());
        assertTrue(home.button("Profile").isPresent());
    }

    @Test
    void everyDashboardButtonLeadsToAWorkingScreen() {
        say(alireza, "/start");
        int messagesBefore = telegram.chat(alireza.getId()).size();

        Set<String> visited = new HashSet<>();
        Deque<String> open = new ArrayDeque<>();
        telegram.last(alireza.getId()).buttons().forEach(button -> addCallback(open, button));

        while (!open.isEmpty()) {
            String data = open.pop();
            if (!visited.add(data)) {
                continue;
            }
            press(alireza, telegram.last(alireza.getId()), data);
            assertFalse(telegram.lastAnswer().alert(),
                    "button " + data + " answered with an alert: " + telegram.lastAnswer().text());
            telegram.last(alireza.getId()).buttons().forEach(button -> addCallback(open, button));
        }

        assertTrue(visited.containsAll(Set.of("m:home", "m:rooms", "m:create", "m:join", "m:stats", "m:top",
                "m:me", "m:how:4", "m:roles:5")), "visited " + visited);
        assertEquals(messagesBefore, telegram.chat(alireza.getId()).size(),
                "the dashboard changes one message in place instead of sending new ones");
    }

    private void addCallback(Deque<String> open, InlineKeyboardButton button) {
        if (button.getCallbackData() != null) {
            open.add(button.getCallbackData());
        }
    }

    @Test
    void emptyStatesAreShownWithoutInventedNumbers() {
        say(alireza, "/stats");
        assertTrue(telegram.last(alireza.getId()).html.contains("No closed cases yet"));

        say(alireza, "/leaderboard");
        assertTrue(telegram.last(alireza.getId()).html.contains("No closed cases yet"));

        say(alireza, "/rooms");
        assertTrue(telegram.last(alireza.getId()).html.contains("You are not seated in any room"));

        say(alireza, "/profile");
        Shown profile = telegram.last(alireza.getId());
        assertTrue(profile.html.contains("@alireza"));
        assertTrue(profile.html.contains("<code>11</code>"));
        assertTrue(profile.html.contains("not ranked yet"));
    }

    @Test
    void createRoomInPrivateOffersToChooseAGroup() {
        say(alireza, "/start");
        press(alireza, telegram.last(alireza.getId()), "m:create");

        InlineKeyboardButton choose = telegram.last(alireza.getId()).button("Choose a Group").orElseThrow();
        assertEquals("https://t.me/" + FakeTelegram.BOT_USERNAME + "?startgroup=new", choose.getUrl());
    }

    // ---------------------------------------------------------------- Rooms

    @Test
    void creatingARoomPostsACardWithCodeAndSeatsTheHost() {
        String code = createRoom();

        Shown card = roomCard();
        assertTrue(card.html.contains("Alireza"));
        assertTrue(card.html.contains("1 of 7 seated"));
        assertTrue(card.html.contains("Friday &lt;Night&gt; &amp; Friends"), "group title is escaped");
        assertTrue(telegram.find(alireza.getId(), "You are seated in room " + code).isPresent());
    }

    @Test
    void playersJoinWithOneTapAndTheCardUpdatesInPlace() {
        String code = createRoom();
        int cardId = roomCard().messageId;

        press(sara, roomCard(), "r:join:" + code);

        assertEquals("You are seated in room " + code + ".", telegram.lastAnswer().text());
        assertEquals(cardId, roomCard().messageId);
        assertTrue(roomCard().html.contains("2 of 7 seated"));
        assertTrue(roomCard().html.contains("Sara"));

        press(sara, roomCard(), "r:join:" + code);
        assertEquals("You are already seated.", telegram.lastAnswer().text());
        assertTrue(roomCard().html.contains("2 of 7 seated"));
    }

    @Test
    void aPlayerWhoNeverStartedTheBotIsSentToThePrivateChatAndSeatedThere() {
        String code = createRoom();
        telegram.blockPrivateChat(dana.getId());

        press(dana, roomCard(), "r:join:" + code);
        assertEquals("https://t.me/" + FakeTelegram.BOT_USERNAME + "?start=join_" + code,
                telegram.lastAnswer().url());
        assertTrue(roomCard().html.contains("1 of 7 seated"), "not seated before the private chat exists");

        telegram.allowPrivateChat(dana.getId());
        say(dana, "/start join_" + code);

        assertTrue(roomCard().html.contains("2 of 7 seated"));
        assertTrue(telegram.last(dana.getId()).html.contains("ROOM " + code));
    }

    @Test
    void joiningWithACodeInPrivateWorksAndExplainsEveryFailure() {
        String code = createRoom();

        say(sara, "/start");
        press(sara, telegram.last(sara.getId()), "m:join");
        assertTrue(telegram.last(sara.getId()).html.contains("Send me the 4-character code"));

        say(sara, "hello there");
        assertTrue(telegram.last(sara.getId()).html.contains("A room code has 4 letters or digits"));

        say(sara, "ZZZZ".equals(code) ? "YYYY" : "ZZZZ");
        assertTrue(telegram.last(sara.getId()).html.contains("No room has the code"));

        say(sara, code.toLowerCase());
        assertTrue(telegram.last(sara.getId()).html.contains("ROOM " + code));
        assertTrue(roomCard().html.contains("2 of 7 seated"));

        say(sara, "/rooms");
        Shown rooms = telegram.last(sara.getId());
        assertTrue(rooms.html.contains("Room " + code));
        assertTrue(rooms.html.contains("Host: Alireza"));
        assertTrue(rooms.button("Enter Room " + code).isPresent());
    }

    @Test
    void aFullRoomAndAClosedRoomCannotBeJoined() {
        String code = createRoom();
        for (long id = 21; id <= 26; id++) {
            say(FakeTelegram.user(id, "Guest" + id), "/join " + code);
        }
        assertTrue(roomCard().html.contains("7 of 7 seated"));

        say(dana, "/join " + code);
        assertTrue(telegram.last(dana.getId()).html.contains("is full"));

        press(sara, roomCard(), "r:close:" + code);
        assertTrue(telegram.lastAnswer().alert(), "only the host may close the room");

        press(alireza, roomCard(), "r:close:" + code);
        assertTrue(telegram.last(GROUP).html.contains("IS CLOSED"));

        say(dana, "/join " + code);
        assertTrue(telegram.last(dana.getId()).html.contains("was closed by its host"));
    }

    @Test
    void onlyTheHostOpensTheCaseAndOnlyWithEnoughPlayers() {
        String code = createRoom();
        press(sara, roomCard(), "r:join:" + code);

        press(sara, roomCard(), "r:start:" + code);
        assertTrue(telegram.lastAnswer().text().contains("Only the host"));

        press(alireza, roomCard(), "r:start:" + code);
        assertTrue(telegram.lastAnswer().text().contains("Needs 2 more detectives"));
    }

    @Test
    void leavingFreesTheSeat() {
        String code = createRoom();
        press(sara, roomCard(), "r:join:" + code);
        press(sara, roomCard(), "r:leave:" + code);

        assertEquals("You left room " + code + ".", telegram.lastAnswer().text());
        assertTrue(roomCard().html.contains("1 of 7 seated"));
    }

    // ---------------------------------------------------------------- A whole case

    @Test
    void aCaseCanBePlayedFromTheFirstSeatToTheVerdictAndIsCounted() {
        String code = openCaseWithFourPlayers();

        User scientist = playerWithRole("FORENSIC SCIENTIST");
        User murderer = playerWithRole("MURDERER");
        List<User> investigators = table.stream()
                .filter(user -> user != scientist && user != murderer)
                .toList();

        Shown opened = telegram.find(GROUP, "CASE " + code + " IS OPEN").orElseThrow();
        assertEquals("banner-case-open.jpg", opened.banner);
        assertTrue(opened.html.contains("Forensic Scientist: <b>" + scientist.getFirstName() + "</b>"));
        assertTrue(telegram.find(GROUP, "EVIDENCE BOARD").isPresent());

        // The murderer picks both cards in one private message.
        Shown choice = telegram.find(murderer.getId(), "CHOOSE THE MEANS OF MURDER").orElseThrow();
        assertEquals(4, choice.buttons().size());
        String means = choice.buttons().get(1).getText();
        press(murderer, choice, choice.buttons().get(1).getCallbackData());
        assertTrue(choice.html.contains("CHOOSE THE KEY EVIDENCE"), "same message, next step");
        String keyEvidence = choice.buttons().get(2).getText();
        press(murderer, choice, choice.buttons().get(2).getCallbackData());
        assertTrue(choice.html.contains("THE CRIME IS SEALED"));

        // The Forensic Scientist learns the solution and answers six tiles.
        Shown solution = telegram.find(scientist.getId(), "THE SOLUTION").orElseThrow();
        assertTrue(solution.html.contains(means) && solution.html.contains(keyEvidence));
        assertTrue(telegram.find(investigators.get(0).getId(), "THE SOLUTION").isEmpty());

        Shown tile = telegram.find(scientist.getId(), "SCENE TILE 1 OF 6").orElseThrow();
        assertTrue(tile.html.contains("Cause of death"));
        for (int i = 1; i <= 6; i++) {
            assertTrue(tile.html.contains("SCENE TILE " + i + " OF 6"));
            assertEquals(6, tile.buttons().size());
            String firstOption = tile.buttons().get(0).getCallbackData();
            press(scientist, tile, firstOption);
            press(scientist, tile, firstOption); // a second tap on the old button changes nothing
        }
        assertTrue(tile.html.contains("ROUND 1 REPORT IS OUT"));
        Shown report = telegram.find(GROUP, "SCENE REPORT").orElseThrow();
        assertEquals(6, countOccurrences(report.html, "▪️"));

        // Nobody but the Forensic Scientist may start the next round.
        press(investigators.get(0), report, report.button("Next Round").orElseThrow().getCallbackData());
        assertTrue(telegram.lastAnswer().alert());

        // The Forensic Scientist cannot accuse.
        press(scientist, report, report.button("Solve the Crime").orElseThrow().getCallbackData());
        assertTrue(telegram.lastAnswer().text().contains("Forensic Scientist cannot solve"));

        // A wrong accusation costs the attempt, and other players cannot press its buttons.
        User first = investigators.get(0);
        accuse(first, report, murderer.getFirstName(), means, wrongCardFor(murderer, keyEvidence), investigators.get(1));
        Shown wrong = telegram.find(GROUP, "has used their attempt").orElseThrow();
        assertTrue(wrong.buttons().isEmpty());
        press(first, report, report.button("Solve the Crime").orElseThrow().getCallbackData());
        assertTrue(telegram.lastAnswer().text().contains("already used your attempt"));

        // Round 2: one tile is replaced.
        press(scientist, tile, tile.button("Next Round").orElseThrow().getCallbackData());
        assertTrue(tile.html.contains("ROUND 2 OF 3"));
        assertEquals(5, tile.buttons().size(), "only Cause of death cannot be replaced");
        press(scientist, tile, tile.buttons().get(0).getCallbackData());
        assertTrue(tile.html.contains("NEW SCENE TILE"));
        press(scientist, tile, tile.buttons().get(3).getCallbackData());
        Shown secondReport = telegram.last(GROUP);
        assertTrue(secondReport.html.contains("round 2 of 3"));

        // The case file in the private chat ("Rejoin Game").
        say(first, "/rooms");
        Shown rooms = telegram.last(first.getId());
        press(first, rooms, rooms.button("Rejoin Game " + code).orElseThrow().getCallbackData());
        Shown caseFile = telegram.last(first.getId());
        assertTrue(caseFile.html.contains("CASE FILE " + code));
        assertTrue(caseFile.html.contains("Your role: <b>Investigator</b>"));
        assertTrue(caseFile.html.contains("attempt to solve the crime is used"));
        assertFalse(caseFile.html.contains(keyEvidence + "</b>   "), "investigators do not see the solution");

        // The correct accusation ends the case.
        accuse(investigators.get(1), secondReport, murderer.getFirstName(), means, keyEvidence, null);
        Shown verdict = telegram.last(GROUP);
        assertEquals("banner-case-solved.jpg", verdict.banner);
        assertTrue(verdict.html.contains("CASE " + code + " SOLVED"));
        assertTrue(verdict.html.contains("Murderer: <b>" + murderer.getFirstName() + "</b>"));

        // The result is counted for everyone.
        PlayerStats solver = stats.statsOf(investigators.get(1).getId()).orElseThrow();
        assertEquals(1, solver.getWins());
        assertEquals(1, solver.getCasesSolved());
        assertEquals(0, stats.statsOf(murderer.getId()).orElseThrow().getWins());
        assertEquals(1, stats.statsOf(scientist.getId()).orElseThrow().getWins());

        say(murderer, "/stats");
        assertTrue(telegram.last(murderer.getId()).html.contains("As Murderer"));
        say(murderer, "/leaderboard");
        Shown board = telegram.last(murderer.getId());
        assertTrue(board.html.contains(investigators.get(1).getFirstName()));
        assertTrue(board.html.contains("▸"), "the viewer's own row is marked");

        // The statistics survive a restart.
        StatsService reloaded = new StatsService(tempDir.resolve("stats.properties"));
        assertEquals(1, reloaded.statsOf(solver.getUserId()).orElseThrow().getWins());

        // The group is free for the next room.
        press(sara, verdict, verdict.button("New Case").orElseThrow().getCallbackData());
        assertTrue(telegram.last(GROUP).html.contains("Waiting for detectives"));
    }

    @Test
    void theMurdererEscapesWhenRoundThreeEndsWithoutASolution() {
        String code = openCaseWithFourPlayers();
        User scientist = playerWithRole("FORENSIC SCIENTIST");
        User murderer = playerWithRole("MURDERER");

        Shown choice = telegram.find(murderer.getId(), "CHOOSE THE MEANS OF MURDER").orElseThrow();
        press(murderer, choice, choice.buttons().get(0).getCallbackData());
        press(murderer, choice, choice.buttons().get(0).getCallbackData());

        Shown tile = telegram.find(scientist.getId(), "SCENE TILE 1 OF 6").orElseThrow();
        for (int i = 0; i < 6; i++) {
            press(scientist, tile, tile.buttons().get(i).getCallbackData());
        }
        for (int round = 2; round <= 3; round++) {
            press(scientist, tile, tile.button("Next Round").orElseThrow().getCallbackData());
            press(scientist, tile, tile.buttons().get(1).getCallbackData());
            press(scientist, tile, tile.buttons().get(0).getCallbackData());
        }
        assertTrue(tile.html.contains("final round"));
        press(scientist, tile, tile.button("Close the Case").orElseThrow().getCallbackData());

        Shown verdict = telegram.last(GROUP);
        assertEquals("banner-case-cold.jpg", verdict.banner);
        assertTrue(verdict.html.contains("CASE " + code + " UNSOLVED"));
        assertEquals(1, stats.statsOf(murderer.getId()).orElseThrow().getEscapes());
        assertEquals(0, stats.statsOf(scientist.getId()).orElseThrow().getWins());
    }

    @Test
    void aPracticeCaseWithTestPlayersRunsAloneAndIsNotCounted() {
        createRoom();
        group(alireza, "/addtestplayers");
        assertTrue(roomCard().html.contains("4 of 7 seated"));
        group(alireza, "/testinvestigator");

        Shown report = telegram.find(GROUP, "SCENE REPORT").orElseThrow();
        assertTrue(telegram.find(GROUP, "Practice case").isPresent());
        assertTrue(telegram.find(alireza.getId(), "YOUR ROLE: INVESTIGATOR").isPresent());

        group(alireza, "/nextround");
        assertTrue(telegram.last(GROUP).html.contains("round 2 of 3"));

        press(alireza, report, report.button("Solve the Crime").orElseThrow().getCallbackData());
        Shown flow = telegram.last(GROUP);
        press(alireza, flow, flow.buttons().get(0).getCallbackData());
        press(alireza, flow, flow.buttons().get(0).getCallbackData());
        press(alireza, flow, flow.buttons().get(0).getCallbackData());

        assertTrue(telegram.last(GROUP).photo, "the case ends with a verdict banner");
        assertTrue(stats.statsOf(alireza.getId()).isEmpty(), "practice cases are not counted");
    }

    @Test
    void endgameClosesARunningCaseWithoutCountingIt() {
        openCaseWithFourPlayers();
        group(dana, "/endgame");
        assertTrue(telegram.last(GROUP).html.contains("Only the host or a player"));

        group(sara, "/endgame");
        assertTrue(telegram.last(GROUP).html.contains("ended the case before it was solved"));
        assertTrue(stats.statsOf(sara.getId()).isEmpty());

        group(sara, "/accuse");
        assertTrue(telegram.last(GROUP).html.contains("There is no running case"));
    }

    @Test
    void commandsForAnotherBotAreIgnored() {
        int before = telegram.chat(GROUP).size();
        group(alireza, "/newgame@SomeOtherBot");
        assertEquals(before, telegram.chat(GROUP).size());

        group(alireza, "/newgame@" + FakeTelegram.BOT_USERNAME);
        assertTrue(telegram.last(GROUP).html.contains("Waiting for detectives"));
    }

    // ---------------------------------------------------------------- Helpers

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

    /** Creates a room as Alireza and returns its code. */
    private String createRoom() {
        group(alireza, "/newgame");
        Matcher matcher = Pattern.compile("ROOM ([A-Z0-9]{4})").matcher(roomCard().html);
        assertTrue(matcher.find(), roomCard().html);
        return matcher.group(1);
    }

    private Shown roomCard() {
        return telegram.find(GROUP, "Room code").orElseThrow();
    }

    private String openCaseWithFourPlayers() {
        String code = createRoom();
        for (User user : List.of(sara, ben, clara)) {
            press(user, roomCard(), "r:join:" + code);
        }
        assertTrue(roomCard().html.contains("Ready."));
        press(alireza, roomCard(), "r:start:" + code);
        assertEquals("The case is open.", telegram.lastAnswer().text());
        return code;
    }

    private User playerWithRole(String role) {
        return table.stream()
                .filter(user -> telegram.find(user.getId(), "YOUR ROLE: " + role).isPresent())
                .findFirst()
                .orElseThrow();
    }

    /** Any clue card of the murderer that is not the Key Evidence, read from the Evidence Board. */
    private String wrongCardFor(User murderer, String keyEvidence) {
        String board = telegram.find(GROUP, "EVIDENCE BOARD").orElseThrow().html;
        String ownBlock = board.substring(board.indexOf("<b>" + murderer.getFirstName() + "</b>"));
        String clueLine = ownBlock.split("\n")[2];
        for (String card : clueLine.substring(clueLine.indexOf(' ') + 1).split(", ")) {
            if (!card.equals(keyEvidence)) {
                return card;
            }
        }
        throw new AssertionError("no other card in " + clueLine);
    }

    /** Walks through the three accusation steps by button label. */
    private void accuse(User accuser, Shown report, String suspect, String means, String clue, User intruder) {
        press(accuser, report, report.button("Solve the Crime").orElseThrow().getCallbackData());
        Shown flow = telegram.last(GROUP);
        assertTrue(flow.html.contains("Who is the murderer?"));
        assertTrue(flow.button(accuser.getFirstName()).isEmpty(), "nobody can accuse themselves");

        if (intruder != null) {
            press(intruder, flow, flow.button(suspect).orElseThrow().getCallbackData());
            assertTrue(telegram.lastAnswer().text().contains("This accusation belongs to " + accuser.getFirstName()));
            assertTrue(flow.html.contains("Who is the murderer?"), "nothing changed");
        }
        press(accuser, flow, flow.button(suspect).orElseThrow().getCallbackData());
        press(accuser, flow, flow.button(means).orElseThrow().getCallbackData());
        press(accuser, flow, flow.button(clue).orElseThrow().getCallbackData());
    }

    private int countOccurrences(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
