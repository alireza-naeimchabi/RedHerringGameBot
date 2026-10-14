package de.winona.telegrambot.bot;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LanguageService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.view.Callbacks;
import de.winona.telegrambot.view.GameScreens;
import de.winona.telegrambot.view.Theme;
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
public class RedHerringBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedHerringBot.class);

    private final Messenger messenger;
    private final Localizer localizer;
    private final MenuController menuController;
    private final RoomController roomController;
    private final GameController gameController;

    public RedHerringBot(String botToken) {
        this(new OkHttpTelegramClient(botToken), new StatsService(), new LanguageService());
        messenger.registerCommands();
    }

    /**
     * For tests: their own Telegram client and statistics file, language choices kept in memory.
     */
    public RedHerringBot(TelegramClient telegramClient, StatsService statsService) {
        this(telegramClient, statsService, LanguageService.inMemory());
    }

    public RedHerringBot(TelegramClient telegramClient, StatsService statsService,
                         LanguageService languageService) {
        LobbyService lobbyService = new LobbyService();
        GameService gameService = new GameService();

        this.messenger = new Messenger(telegramClient);
        this.localizer = new Localizer(languageService);
        this.gameController = new GameController(messenger, localizer, gameService, statsService);
        this.roomController = new RoomController(messenger, localizer, lobbyService, gameService, gameController);
        this.menuController = new MenuController(messenger, localizer, lobbyService, gameService, statsService,
                roomController, gameController);
    }

    @Override
    public void consume(Update update) {
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
            if (privateChat && !gameController.onPrivateText(user, text)) {
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
                messenger.send(message.getChatId(), localizer.forUser(user.getId(),
                        t -> GameScreens.notice(t, Theme.ICON_ROOMS, Msg.of("private.group_command"))));
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
            case "/start", "/newgame" -> roomController.askRoomLanguage(chatId, user);
            case "/room" -> roomController.showRoomCard(chatId, user);
            case "/join" -> roomController.joinByCommand(chatId, user);
            case "/leave" -> roomController.leaveByCommand(chatId, user);
            case "/startgame" -> roomController.openCaseByCommand(chatId, user, null);
            case "/board" -> gameController.showEvidenceBoard(chatId, user);
            case "/lab" -> gameController.labTestByCommand(chatId, user);
            case "/accuse" -> gameController.accuseByCommand(chatId, user);
            case "/nextround" -> gameController.nextRoundByCommand(chatId, user);
            case "/endgame" -> endGame(chatId, user);
            case "/help" -> messenger.send(chatId, localizer.forUser(user.getId(),
                    t -> GameScreens.groupHelp(t, messenger.deepLink("how").orElse(null))));

            // Practice helpers for trying the bot alone
            case "/addtestplayers" -> roomController.addTestPlayers(chatId, user);
            case "/testmurderer" -> roomController.openCaseByCommand(chatId, user, Role.MURDERER);
            case "/testscientist" -> roomController.openCaseByCommand(chatId, user, Role.FORENSIC_SCIENTIST);
            case "/testinvestigator" -> roomController.openCaseByCommand(chatId, user, Role.INVESTIGATOR);
            default -> {
            }
        }
    }

    private void endGame(long chatId, User user) {
        if (roomController.closeRoomByCommand(chatId, user)) {
            return;
        }
        if (!gameController.abandonCase(chatId, user)) {
            messenger.send(chatId, localizer.forUser(user.getId(),
                    t -> GameScreens.notice(t, Theme.ICON_ROOMS, Msg.of("group.nothing_to_end"))));
        }
    }

    // ---------------------------------------------------------------- Buttons

    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData() == null ? "" : callback.getData();
        Click click = new Click(messenger, callback.getId(), callback.getFrom(),
                localizer.textsFor(callback.getFrom().getId()),
                messageOf(callback), chatTitleOf(callback), data.split(Callbacks.SEPARATOR));
        try {
            switch (click.part(0)) {
                case Callbacks.MENU -> menuController.onClick(click);
                case Callbacks.ROOM -> roomController.onClick(click);
                default -> gameController.onClick(click);
            }
        } catch (RuntimeException e) {
            log.warn("Could not handle button '{}'.", data, e);
            click.alert("err.button_invalid");
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
