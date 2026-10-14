package de.winona.telegrambot.view;

import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.PlayerStats;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import static de.winona.telegrambot.view.Keyboard.button;
import static de.winona.telegrambot.view.Keyboard.link;
import static de.winona.telegrambot.view.Theme.*;

/**
 * The screens of the private dashboard. Every method only turns data into a {@link Screen};
 * nothing here talks to Telegram or changes the game. The first parameter is always the
 * {@link Texts} of the reader's language, and every word comes from there.
 */
public final class MenuScreens {

    public static final int HOW_TO_PLAY_PAGES = 5;
    public static final int ROLE_PAGES = 5;
    public static final int LEADERBOARD_SIZE = 10;

    private static final int MAX_ROOMS_SHOWN = 4;
    private static final int MAX_NAME_LENGTH = 22;
    private static final int MAX_GROUP_TITLE_LENGTH = 32;
    private static final String[] ROLE_KEYS = {"scientist", "murderer", "investigator", "accomplice", "witness"};

    private MenuScreens() {
    }

    // ---------------------------------------------------------------- Home

    public static Screen home(Texts t, String firstName, List<RoomSummary> rooms, Optional<PlayerStats> stats) {
        String rank = t.rank(stats.map(PlayerStats::getWins).orElse(0));

        StringBuilder text = new StringBuilder();
        text.append(bold(t.get("brand.headline"))).append("\n\n");
        text.append(esc(t.get("brand.tagline"))).append("\n\n");
        text.append(note(t.get("home.rank_line", rank, t.name(firstName, MAX_NAME_LENGTH))
                + "\n" + statusLine(t, rooms)));

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.CREATE_ROOM))
                .row(button(Theme.ICON_JOIN + " " + t.get("btn.join_game_room"), Callbacks.JOIN_ROOM))
                .row(button(Theme.ICON_HOW_TO + " " + t.get("btn.how_to_play"), Callbacks.howToPlay(1)),
                        button(Theme.ICON_ROLES + " " + t.get("btn.game_roles"), Callbacks.roles(1)))
                .row(button(Theme.ICON_STATS + " " + t.get("btn.my_statistics"), Callbacks.STATISTICS),
                        languageButton());

        return t.banner(Banner.HOME, text.toString(), withNavigation(t, keyboard).build());
    }

    private static String statusLine(Texts t, List<RoomSummary> rooms) {
        if (rooms.isEmpty()) {
            return t.get("home.status.none");
        }
        RoomSummary first = rooms.get(0);
        String line = first.inProgress()
                ? Theme.ICON_LIVE + " " + t.get("home.status.case", first.code(), first.round(), Game.MAX_ROUNDS)
                : Theme.ICON_WAITING + " " + t.get("home.status.room", first.code(),
                first.players().size(), Lobby.MAX_PLAYERS);
        if (rooms.size() > 1) {
            line += "\n" + t.plural("home.status.more", rooms.size() - 1);
        }
        return line;
    }

    // ---------------------------------------------------------------- Language

    /**
     * The button is written in every language, so it can be found without reading the current one.
     */
    private static InlineKeyboardButton languageButton() {
        StringBuilder label = new StringBuilder(Theme.ICON_LANGUAGE);
        for (Language language : Language.values()) {
            label.append(label.length() > Theme.ICON_LANGUAGE.length() ? " · " : " ")
                    .append(Texts.of(language).get("btn.language"));
        }
        return button(label.toString(), Callbacks.LANGUAGE);
    }

    public static Screen language(Texts t) {
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_LANGUAGE, t.get("lang.title"), t.get("lang.sub"))).append("\n\n");
        text.append(t.get("lang.current", bold(t.language().flag() + " " + t.language().nativeName())))
                .append("\n\n");
        text.append(t.get("lang.body"));

        Keyboard keyboard = Keyboard.create();
        List<InlineKeyboardButton> choices = new ArrayList<>();
        for (Language language : Language.values()) {
            String mark = language == t.language() ? " ✓" : "";
            choices.add(button(language.flag() + " " + language.nativeName() + mark,
                    Callbacks.setLanguage(language.code())));
        }
        keyboard.grid(choices, 2);
        return t.banner(Banner.RECORDS, text.toString(), withNavigation(t, keyboard).build());
    }

    // ---------------------------------------------------------------- Game rooms

    public static Screen rooms(Texts t, List<RoomSummary> rooms) {
        StringBuilder text = new StringBuilder();
        Keyboard keyboard = Keyboard.create();

        if (rooms.isEmpty()) {
            text.append(heading(Theme.ICON_ROOMS, t.get("rooms.title"), t.get("rooms.none.sub"))).append("\n\n");
            text.append(t.get("rooms.none.body"));
        } else {
            text.append(heading(Theme.ICON_ROOMS, t.get("rooms.title"),
                    t.plural("rooms.count", rooms.size()))).append("\n");
            for (RoomSummary room : rooms.stream().limit(MAX_ROOMS_SHOWN).toList()) {
                text.append("\n").append(roomCard(t, room)).append("\n");
                keyboard.row(room.inProgress()
                        ? button(t.get("btn.rejoin_code", room.code()), Callbacks.caseFile(room.code()))
                        : button(t.get("btn.enter_room_code", room.code()), Callbacks.roomDetails(room.code())));
            }
            if (rooms.size() > MAX_ROOMS_SHOWN) {
                text.append("\n").append(italic(t.get("rooms.showing", MAX_ROOMS_SHOWN)));
            }
        }

        keyboard.row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.CREATE_ROOM),
                button(Theme.ICON_JOIN + " " + t.get("btn.join_game_room"), Callbacks.JOIN_ROOM));
        return t.banner(Banner.ROOMS, text.toString(), withNavigation(t, keyboard).build());
    }

    private static String roomCard(Texts t, RoomSummary room) {
        String status = room.inProgress()
                ? Theme.ICON_LIVE + " " + t.get("room.card.live", room.code(), room.round())
                : Theme.ICON_WAITING + " " + t.get("room.card.waiting", room.code());
        return status + "\n"
                + italic(t.plainName(room.room().groupTitle(), MAX_GROUP_TITLE_LENGTH)) + "\n"
                + seatsLine(t, room) + "\n"
                + Theme.initials(room.playerNames()) + "\n"
                + t.get("room.host", t.name(room.room().host().name(), MAX_NAME_LENGTH));
    }

    private static String seatsLine(Texts t, RoomSummary room) {
        int capacity = room.inProgress() ? room.players().size() : Lobby.MAX_PLAYERS;
        return Theme.seats(room.players().size(), capacity) + "  "
                + t.get("room.seats", room.players().size(), capacity);
    }

    public static Screen roomDetails(Texts t, RoomSummary room, long viewerId) {
        boolean host = room.room().isHost(viewerId);
        boolean seated = room.hasPlayer(viewerId);

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, t.get("room.title", room.code()),
                t.plainName(room.room().groupTitle(), MAX_GROUP_TITLE_LENGTH))).append("\n\n");
        text.append(room.inProgress()
                ? Theme.ICON_LIVE + " " + t.get("room.status.live", room.round(), Game.MAX_ROUNDS)
                : Theme.ICON_WAITING + " " + t.get("room.status.waiting")).append("\n");
        text.append(seatsLine(t, room)).append("\n\n");
        text.append(playerList(t, room.players(), room)).append("\n");

        Keyboard keyboard = Keyboard.create();
        if (room.inProgress()) {
            text.append(note(t.get("room.note.live")));
            if (seated) {
                keyboard.row(button(t.get("btn.rejoin"), Callbacks.caseFile(room.code())));
            }
        } else {
            int missing = Math.max(0, Lobby.MIN_PLAYERS - room.players().size());
            text.append(note(missing > 0
                    ? t.plural("room.note.missing", missing)
                    : t.get("room.note.ready")));
            if (host) {
                keyboard.row(button("▶️ " + t.get("btn.open_case"), Callbacks.startRoom(room.code())));
            }
            InlineKeyboardButton seat = seated
                    ? button(t.get("btn.leave_room"), Callbacks.leaveRoom(room.code()))
                    : button(Theme.ICON_JOIN + " " + t.get("btn.join_room"), Callbacks.joinRoom(room.code()));
            if (host) {
                keyboard.row(seat, button(t.get("btn.close_room"), Callbacks.closeRoom(room.code())));
            } else {
                keyboard.row(seat);
            }
        }
        keyboard.row(button("↻ " + t.get("btn.refresh"), Callbacks.roomDetails(room.code())),
                button("◀ " + t.get("btn.my_game_rooms"), Callbacks.ROOMS));
        return t.banner(Banner.ROOMS, text.toString(), withNavigation(t, keyboard).build());
    }

    /**
     * Numbered list of the players in a room, with the host marked.
     */
    public static String playerList(Texts t, List<Player> players, RoomSummary room) {
        if (players.isEmpty()) {
            return italic(t.get("room.nobody")) + "\n";
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            list.append(t.number(i + 1)).append(". ").append(t.name(player.name(), MAX_NAME_LENGTH));
            if (room.room().isHost(player.userId())) {
                list.append("  ").append(italic(t.get("room.host_tag")));
            }
            list.append("\n");
        }
        return list.toString();
    }

    public static Screen createRoom(Texts t, String botUsername) {
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_CREATE, t.get("create.title"), t.get("create.sub"))).append("\n\n");
        text.append(t.get("create.body")).append("\n\n");

        Keyboard keyboard = Keyboard.create();
        if (botUsername == null || botUsername.isBlank()) {
            text.append(t.get("create.no_link"));
        } else {
            text.append(t.get("create.steps")).append("\n\n");
            text.append(italic(t.get("create.already")));
            keyboard.row(link("➕ " + t.get("btn.choose_group"), "https://t.me/" + botUsername + "?startgroup=new"));
        }
        keyboard.row(button(Theme.ICON_JOIN + " " + t.get("btn.join_game_room"), Callbacks.JOIN_ROOM));
        return t.banner(Banner.ROOMS, text.toString(), withNavigation(t, keyboard).build());
    }

    public static Screen joinPrompt(Texts t) {
        String text = heading(Theme.ICON_JOIN, t.get("join.title"), t.get("join.sub")) + "\n\n"
                + t.get("join.body", code("K7Q2"));
        Keyboard keyboard = Keyboard.create()
                .row(button(t.get("btn.cancel"), Callbacks.HOME));
        return t.banner(Banner.ROOMS, text, keyboard.build());
    }

    /**
     * Shown when a room code cannot be joined. The reason is a full sentence.
     */
    public static Screen joinFailed(Texts t, String code, Msg reason) {
        String text = heading(Theme.ICON_JOIN, t.get("join.title"), t.get("join.failed.sub", code)) + "\n\n"
                + Theme.ICON_WRONG + " " + esc(t.get(reason));
        Keyboard keyboard = Keyboard.create()
                .row(button(t.get("btn.another_code"), Callbacks.JOIN_ROOM))
                .row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.CREATE_ROOM));
        return t.banner(Banner.ROOMS, text, withNavigation(t, keyboard).build());
    }

    // ---------------------------------------------------------------- How to play

    public static Screen howToPlay(Texts t, int requestedPage) {
        int page = clamp(requestedPage, HOW_TO_PLAY_PAGES);
        String body = heading(Theme.ICON_HOW_TO, t.get("how.title"), t.get("how." + page + ".sub")) + "\n\n"
                + t.get("how." + page + ".body");
        if (page == 1) {
            body += "\n\n" + italic(t.get("brand.fan_note"));
        }

        Keyboard keyboard = Keyboard.create()
                .row(pager(t, page, HOW_TO_PLAY_PAGES, Callbacks::howToPlay))
                .row(button(Theme.ICON_ROLES + " " + t.get("btn.game_roles"), Callbacks.roles(1)));
        return t.banner(Banner.BRIEFING, body, withNavigation(t, keyboard).build());
    }

    // ---------------------------------------------------------------- Roles

    public static Screen roles(Texts t, int requestedPage) {
        int page = clamp(requestedPage, ROLE_PAGES);
        String key = "roles." + ROLE_KEYS[page - 1];
        String body = heading(Theme.ICON_ROLES, t.get(key + ".name"), t.get(key + ".kind")) + "\n\n"
                + esc(t.get(key + ".who")) + "\n\n"
                + bold(t.get("roles.job")) + "\n" + esc(t.get(key + ".task")) + "\n\n"
                + bold(t.get("roles.goal")) + "\n" + esc(t.get(key + ".goal"));
        if (page > 3) {
            body += "\n\n" + note(t.get("roles.not_in_bot"));
        }

        Keyboard keyboard = Keyboard.create()
                .row(pager(t, page, ROLE_PAGES, Callbacks::roles))
                .row(button(Theme.ICON_HOW_TO + " " + t.get("btn.how_to_play"), Callbacks.howToPlay(1)));
        return t.banner(Banner.ROLES, body, withNavigation(t, keyboard).build());
    }

    // ---------------------------------------------------------------- Records

    public static Screen statistics(Texts t, Optional<PlayerStats> stats, Optional<Integer> rank) {
        Keyboard keyboard = Keyboard.create();
        StringBuilder text = new StringBuilder();

        if (stats.isEmpty() || stats.get().getGames() == 0) {
            text.append(heading(Theme.ICON_STATS, t.get("stats.title"), t.get("stats.none.sub"))).append("\n\n");
            text.append(t.get("stats.none.body")).append("\n\n");
            text.append(italic(t.get("stats.none.practice")));
            keyboard.row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.CREATE_ROOM),
                    button(Theme.ICON_JOIN + " " + t.get("btn.join_game_room"), Callbacks.JOIN_ROOM));
        } else {
            PlayerStats record = stats.get();
            String subline = t.rank(record.getWins())
                    + rank.map(position -> t.get("stats.rank_suffix", position)).orElse("");
            text.append(heading(Theme.ICON_STATS, t.get("stats.title"), subline)).append("\n\n");

            List<String[]> rows = new ArrayList<>();
            rows.add(statRow(t, "closed", t.number(record.getGames())));
            rows.add(statRow(t, "won", t.number(record.getWins())));
            rows.add(statRow(t, "rate", t.get("common.percent", record.winRatePercent())));
            rows.add(statRow(t, "solved", t.number(record.getCasesSolved())));
            rows.add(statRow(t, "escapes", t.number(record.getEscapes())));
            rows.add(null);
            rows.add(statRow(t, "investigator", t.number(record.getAsInvestigator())));
            rows.add(statRow(t, "murderer", t.number(record.getAsMurderer())));
            rows.add(statRow(t, "scientist", t.number(record.getAsForensicScientist())));
            text.append(table(t, rows));
            keyboard.row(button(Theme.ICON_LEADERBOARD + " " + t.get("btn.leaderboard"), Callbacks.LEADERBOARD));
        }
        return t.banner(Banner.RECORDS, text.toString(), withNavigation(t, keyboard).build());
    }

    private static String[] statRow(Texts t, String name, String value) {
        return new String[]{t.get("stats.row." + name), value};
    }

    /**
     * Label and value pairs. Left-to-right languages get an aligned table in a fixed-width font.
     * Padding with spaces does not line up in right-to-left text, so there every pair is one line.
     */
    private static String table(Texts t, List<String[]> rows) {
        StringBuilder table = new StringBuilder();
        if (t.isRightToLeft()) {
            for (String[] row : rows) {
                table.append(row == null ? "" : esc(row[0]) + ": " + bold(row[1])).append("\n");
            }
            return table.toString().stripTrailing();
        }
        table.append("<pre>");
        for (String[] row : rows) {
            table.append(row == null ? "\n" : String.format("%-17s%5s%n", row[0], row[1]));
        }
        return table.append("</pre>").toString();
    }

    public static Screen leaderboard(Texts t, List<PlayerStats> top, long viewerId, Optional<Integer> viewerRank) {
        Keyboard keyboard = Keyboard.create();
        StringBuilder text = new StringBuilder();

        if (top.isEmpty()) {
            text.append(heading(Theme.ICON_LEADERBOARD, t.get("top.title"), t.get("top.none.sub"))).append("\n\n");
            text.append(t.get("top.none.body"));
            keyboard.row(button(Theme.ICON_CREATE + " " + t.get("btn.create_room"), Callbacks.CREATE_ROOM));
        } else {
            text.append(heading(Theme.ICON_LEADERBOARD, t.get("top.title"), t.get("top.sub"))).append("\n\n");
            text.append(t.isRightToLeft() ? ranking(t, top, viewerId) : rankingTable(t, top, viewerId));
            boolean viewerListed = top.stream().anyMatch(entry -> entry.getUserId() == viewerId);
            if (!viewerListed) {
                text.append("\n").append(viewerRank
                        .map(position -> t.get("top.you_rank", position))
                        .orElse(italic(t.get("top.finish"))));
            }
            keyboard.row(button(Theme.ICON_STATS + " " + t.get("btn.my_statistics"), Callbacks.STATISTICS));
        }
        return t.banner(Banner.RECORDS, text.toString(), withNavigation(t, keyboard).build());
    }

    private static String rankingTable(Texts t, List<PlayerStats> top, long viewerId) {
        StringBuilder table = new StringBuilder("<pre>");
        table.append(String.format("%-3s%-14s%5s%6s%n", "#",
                t.get("top.col.detective"), t.get("top.col.won"), t.get("top.col.rate")));
        for (int i = 0; i < top.size(); i++) {
            PlayerStats entry = top.get(i);
            String marker = entry.getUserId() == viewerId ? "▸" : " ";
            table.append(String.format("%-3s%-14s%5d%5d%%%s%n", i + 1,
                    esc(t.shorten(entry.getName(), 13)), entry.getWins(), entry.winRatePercent(), marker));
        }
        return table.append("</pre>").toString();
    }

    private static String ranking(Texts t, List<PlayerStats> top, long viewerId) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            PlayerStats entry = top.get(i);
            String line = t.get("top.row", i + 1, t.name(entry.getName(), 18), entry.getWins(),
                    t.get("common.percent", entry.winRatePercent()));
            list.append(entry.getUserId() == viewerId ? "▸ <b>" + line + "</b>" : line).append("\n");
        }
        return list.toString();
    }

    public static Screen profile(Texts t, String fullName, String username, long userId,
                                 Optional<PlayerStats> stats, Optional<Integer> rank, int openRooms) {
        int wins = stats.map(PlayerStats::getWins).orElse(0);
        int games = stats.map(PlayerStats::getGames).orElse(0);

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_PROFILE, t.get("profile.title"), t.rank(wins))).append("\n\n");
        text.append(bold(t.plainName(fullName, 40))).append("\n");
        if (username != null && !username.isBlank()) {
            text.append(t.dyn("@" + username)).append("\n");
        }
        text.append(t.get("profile.id", code(String.valueOf(userId)))).append("\n\n");
        text.append(t.get("profile.closed", games)).append("\n");
        text.append(t.get("profile.won", wins)).append("\n");
        text.append(t.get("profile.rank", rank
                .map(position -> t.get("profile.rank.value", position))
                .orElse(t.get("profile.rank.none")))).append("\n");
        text.append(t.get("profile.rooms", openRooms)).append("\n");
        text.append(t.get("profile.language", t.language().flag() + " " + t.language().nativeName()));

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_STATS + " " + t.get("btn.my_statistics"), Callbacks.STATISTICS),
                        languageButton());
        return t.banner(Banner.RECORDS, text.toString(), withNavigation(t, keyboard).build());
    }

    // ---------------------------------------------------------------- Shared parts

    /**
     * Adds the navigation row that every dashboard screen ends with.
     */
    public static Keyboard withNavigation(Texts t, Keyboard keyboard) {
        List<InlineKeyboardButton> buttons = new ArrayList<>(List.of(
                button(Theme.ICON_HOME + " " + t.get("nav.home"), Callbacks.HOME),
                button(Theme.ICON_ROOMS + " " + t.get("nav.rooms"), Callbacks.ROOMS),
                button(Theme.ICON_LEADERBOARD + " " + t.get("nav.leaders"), Callbacks.LEADERBOARD),
                button(Theme.ICON_PROFILE + " " + t.get("nav.profile"), Callbacks.PROFILE)));
        return keyboard.row(inReadingOrder(t, buttons));
    }

    /**
     * Back, page number and Next. In a right-to-left language "next" lies to the left,
     * so the row and its arrows are mirrored.
     */
    private static InlineKeyboardButton[] pager(Texts t, int page, int pages, IntFunction<String> target) {
        String towardsStart = t.isRightToLeft() ? "▶" : "◀";
        String towardsEnd = t.isRightToLeft() ? "◀" : "▶";

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(page > 1
                ? button(towardsStart + " " + t.get("btn.back"), target.apply(page - 1))
                : button("·", Callbacks.NO_ACTION));
        buttons.add(button(t.get("common.page", page, pages), Callbacks.NO_ACTION));
        buttons.add(page < pages
                ? button(t.get("btn.next") + " " + towardsEnd, target.apply(page + 1))
                : button("·", Callbacks.NO_ACTION));
        return inReadingOrder(t, buttons);
    }

    /**
     * Telegram always lays buttons out from left to right. This puts the first button where reading starts.
     */
    private static InlineKeyboardButton[] inReadingOrder(Texts t, List<InlineKeyboardButton> buttons) {
        if (t.isRightToLeft()) {
            Collections.reverse(buttons);
        }
        return buttons.toArray(InlineKeyboardButton[]::new);
    }

    private static int clamp(int page, int pages) {
        return Math.max(1, Math.min(pages, page));
    }
}
