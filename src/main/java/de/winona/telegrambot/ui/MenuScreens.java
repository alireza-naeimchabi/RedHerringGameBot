package de.winona.telegrambot.ui;

import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.PlayerStats;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.winona.telegrambot.ui.Keyboard.button;
import static de.winona.telegrambot.ui.Keyboard.link;
import static de.winona.telegrambot.ui.Theme.bold;
import static de.winona.telegrambot.ui.Theme.code;
import static de.winona.telegrambot.ui.Theme.esc;
import static de.winona.telegrambot.ui.Theme.heading;
import static de.winona.telegrambot.ui.Theme.italic;
import static de.winona.telegrambot.ui.Theme.note;

/**
 * The screens of the private dashboard. Every method only turns data into a {@link Screen};
 * nothing here talks to Telegram or changes the game.
 */
public final class MenuScreens {

    public static final int HOW_TO_PLAY_PAGES = 4;
    public static final int ROLE_PAGES = 5;
    public static final int LEADERBOARD_SIZE = 10;

    private static final int MAX_ROOMS_SHOWN = 4;
    private static final int MAX_NAME_LENGTH = 22;
    private static final int MAX_GROUP_TITLE_LENGTH = 32;

    private MenuScreens() {
    }

    // ---------------------------------------------------------------- Home

    public static Screen home(String firstName, List<RoomSummary> rooms, Optional<PlayerStats> stats) {
        String rank = Theme.rankTitle(stats.map(PlayerStats::getWins).orElse(0));

        StringBuilder text = new StringBuilder();
        text.append(bold(Theme.HEADLINE)).append("\n\n");
        text.append(esc(Theme.TAGLINE)).append("\n\n");
        text.append(note(rank + " " + bold(shorten(firstName, MAX_NAME_LENGTH)) + "\n" + statusLine(rooms)));

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.CREATE_ROOM))
                .row(button(Theme.ICON_JOIN + " Join Game Room", Callbacks.JOIN_ROOM))
                .row(button(Theme.ICON_HOW_TO + " How to Play", Callbacks.howToPlay(1)),
                        button(Theme.ICON_ROLES + " Game Roles", Callbacks.roles(1)))
                .row(button(Theme.ICON_STATS + " My Statistics", Callbacks.STATISTICS));

        return Screen.withBanner(Banner.HOME, text.toString(), withNavigation(keyboard).build());
    }

    private static String statusLine(List<RoomSummary> rooms) {
        if (rooms.isEmpty()) {
            return "No open cases. Create a room to start one.";
        }
        RoomSummary first = rooms.get(0);
        String line = first.inProgress()
                ? Theme.ICON_LIVE + " Case " + code(first.code()) + " is underway, round "
                        + first.round() + " of 3."
                : Theme.ICON_WAITING + " Room " + code(first.code()) + " is waiting for detectives, "
                        + first.players().size() + " of " + Lobby.MAX_PLAYERS + " seated.";
        if (rooms.size() > 1) {
            line += "\n+ " + Theme.plural(rooms.size() - 1, "more room", "more rooms") + " in My Game Rooms.";
        }
        return line;
    }

    // ---------------------------------------------------------------- Game rooms

    public static Screen rooms(List<RoomSummary> rooms) {
        StringBuilder text = new StringBuilder();
        Keyboard keyboard = Keyboard.create();

        if (rooms.isEmpty()) {
            text.append(heading(Theme.ICON_ROOMS, "MY GAME ROOMS", "No open cases")).append("\n\n");
            text.append("You are not seated in any room right now.\n\n");
            text.append("Create a room for your group, or join a friend's room with its code.");
        } else {
            text.append(heading(Theme.ICON_ROOMS, "MY GAME ROOMS",
                    Theme.plural(rooms.size(), "room", "rooms"))).append("\n");
            for (RoomSummary room : rooms.stream().limit(MAX_ROOMS_SHOWN).toList()) {
                text.append("\n").append(roomCard(room)).append("\n");
                keyboard.row(room.inProgress()
                        ? button("Rejoin Game " + room.code(), Callbacks.caseFile(room.code()))
                        : button("Enter Room " + room.code(), Callbacks.roomDetails(room.code())));
            }
            if (rooms.size() > MAX_ROOMS_SHOWN) {
                text.append("\n").append(italic("Showing your " + MAX_ROOMS_SHOWN + " most recent rooms."));
            }
        }

        keyboard.row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.CREATE_ROOM),
                button(Theme.ICON_JOIN + " Join Game Room", Callbacks.JOIN_ROOM));
        return Screen.withBanner(Banner.ROOMS, text.toString(), withNavigation(keyboard).build());
    }

    private static String roomCard(RoomSummary room) {
        String status = room.inProgress()
                ? Theme.ICON_LIVE + " " + bold("Room " + room.code()) + "  Case underway, round " + room.round()
                : Theme.ICON_WAITING + " " + bold("Room " + room.code()) + "  Waiting for detectives";
        return status + "\n"
                + italic(shorten(room.room().groupTitle(), MAX_GROUP_TITLE_LENGTH)) + "\n"
                + seatsLine(room) + "\n"
                + Theme.initials(room.playerNames()) + "\n"
                + "Host: " + esc(shorten(room.room().host().name(), MAX_NAME_LENGTH));
    }

    private static String seatsLine(RoomSummary room) {
        int capacity = room.inProgress() ? room.players().size() : Lobby.MAX_PLAYERS;
        return Theme.seats(room.players().size(), capacity) + "  "
                + room.players().size() + " of " + capacity + " seated";
    }

    public static Screen roomDetails(RoomSummary room, long viewerId) {
        boolean host = room.room().isHost(viewerId);
        boolean seated = room.hasPlayer(viewerId);

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_ROOMS, "ROOM " + room.code(),
                shorten(room.room().groupTitle(), MAX_GROUP_TITLE_LENGTH))).append("\n\n");
        text.append(room.inProgress()
                ? Theme.ICON_LIVE + " Case underway, round " + room.round() + " of 3"
                : Theme.ICON_WAITING + " Waiting for detectives").append("\n");
        text.append(seatsLine(room)).append("\n\n");
        text.append(playerList(room.players(), room)).append("\n");

        Keyboard keyboard = Keyboard.create();
        if (room.inProgress()) {
            text.append(note("The case is being investigated in the group chat. "
                    + "Open your case file to see your role and the scene report."));
            if (seated) {
                keyboard.row(button("Rejoin Game", Callbacks.caseFile(room.code())));
            }
        } else {
            int missing = Math.max(0, Lobby.MIN_PLAYERS - room.players().size());
            text.append(note(missing > 0
                    ? "Needs " + Theme.plural(missing, "more detective", "more detectives")
                            + " before the case can open."
                    : "Ready. The host can open the case."));
            if (host) {
                keyboard.row(button("▶️ Open the Case", Callbacks.startRoom(room.code())));
            }
            InlineKeyboardButton seat = seated
                    ? button("Leave Room", Callbacks.leaveRoom(room.code()))
                    : button(Theme.ICON_JOIN + " Join Room", Callbacks.joinRoom(room.code()));
            if (host) {
                keyboard.row(seat, button("Close Room", Callbacks.closeRoom(room.code())));
            } else {
                keyboard.row(seat);
            }
        }
        keyboard.row(button("↻ Refresh", Callbacks.roomDetails(room.code())),
                button("◀ My Game Rooms", Callbacks.ROOMS));
        return Screen.withBanner(Banner.ROOMS, text.toString(), withNavigation(keyboard).build());
    }

    /** Numbered list of the players in a room, with the host marked. */
    public static String playerList(List<Player> players, RoomSummary room) {
        if (players.isEmpty()) {
            return italic("No one is seated yet.") + "\n";
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            list.append(i + 1).append(". ").append(esc(shorten(player.name(), MAX_NAME_LENGTH)));
            if (room.room().isHost(player.userId())) {
                list.append("  ").append(italic("host"));
            }
            list.append("\n");
        }
        return list.toString();
    }

    public static Screen createRoom(String botUsername) {
        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_CREATE, "CREATE GAME ROOM", "A case needs a group chat")).append("\n\n");
        text.append("Everyone discusses the evidence together, so a room always lives in a Telegram group.\n\n");

        Keyboard keyboard = Keyboard.create();
        if (botUsername == null || botUsername.isBlank()) {
            text.append("Add me to your group and send /newgame there. "
                    + "I post the room card with its code.");
        } else {
            text.append("1. Tap ").append(bold("Choose a Group")).append(" and pick your group.\n");
            text.append("2. I open a room there and post its code.\n");
            text.append("3. Your friends join with one tap.\n\n");
            text.append(italic("Already in a group with me? Send /newgame there."));
            keyboard.row(link("➕ Choose a Group", "https://t.me/" + botUsername + "?startgroup=new"));
        }
        keyboard.row(button(Theme.ICON_JOIN + " Join Game Room", Callbacks.JOIN_ROOM));
        return Screen.withBanner(Banner.ROOMS, text.toString(), withNavigation(keyboard).build());
    }

    public static Screen joinPrompt() {
        String text = heading(Theme.ICON_JOIN, "JOIN GAME ROOM", "Enter a room code") + "\n\n"
                + "Send me the 4-character code of the room, for example " + code("K7Q2") + ".\n\n"
                + "You find the code on the room card in your group chat.";
        Keyboard keyboard = Keyboard.create()
                .row(button("Cancel", Callbacks.HOME));
        return Screen.withBanner(Banner.ROOMS, text, keyboard.build());
    }

    /** Shown when a room code cannot be joined. The reason is a full sentence. */
    public static Screen joinFailed(String code, String reason) {
        String text = heading(Theme.ICON_JOIN, "JOIN GAME ROOM", "Room " + code) + "\n\n"
                + Theme.ICON_WRONG + " " + esc(reason);
        Keyboard keyboard = Keyboard.create()
                .row(button("Enter Another Code", Callbacks.JOIN_ROOM))
                .row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.CREATE_ROOM));
        return Screen.withBanner(Banner.ROOMS, text, withNavigation(keyboard).build());
    }

    // ---------------------------------------------------------------- How to play

    public static Screen howToPlay(int requestedPage) {
        int page = clamp(requestedPage, HOW_TO_PLAY_PAGES);
        String body = switch (page) {
            case 1 -> heading(Theme.ICON_HOW_TO, "HOW TO PLAY", "1. The case") + "\n\n"
                    + "A murder has been committed, and the murderer is one of the investigators "
                    + "in your group.\n\n"
                    + "To solve the case, the investigators must name three things:\n"
                    + "the " + bold("murderer") + ", the " + bold("Means of Murder")
                    + " and the " + bold("Key Evidence") + ".\n\n"
                    + "One case takes 4 to 7 players and about 15 minutes.\n\n"
                    + italic(Theme.FAN_NOTE);
            case 2 -> heading(Theme.ICON_HOW_TO, "HOW TO PLAY", "2. Setting the scene") + "\n\n"
                    + "• Roles are dealt in your private chat with me.\n"
                    + "• Every player except the Forensic Scientist gets 4 means cards "
                    + Theme.ICON_MEANS + " and 4 clue cards " + Theme.ICON_CLUE
                    + ". The Evidence Board shows them to everyone.\n"
                    + "• The murderer secretly picks one of their own means cards and one of their own "
                    + "clue cards. This pair is the solution.\n"
                    + "• The Forensic Scientist learns the solution, but may never talk about it.";
            case 3 -> heading(Theme.ICON_HOW_TO, "HOW TO PLAY", "3. The investigation") + "\n\n"
                    + "The Forensic Scientist answers six scene tiles, such as "
                    + italic("Cause of death") + " or " + italic("Location")
                    + ", by picking one option on each. Every answer appears in the group at once.\n\n"
                    + "Discuss what the answers point to. The murderer discusses too, "
                    + "and steers suspicion towards someone else.\n\n"
                    + "A case has 3 rounds. In rounds 2 and 3 the Forensic Scientist replaces "
                    + "one tile with a new one.";
            default -> heading(Theme.ICON_HOW_TO, "HOW TO PLAY", "4. Solving the crime") + "\n\n"
                    + "Tap " + bold("Solve the Crime") + " at any time and name a player, "
                    + "one of their means cards and one of their clue cards.\n\n"
                    + Theme.ICON_OK + " All three correct: the investigators and the Forensic Scientist win.\n"
                    + Theme.ICON_WRONG + " Anything wrong: the answer is only \"no\", "
                    + "and your single attempt is used.\n\n"
                    + "The murderer wins if round 3 ends without a correct accusation, "
                    + "or if every investigator has used their attempt.";
        };

        Keyboard keyboard = Keyboard.create()
                .row(pager(page, HOW_TO_PLAY_PAGES, Callbacks::howToPlay))
                .row(button(Theme.ICON_ROLES + " Game Roles", Callbacks.roles(1)));
        return Screen.withBanner(Banner.BRIEFING, body, withNavigation(keyboard).build());
    }

    // ---------------------------------------------------------------- Roles

    public static Screen roles(int requestedPage) {
        int page = clamp(requestedPage, ROLE_PAGES);
        String body = switch (page) {
            case 1 -> roleCard("FORENSIC SCIENTIST", "Standard role, one per case",
                    "You know the murderer and the solution, and you lead the investigation "
                            + "without saying a word about it.",
                    "Answer the scene tiles so they point to the Means of Murder and the Key Evidence. "
                            + "Start rounds 2 and 3 and replace one tile each time.",
                    "Someone solves the crime. You win together with the investigators.");
            case 2 -> roleCard("MURDERER", "Standard role, one per case",
                    "You committed the crime and sit among the investigators.",
                    "Secretly pick one of your means cards and one of your clue cards. "
                            + "Then join the discussion and shift suspicion to someone else.",
                    "Nobody names you together with both of your cards.");
            case 3 -> roleCard("INVESTIGATOR", "Standard role, everyone else",
                    "You read the scene report and look for the cards it describes.",
                    "Discuss, defend yourself against false suspicion, and use your single "
                            + "attempt to solve the crime when you are sure.",
                    "Any investigator names the murderer, "
                            + "the Means of Murder and the Key Evidence.");
            case 4 -> roleCard("ACCOMPLICE", "Optional role in the board game, from 6 players",
                    "You know the murderer and the solution and help them get away.",
                    "Protect the murderer in the discussion without exposing yourself.",
                    "The murderer gets away. You win together.")
                    + "\n\n" + note("Not part of this bot yet. Cases here use the three standard roles.");
            default -> roleCard("WITNESS", "Optional role in the board game, with the Accomplice",
                    "You saw the two culprits leave, but not who of them did it or how.",
                    "Guide the investigators quietly. If the murderer is caught but can "
                            + "point you out afterwards, the culprits still escape.",
                    "The crime is solved and the murderer cannot point you out.")
                    + "\n\n" + note("Not part of this bot yet. Cases here use the three standard roles.");
        };

        Keyboard keyboard = Keyboard.create()
                .row(pager(page, ROLE_PAGES, Callbacks::roles))
                .row(button(Theme.ICON_HOW_TO + " How to Play", Callbacks.howToPlay(1)));
        return Screen.withBanner(Banner.ROLES, body, withNavigation(keyboard).build());
    }

    private static String roleCard(String name, String kind, String who, String task, String goal) {
        return heading(Theme.ICON_ROLES, name, kind) + "\n\n"
                + esc(who) + "\n\n"
                + bold("Your job") + "\n" + esc(task) + "\n\n"
                + bold("Your goal") + "\n" + esc(goal);
    }

    // ---------------------------------------------------------------- Records

    public static Screen statistics(Optional<PlayerStats> stats, Optional<Integer> rank) {
        Keyboard keyboard = Keyboard.create();
        StringBuilder text = new StringBuilder();

        if (stats.isEmpty() || stats.get().getGames() == 0) {
            text.append(heading(Theme.ICON_STATS, "MY STATISTICS", "No closed cases yet")).append("\n\n");
            text.append("Your record starts with your first finished case. ");
            text.append("Wins, roles and solved crimes will appear here.\n\n");
            text.append(italic("Practice cases with test players are not counted."));
            keyboard.row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.CREATE_ROOM),
                    button(Theme.ICON_JOIN + " Join Game Room", Callbacks.JOIN_ROOM));
        } else {
            PlayerStats record = stats.get();
            String subline = Theme.rankTitle(record.getWins())
                    + rank.map(position -> ", rank " + position + " on the leaderboard").orElse("");
            text.append(heading(Theme.ICON_STATS, "MY STATISTICS", subline)).append("\n\n");
            text.append("<pre>")
                    .append(statRow("Cases closed", record.getGames()))
                    .append(statRow("Cases won", record.getWins()))
                    .append(statRow("Win rate", record.winRatePercent() + "%"))
                    .append(statRow("Crimes solved", record.getCasesSolved()))
                    .append(statRow("Clean escapes", record.getEscapes()))
                    .append("\n")
                    .append(statRow("As Investigator", record.getAsInvestigator()))
                    .append(statRow("As Murderer", record.getAsMurderer()))
                    .append(statRow("As Scientist", record.getAsForensicScientist()))
                    .append("</pre>");
            keyboard.row(button(Theme.ICON_LEADERBOARD + " Leaderboard", Callbacks.LEADERBOARD));
        }
        return Screen.withBanner(Banner.RECORDS, text.toString(), withNavigation(keyboard).build());
    }

    private static String statRow(String label, Object value) {
        return String.format("%-17s%5s%n", label, value);
    }

    public static Screen leaderboard(List<PlayerStats> top, long viewerId, Optional<Integer> viewerRank) {
        Keyboard keyboard = Keyboard.create();
        StringBuilder text = new StringBuilder();

        if (top.isEmpty()) {
            text.append(heading(Theme.ICON_LEADERBOARD, "LEADERBOARD", "No closed cases yet")).append("\n\n");
            text.append("The board fills up as soon as the first case is finished. ");
            text.append("Be the first name on it.");
            keyboard.row(button(Theme.ICON_CREATE + " Create Game Room", Callbacks.CREATE_ROOM));
        } else {
            text.append(heading(Theme.ICON_LEADERBOARD, "LEADERBOARD", "Ranked by cases won")).append("\n\n");
            text.append("<pre>");
            text.append(String.format("%-3s%-14s%5s%6s%n", "#", "Detective", "Won", "Rate"));
            for (int i = 0; i < top.size(); i++) {
                PlayerStats entry = top.get(i);
                String marker = entry.getUserId() == viewerId ? "▸" : " ";
                text.append(String.format("%-3s%-14s%5d%5d%%%s%n", i + 1,
                        esc(shorten(entry.getName(), 13)), entry.getWins(), entry.winRatePercent(), marker));
            }
            text.append("</pre>");
            boolean viewerListed = top.stream().anyMatch(entry -> entry.getUserId() == viewerId);
            if (!viewerListed) {
                text.append("\n").append(viewerRank
                        .map(position -> "You are on rank " + position + ".")
                        .orElse(italic("Finish a case to appear on the board.")));
            }
            keyboard.row(button(Theme.ICON_STATS + " My Statistics", Callbacks.STATISTICS));
        }
        return Screen.withBanner(Banner.RECORDS, text.toString(), withNavigation(keyboard).build());
    }

    public static Screen profile(String fullName, String username, long userId,
                                 Optional<PlayerStats> stats, Optional<Integer> rank, int openRooms) {
        int wins = stats.map(PlayerStats::getWins).orElse(0);
        int games = stats.map(PlayerStats::getGames).orElse(0);

        StringBuilder text = new StringBuilder();
        text.append(heading(Theme.ICON_PROFILE, "PROFILE", Theme.rankTitle(wins))).append("\n\n");
        text.append(bold(shorten(fullName, 40))).append("\n");
        if (username != null && !username.isBlank()) {
            text.append("@").append(esc(username)).append("\n");
        }
        text.append("Telegram ID ").append(code(String.valueOf(userId))).append("\n\n");
        text.append("Cases closed: ").append(games).append("\n");
        text.append("Cases won: ").append(wins).append("\n");
        text.append("Leaderboard: ").append(rank.map(position -> "rank " + position).orElse("not ranked yet"))
                .append("\n");
        text.append("Open rooms: ").append(openRooms);

        Keyboard keyboard = Keyboard.create()
                .row(button(Theme.ICON_STATS + " My Statistics", Callbacks.STATISTICS));
        return Screen.withBanner(Banner.RECORDS, text.toString(), withNavigation(keyboard).build());
    }

    // ---------------------------------------------------------------- Shared parts

    /** Adds the navigation row that every dashboard screen ends with. */
    public static Keyboard withNavigation(Keyboard keyboard) {
        return keyboard.row(
                button(Theme.ICON_HOME + " Home", Callbacks.HOME),
                button(Theme.ICON_ROOMS + " Rooms", Callbacks.ROOMS),
                button(Theme.ICON_LEADERBOARD + " Leaders", Callbacks.LEADERBOARD),
                button(Theme.ICON_PROFILE + " Profile", Callbacks.PROFILE));
    }

    private static InlineKeyboardButton[] pager(int page, int pages,
                                                java.util.function.IntFunction<String> target) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(page > 1
                ? button("◀ Back", target.apply(page - 1))
                : button("·", Callbacks.NO_ACTION));
        buttons.add(button(page + " / " + pages, Callbacks.NO_ACTION));
        buttons.add(page < pages
                ? button("Next ▶", target.apply(page + 1))
                : button("·", Callbacks.NO_ACTION));
        return buttons.toArray(InlineKeyboardButton[]::new);
    }

    private static int clamp(int page, int pages) {
        return Math.max(1, Math.min(pages, page));
    }

    /** Cuts long names so one long title cannot break a screen. */
    public static String shorten(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "Unknown";
        }
        String clean = text.strip();
        if (clean.codePointCount(0, clean.length()) <= maxLength) {
            return clean;
        }
        int end = clean.offsetByCodePoints(0, maxLength - 1);
        return clean.substring(0, end).stripTrailing() + "…";
    }
}
