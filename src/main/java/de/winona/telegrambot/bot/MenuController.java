package de.winona.telegrambot.bot;

import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.PlayerStats;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.view.MenuScreens;
import de.winona.telegrambot.view.RoomSummary;
import de.winona.telegrambot.view.Screen;
import de.winona.telegrambot.service.RoomCodeGenerator;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.*;

/**
 * The private dashboard: home, rooms, rules, roles, statistics, leaderboard, profile and language.
 * It reads data from the services and lets {@link MenuScreens} turn it into screens,
 * always in the language of the player it is talking to.
 */
public class MenuController {

    private static final String START_JOIN_PREFIX = "join_";
    private static final String START_HOW_TO_PLAY = "how";

    private final Messenger messenger;
    private final Localizer localizer;
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final StatsService statsService;
    private final RoomController roomController;
    private final GameController gameController;
    /**
     * Players who tapped "Join Game Room" and are expected to send a code next.
     */
    private final Set<Long> waitingForCode = new HashSet<>();

    public MenuController(Messenger messenger, Localizer localizer, LobbyService lobbyService,
                          GameService gameService, StatsService statsService, RoomController roomController,
                          GameController gameController) {
        this.messenger = messenger;
        this.localizer = localizer;
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.statsService = statsService;
        this.roomController = roomController;
        this.gameController = gameController;
    }

    // ---------------------------------------------------------------- Commands and text (private chat)

    /**
     * Handles a command in the private chat. Returns false if the command is unknown.
     */
    public boolean onCommand(String command, String argument, User user) {
        waitingForCode.remove(user.getId());
        Texts t = localizer.textsFor(user.getId());
        switch (command) {
            case "/start" -> onStart(argument, user);
            case "/menu", "/home" -> send(user, home(t, user));
            case "/rooms", "/room" -> send(user, MenuScreens.rooms(t, roomsOf(user.getId())));
            case "/newgame" -> send(user, MenuScreens.createRoom(t, messenger.botUsername()));
            case "/join" -> {
                if (argument.isBlank()) {
                    askForCode(user, null);
                } else {
                    joinWithCode(user, argument, null);
                }
            }
            case "/help", "/howtoplay" -> send(user, MenuScreens.howToPlay(t, 1));
            case "/roles" -> send(user, MenuScreens.roles(t, 1));
            case "/stats" -> send(user, statistics(t, user));
            case "/leaderboard" -> send(user, leaderboard(t, user));
            case "/profile" -> send(user, profile(t, user));
            case "/language", "/lang" -> send(user, MenuScreens.language(t));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void onStart(String parameter, User user) {
        Texts t = localizer.textsFor(user.getId());
        if (parameter.startsWith(START_JOIN_PREFIX)) {
            joinWithCode(user, parameter.substring(START_JOIN_PREFIX.length()), null);
        } else if (parameter.equals(START_HOW_TO_PLAY)) {
            send(user, MenuScreens.howToPlay(t, 1));
        } else {
            send(user, home(t, user));
        }
    }

    /**
     * Handles plain text in the private chat: a room code, if one is expected or it looks like one.
     */
    public void onText(String text, User user) {
        Texts t = localizer.textsFor(user.getId());
        boolean expected = waitingForCode.contains(user.getId());
        if (RoomCodeGenerator.looksLikeCode(text)) {
            joinWithCode(user, text, null);
        } else if (expected) {
            send(user, MenuScreens.joinFailed(t, t.shorten(text, 12),
                    Msg.of("join.bad_code", RoomCodeGenerator.CODE_LENGTH)));
        } else {
            send(user, home(t, user));
        }
    }

    // ---------------------------------------------------------------- Buttons ("m:...")

    public void onClick(Click click) {
        if (!click.inPrivateChat()) {
            click.alert("err.dashboard_private");
            return;
        }
        User user = click.user();
        Texts t = click.texts();
        String action = click.part(1);
        if (!action.equals("join")) {
            waitingForCode.remove(user.getId());
        }

        switch (action) {
            case "home" -> show(click, home(t, user));
            case "rooms" -> show(click, MenuScreens.rooms(t, roomsOf(user.getId())));
            case "create" -> show(click, MenuScreens.createRoom(t, messenger.botUsername()));
            case "join" -> askForCode(user, click);
            case "how" -> show(click, MenuScreens.howToPlay(t, pageOf(click)));
            case "roles" -> show(click, MenuScreens.roles(t, pageOf(click)));
            case "stats" -> show(click, statistics(t, user));
            case "top" -> show(click, leaderboard(t, user));
            case "me" -> show(click, profile(t, user));
            case "lang" -> show(click, MenuScreens.language(t));
            case "setlang" -> setLanguage(click, click.part(2));
            case "room" -> showRoom(click, click.part(2));
            case "case" -> showCaseFile(click, click.part(2));
            case "noop" -> click.finish();
            default -> click.alert("err.button_invalid");
        }
    }

    private int pageOf(Click click) {
        try {
            return click.intPart(2);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Stores the chosen language and shows the dashboard in it right away.
     */
    private void setLanguage(Click click, String languageCode) {
        Optional<Language> chosen = Arrays.stream(Language.values())
                .filter(language -> language.code().equals(languageCode))
                .findFirst();
        if (chosen.isEmpty()) {
            click.alert("err.button_invalid");
            return;
        }
        localizer.setLanguage(click.userId(), chosen.get());
        Texts t = localizer.textsFor(click.userId());
        // The confirmation is already written in the new language.
        click.toastText(t.get("lang.changed"));
        show(click, home(t, click.user()));
    }

    private void showRoom(Click click, String code) {
        Optional<RoomSummary> room = findRoom(code);
        if (room.isEmpty()) {
            click.alert("err.room_closed_code", code);
            show(click, MenuScreens.rooms(click.texts(), roomsOf(click.userId())));
            return;
        }
        show(click, MenuScreens.roomDetails(click.texts(), room.get(), click.userId()));
    }

    private void showCaseFile(Click click, String code) {
        Optional<Screen> caseFile = gameController.caseFile(code, click.userId());
        if (caseFile.isEmpty()) {
            click.alert("err.case_closed_code", code);
            show(click, MenuScreens.rooms(click.texts(), roomsOf(click.userId())));
            return;
        }
        show(click, caseFile.get());
    }

    // ---------------------------------------------------------------- Joining with a code

    private void askForCode(User user, Click click) {
        waitingForCode.add(user.getId());
        Screen prompt = MenuScreens.joinPrompt(localizer.textsFor(user.getId()));
        if (click == null) {
            send(user, prompt);
        } else {
            show(click, prompt);
        }
    }

    private void joinWithCode(User user, String rawCode, Click click) {
        Texts t = localizer.textsFor(user.getId());
        String code = RoomCodeGenerator.normalize(rawCode);
        JoinOutcome outcome = roomController.join(code, user, false);

        Screen screen;
        if (outcome.seated()) {
            waitingForCode.remove(user.getId());
            screen = findRoom(code)
                    .map(room -> MenuScreens.roomDetails(t, room, user.getId()))
                    .orElseGet(() -> home(t, user));
        } else if (outcome == JoinOutcome.IN_PROGRESS && gameController.caseFile(code, user.getId()).isPresent()) {
            waitingForCode.remove(user.getId());
            screen = gameController.caseFile(code, user.getId()).get();
        } else {
            screen = MenuScreens.joinFailed(t, t.shorten(code, 12), outcome.message(code));
        }

        if (click == null) {
            send(user, screen);
        } else {
            show(click, screen);
        }
    }

    // ---------------------------------------------------------------- Data for the screens

    private Screen home(Texts t, User user) {
        return MenuScreens.home(t, user.getFirstName(), roomsOf(user.getId()), statsService.statsOf(user.getId()));
    }

    private Screen statistics(Texts t, User user) {
        return MenuScreens.statistics(t, statsService.statsOf(user.getId()), statsService.rankOf(user.getId()));
    }

    private Screen leaderboard(Texts t, User user) {
        return MenuScreens.leaderboard(t, statsService.leaderboard(MenuScreens.LEADERBOARD_SIZE),
                user.getId(), statsService.rankOf(user.getId()));
    }

    private Screen profile(Texts t, User user) {
        String fullName = user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
        Optional<PlayerStats> stats = statsService.statsOf(user.getId());
        return MenuScreens.profile(t, fullName, user.getUserName(), user.getId(), stats,
                statsService.rankOf(user.getId()), roomsOf(user.getId()).size());
    }

    /**
     * All rooms the player sits in or hosts. Running cases come first.
     */
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
