package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.ui.Callbacks;
import de.winona.telegrambot.ui.Keyboard;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.ui.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Entry point for everything Telegram sends. This class only decides who is responsible:
 * the dashboard ({@link MenuController}), the rooms ({@link RoomController})
 * or the running case ({@link GameController}).
 */
public class RedHerringBot implements LongPollingSingleThreadUpdateConsumer
{

    private static final Logger log = LoggerFactory.getLogger(RedHerringBot.class);

    private final Messenger messenger;
    private final MenuController menuController;
    private final RoomController roomController;
    private final GameController gameController;

    public RedHerringBot(String botToken) {
        this(new OkHttpTelegramClient(botToken), new StatsService());
        messenger.registerCommands();
    }

    /** Lets tests plug in their own Telegram client and statistics file. */
    public RedHerringBot(TelegramClient telegramClient, StatsService statsService) {
        LobbyService lobbyService = new LobbyService();
        GameService gameService = new GameService();

        this.messenger = new Messenger(telegramClient);
        this.gameController = new GameController(messenger, gameService, statsService);
        this.roomController = new RoomController(messenger, lobbyService, gameService, gameController);
        this.menuController = new MenuController(messenger, lobbyService, gameService, statsService,
                roomController, gameController);
    }

    @Override
    public void consume(Update update)
    {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (RuntimeException e) {
            // One broken update must never stop the bot for everyone else.
            log.error("Could not process an update.", e);
        }
    }

    // ---------------------------------------------------------------- Messages

    private void handleMessage(Message message) {
        User user = message.getFrom();
        if (user == null) {
            return;
        }
        String text = message.getText().trim();
        boolean privateChat = message.getChat().isUserChat();

        if (!text.startsWith("/")) {
            if (privateChat) {
                menuController.onText(text, user);
            }
            return;
        }

        String[] words = text.split("\\s+", 2);
        String[] commandAndBot = words[0].split("@", 2);
        String command = commandAndBot[0].toLowerCase();
        String argument = words.length > 1 ? words[1].trim() : "";

        if (commandAndBot.length > 1 && isForAnotherBot(commandAndBot[1])) {
            return;
        }

        if (privateChat) {
            if (!menuController.onCommand(command, argument, user)) {
                messenger.send(message.getChatId(), Screen.text(Theme.ICON_ROOMS
                        + " This command works in a group chat during a case. Send /start for the dashboard."));
            }
        } else if (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat()) {
            handleGroupCommand(command, message, user);
        }
    }

    private boolean isForAnotherBot(String addressedBot) {
        String ownName = messenger.botUsername();
        return !ownName.isBlank() && !ownName.equalsIgnoreCase(addressedBot);
    }

    private void handleGroupCommand(String command, Message message, User user) {
        long chatId = message.getChatId();

        switch (command) {
            case "/start", "/newgame" -> roomController.createRoom(chatId, message.getChat().getTitle(), user);
            case "/room" -> roomController.showRoomCard(chatId);
            case "/join" -> roomController.joinByCommand(chatId, user);
            case "/leave" -> roomController.leaveByCommand(chatId, user);
            case "/startgame" -> roomController.openCaseByCommand(chatId, user, null);
            case "/board" -> gameController.showEvidenceBoard(chatId);
            case "/accuse" -> gameController.accuseByCommand(chatId, user);
            case "/nextround" -> gameController.nextRoundByCommand(chatId, user);
            case "/endgame" -> endGame(chatId, user);
            case "/help" -> messenger.send(chatId, groupHelp());

            // Practice helpers for trying the bot alone
            case "/addtestplayers" -> roomController.addTestPlayers(chatId, user);
            case "/testmurderer" -> roomController.openCaseByCommand(chatId, user, Role.MURDERER);
            case "/testscientist" -> roomController.openCaseByCommand(chatId, user, Role.FORENSIC_SCIENTIST);
            case "/testinvestigator" -> roomController.openCaseByCommand(chatId, user, Role.INVESTIGATOR);
            default -> { }
        }
    }

    private void endGame(long chatId, User user) {
        if (roomController.closeRoomByCommand(chatId, user)) {
            return;
        }
        if (!gameController.abandonCase(chatId, user)) {
            messenger.send(chatId, Screen.text(Theme.ICON_ROOMS + " There is nothing to end in this group."));
        }
    }

    private Screen groupHelp() {
        String text = Theme.heading(Theme.ICON_HOW_TO, "HOW TO PLAY", "The short version") + "\n\n"
                + "One of you is the murderer. The Forensic Scientist knows the solution and answers "
                + "scene tiles. Everyone else has one attempt to name the murderer, "
                + "the Means of Murder and the Key Evidence within 3 rounds.\n\n"
                + "/newgame creates a room, /accuse solves the crime, /board shows the cards.";
        return messenger.deepLink("how")
                .map(url -> Screen.text(text, Keyboard.create()
                        .row(Keyboard.link(Theme.ICON_HOW_TO + " Open the Full Guide", url))
                        .build()))
                .orElse(Screen.text(text));
    }

    // ---------------------------------------------------------------- Buttons

    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData() == null ? "" : callback.getData();
        Click click = new Click(messenger, callback.getId(), callback.getFrom(),
                messageOf(callback), chatTitleOf(callback), data.split(Callbacks.SEPARATOR));
        try {
            switch (click.part(0)) {
                case Callbacks.MENU -> menuController.onClick(click);
                case Callbacks.ROOM -> roomController.onClick(click);
                default -> gameController.onClick(click);
            }
        } catch (RuntimeException e) {
            log.warn("Could not handle button '{}'.", data, e);
            click.alert("This button is no longer valid.");
        } finally {
            click.finish();
        }
    }

    private MessageRef messageOf(CallbackQuery callback) {
        MaybeInaccessibleMessage message = callback.getMessage();
        if (message == null || message.getChatId() == null || message.getMessageId() == null) {
            return null;
        }
        boolean photo = message instanceof Message accessible && accessible.hasPhoto();
        return new MessageRef(message.getChatId(), message.getMessageId(), photo);
    }

    private String chatTitleOf(CallbackQuery callback) {
        MaybeInaccessibleMessage message = callback.getMessage();
        return message == null || message.getChat() == null ? null : message.getChat().getTitle();
    }
}
