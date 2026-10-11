package de.winona.telegrambot.ui;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.model.CardType;
import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Hand;
import de.winona.telegrambot.model.Hint;
import de.winona.telegrambot.model.HintTile;
import de.winona.telegrambot.model.LabResult;
import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.model.RoomEnd;
import de.winona.telegrambot.model.TileSubject;
import de.winona.telegrambot.util.CardKeyboardFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static de.winona.telegrambot.ui.Keyboard.button;
import static de.winona.telegrambot.ui.Keyboard.link;
import static de.winona.telegrambot.ui.Theme.bold;
import static de.winona.telegrambot.ui.Theme.code;
import static de.winona.telegrambot.ui.Theme.esc;
import static de.winona.telegrambot.ui.Theme.heading;
import static de.winona.telegrambot.ui.Theme.italic;
import static de.winona.telegrambot.ui.Theme.note;

/**
 * The screens of a room and a running case: the room card in the group, the private role
 * messages, the scene report, the lab, the accusation and the verdict.
 * The first parameter is always the {@link Texts} of the language to write in.
 */
public final class GameScreens
{

    private static final int NAME_LENGTH = 22;
    private static final int TITLE_LENGTH = 32;

    private GameScreens() {
    }

    // ---------------------------------------------------------------- Room card (group)

    public static Screen lobbyCard(Texts t, Lobby lobby, String botUsername) {
        RoomSummary room = RoomSummary.of(lobby);
        String roomCode = room.code();

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, t.get("room.title", roomCode),
                t.plainName(lobby.getRoom().groupTitle(), TITLE_LENGTH))).append("\n\n");
        text.append(Theme.ICON_WAITING).append(" ").append(t.get("room.status.waiting")).append("\n");
        text.append(Theme.seats(lobby.getPlayers().size(), Lobby.MAX_PLAYERS)).append("  ")
                .append(t.get("room.seats", lobby.getPlayers().size(), Lobby.MAX_PLAYERS)).append("\n\n");
        text.append(MenuScreens.playerList(t, lobby.getPlayers(), room)).append("\n");
        text.append(note(lobby.hasEnoughPlayers()
                ? t.get("lobby.ready", esc(name(t, lobby.getRoom().host())))
                : t.plural("room.note.missing", lobby.missingPlayers())));
        text.append("\n").append(t.get("lobby.code", code(roomCode)));

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_JOIN + " " + t.get("btn.join_room"), Callbacks.joinRoom(roomCode)),
                        button(t.get("btn.leave_room"), Callbacks.leaveRoom(roomCode)))
                .row(button("▶️ " + t.get("btn.open_case"), Callbacks.startRoom(roomCode)));
        if (botUsername != null && !botUsername.isBlank()) {
            keyboard.row(link(Theme.ICON_HOW_TO + " " + t.get("btn.how_to_play"),
                            "https://t.me/" + botUsername + "?start=how"),
                    button(t.get("btn.close_room"), Callbacks.closeRoom(roomCode)));
        } else {
            keyboard.row(button(t.get("btn.close_room"), Callbacks.closeRoom(roomCode)));
        }
        return t.text(text.toString(), keyboard.build());
    }

    /** Replaces the room card when the room can no longer be joined. */
    public static Screen roomEnded(Texts t, String roomCode, RoomEnd reason) {
        return switch (reason) {
            case CLOSED_BY_HOST -> t.text(Theme.ICON_ROOMS + " " + bold(t.get("ended.closed.title", roomCode))
                    + "\n\n" + t.get("ended.closed.body"), newRoomKeyboard(t));
            case EXPIRED -> t.text(Theme.ICON_ROOMS + " " + bold(t.get("ended.expired.title", roomCode))
                    + "\n\n" + t.get("ended.expired.body"), newRoomKeyboard(t));
            case CASE_STARTED -> t.text(Theme.ICON_LIVE + " " + bold(t.get("ended.started.title", roomCode))
                    + "\n\n" + t.get("ended.started.body"));
        };
    }

    private static InlineKeyboardMarkup newRoomKeyboard(Texts t) {
        return Keyboard.create()
                .row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.newRoomHere()))
                .build();
    }

    /** Shown in a group that has neither a room nor a case. */
    public static Screen noRoomHere(Texts t) {
        return t.text(Theme.ICON_ROOMS + " " + t.get("group.no_room"), newRoomKeyboard(t));
    }

    /** One line, for example a problem with a command. */
    public static Screen notice(Texts t, String icon, Msg message) {
        return t.text(icon + " " + esc(t.get(message)));
    }

    /** Private confirmation after taking a seat. */
    public static Screen seatTaken(Texts t, Lobby lobby) {
        Room room = lobby.getRoom();
        String text = Theme.ICON_OK + " " + bold(t.get("seat.title", room.code())) + "\n"
                + italic(t.plainName(room.groupTitle(), TITLE_LENGTH)) + "\n\n"
                + t.get("seat.body");
        return t.text(text, Keyboard.create()
                .row(button(t.get("btn.enter_room_code", room.code()), Callbacks.roomDetails(room.code())),
                        button(Theme.ICON_HOW_TO + " " + t.get("btn.how_to_play"), Callbacks.howToPlay(1)))
                .build());
    }

    /** Asks a player in the group to open the private chat, which also seats them. */
    public static Screen needsPrivateChat(Texts t, String firstName, String deepLink) {
        String text = Theme.ICON_SECRET + " "
                + t.get("group.needs_private", bold(t.plainName(firstName, NAME_LENGTH)));
        if (deepLink == null) {
            return t.text(text);
        }
        return t.text(text, Keyboard.create()
                .row(link(t.get("btn.open_private_chat"), deepLink))
                .build());
    }

    /** The short rules for the group, with a link to the full guide in the private chat. */
    public static Screen groupHelp(Texts t, String deepLink) {
        String text = heading(Theme.ICON_HOW_TO, t.get("how.title"), t.get("group.help.sub")) + "\n\n"
                + t.get("group.help.body");
        if (deepLink == null) {
            return t.text(text);
        }
        return t.text(text, Keyboard.create()
                .row(link(Theme.ICON_HOW_TO + " " + t.get("btn.full_guide"), deepLink))
                .build());
    }

    // ---------------------------------------------------------------- Case opening

    public static Screen caseOpened(Texts t, Game game) {
        String suspects = game.getSuspects().stream()
                .map(player -> esc(name(t, player)))
                .collect(Collectors.joining(t.get("common.list_separator")));

        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_LIVE).append(" ").append(bold(t.get("case.open.title", game.getRoom().code())))
                .append("\n").append(italic(t.get("case.round", 1, Game.MAX_ROUNDS))).append("\n\n");
        text.append(t.get("case.open.dealt")).append(" ").append(Theme.ICON_SECRET).append("\n\n");
        text.append(Theme.ICON_SCIENTIST).append(" ")
                .append(t.get("case.open.scientist", bold(name(t, game.getForensicScientist())))).append("\n");
        text.append(t.get("case.open.scientist_note")).append("\n\n");
        text.append(t.get("case.open.suspects", suspects)).append("\n\n");
        text.append(italic(t.get("case.open.choosing")));
        if (game.isPractice()) {
            text.append("\n\n").append(note(t.get("case.practice")));
        }
        return t.banner(Banner.CASE_OPEN, text.toString(), null);
    }

    public static Screen evidenceBoard(Texts t, Game game) {
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, t.get("board.title"), t.get("board.sub", game.getRoom().code())))
                .append("\n");

        game.getHands().forEach((player, hand) -> {
            text.append("\n").append(bold(name(t, player))).append("\n");
            text.append(Theme.ICON_MEANS).append(" ").append(cardNames(t, game, hand.weapons())).append("\n");
            text.append(Theme.ICON_CLUE).append(" ").append(cardNames(t, game, hand.evidence())).append("\n");
        });
        text.append("\n").append(italic(t.get("board.rule")));
        if (!game.getLabResults().isEmpty()) {
            text.append("\n").append(Theme.ICON_TRACE).append(" ").append(t.get("board.legend.trace"))
                    .append("   ").append(Theme.struck(t.get("board.legend.struck"))).append(" ")
                    .append(t.get("board.legend.cleared"));
        }
        return t.text(text.toString());
    }

    /** Card names with the lab's findings: cleared cards are crossed out, cards with a trace stand out. */
    private static String cardNames(Texts t, Game game, List<Card> cards) {
        return cards.stream()
                .map(card -> game.findLabResult(card)
                        .map(result -> result.trace()
                                ? Theme.ICON_TRACE + bold(t.card(card))
                                : Theme.struck(t.card(card)))
                        .orElse(esc(t.card(card))))
                .collect(Collectors.joining(t.get("common.list_separator")));
    }

    /** The private message that tells a player their role. */
    public static Screen roleBriefing(Texts t, Game game, Role role) {
        String roleKey = role.name().toLowerCase(Locale.ROOT);
        String text = Theme.ICON_SECRET + " "
                + bold(t.get("brief.title", t.role(role).toUpperCase(Locale.ROOT))) + "\n"
                + italic(t.get("brief.sub", game.getRoom().code(),
                        t.plainName(game.getRoom().groupTitle(), TITLE_LENGTH))) + "\n\n"
                + esc(t.roleDescription(role)) + "\n\n"
                + note(t.get("brief.next." + roleKey));
        return t.text(text);
    }

    // ---------------------------------------------------------------- Murderer (private)

    public static Screen chooseMeans(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_MEANS + " " + bold(t.get("murder.means.title")) + "\n"
                + italic(t.get("common.step", 1, 5)) + "\n\n"
                + t.get("murder.means.body");
        return t.text(text, CardKeyboardFactory.createCardKeyboard(t,
                game.getMurdererHand().weapons(), Callbacks.game(Callbacks.WEAPON, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen chooseKeyEvidence(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_CLUE + " " + bold(t.get("murder.clue.title")) + "\n"
                + italic(t.get("common.step", 2, 5)) + "\n\n"
                + t.get("term.means_label", bold(t.card(game.getMurderWeapon()))) + "\n\n"
                + t.get("murder.clue.body");
        return t.text(text, CardKeyboardFactory.createCardKeyboard(t,
                game.getMurdererHand().evidence(), Callbacks.game(Callbacks.EVIDENCE, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen chooseScapegoat(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_HERRING + " " + bold(t.get("frame.title")) + "\n"
                + italic(t.get("common.step", 3, 5)) + "\n\n"
                + t.get("frame.who.cards", bold(t.card(game.getMurderWeapon())),
                        bold(t.card(game.getKeyEvidence()))) + "\n\n"
                + t.get("frame.who.body");

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Player> others = game.getPossibleScapegoats();
        for (int i = 0; i < others.size(); i++) {
            buttons.add(button(name(t, others.get(i)), Callbacks.game(Callbacks.FRAME_PLAYER, chatId, i)));
        }
        return t.text(text, Keyboard.create().grid(buttons, CardKeyboardFactory.COLUMNS).build());
    }

    public static Screen choosePlantedMeans(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_HERRING + " " + bold(t.get("frame.title")) + "\n"
                + italic(t.get("common.step", 4, 5)) + "\n\n"
                + t.get("frame.means.intro", bold(name(t, game.getScapegoat())),
                        bold(t.card(game.getMurderWeapon()))) + "\n\n"
                + t.get("frame.means.ask") + " " + italic(t.get("frame.means.tip"));
        return t.text(text, CardKeyboardFactory.createCardKeyboard(t,
                game.getScapegoatHand().weapons(), Callbacks.game(Callbacks.FRAME_MEANS, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen choosePlantedClue(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_HERRING + " " + bold(t.get("frame.title")) + "\n"
                + italic(t.get("common.step", 5, 5)) + "\n\n"
                + t.get("frame.clue.intro", bold(t.card(game.getPlantedMeans())),
                        bold(t.card(game.getKeyEvidence()))) + "\n\n"
                + t.get("frame.clue.ask");
        return t.text(text, CardKeyboardFactory.createCardKeyboard(t,
                game.getScapegoatHand().evidence(), Callbacks.game(Callbacks.FRAME_CLUE, chatId) + Callbacks.SEPARATOR));
    }

    public static Screen crimeSealed(Texts t, Game game) {
        String scapegoat = name(t, game.getScapegoat());
        String text = Theme.ICON_OK + " " + bold(t.get("sealed.title")) + "\n\n"
                + bold(t.get("sealed.crime")) + "\n"
                + Theme.ICON_MEANS + " " + esc(t.card(game.getMurderWeapon())) + "   "
                + Theme.ICON_CLUE + " " + esc(t.card(game.getKeyEvidence())) + "\n\n"
                + bold(t.get("sealed.herring", scapegoat)) + "\n"
                + Theme.ICON_MEANS + " " + esc(t.card(game.getPlantedMeans())) + "   "
                + Theme.ICON_CLUE + " " + esc(t.card(game.getPlantedClue())) + "\n\n"
                + note(t.get("sealed.note", esc(scapegoat)));
        return t.text(text);
    }

    public static Screen crimeCommitted(Texts t) {
        return t.text(Theme.ICON_MEANS + " " + bold(t.get("crime.committed.title")) + "\n"
                + t.get("crime.committed.body"));
    }

    // ---------------------------------------------------------------- Forensic Scientist (private)

    public static Screen solutionForScientist(Texts t, Game game) {
        String text = Theme.ICON_SCIENTIST + " " + bold(t.get("sci.solution.title")) + "\n"
                + italic(t.get("sci.solution.sub")) + "\n\n"
                + t.get("term.murderer_label", bold(name(t, game.getMurderer()))) + "\n"
                + Theme.ICON_MEANS + " " + t.get("term.means_label", bold(t.card(game.getMurderWeapon()))) + "\n"
                + Theme.ICON_CLUE + " " + t.get("term.evidence_label", bold(t.card(game.getKeyEvidence()))) + "\n\n"
                + note(t.get("sci.note") + "\n" + Theme.ICON_HERRING + " " + t.get("sci.herring_note"));
        return t.text(text);
    }

    /** The scene tile the Forensic Scientist answers next, with the answers given so far. */
    public static Screen sceneTile(Texts t, Game game, HintTile tile) {
        long chatId = game.getRoom().groupChatId();
        int number = game.getCurrentHintIndex() + 1;

        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_SCIENTIST).append(" ")
                .append(bold(t.get("tile.heading", number, game.getTileCount()))).append("\n\n");
        text.append(hintLines(t, game.getHints()));
        if (!game.getHints().isEmpty()) {
            text.append("\n");
        }
        text.append(tileQuestion(t, game, tile));

        String prefix = Callbacks.game(Callbacks.HINT, chatId, game.getCurrentHintIndex()) + Callbacks.SEPARATOR;
        return t.text(text.toString(), CardKeyboardFactory.createOptionKeyboard(t, tile.options(), prefix));
    }

    public static Screen tilesPlaced(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_OK).append(" ").append(bold(t.get("placed.title", game.getRound())))
                .append("\n\n");
        text.append(hintLines(t, game.getHints())).append("\n");
        text.append(note(t.get(game.isLastRound() ? "placed.final" : "placed.next")));

        return t.text(text.toString(), Keyboard.create()
                .row(nextRoundButton(t, game, chatId))
                .build());
    }

    /** Start of a new round: the Forensic Scientist picks one of the offered tiles. */
    public static Screen chooseNewTile(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_ROUND).append(" ")
                .append(bold(t.get("round.title", game.getRound(), Game.MAX_ROUNDS)))
                .append("\n").append(italic(t.get("newtile.sub"))).append("\n\n");
        text.append(hintLines(t, game.getHints())).append("\n");
        text.append(t.get("newtile.ask")).append(" ").append(italic(t.get("newtile.tip")));

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<HintTile> offered = game.getOfferedTiles();
        for (int i = 0; i < offered.size(); i++) {
            buttons.add(button(subjectIcon(offered.get(i).subject()) + " " + t.tile(offered.get(i).title()),
                    Callbacks.game(Callbacks.OFFERED_TILE, chatId, game.getRound(), i)));
        }
        return t.text(text.toString(), Keyboard.create().grid(buttons, 1).build());
    }

    public static Screen chooseTileToReplace(Texts t, Game game, HintTile newTile)
    {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(Theme.ICON_ROUND).append(" ")
                .append(bold(t.get("round.title", game.getRound(), Game.MAX_ROUNDS)))
                .append("\n").append(italic(t.get("replace.sub"))).append("\n\n");
        text.append(t.get("replace.new", subjectIcon(newTile.subject()) + " " + bold(t.tile(newTile.title()))))
                .append("\n\n");
        text.append(t.get("replace.ask")).append(" ").append(italic(t.get("replace.fixed")));

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Hint> hints = game.getHints();
        for (int i = Game.FIXED_TILES; i < hints.size(); i++) {
            buttons.add(button(subjectIcon(hints.get(i).subject()) + " " + t.tile(hints.get(i).title())
                            + ": " + answerText(t, hints.get(i)),
                    Callbacks.game(Callbacks.REPLACE_SLOT, chatId, game.getRound(), i)));
        }
        return t.text(text.toString(), Keyboard.create().grid(buttons, 1).build());
    }

    public static Screen replacementTile(Texts t, Game game, HintTile tile)
    {
        String text = Theme.ICON_SCIENTIST + " " + bold(t.get("newtile.title")) + "\n\n"
                + tileQuestion(t, game, tile);
        return t.text(text);
    }

    public static Screen caseClosedByScientist(Texts t) {
        return t.text(Theme.ICON_OK + " " + t.get("placed.closed"));
    }

    // ---------------------------------------------------------------- Investigation (group)

    public static Screen tileAnswered(Texts t, Game game, Hint hint) {
        return t.text(Theme.ICON_SCIENTIST + " " + t.get("report.tile_answered", game.getHints().size(), game.getTileCount())
                + "\n" + hintLine(t, hint));
    }

    public static Screen roundStarted(Texts t, Game game) {
        return t.text(Theme.ICON_ROUND + " " + bold(t.get("round.title", game.getRound(), Game.MAX_ROUNDS)) + "\n"
                + t.get("round.body"));
    }

    public static Screen sceneReport(Texts t, Game game) {
        long chatId = game.getRoom().groupChatId();
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_SCIENTIST, t.get("report.title"),
                t.get("report.sub", game.getRoom().code(), game.getRound(), Game.MAX_ROUNDS)))
                .append("\n\n");
        text.append(hintLines(t, game.getHints())).append("\n");
        if (!game.getLabResults().isEmpty()) {
            text.append(bold(t.get("report.lab"))).append("\n")
                    .append(labLines(t, game.getLabResults())).append("\n");
        }
        text.append(note(game.isLastRound()
                ? t.get("report.final")
                : Theme.ICON_LAB + " " + t.get("report.lab_open") + "\n"
                        + Theme.ICON_HERRING + " " + t.get("report.herring")));

        return t.text(text.toString(), Keyboard.create()
                .row(button(Theme.ICON_LAB + " " + t.get("btn.lab_test"), Callbacks.game(Callbacks.LAB_START, chatId)),
                        button(Theme.ICON_ACCUSE + " " + t.get("btn.solve"),
                                Callbacks.game(Callbacks.ACCUSE_START, chatId)))
                .row(button(Theme.ICON_ROOMS + " " + t.get("btn.evidence_board"),
                                Callbacks.game(Callbacks.EVIDENCE_BOARD, chatId)),
                        nextRoundButton(t, game, chatId))
                .build());
    }

    private static InlineKeyboardButton nextRoundButton(Texts t, Game game, long chatId) {
        return button(game.isLastRound() ? "⏹ " + t.get("btn.close_case") : "⏭ " + t.get("btn.next_round"),
                Callbacks.game(Callbacks.NEXT_ROUND, chatId));
    }

    /** Every answer starts with the icon of the card type it describes. */
    private static String hintLines(Texts t, List<Hint> hints) {
        StringBuilder lines = new StringBuilder();
        for (Hint hint : hints) {
            lines.append(hintLine(t, hint)).append("\n");
        }
        return lines.toString();
    }

    private static String hintLine(Texts t, Hint hint)
    {
        return subjectIcon(hint.subject()) + " " + esc(t.tile(hint.title())) + ": " + bold(answerText(t, hint));
    }

    /** A typed answer exactly as written, a chosen option in the reader's language. Not escaped. */
    private static String answerText(Texts t, Hint hint) {
        return hint.typed()
                ? t.plainName(hint.choice(), Game.MAX_ANSWER_LENGTH)
                : t.option(hint.choice());
    }

    private static String subjectIcon(TileSubject subject) {
        return switch (subject) {
            case MEANS -> Theme.ICON_MEANS;
            case KEY_EVIDENCE -> Theme.ICON_CLUE;
            case MURDERER -> Theme.ICON_PROFILE;
        };
    }

    private static String cardIcon(Card card) {
        return card.type() == CardType.WEAPON ? Theme.ICON_MEANS : Theme.ICON_CLUE;
    }

    private static String tileQuestion(Texts t, Game game, HintTile tile)
    {
        String describes = switch (tile.subject()) {
            case MEANS -> t.get("tile.describes.means", bold(t.card(game.getMurderWeapon())));
            case KEY_EVIDENCE -> t.get("tile.describes.evidence", bold(t.card(game.getKeyEvidence())));
            case MURDERER -> t.get("tile.describes.murderer", bold(name(t, game.getMurderer())));
        };
        return subjectIcon(tile.subject()) + " " + bold(t.tile(tile.title())) + "\n"
                + describes + "\n"
                + t.get("tile.type", Game.MAX_ANSWER_LENGTH) + "\n"
                + italic(t.get("tile.examples", examples(t, tile)));
    }

    /** The tile's options as ideas for a typed answer. */
    private static String examples(Texts t, HintTile tile) {
        return tile.options().stream().map(t::option).collect(Collectors.joining(" · "));
    }


    private static String labLines(Texts t, List<LabResult> results) {
        StringBuilder lines = new StringBuilder();
        for (LabResult result : results) {
            String card = result.trace()
                    ? Theme.ICON_TRACE + " " + bold(t.card(result.card()))
                    : Theme.ICON_CLEAN + " " + Theme.struck(t.card(result.card()));
            lines.append(t.get("lab.line", card, esc(name(t, result.owner())), result.round(),
                    esc(name(t, result.tester())))).append("\n");
        }
        return lines.toString();
    }

    // ---------------------------------------------------------------- Forensic lab (group)

    public static Screen labChooseSuspect(Texts t, Game game, Player tester) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_LAB + " " + t.get("lab.who.title", bold(name(t, tester))) + "\n"
                + italic(t.get("lab.step", game.getRound(), 1, 2)) + "\n\n"
                + t.get("lab.who.ask");

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Player> suspects = game.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            buttons.add(button(name(t, suspects.get(i)),
                    Callbacks.game(Callbacks.LAB_SUSPECT, chatId, tester.userId(), i)));
        }
        return t.text(text, Keyboard.create()
                .grid(buttons, CardKeyboardFactory.COLUMNS)
                .row(button(t.get("btn.cancel"), Callbacks.game(Callbacks.LAB_CANCEL, chatId, tester.userId())))
                .build());
    }

    /** All eight cards of one suspect. Cards the lab already knows show their result. */
    public static Screen labChooseCard(Texts t, Game game, Player tester, int suspectIndex) {
        long chatId = game.getRoom().groupChatId();
        Player owner = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(owner);
        String text = Theme.ICON_LAB + " "
                + t.get("lab.card.title", bold(name(t, tester)), bold(name(t, owner))) + "\n"
                + italic(t.get("lab.step", game.getRound(), 2, 2)) + "\n\n"
                + t.get("lab.card.ask");

        List<Card> cards = new ArrayList<>(hand.weapons());
        cards.addAll(hand.evidence());
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            String mark = game.findLabResult(card)
                    .map(result -> result.trace() ? " " + Theme.ICON_TRACE : " ✓")
                    .orElse("");
            buttons.add(button(cardIcon(card) + " " + t.card(card) + mark,
                    Callbacks.game(Callbacks.LAB_CARD, chatId, tester.userId(), suspectIndex, i)));
        }
        return t.text(text, Keyboard.create()
                .grid(buttons, CardKeyboardFactory.COLUMNS)
                .row(button(t.get("btn.cancel"), Callbacks.game(Callbacks.LAB_CANCEL, chatId, tester.userId())))
                .build());
    }

    public static Screen labResult(Texts t, Game game, LabResult result) {
        String finding = result.trace()
                ? Theme.ICON_TRACE + " " + bold(t.get("lab.trace")) + "\n" + t.get("lab.trace.body")
                : Theme.ICON_CLEAN + " " + bold(t.get("lab.clean")) + "\n" + t.get("lab.clean.body");
        return t.text(Theme.ICON_LAB + " " + bold(t.get("lab.result.title"))
                + italic("  " + t.get("lab.result.round", result.round())) + "\n"
                + t.get("lab.result.tested", esc(name(t, result.tester())),
                        cardIcon(result.card()) + " " + bold(t.card(result.card())),
                        bold(name(t, result.owner()))) + "\n\n"
                + finding + "\n\n"
                + italic(t.get("lab.used", game.getTestsUsedThisRound(), game.getTestsPerRound())));
    }

    public static Screen labCancelled(Texts t, Player tester) {
        return t.text(Theme.ICON_LAB + " " + t.get("lab.cancelled", esc(name(t, tester))));
    }

    // ---------------------------------------------------------------- Accusation (group)

    public static Screen accuseChooseSuspect(Texts t, Game game, Player accuser) {
        long chatId = game.getRoom().groupChatId();
        String text = Theme.ICON_ACCUSE + " " + t.get("accuse.who.title", bold(name(t, accuser))) + "\n"
                + italic(t.get("common.step", 1, 3)) + "\n\n"
                + t.get("accuse.who.ask");

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        List<Player> suspects = game.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            if (suspects.get(i).userId() != accuser.userId()) {
                buttons.add(button(name(t, suspects.get(i)),
                        Callbacks.game(Callbacks.ACCUSE_SUSPECT, chatId, accuser.userId(), i)));
            }
        }
        return t.text(text, Keyboard.create()
                .grid(buttons, CardKeyboardFactory.COLUMNS)
                .row(cancelButton(t, chatId, accuser))
                .build());
    }

    public static Screen accuseChooseMeans(Texts t, Game game, Player accuser, int suspectIndex) {
        long chatId = game.getRoom().groupChatId();
        Player suspect = game.getSuspects().get(suspectIndex);
        String text = Theme.ICON_ACCUSE + " "
                + t.get("accuse.line", bold(name(t, accuser)), bold(name(t, suspect))) + "\n"
                + italic(t.get("common.step", 2, 3)) + "\n\n"
                + Theme.ICON_MEANS + " " + t.get("accuse.means.ask");
        String prefix = Callbacks.game(Callbacks.ACCUSE_WEAPON, chatId, accuser.userId(), suspectIndex)
                + Callbacks.SEPARATOR;
        return t.text(text, Keyboard.create()
                .grid(CardKeyboardFactory.cardButtons(t, game.getHands().get(suspect).weapons(), prefix),
                        CardKeyboardFactory.COLUMNS)
                .row(cancelButton(t, chatId, accuser))
                .build());
    }

    public static Screen accuseChooseKeyEvidence(Texts t, Game game, Player accuser, int suspectIndex,
                                                 int weaponIndex) {
        long chatId = game.getRoom().groupChatId();
        Player suspect = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(suspect);
        String text = Theme.ICON_ACCUSE + " "
                + t.get("accuse.line", bold(name(t, accuser)), bold(name(t, suspect))) + "\n"
                + italic(t.get("common.step", 3, 3)) + "\n\n"
                + Theme.ICON_MEANS + " " + t.get("term.means_label", bold(t.card(hand.weapons().get(weaponIndex))))
                + "\n"
                + Theme.ICON_CLUE + " " + t.get("accuse.clue.ask");
        String prefix = Callbacks.game(Callbacks.ACCUSE_EVIDENCE, chatId, accuser.userId(), suspectIndex, weaponIndex)
                + Callbacks.SEPARATOR;
        return t.text(text, Keyboard.create()
                .grid(CardKeyboardFactory.cardButtons(t, hand.evidence(), prefix), CardKeyboardFactory.COLUMNS)
                .row(cancelButton(t, chatId, accuser))
                .build());
    }

    private static InlineKeyboardButton cancelButton(Texts t, long chatId, Player accuser) {
        return button(t.get("btn.cancel"), Callbacks.game(Callbacks.ACCUSE_CANCEL, chatId, accuser.userId()));
    }

    public static Screen accusationWithdrawn(Texts t, Player accuser) {
        return t.text(Theme.ICON_ACCUSE + " " + t.get("accuse.withdrawn", esc(name(t, accuser))));
    }

    private static String accusationLine(Texts t, Player accuser, Player suspect, Card weapon, Card evidence) {
        return Theme.ICON_ACCUSE + " "
                + t.get("accuse.summary", bold(name(t, accuser)), bold(name(t, suspect))) + "\n"
                + Theme.ICON_MEANS + " " + esc(t.card(weapon)) + "   "
                + Theme.ICON_CLUE + " " + esc(t.card(evidence));
    }

    public static Screen accusationWrong(Texts t, Player accuser, Player suspect, Card weapon, Card evidence) {
        return t.text(accusationLine(t, accuser, suspect, weapon, evidence) + "\n\n"
                + Theme.ICON_WRONG + " " + t.get("accuse.wrong") + "\n"
                + t.get("accuse.wrong.used", esc(name(t, accuser))));
    }

    public static Screen accusationFinal(Texts t, Player accuser, Player suspect, Card weapon, Card evidence,
                                         boolean correct) {
        return t.text(accusationLine(t, accuser, suspect, weapon, evidence) + "\n\n"
                + (correct
                ? Theme.ICON_OK + " " + t.get("accuse.right")
                : Theme.ICON_WRONG + " " + t.get("accuse.last")));
    }

    public static Screen accusationOfScapegoat(Texts t, Player accuser, Player suspect, Card weapon,
                                               Card evidence) {
        return t.text(accusationLine(t, accuser, suspect, weapon, evidence) + "\n\n"
                + Theme.ICON_HERRING + " " + t.get("accuse.herring"));
    }

    // ---------------------------------------------------------------- Verdict (group)

    public static Screen caseSolved(Texts t, Game game, Player solver) {
        String text = Theme.ICON_OK + " " + bold(t.get("verdict.solved.title", game.getRoom().code())) + "\n"
                + italic(t.get("verdict.solved.sub")) + "\n\n"
                + t.get("verdict.solved.by", esc(name(t, solver))) + "\n\n"
                + solution(t, game) + "\n\n"
                + roleReveal(t, game)
                + practiceNote(t, game);
        return t.banner(Banner.CASE_SOLVED, text, afterCaseKeyboard(t));
    }

    /** @param reason why the case stays unsolved; names in it must not be escaped yet */
    public static Screen murdererEscaped(Texts t, Game game, Msg reason) {
        String text = Theme.ICON_MEANS + " " + bold(t.get("verdict.cold.title", game.getRoom().code())) + "\n"
                + italic(t.get("verdict.cold.sub")) + "\n\n"
                + esc(t.get(reason)) + "\n\n"
                + solution(t, game) + "\n\n"
                + roleReveal(t, game)
                + practiceNote(t, game);
        return t.banner(Banner.CASE_COLD, text, afterCaseKeyboard(t));
    }

    public static Screen caseAbandoned(Texts t, Game game, String closedBy) {
        return t.text(Theme.ICON_ROOMS + " " + bold(t.get("verdict.abandoned.title", game.getRoom().code()))
                        + "\n\n" + t.get("verdict.abandoned.body", t.name(closedBy, NAME_LENGTH)),
                afterCaseKeyboard(t));
    }

    private static InlineKeyboardMarkup afterCaseKeyboard(Texts t) {
        return Keyboard.create()
                .row(button(Theme.ICON_CREATE + " " + t.get("btn.new_case"), Callbacks.newRoomHere()))
                .build();
    }

    private static String solution(Texts t, Game game) {
        return note(t.get("term.murderer_label", bold(name(t, game.getMurderer()))) + "\n"
                + Theme.ICON_MEANS + " " + t.get("term.means_label", bold(t.card(game.getMurderWeapon()))) + "\n"
                + Theme.ICON_CLUE + " " + t.get("term.evidence_label", bold(t.card(game.getKeyEvidence()))) + "\n"
                + Theme.ICON_HERRING + " " + t.get("verdict.herring", bold(name(t, game.getScapegoat())),
                        esc(t.card(game.getPlantedMeans())), esc(t.card(game.getPlantedClue()))));
    }

    private static String roleReveal(Texts t, Game game) {
        String investigators = game.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == Role.INVESTIGATOR)
                .map(entry -> esc(name(t, entry.getKey())))
                .collect(Collectors.joining(t.get("common.list_separator")));
        return Theme.ICON_SCIENTIST + " "
                + t.get("verdict.scientist", esc(name(t, game.getForensicScientist()))) + "\n"
                + Theme.ICON_CLUE + " " + t.get("verdict.investigators", investigators);
    }

    private static String practiceNote(Texts t, Game game) {
        return game.isPractice() ? "\n\n" + italic(t.get("case.practice")) : "";
    }

    // ---------------------------------------------------------------- Case file (private, "Rejoin Game")

    /** Everything one player may know about their running case, for the private chat. */
    public static Screen caseFile(Texts t, Game game, long viewerId) {
        Role role = game.getRole(viewerId).orElseThrow();

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, t.get("file.title", game.getRoom().code()),
                t.plainName(game.getRoom().groupTitle(), TITLE_LENGTH))).append("\n\n");
        text.append(Theme.ICON_LIVE).append(" ").append(t.get("case.round", game.getRound(), Game.MAX_ROUNDS))
                .append("\n");
        text.append(Theme.ICON_SECRET).append(" ").append(t.get("file.role", bold(t.role(role)))).append("\n");

        if (role != Role.INVESTIGATOR && game.hasSolution()) {
            text.append(Theme.ICON_MEANS).append(" ").append(bold(t.card(game.getMurderWeapon())))
                    .append("   ").append(Theme.ICON_CLUE).append(" ").append(bold(t.card(game.getKeyEvidence())))
                    .append("\n");
        }
        if (role == Role.MURDERER && game.isCrimeSealed()) {
            text.append(Theme.ICON_HERRING).append(" ").append(t.get("file.planted",
                    esc(name(t, game.getScapegoat())), esc(t.card(game.getPlantedMeans())),
                    esc(t.card(game.getPlantedClue())))).append("\n");
        }
        if (role != Role.FORENSIC_SCIENTIST) {
            text.append(Theme.ICON_ACCUSE).append(" ")
                    .append(t.get(game.hasAccused(viewerId) ? "file.attempt.used" : "file.attempt.open"))
                    .append("\n");
            text.append(Theme.ICON_LAB).append(" ")
                    .append(t.get(game.canTest(viewerId) ? "file.lab.yes" : "file.lab.no"))
                    .append("\n");
        }

        text.append("\n").append(bold(t.get("file.report"))).append("\n");
        text.append(game.getHints().isEmpty()
                ? italic(t.get("file.no_tiles")) + "\n"
                : hintLines(t, game.getHints()));

        text.append("\n").append(bold(t.get("file.board"))).append("\n");
        for (Map.Entry<Player, Hand> entry : game.getHands().entrySet()) {
            text.append(esc(name(t, entry.getKey())));
            if (entry.getKey().userId() == viewerId) {
                text.append(" ").append(italic(t.get("file.you")));
            }
            text.append("\n")
                    .append(Theme.ICON_MEANS).append(" ").append(cardNames(t, game, entry.getValue().weapons()))
                    .append("\n")
                    .append(Theme.ICON_CLUE).append(" ").append(cardNames(t, game, entry.getValue().evidence()))
                    .append("\n");
        }
        text.append("\n").append(italic(t.get("file.continues")));

        return t.text(text.toString(), Keyboard.create()
                .row(button("↻ " + t.get("btn.refresh"), Callbacks.caseFile(game.getRoom().code())))
                .row(button(Theme.ICON_HOME + " " + t.get("nav.home"), Callbacks.HOME),
                        button(Theme.ICON_ROOMS + " " + t.get("nav.rooms"), Callbacks.ROOMS))
                .build());
    }

    /** A player's name, shortened but not escaped: wrap it in bold(), italic() or esc(). */
    private static String name(Texts t, Player player) {
        return t.plainName(player.name(), NAME_LENGTH);
    }
}
