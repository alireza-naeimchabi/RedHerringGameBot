package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.PlayerStats;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.ui.MenuScreens;
import de.winona.telegrambot.ui.RoomSummary;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.util.RoomCodeGenerator;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The private dashboard: home, rooms, rules, roles, statistics, leaderboard and profile.
 * It reads data from the services and lets {@link MenuScreens} turn it into screens.
 */
public class MenuController {

    private static final String START_JOIN_PREFIX = "join_";
    private static final String START_HOW_TO_PLAY = "how";

    private final Messenger messenger;
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final StatsService statsService;
    private final RoomController roomController;
    private final GameController gameController;
    /** Players who tapped "Join Game Room" and are expected to send a code next. */
    private final Set<Long> waitingForCode = new HashSet<>();

    public MenuController(Messenger messenger, LobbyService lobbyService, GameService gameService,
                          StatsService statsService, RoomController roomController,
                          GameController gameController) {
        this.messenger = messenger;
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.statsService = statsService;
        this.roomController = roomController;
        this.gameController = gameController;
    }

    // ---------------------------------------------------------------- Commands and text (private chat)

    /** Handles a command in the private chat. Returns false if the command is unknown. */
    public boolean onCommand(String command, String argument, User user) {
        waitingForCode.remove(user.getId());
        switch (command) {
            case "/start" -> onStart(argument, user);
            case "/menu", "/home" -> send(user, home(user));
            case "/rooms", "/room" -> send(user, MenuScreens.rooms(roomsOf(user.getId())));
            case "/newgame" -> send(user, MenuScreens.createRoom(messenger.botUsername()));
            case "/join" -> {
                if (argument.isBlank()) {
                    askForCode(user, null);
                } else {
                    joinWithCode(user, argument, null);
                }
            }
            case "/help", "/howtoplay" -> send(user, MenuScreens.howToPlay(1));
            case "/roles" -> send(user, MenuScreens.roles(1));
            case "/stats" -> send(user, statistics(user));
            case "/leaderboard" -> send(user, leaderboard(user));
            case "/profile" -> send(user, profile(user));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void onStart(String parameter, User user) {
        if (parameter.startsWith(START_JOIN_PREFIX)) {
            joinWithCode(user, parameter.substring(START_JOIN_PREFIX.length()), null);
        } else if (parameter.equals(START_HOW_TO_PLAY)) {
            send(user, MenuScreens.howToPlay(1));
        } else {
            send(user, home(user));
        }
    }

    /** Handles plain text in the private chat: a room code, if one is expected or it looks like one. */
    public void onText(String text, User user) {
        boolean expected = waitingForCode.contains(user.getId());
        if (RoomCodeGenerator.looksLikeCode(text)) {
            joinWithCode(user, text, null);
        } else if (expected) {
            send(user, MenuScreens.joinFailed(MenuScreens.shorten(text, 12),
                    "A room code has " + RoomCodeGenerator.CODE_LENGTH
                            + " letters or digits, for example K7Q2. Send the code again."));
        } else {
            send(user, home(user));
        }
    }

    // ---------------------------------------------------------------- Buttons ("m:...")

    public void onClick(Click click) {
        if (!click.inPrivateChat()) {
            click.alert("Open the dashboard in our private chat.");
            return;
        }
        User user = click.user();
        String action = click.part(1);
        if (!action.equals("join")) {
            waitingForCode.remove(user.getId());
        }

        switch (action) {
            case "home" -> show(click, home(user));
            case "rooms" -> show(click, MenuScreens.rooms(roomsOf(user.getId())));
            case "create" -> show(click, MenuScreens.createRoom(messenger.botUsername()));
            case "join" -> askForCode(user, click);
            case "how" -> show(click, MenuScreens.howToPlay(pageOf(click)));
            case "roles" -> show(click, MenuScreens.roles(pageOf(click)));
            case "stats" -> show(click, statistics(user));
            case "top" -> show(click, leaderboard(user));
            case "me" -> show(click, profile(user));
            case "room" -> showRoom(click, click.part(2));
            case "case" -> showCaseFile(click, click.part(2));
            case "noop" -> click.finish();
            default -> click.alert("This button is no longer valid.");
        }
    }

    private int pageOf(Click click) {
        try {
            return click.intPart(2);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void showRoom(Click click, String code) {
        Optional<RoomSummary> room = findRoom(code);
        if (room.isEmpty()) {
            click.alert("Room " + code + " is no longer open.");
            show(click, MenuScreens.rooms(roomsOf(click.userId())));
            return;
        }
        show(click, MenuScreens.roomDetails(room.get(), click.userId()));
    }

    private void showCaseFile(Click click, String code) {
        Optional<Screen> caseFile = gameController.caseFile(code, click.userId());
        if (caseFile.isEmpty()) {
            click.alert("Case " + code + " is closed.");
            show(click, MenuScreens.rooms(roomsOf(click.userId())));
            return;
        }
        show(click, caseFile.get());
    }

    // ---------------------------------------------------------------- Joining with a code

    private void askForCode(User user, Click click) {
        waitingForCode.add(user.getId());
        if (click == null) {
            send(user, MenuScreens.joinPrompt());
        } else {
            show(click, MenuScreens.joinPrompt());
        }
    }

    private void joinWithCode(User user, String rawCode, Click click) {
        String code = RoomCodeGenerator.normalize(rawCode);
        JoinOutcome outcome = roomController.join(code, user, false);

        Screen screen;
        if (outcome.seated()) {
            waitingForCode.remove(user.getId());
            screen = findRoom(code)
                    .map(room -> MenuScreens.roomDetails(room, user.getId()))
                    .orElseGet(() -> home(user));
        } else if (outcome == JoinOutcome.IN_PROGRESS && gameController.caseFile(code, user.getId()).isPresent()) {
            waitingForCode.remove(user.getId());
            screen = gameController.caseFile(code, user.getId()).get();
        } else {
            screen = MenuScreens.joinFailed(MenuScreens.shorten(code, 12), outcome.message(code));
        }

        if (click == null) {
            send(user, screen);
        } else {
            show(click, screen);
        }
    }

    // ---------------------------------------------------------------- Data for the screens

    private Screen home(User user) {
        return MenuScreens.home(user.getFirstName(), roomsOf(user.getId()), statsService.statsOf(user.getId()));
    }

    private Screen statistics(User user) {
        return MenuScreens.statistics(statsService.statsOf(user.getId()), statsService.rankOf(user.getId()));
    }

    private Screen leaderboard(User user) {
        return MenuScreens.leaderboard(statsService.leaderboard(MenuScreens.LEADERBOARD_SIZE),
                user.getId(), statsService.rankOf(user.getId()));
    }

    private Screen profile(User user) {
        String fullName = user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
        Optional<PlayerStats> stats = statsService.statsOf(user.getId());
        return MenuScreens.profile(fullName, user.getUserName(), user.getId(), stats,
                statsService.rankOf(user.getId()), roomsOf(user.getId()).size());
    }

    /** All rooms the player sits in or hosts. Running cases come first. */
    private List<RoomSummary> roomsOf(long userId) {
        List<RoomSummary> rooms = new ArrayList<>();
        gameService.gamesOf(userId).forEach(game -> rooms.add(RoomSummary.of(game)));
        lobbyService.lobbiesOf(userId).forEach(lobby -> rooms.add(RoomSummary.of(lobby)));
        return rooms;
    }

    private Optional<RoomSummary> findRoom(String code) {
        Optional<RoomSummary> lobby = lobbyService.findByCode(code).map(RoomSummary::of);
        return lobby.isPresent() ? lobby : gameService.findByCode(code).map(RoomSummary::of);
    }

    // ---------------------------------------------------------------- Showing

    private void send(User user, Screen screen) {
        messenger.send(user.getId(), screen);
    }

    private void show(Click click, Screen screen) {
        messenger.show(click.message(), screen);
    }
}
