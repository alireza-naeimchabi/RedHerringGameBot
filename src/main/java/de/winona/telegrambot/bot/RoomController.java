package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.model.RoomEnd;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.ui.Callbacks;
import de.winona.telegrambot.ui.GameScreens;
import de.winona.telegrambot.ui.Keyboard;
import de.winona.telegrambot.ui.MenuScreens;
import de.winona.telegrambot.ui.RoomSummary;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.ui.Theme;
import de.winona.telegrambot.util.RoomCodeGenerator;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

/**
 * Everything that happens to a room before the case opens:
 * creating it, taking and leaving seats, opening the case and closing the room.
 */
public class RoomController {

    private final Messenger messenger;
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final GameController gameController;

    public RoomController(Messenger messenger, LobbyService lobbyService,
                          GameService gameService, GameController gameController) {
        this.messenger = messenger;
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.gameController = gameController;
    }

    // ---------------------------------------------------------------- Creating a room

    /** Opens a room in a group and posts its room card. */
    public void createRoom(long groupChatId, String groupTitle, User creator) {
        if (gameService.getGame(groupChatId).isPresent()) {
            messenger.send(groupChatId, Screen.text(Theme.ICON_LIVE
                    + " A case is already underway in this group. Finish it first, or send /endgame."));
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
        if (messenger.send(host.userId(), GameScreens.seatTaken(lobby)).isPresent()) {
            lobby.addPlayer(host);
        }
        postRoomCard(lobby);
    }

    /** Posts the room card again, for example after the chat has moved on. */
    public void showRoomCard(long groupChatId) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isPresent()) {
            postRoomCard(lobby.get());
        } else if (gameService.getGame(groupChatId).isPresent()) {
            gameController.showSceneReport(groupChatId);
        } else {
            messenger.send(groupChatId, noRoomHere());
        }
    }

    private Screen noRoomHere() {
        return Screen.text(Theme.ICON_ROOMS + " There is no open room in this group.",
                Keyboard.create()
                        .row(Keyboard.button(Theme.ICON_CREATE + " Create Game Room",
                                Callbacks.newRoomHere()))
                        .build());
    }

    private void postRoomCard(Lobby lobby) {
        long chatId = lobby.getRoom().groupChatId();
        if (lobby.getCardMessageId() != null) {
            messenger.delete(new MessageRef(chatId, lobby.getCardMessageId(), false));
        }
        Integer messageId = messenger.send(chatId, GameScreens.lobbyCard(lobby, messenger.botUsername()))
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
        Integer messageId = messenger.show(card, GameScreens.lobbyCard(lobby, messenger.botUsername()))
                .map(MessageRef::messageId)
                .orElse(null);
        lobby.setCardMessageId(messageId);
    }

    private void replaceRoomCard(Lobby lobby, RoomEnd reason) {
        Screen ended = GameScreens.roomEnded(lobby.getRoom().code(), reason);
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
        if (confirmInPrivate && messenger.send(user.getId(), GameScreens.seatTaken(lobby)).isEmpty()) {
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
    public String leave(String code, User user) {
        Optional<Lobby> lobby = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobby.isEmpty()) {
            return "This room is no longer open.";
        }
        if (!lobby.get().removePlayer(user.getId())) {
            return "You are not seated in this room.";
        }
        refreshRoomCard(lobby.get());
        return "You left room " + lobby.get().getRoom().code() + ".";
    }

    // ---------------------------------------------------------------- Opening and closing

    /**
     * Opens the case if the host asks for it and enough players are seated.
     *
     * @param forcedRole for practice cases: the role the host wants to play, otherwise null
     * @return empty if the case was opened, otherwise the reason why not
     */
    public Optional<String> openCase(String code, User user, Role forcedRole) {
        Optional<Lobby> lobbyOpt = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobbyOpt.isEmpty()) {
            return Optional.of("This room is no longer open.");
        }
        Lobby lobby = lobbyOpt.get();
        Room room = lobby.getRoom();

        if (!room.isHost(user.getId())) {
            return Optional.of("Only the host, " + room.host().name() + ", can open the case.");
        }
        if (!lobby.hasEnoughPlayers()) {
            return Optional.of("Needs " + Theme.plural(lobby.missingPlayers(), "more detective", "more detectives")
                    + ". A case takes at least " + Lobby.MIN_PLAYERS + " players.");
        }
        if (forcedRole != null && !lobby.hasPlayer(user.getId())) {
            return Optional.of("Take a seat first. Then you can pick a role for a practice case.");
        }

        lobbyService.removeLobby(room.groupChatId(), RoomEnd.CASE_STARTED);
        replaceRoomCard(lobby, RoomEnd.CASE_STARTED);
        gameController.openCase(room, lobby.getPlayers(), forcedRole == null ? null : toPlayer(user), forcedRole);
        return Optional.empty();
    }

    /** Closes a room that has not started. Returns empty on success, otherwise the reason why not. */
    public Optional<String> closeRoom(String code, User user) {
        Optional<Lobby> lobbyOpt = lobbyService.findByCode(RoomCodeGenerator.normalize(code));
        if (lobbyOpt.isEmpty()) {
            return Optional.of("This room is no longer open.");
        }
        Lobby lobby = lobbyOpt.get();
        if (!lobby.getRoom().isHost(user.getId())) {
            return Optional.of("Only the host, " + lobby.getRoom().host().name() + ", can close the room.");
        }
        lobbyService.removeLobby(lobby.getRoom().groupChatId(), RoomEnd.CLOSED_BY_HOST);
        replaceRoomCard(lobby, RoomEnd.CLOSED_BY_HOST);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- Group commands

    public void joinByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            messenger.send(groupChatId, gameService.getGame(groupChatId).isPresent()
                    ? Screen.text(Theme.ICON_LIVE + " The case is already underway. Join the next one.")
                    : noRoomHere());
            return;
        }
        String code = lobby.get().getRoom().code();
        JoinOutcome outcome = join(code, user, true);
        if (outcome == JoinOutcome.NEEDS_PRIVATE_CHAT) {
            messenger.send(groupChatId, needsPrivateChat(user, code));
        } else if (!outcome.seated()) {
            messenger.send(groupChatId, Screen.text(Theme.ICON_WRONG + " " + Theme.esc(outcome.message(code))));
        }
    }

    private Screen needsPrivateChat(User user, String code) {
        String text = Theme.ICON_SECRET + " " + Theme.bold(user.getFirstName())
                + ", I need a private chat with you to send your secret role. Open it and you are seated.";
        return messenger.deepLink("join_" + code)
                .map(url -> Screen.text(text, Keyboard.create()
                        .row(Keyboard.link("Open Private Chat", url))
                        .build()))
                .orElse(Screen.text(text));
    }

    public void leaveByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            messenger.send(groupChatId, noRoomHere());
            return;
        }
        messenger.send(groupChatId, Screen.text(Theme.esc(leave(lobby.get().getRoom().code(), user))));
    }

    public void openCaseByCommand(long groupChatId, User user, Role forcedRole) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            messenger.send(groupChatId, noRoomHere());
            return;
        }
        openCase(lobby.get().getRoom().code(), user, forcedRole)
                .ifPresent(problem -> messenger.send(groupChatId,
                        Screen.text(Theme.ICON_WRONG + " " + Theme.esc(problem))));
    }

    /** /endgame while the room is still waiting: closes the room. Returns false if there is no room. */
    public boolean closeRoomByCommand(long groupChatId, User user) {
        Optional<Lobby> lobby = lobbyService.getLobby(groupChatId);
        if (lobby.isEmpty()) {
            return false;
        }
        closeRoom(lobby.get().getRoom().code(), user)
                .ifPresent(problem -> messenger.send(groupChatId,
                        Screen.text(Theme.ICON_WRONG + " " + Theme.esc(problem))));
        return true;
    }

    /** Practice helper for the host: fills three seats with test players. */
    public void addTestPlayers(long groupChatId, User user) {
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(groupChatId);
        if (lobbyOpt.isEmpty()) {
            messenger.send(groupChatId, noRoomHere());
            return;
        }
        Lobby lobby = lobbyOpt.get();
        if (!lobby.getRoom().isHost(user.getId())) {
            messenger.send(groupChatId, Screen.text(Theme.ICON_WRONG + " Only the host can add test players."));
            return;
        }
        lobby.addPlayer(new Player(-1, "Test Anna"));
        lobby.addPlayer(new Player(-2, "Test Ben"));
        lobby.addPlayer(new Player(-3, "Test Clara"));
        refreshRoomCard(lobby);
        messenger.send(groupChatId, Screen.text("Added test players for a practice case. "
                + "Practice cases are not counted in the statistics."));
    }

    // ---------------------------------------------------------------- Buttons

    /** Handles the buttons of the room card and of the private room screen ("r:..."). */
    public void onClick(Click click) {
        String action = click.part(1);
        String code = click.part(2);

        switch (action) {
            case "new" -> {
                if (click.message() == null || click.inPrivateChat()) {
                    click.alert("A room can only be created in a group chat.");
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
                        click.toast("The case is open.");
                        if (click.inPrivateChat()) {
                            gameController.caseFile(code, click.userId())
                                    .ifPresent(screen -> messenger.show(click.message(), screen));
                        }
                    });
            case "close" -> closeRoom(code, click.user()).ifPresentOrElse(
                    click::alert,
                    () -> {
                        click.toast("Room " + code + " is closed.");
                        if (click.inPrivateChat()) {
                            messenger.show(click.message(), MenuScreens.joinFailed(code,
                                    JoinOutcome.CLOSED.message(code)));
                        }
                    });
            default -> click.alert("This button is no longer valid.");
        }
    }

    private void onJoinClick(Click click, String code) {
        JoinOutcome outcome = join(code, click.user(), !click.inPrivateChat());
        switch (outcome) {
            case JOINED -> {
                click.toast("You are seated in room " + code + ".");
                refreshPrivateRoomScreen(click, code);
            }
            case ALREADY_SEATED -> click.toast("You are already seated.");
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
            messenger.show(click.message(), GameScreens.roomEnded(code, RoomEnd.EXPIRED));
        }
    }

    private void refreshPrivateRoomScreen(Click click, String code) {
        if (!click.inPrivateChat()) {
            return;
        }
        lobbyService.findByCode(code).ifPresent(lobby -> messenger.show(click.message(),
                MenuScreens.roomDetails(RoomSummary.of(lobby), click.userId())));
    }

    private Player toPlayer(User user) {
        return new Player(user.getId(), user.getFirstName());
    }
}
