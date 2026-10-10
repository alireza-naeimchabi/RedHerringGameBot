package de.winona.telegrambot.bot;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.model.RoomEnd;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.ui.GameScreens;
import de.winona.telegrambot.ui.MenuScreens;
import de.winona.telegrambot.ui.RoomSummary;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.ui.Theme;
import de.winona.telegrambot.util.RoomCodeGenerator;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything that happens to a room before the case opens:
 * creating it, taking and leaving seats, opening the case and closing the room.
 */
public class RoomController {

    private final Messenger messenger;
    private final Localizer localizer;
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final GameController gameController;

    public RoomController(Messenger messenger, Localizer localizer, LobbyService lobbyService,
                          GameService gameService, GameController gameController) {
        this.messenger = messenger;
        this.localizer = localizer;
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.gameController = gameController;
    }

    // ---------------------------------------------------------------- Creating a room

    /** Opens a room in a group and posts its room card. */
    public void createRoom(long groupChatId, String groupTitle, User creator) {
        if (gameService.getGame(groupChatId).isPresent()) {
            sendNotice(groupChatId, creator, Theme.ICON_LIVE, Msg.of("group.case_running"));
            return;
        }
        Optional<Lobby> existing = lobbyService.getLobby(groupChatId);
        if (existing.isPresent()) {
            postRoomCard(existing.get());
            return;
        }

        Player host = toPlayer(creator);
        Lobby lobby = lobbyService.createLobby(groupChatId, groupTitle, host).orElseThrow();
        // The host is seated right away if the bot is allowed to write to them in private.
        if (messenger.send(host.userId(), seatConfirmation(host.userId(), lobby)).isPresent()) {
            lobby.addPlayer(host);
        }
        postRoomCard(lobby);
    }

    /** Posts the room card again, for example after the chat has moved on. */
    public void showRoomCard(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isPresent()) {
            postRoomCard(lobby.get());
        } else if (gameService.getGame(groupChatId).isPresent()) {
            gameController.showSceneReport(groupChatId, user);
        } else {
            sendNoRoomHere(groupChatId, user);
        }
    }

    private void postRoomCard(Lobby lobby) {
        long chatId = lobby.getRoom().groupChatId();
        if (lobby.getCardMessageId() != null) {
            messenger.delete(new MessageRef(chatId, lobby.getCardMessageId(), false));
        }
        Integer messageId = messenger.send(chatId, roomCard(lobby))
                .map(MessageRef::messageId)
                .orElse(null);
        lobby.setCardMessageId(messageId);
    }

    /** Updates the room card in the group after a seat changed. */
    private void refreshRoomCard(Lobby lobby) {
        if (lobby.getCardMessageId() == null) {
            postRoomCard(lobby);
            return;
        }
        MessageRef card = new MessageRef(lobby.getRoom().groupChatId(), lobby.getCardMessageId(), false);
        Integer messageId = messenger.show(card, roomCard(lobby))
                .map(MessageRef::messageId)
                .orElse(null);
        lobby.setCardMessageId(messageId);
    }

    private void replaceRoomCard(Lobby lobby, RoomEnd reason) {
        Screen ended = forRoom(lobby, t -> GameScreens.roomEnded(t, lobby.getRoom().code(), reason));
        if (lobby.getCardMessageId() == null) {
            messenger.send(lobby.getRoom().groupChatId(), ended);
        } else {
            messenger.show(new MessageRef(lobby.getRoom().groupChatId(), lobby.getCardMessageId(), false), ended);
        }
    }

    // ---------------------------------------------------------------- Seats

    /**
     * Seats a player in the room with the given code.
     *
     * @param confirmInPrivate true if the player joined from the group, so the bot first has to
     *                         check that it may write to them in private
     */
    public JoinOutcome join(String rawCode, User user, boolean confirmInPrivate) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Optional<Lobby> lobbyOpt = lobbyService.findByCode(code);
        if (lobbyOpt.isEmpty()) {
            return whyNotJoinable(code);
        }

        Lobby lobby = lobbyOpt.get();
        if (lobby.hasPlayer(user.getId())) {
            return JoinOutcome.ALREADY_SEATED;
        }
        if (lobby.isFull()) {
            return JoinOutcome.FULL;
        }
        if (confirmInPrivate && messenger.send(user.getId(), seatConfirmation(user.getId(), lobby)).isEmpty()) {
            return JoinOutcome.NEEDS_PRIVATE_CHAT;
        }

        lobby.addPlayer(toPlayer(user));
        refreshRoomCard(lobby);
        return JoinOutcome.JOINED;
    }

    private JoinOutcome whyNotJoinable(String code) {
        if (gameService.findByCode(code).isPresent()) {
            return JoinOutcome.IN_PROGRESS;
        }
        return lobbyService.endOf(code)
                .map(end -> switch (end) {
                    case CLOSED_BY_HOST -> JoinOutcome.CLOSED;
                    case EXPIRED -> JoinOutcome.EXPIRED;
                    case CASE_STARTED -> JoinOutcome.FINISHED;
                })
                .orElse(JoinOutcome.UNKNOWN);
    }

    /** Frees the player's seat. Returns a sentence for the player. */
    public Msg leave(String code, User user) {
        Optional<Lobby> lobby = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobby.isEmpty()) {
            return Msg.of("err.room_gone");
        }
        if (!lobby.get().removePlayer(user.getId())) {
            return Msg.of("err.not_seated");
        }
        refreshRoomCard(lobby.get());
        return Msg.of("room.left", lobby.get().getRoom().code());
    }

    // ---------------------------------------------------------------- Opening and closing

    /**
     * Opens the case if the host asks for it and enough players are seated.
     *
     * @param forcedRole for practice cases: the role the host wants to play, otherwise null
     * @return empty if the case was opened, otherwise the reason why not
     */
    public Optional<Msg> openCase(String code, User user, Role forcedRole) {
        Optional<Lobby> lobbyOpt = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobbyOpt.isEmpty()) {
            return Optional.of(Msg.of("err.room_gone"));
        }
        Lobby lobby = lobbyOpt.get();
        Room room = lobby.getRoom();

        if (!room.isHost(user.getId())) {
            return Optional.of(Msg.of("err.only_host_open", room.host().name()));
        }
        if (!lobby.hasEnoughPlayers()) {
            return Optional.of(Msg.of(lobby.missingPlayers() == 1 ? "err.need_players.one" : "err.need_players.other",
                    lobby.missingPlayers(), Lobby.MIN_PLAYERS));
        }
        if (forcedRole != null && !lobby.hasPlayer(user.getId())) {
            return Optional.of(Msg.of("err.seat_first"));
        }

        lobbyService.removeLobby(room.groupChatId(), RoomEnd.CASE_STARTED);
        replaceRoomCard(lobby, RoomEnd.CASE_STARTED);
        gameController.openCase(room, lobby.getPlayers(), forcedRole == null ? null : toPlayer(user), forcedRole);
        return Optional.empty();
    }

    /** Closes a room that has not started. Returns empty on success, otherwise the reason why not. */
    public Optional<Msg> closeRoom(String code, User user) {
        Optional<Lobby> lobbyOpt = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobbyOpt.isEmpty()) {
            return Optional.of(Msg.of("err.room_gone"));
        }
        Lobby lobby = lobbyOpt.get();
        if (!lobby.getRoom().isHost(user.getId())) {
            return Optional.of(Msg.of("err.only_host_close", lobby.getRoom().host().name()));
        }
        lobbyService.removeLobby(lobby.getRoom().groupChatId(), RoomEnd.CLOSED_BY_HOST);
        replaceRoomCard(lobby, RoomEnd.CLOSED_BY_HOST);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- Group commands

    public void joinByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            if (gameService.getGame(groupChatId).isPresent()) {
                sendNotice(groupChatId, user, Theme.ICON_LIVE, Msg.of("group.case_underway_join"));
            } else {
                sendNoRoomHere(groupChatId, user);
            }
            return;
        }
        String code = lobby.get().getRoom().code();
        JoinOutcome outcome = join(code, user, true);
        if (outcome == JoinOutcome.NEEDS_PRIVATE_CHAT) {
            String deepLink = messenger.deepLink("join_" + code).orElse(null);
            messenger.send(groupChatId, localizer.forUser(user.getId(),
                    t -> GameScreens.needsPrivateChat(t, user.getFirstName(), deepLink)));
        } else if (!outcome.seated()) {
            sendNotice(groupChatId, user, Theme.ICON_WRONG, outcome.message(code));
        }
    }

    public void leaveByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            sendNoRoomHere(groupChatId, user);
            return;
        }
        Msg result = leave(lobby.get().getRoom().code(), user);
        messenger.send(groupChatId, localizer.forUser(user.getId(), t -> t.text(Theme.esc(t.get(result)))));
    }

    public void openCaseByCommand(long groupChatId, User user, Role forcedRole) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            sendNoRoomHere(groupChatId, user);
            return;
        }
        openCase(lobby.get().getRoom().code(), user, forcedRole)
                .ifPresent(problem -> sendNotice(groupChatId, user, Theme.ICON_WRONG, problem));
    }

    /** /endgame while the room is still waiting: closes the room. Returns false if there is no room. */
    public boolean closeRoomByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            return false;
        }
        closeRoom(lobby.get().getRoom().code(), user)
                .ifPresent(problem -> sendNotice(groupChatId, user, Theme.ICON_WRONG, problem));
        return true;
    }

    /** Practice helper for the host: fills three seats with test players. */
    public void addTestPlayers(long groupChatId, User user) {
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(groupChatId);
        if (lobbyOpt.isEmpty()) {
            sendNoRoomHere(groupChatId, user);
            return;
        }
        Lobby lobby = lobbyOpt.get();
        if (!lobby.getRoom().isHost(user.getId())) {
            sendNotice(groupChatId, user, Theme.ICON_WRONG, Msg.of("err.only_host_test"));
            return;
        }
        lobby.addPlayer(new Player(-1, "Test Anna"));
        lobby.addPlayer(new Player(-2, "Test Ben"));
        lobby.addPlayer(new Player(-3, "Test Clara"));
        refreshRoomCard(lobby);
        messenger.send(groupChatId, localizer.forUser(user.getId(), t -> t.text(t.get("group.test_added"))));
    }

    // ---------------------------------------------------------------- Buttons

    /** Handles the buttons of the room card and of the private room screen ("r:..."). */
    public void onClick(Click click) {
        String action = click.part(1);
        String code = click.part(2);

        switch (action) {
            case "new" -> {
                if (click.message() == null || click.inPrivateChat()) {
                    click.alert("err.create_in_group");
                    return;
                }
                createRoom(click.message().chatId(), click.chatTitle(), click.user());
            }
            case "join" -> onJoinClick(click, code);
            case "leave" -> {
                click.toast(leave(code, click.user()));
                refreshPrivateRoomScreen(click, code);
            }
            case "start" -> openCase(code, click.user(), null).ifPresentOrElse(
                    click::alert,
                    () -> {
                        click.toast("toast.case_open");
                        if (click.inPrivateChat()) {
                            gameController.caseFile(code, click.userId())
                                    .ifPresent(screen -> messenger.show(click.message(), screen));
                        }
                    });
            case "close" -> closeRoom(code, click.user()).ifPresentOrElse(
                    click::alert,
                    () -> {
                        click.toast("toast.room_closed", code);
                        if (click.inPrivateChat()) {
                            messenger.show(click.message(), MenuScreens.joinFailed(click.texts(), code,
                                    JoinOutcome.CLOSED.message(code)));
                        }
                    });
            default -> click.alert("err.button_invalid");
        }
    }

    private void onJoinClick(Click click, String code) {
        JoinOutcome outcome = join(code, click.user(), !click.inPrivateChat());
        switch (outcome) {
            case JOINED -> {
                click.toast(outcome.message(code));
                refreshPrivateRoomScreen(click, code);
            }
            case ALREADY_SEATED -> click.toast(outcome.message(code));
            case NEEDS_PRIVATE_CHAT -> {
                boolean opened = messenger.deepLink("join_" + code).map(click::openLink).orElse(false);
                if (!opened) {
                    click.alert(outcome.message(code));
                }
            }
            default -> {
                click.alert(outcome.message(code));
                expireStaleCard(click, code);
            }
        }
    }

    /** If someone presses a button on the card of an expired room, the card is replaced. */
    private void expireStaleCard(Click click, String code) {
        boolean expired = lobbyService.endOf(code).filter(end -> end == RoomEnd.EXPIRED).isPresent();
        if (expired && click.message() != null && !click.inPrivateChat()) {
            messenger.show(click.message(), GameScreens.roomEnded(click.texts(), code, RoomEnd.EXPIRED));
        }
    }

    private void refreshPrivateRoomScreen(Click click, String code) {
        if (!click.inPrivateChat()) {
            return;
        }
        lobbyService.findByCode(code).ifPresent(lobby -> messenger.show(click.message(),
                MenuScreens.roomDetails(click.texts(), RoomSummary.of(lobby), click.userId())));
    }

    // ---------------------------------------------------------------- Who sees what, in which language

    /** The room card is one message for the whole group, so it is written for everyone in the room. */
    private Screen roomCard(Lobby lobby) {
        return forRoom(lobby, t -> GameScreens.lobbyCard(t, lobby, messenger.botUsername()));
    }

    private Screen forRoom(Lobby lobby, Function<Texts, Screen> view) {
        List<Player> readers = new ArrayList<>(lobby.getPlayers());
        readers.add(lobby.getRoom().host());
        return localizer.forPlayers(readers, view);
    }

    private Screen seatConfirmation(long userId, Lobby lobby) {
        return localizer.forUser(userId, t -> GameScreens.seatTaken(t, lobby));
    }

    /** Answers a command in the group, in the language of the player who sent it. */
    private void sendNotice(long groupChatId, User user, String icon, Msg message) {
        messenger.send(groupChatId, localizer.forUser(user.getId(), t -> GameScreens.notice(t, icon, message)));
    }

    private void sendNoRoomHere(long groupChatId, User user) {
        messenger.send(groupChatId, localizer.forUser(user.getId(), GameScreens::noRoomHere));
    }

    private Player toPlayer(User user) {
        return new Player(user.getId(), user.getFirstName());
    }
}
