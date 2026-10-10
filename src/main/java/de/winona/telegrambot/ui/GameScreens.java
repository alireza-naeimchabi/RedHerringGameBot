package de.winona.telegrambot.ui;

import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Hand;
import de.winona.telegrambot.model.Hint;
import de.winona.telegrambot.model.HintTile;
import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.model.RoomEnd;
import de.winona.telegrambot.util.CardKeyboardFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.winona.telegrambot.ui.Keyboard.button;
import static de.winona.telegrambot.ui.Keyboard.link;
import static de.winona.telegrambot.ui.MenuScreens.shorten;
import static de.winona.telegrambot.ui.Theme.bold;
import static de.winona.telegrambot.ui.Theme.code;
import static de.winona.telegrambot.ui.Theme.esc;
import static de.winona.telegrambot.ui.Theme.heading;
import static de.winona.telegrambot.ui.Theme.italic;
import static de.winona.telegrambot.ui.Theme.note;

/**
 * The screens of a room and a running case: the room card in the group, the private role
 * messages, the scene report, the accusation and the verdict.
 */
public final class GameScreens {

    private static final int NAME_LENGTH = 22;
    private static final int TITLE_LENGTH = 32;
    private static final String PRACTICE_NOTE = "Practice case. It is not counted in the statistics.";

    private GameScreens() {
    }

    // ---------------------------------------------------------------- Room card (group)

    public static Screen lobbyCard(Lobby lobby, String botUsername) {
        RoomSummary room = RoomSummary.of(lobby);
        String roomCode = room.code();

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, "ROOM " + roomCode,
                shorten(lobby.getRoom().groupTitle(), TITLE_LENGTH))).append("\n\n");
        text.append(Theme.ICON_WAITING).append(" Waiting for detectives\n");
        text.append(Theme.seats(lobby.getPlayers().size(), Lobby.MAX_PLAYERS)).append("  ")
                .append(lobby.getPlayers().size()).append(" of ").append(Lobby.MAX_PLAYERS)
                .append(" seated\n\n");
        text.append(MenuScreens.playerList(lobby.getPlayers(), room)).append("\n");
        text.append(note(lobby.hasEnoughPlayers()
                ? "Ready. " + esc(name(lobby.getRoom().host())) + " can open the case."
                : "Needs " + Theme.plural(lobby.missingPlayers(), "more detective", "more detectives")
                        + " before the case can open."));
        text.append("\nRoom code ").append(code(roomCode));

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_JOIN + " Join Room", Callbacks.joinRoom(roomCode)),
                        button("Leave Room", Callbacks.leaveRoom(roomCode)))
                .row(button("▶️ Open the Case", Callbacks.startRoom(roomCode)));
        if (botUsername != null && !botUsername.isBlank()) {
            keyboard.row(link(Theme.ICON_HOW_TO + " How to Play", "https://t.me/" + botUsername + "?start=how"),
                    button("Close Room", Callbacks.closeRoom(roomCode)));
        } else {
            keyboard.row(button("Close Room", Callbacks.closeRoom(roomCode)));
        }
        return Screen.text(text.toString(), keyboard.build());
    }

    /** Replaces the room card when the room can no longer be joined. */
    public static Screen roomEnded(String roomCode, RoomEnd reason) {
        return switch (reason) {
            case CLOSED_BY_HOST -> Screen.text(Theme.ICON_ROOMS + " " + bold("ROOM " + roomCode + " IS CLOSED")
                    + "\n\nThe host closed this room. Its code no longer works.", newRoomKeyboard());
            case EXPIRED -> Screen.text(Theme.ICON_ROOMS + " " + bold("ROOM " + roomCode + " HAS EXPIRED")
                    + "\n\nNobody opened the case in time. Its code no longer works.", newRoomKeyboard());
            case CASE_STARTED -> Screen.text(Theme.ICON_LIVE + " " + bold("ROOM " + roomCode + " IS LOCKED")
                    + "\n\nThe seats are taken and the case is open.");
        };
    }

    private static InlineKeyboardMarkup newRoomKeyboard() {
        return Keyboard.create()
                .row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.newRoomHere()))
                .build();
    }

    /** Private confirmation after taking a seat. */
    public static Screen seatTaken(Lobby lobby) {
        Room room = lobby.getRoom();
        String text = Theme.ICON_OK + " " + bold("You are seated in room " + room.code()) + "\n"
                + italic(shorten(room.groupTitle(), TITLE_LENGTH)) + "\n\n"
                + "Your secret role arrives in this chat when the host opens the case. "
                + "The discussion happens in the group.";
        return Screen.text(text, Keyboard.create()
                .row(button("Enter Room " + room.code(), Callbacks.roomDetails(room.code())),
                        button(Theme.ICON_HOW_TO + " How to Play", Callbacks.howToPlay(1)))
                .build());
    }

    // ---------------------------------------------------------------- Case opening

    public static Screen caseOpened(Game game) {
        String suspects = game.getSuspects().stream()
                .map(player -> esc(name(player)))
                .collect(Collectors.joining(", "));

        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_LIVE).append(" ").append(bold("CASE " + game.getRoom().code() + " IS OPEN"))
                .append("\n").append(italic("Round 1 of " + Game.MAX_ROUNDS)).append("\n\n");
        text.append("Roles have been dealt in private. ").append(Theme.ICON_SECRET).append("\n\n");
        text.append(Theme.ICON_SCIENTIST).append(" Forensic Scientist: ")
                .append(bold(name(game.getForensicScientist()))).append("\n");
        text.append("They know the solution and answer only through the scene tiles.\n\n");
        text.append("Suspects: ").append(suspects).append("\n\n");
        text.append(italic("The murderer is choosing the Means of Murder and the Key Evidence."));
        if (game.isPractice()) {
            text.append("\n\n").append(note(PRACTICE_NOTE));
        }
        return Screen.withBanner(Banner.CASE_OPEN, text.toString(), null);
    }

    public static Screen evidenceBoard(Game game) {
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, "EVIDENCE BOARD", "Case " + game.getRoom().code())).append("\n");

        game.getHands().forEach((player, hand) -> {
            text.append("\n").append(bold(name(player))).append("\n");
            text.append(Theme.ICON_MEANS).append(" ").append(cardNames(hand.weapons())).append("\n");
            text.append(Theme.ICON_CLUE).append(" ").append(cardNames(hand.evidence())).append("\n");
        });
        text.append("\n").append(italic("One means card and one clue card of the same player are the solution."));
        return Screen.text(text.toString());
    }

    private static String cardNames(List<Card> cards) {
        return cards.stream()
                .map(card -> esc(card.name()))
                .collect(Collectors.joining(", "));
    }

    /** The private message that tells a player their role. */
    public static Screen roleBriefing(Game game, Role role) {
        String nextStep = switch (role) {
            case FORENSIC_SCIENTIST -> "Wait for the murderer's choice. Then I show you the solution "
                    + "and the first scene tile here.";
            case MURDERER -> "Pick your two cards below. Then join the discussion in the group "
                    + "and act like an investigator.";
            case INVESTIGATOR -> "Watch the scene report in the group. Use " + bold("Solve the Crime")
                    + " once, when you are sure.";
        };
        String text = Theme.ICON_SECRET + " " + bold("YOUR ROLE: " + role.getTitle().toUpperCase()) + "\n"
                + italic("Case " + game.getRoom().code() + ", "
                        + shorten(game.getRoom().groupTitle(), TITLE_LENGTH)) + "\n\n"
                + esc(role.getDescription()) + "\n\n"
                + note(nextStep);
        return Screen.text(text);
    }

    // ---------------------------------------------------------------- Murderer (private)

    public static Screen chooseMeans(Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_MEANS + " " + bold("CHOOSE THE MEANS OF MURDER") + "\n"
                + italic("Step 1 of 2") + "\n\n"
                + "Which of your means cards did you use? Nobody else sees this choice.";
        return Screen.text(text, CardKeyboardFactory.createCardKeyboard(
                game.getMurdererHand().weapons(), Callbacks.game(Callbacks.WEAPON, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen chooseKeyEvidence(Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_CLUE + " " + bold("CHOOSE THE KEY EVIDENCE") + "\n"
                + italic("Step 2 of 2") + "\n\n"
                + "Means of Murder: " + bold(game.getMurderWeapon().name()) + "\n\n"
                + "Which of your clue cards did you leave behind?";
        return Screen.text(text, CardKeyboardFactory.createCardKeyboard(
                game.getMurdererHand().evidence(), Callbacks.game(Callbacks.EVIDENCE, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen crimeSealed(Game game) {
        String text = Theme.ICON_OK + " " + bold("THE CRIME IS SEALED") + "\n\n"
                + Theme.ICON_MEANS + " Means of Murder: " + bold(game.getMurderWeapon().name()) + "\n"
                + Theme.ICON_CLUE + " Key Evidence: " + bold(game.getKeyEvidence().name()) + "\n\n"
                + note("Return to the group and blend in. You win if nobody names you with both cards.");
        return Screen.text(text);
    }

    public static Screen crimeCommitted() {
        return Screen.text(Theme.ICON_MEANS + " " + bold("The crime has been committed.") + "\n"
                + "The Forensic Scientist is examining the scene. The first answers arrive shortly.");
    }

    // ---------------------------------------------------------------- Forensic Scientist (private)

    public static Screen solutionForScientist(Game game) {
        String text = Theme.ICON_SCIENTIST + " " + bold("THE SOLUTION") + "\n"
                + italic("For your eyes only") + "\n\n"
                + "Murderer: " + bold(name(game.getMurderer())) + "\n"
                + Theme.ICON_MEANS + " Means of Murder: " + bold(game.getMurderWeapon().name()) + "\n"
                + Theme.ICON_CLUE + " Key Evidence: " + bold(game.getKeyEvidence().name()) + "\n\n"
                + note("Guide the investigators with your answers. Never reveal anything in words.");
        return Screen.text(text);
    }

    /** The scene tile the Forensic Scientist answers next, with the answers given so far. */
    public static Screen sceneTile(Game game, HintTile tile) {
        long chatId = game.getRoom().groupChatId();
        int number = game.getCurrentHintIndex() + 1;
        int total = game.getTileCount();

        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_SCIENTIST).append(" ").append(bold("SCENE TILE " + number + " OF " + total))
                .append("\n\n");
        text.append(hintLines(game.getHints()));
        if (!game.getHints().isEmpty()) {
            text.append("\n");
        }
        text.append(bold(tile.title())).append("\n");
        text.append("Pick the option that fits the solution best.");

        String prefix = Callbacks.game(Callbacks.HINT, chatId, game.getCurrentHintIndex()) + Callbacks.SEPARATOR;
        return Screen.text(text.toString(), CardKeyboardFactory.createTextKeyboard(tile.options(), prefix));
    }

    public static Screen tilesPlaced(Game game) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_OK).append(" ").append(bold("ROUND " + game.getRound() + " REPORT IS OUT"))
                .append("\n\n");
        text.append(hintLines(game.getHints())).append("\n");
        text.append(note(game.isLastRound()
                ? "This is the final round. When the discussion is over, close the case."
                : "Let the group discuss. Then start the next round and replace one tile."));

        return Screen.text(text.toString(), Keyboard.create()
                .row(nextRoundButton(game, chatId))
                .build());
    }

    public static Screen chooseTileToReplace(Game game, HintTile newTile) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_ROUND).append(" ").append(bold("ROUND " + game.getRound() + " OF " + Game.MAX_ROUNDS))
                .append("\n").append(italic("Replace one scene tile")).append("\n\n");
        text.append("New tile: ").append(bold(newTile.title())).append("\n\n");
        text.append("Which answer do you take off the board? ")
                .append(italic("Cause of death and Location always stay."));

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Hint> hints = game.getHints();
        for (int i = Game.FIXED_TILES; i < hints.size(); i++) {
            buttons.add(button(hints.get(i).title() + ": " + hints.get(i).choice(),
                    Callbacks.game(Callbacks.REPLACE_SLOT, chatId, game.getRound(), i)));
        }
        return Screen.text(text.toString(), Keyboard.create().grid(buttons, 1).build());
    }

    public static Screen replacementTile(Game game, HintTile tile) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_SCIENTIST + " " + bold("NEW SCENE TILE") + "\n\n"
                + bold(tile.title()) + "\n"
                + "Pick the option that fits the solution best.";
        String prefix = Callbacks.game(Callbacks.REPLACEMENT_HINT, chatId, game.getRound()) + Callbacks.SEPARATOR;
        return Screen.text(text, CardKeyboardFactory.createTextKeyboard(tile.options(), prefix));
    }

    // ---------------------------------------------------------------- Investigation (group)

    public static Screen tileAnswered(Game game, Hint hint) {
        return Screen.text(Theme.ICON_SCIENTIST + " Scene tile " + game.getHints().size() + "\n"
                + esc(hint.title()) + ": " + bold(hint.choice()));
    }

    public static Screen roundStarted(Game game) {
        return Screen.text(Theme.ICON_ROUND + " " + bold("ROUND " + game.getRound() + " OF " + Game.MAX_ROUNDS) + "\n"
                + "The Forensic Scientist replaces one scene tile with a new one.");
    }

    public static Screen sceneReport(Game game) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_SCIENTIST, "SCENE REPORT",
                "Case " + game.getRoom().code() + ", round " + game.getRound() + " of " + Game.MAX_ROUNDS))
                .append("\n\n");
        text.append(hintLines(game.getHints())).append("\n");
        text.append(note(game.isLastRound()
                ? "Final round. Solve the crime now, or the murderer escapes when the "
                        + "Forensic Scientist closes the case."
                : "Discuss what the answers point to. Each investigator has one attempt to solve the crime."));

        return Screen.text(text.toString(), Keyboard.create()
                .row(button(Theme.ICON_ACCUSE + " Solve the Crime", Callbacks.game(Callbacks.ACCUSE_START, chatId)))
                .row(button(Theme.ICON_ROOMS + " Evidence Board", Callbacks.game(Callbacks.EVIDENCE_BOARD, chatId)),
                        nextRoundButton(game, chatId))
                .build());
    }

    private static InlineKeyboardButton nextRoundButton(Game game, long chatId) {
        return button(game.isLastRound() ? "⏹ Close the Case" : "⏭ Next Round",
                Callbacks.game(Callbacks.NEXT_ROUND, chatId));
    }

    private static String hintLines(List<Hint> hints) {
        StringBuilder lines = new StringBuilder();
        for (Hint hint : hints) {
            lines.append("▪️ ").append(esc(hint.title())).append(": ").append(bold(hint.choice())).append("\n");
        }
        return lines.toString();
    }

    // ---------------------------------------------------------------- Accusation (group)

    public static Screen accuseChooseSuspect(Game game, Player accuser) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_ACCUSE + " " + bold(name(accuser)) + " steps forward to solve the crime.\n"
                + italic("Step 1 of 3") + "\n\n"
                + "Who is the murderer?";

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Player> suspects = game.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            if (suspects.get(i).userId() != accuser.userId()) {
                buttons.add(button(name(suspects.get(i)),
                        Callbacks.game(Callbacks.ACCUSE_SUSPECT, chatId, accuser.userId(), i)));
            }
        }
        return Screen.text(text, Keyboard.create()
                .grid(buttons, CardKeyboardFactory.COLUMNS)
                .row(cancelButton(chatId, accuser))
                .build());
    }

    public static Screen accuseChooseMeans(Game game, Player accuser, int suspectIndex) {
        long chatId = game.getRoom().groupChatId();
        Player suspect = game.getSuspects().get(suspectIndex);
        String text = Theme.ICON_ACCUSE + " " + bold(name(accuser)) + " accuses " + bold(name(suspect)) + ".\n"
                + italic("Step 2 of 3") + "\n\n"
                + Theme.ICON_MEANS + " What was the Means of Murder?";
        String prefix = Callbacks.game(Callbacks.ACCUSE_WEAPON, chatId, accuser.userId(), suspectIndex)
                + Callbacks.SEPARATOR;
        return Screen.text(text, Keyboard.create()
                .grid(CardKeyboardFactory.cardButtons(game.getHands().get(suspect).weapons(), prefix),
                        CardKeyboardFactory.COLUMNS)
                .row(cancelButton(chatId, accuser))
                .build());
    }

    public static Screen accuseChooseKeyEvidence(Game game, Player accuser, int suspectIndex, int weaponIndex) {
        long chatId = game.getRoom().groupChatId();
        Player suspect = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(suspect);
        String text = Theme.ICON_ACCUSE + " " + bold(name(accuser)) + " accuses " + bold(name(suspect)) + ".\n"
                + italic("Step 3 of 3") + "\n\n"
                + Theme.ICON_MEANS + " Means of Murder: " + bold(hand.weapons().get(weaponIndex).name()) + "\n"
                + Theme.ICON_CLUE + " What is the Key Evidence?";
        String prefix = Callbacks.game(Callbacks.ACCUSE_EVIDENCE, chatId, accuser.userId(), suspectIndex, weaponIndex)
                + Callbacks.SEPARATOR;
        return Screen.text(text, Keyboard.create()
                .grid(CardKeyboardFactory.cardButtons(hand.evidence(), prefix), CardKeyboardFactory.COLUMNS)
                .row(cancelButton(chatId, accuser))
                .build());
    }

    private static InlineKeyboardButton cancelButton(long chatId, Player accuser) {
        return button("Cancel", Callbacks.game(Callbacks.ACCUSE_CANCEL, chatId, accuser.userId()));
    }

    public static Screen accusationWithdrawn(Player accuser) {
        return Screen.text(Theme.ICON_ACCUSE + " " + esc(name(accuser))
                + " stepped back. The attempt is still available.");
    }

    private static String accusationLine(Player accuser, Player suspect, Card weapon, Card evidence) {
        return Theme.ICON_ACCUSE + " " + bold(name(accuser)) + " accuses " + bold(name(suspect)) + "\n"
                + Theme.ICON_MEANS + " " + esc(weapon.name()) + "   "
                + Theme.ICON_CLUE + " " + esc(evidence.name());
    }

    public static Screen accusationWrong(Player accuser, Player suspect, Card weapon, Card evidence) {
        return Screen.text(accusationLine(accuser, suspect, weapon, evidence) + "\n\n"
                + Theme.ICON_WRONG + " " + bold("No.") + " The Forensic Scientist shakes their head.\n"
                + esc(name(accuser)) + " has used their attempt. The investigation continues.");
    }

    public static Screen accusationFinal(Player accuser, Player suspect, Card weapon, Card evidence,
                                         boolean correct) {
        return Screen.text(accusationLine(accuser, suspect, weapon, evidence) + "\n\n"
                + (correct
                ? Theme.ICON_OK + " " + bold("Yes.") + " The Forensic Scientist nods."
                : Theme.ICON_WRONG + " " + bold("No.") + " That was the last attempt."));
    }

    // ---------------------------------------------------------------- Verdict (group)

    public static Screen caseSolved(Game game, Player solver) {
        String text = Theme.ICON_OK + " " + bold("CASE " + game.getRoom().code() + " SOLVED") + "\n"
                + italic("The investigators and the Forensic Scientist win") + "\n\n"
                + esc(name(solver)) + " named the murderer and both cards.\n\n"
                + solution(game) + "\n\n"
                + roleReveal(game)
                + practiceNote(game);
        return Screen.withBanner(Banner.CASE_SOLVED, text, afterCaseKeyboard());
    }

    public static Screen murdererEscaped(Game game, String reason) {
        String text = Theme.ICON_MEANS + " " + bold("CASE " + game.getRoom().code() + " UNSOLVED") + "\n"
                + italic("The murderer wins") + "\n\n"
                + esc(reason) + "\n\n"
                + solution(game) + "\n\n"
                + roleReveal(game)
                + practiceNote(game);
        return Screen.withBanner(Banner.CASE_COLD, text, afterCaseKeyboard());
    }

    public static Screen caseAbandoned(Game game, String closedBy) {
        return Screen.text(Theme.ICON_ROOMS + " " + bold("CASE " + game.getRoom().code() + " CLOSED") + "\n\n"
                + esc(closedBy) + " ended the case before it was solved. Nothing is counted.",
                afterCaseKeyboard());
    }

    private static InlineKeyboardMarkup afterCaseKeyboard() {
        return Keyboard.create()
                .row(button(Theme.ICON_CREATE + " New Case in This Group", Callbacks.newRoomHere()))
                .build();
    }

    private static String solution(Game game) {
        return note("Murderer: " + bold(name(game.getMurderer())) + "\n"
                + Theme.ICON_MEANS + " Means of Murder: " + bold(game.getMurderWeapon().name()) + "\n"
                + Theme.ICON_CLUE + " Key Evidence: " + bold(game.getKeyEvidence().name()));
    }

    private static String roleReveal(Game game) {
        String investigators = game.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == Role.INVESTIGATOR)
                .map(entry -> esc(name(entry.getKey())))
                .collect(Collectors.joining(", "));
        return Theme.ICON_SCIENTIST + " Forensic Scientist: " + esc(name(game.getForensicScientist())) + "\n"
                + Theme.ICON_CLUE + " Investigators: " + investigators;
    }

    private static String practiceNote(Game game) {
        return game.isPractice() ? "\n\n" + italic(PRACTICE_NOTE) : "";
    }

    // ---------------------------------------------------------------- Case file (private, "Rejoin Game")

    /** Everything one player may know about their running case, for the private chat. */
    public static Screen caseFile(Game game, long viewerId) {
        Role role = game.getRole(viewerId).orElseThrow();

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, "CASE FILE " + game.getRoom().code(),
                shorten(game.getRoom().groupTitle(), TITLE_LENGTH))).append("\n\n");
        text.append(Theme.ICON_LIVE).append(" Round ").append(game.getRound()).append(" of ")
                .append(Game.MAX_ROUNDS).append("\n");
        text.append(Theme.ICON_SECRET).append(" Your role: ").append(bold(role.getTitle())).append("\n");

        if (role != Role.INVESTIGATOR && game.hasSolution()) {
            text.append(Theme.ICON_MEANS).append(" ").append(bold(game.getMurderWeapon().name()))
                    .append("   ").append(Theme.ICON_CLUE).append(" ").append(bold(game.getKeyEvidence().name()))
                    .append("\n");
        }
        if (role != Role.FORENSIC_SCIENTIST) {
            text.append(Theme.ICON_ACCUSE).append(game.hasAccused(viewerId)
                    ? " Your attempt to solve the crime is used."
                    : " Your attempt to solve the crime is still available.").append("\n");
        }

        text.append("\n").append(bold("Scene report")).append("\n");
        text.append(game.getHints().isEmpty()
                ? italic("No scene tiles answered yet.") + "\n"
                : hintLines(game.getHints()));

        text.append("\n").append(bold("Evidence board")).append("\n");
        for (Map.Entry<Player, Hand> entry : game.getHands().entrySet()) {
            text.append(esc(name(entry.getKey())));
            if (entry.getKey().userId() == viewerId) {
                text.append(" ").append(italic("you"));
            }
            text.append("\n")
                    .append(Theme.ICON_MEANS).append(" ").append(cardNames(entry.getValue().weapons())).append("\n")
                    .append(Theme.ICON_CLUE).append(" ").append(cardNames(entry.getValue().evidence())).append("\n");
        }
        text.append("\n").append(italic("The case continues in the group chat."));

        return Screen.text(text.toString(), Keyboard.create()
                .row(button("↻ Refresh", Callbacks.caseFile(game.getRoom().code())))
                .row(button(Theme.ICON_HOME + " Home", Callbacks.HOME),
                        button(Theme.ICON_ROOMS + " Rooms", Callbacks.ROOMS))
                .build());
    }

    private static String name(Player player) {
        return shorten(player.name(), NAME_LENGTH);
    }
}
