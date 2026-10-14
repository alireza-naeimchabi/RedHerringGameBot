package de.winona.telegrambot.telegram;

import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.view.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllGroupChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

/**
 * The only class that talks to Telegram. It knows how to show a {@link Screen}:
 * as a photo with caption or as a text message, new or by changing an existing message.
 */
public class Messenger {

    private static final Logger log = LoggerFactory.getLogger(Messenger.class);
    private static final String HTML = "HTML";
    private static final String NOT_MODIFIED = "message is not modified";

    private final TelegramClient client;
    /**
     * Telegram's id for each banner image after its first upload, so it is uploaded only once.
     */
    private final Map<String, String> uploadedBanners = new HashMap<>();
    private String botUsername;

    public Messenger(TelegramClient client) {
        this.client = client;
    }

    // ---------------------------------------------------------------- Sending

    /**
     * Sends a screen as a new message. Returns where it landed, or empty if sending failed.
     */
    public Optional<MessageRef> send(long chatId, Screen screen) {
        if (screen.hasBanner()) {
            if (!screen.fitsCaption()) {
                // Too long for a caption, for example a group message in two languages:
                // the artwork goes first and the text follows as its own message.
                sendPhoto(chatId, new Screen(screen.banner(), screen.language(), "", null));
                return sendText(chatId, screen);
            }
            Optional<MessageRef> photo = sendPhoto(chatId, screen);
            if (photo.isPresent()) {
                return photo;
            }
        }
        return sendText(chatId, screen);
    }

    /**
     * Shows a screen in place of an existing message, so menus do not flood the chat.
     * If the message cannot be changed, the screen is sent as a new message instead.
     */
    public Optional<MessageRef> show(MessageRef target, Screen screen) {
        if (target == null) {
            return Optional.empty();
        }
        boolean edited = false;
        if (screen.hasBanner() && target.photo()) {
            edited = editPhoto(target, screen);
        } else if (!screen.hasBanner() && !target.photo()) {
            edited = editText(target, screen);
        }
        if (edited) {
            return Optional.of(target);
        }
        delete(target);
        return send(target.chatId(), screen);
    }

    private Optional<MessageRef> sendText(long chatId, Screen screen) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(screen.html())
                .parseMode(HTML)
                .replyMarkup(screen.keyboard())
                .build();
        try {
            Message sent = client.execute(message);
            return Optional.of(new MessageRef(chatId, sent.getMessageId(), false));
        } catch (TelegramApiException e) {
            log.debug("Could not send a message to chat {}: {}", chatId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MessageRef> sendPhoto(long chatId, Screen screen) {
        if (!screen.fitsCaption()) {
            return Optional.empty();
        }
        String path = bannerPath(screen);
        try (InputStream image = openBanner(path)) {
            InputFile photo = photoFile(path, image);
            if (photo == null) {
                return Optional.empty();
            }
            Message sent = client.execute(SendPhoto.builder()
                    .chatId(chatId)
                    .photo(photo)
                    .caption(screen.html().isEmpty() ? null : screen.html())
                    .parseMode(screen.html().isEmpty() ? null : HTML)
                    .replyMarkup(screen.keyboard())
                    .build());
            rememberBanner(path, sent);
            return Optional.of(new MessageRef(chatId, sent.getMessageId(), true));
        } catch (TelegramApiException | IOException e) {
            log.debug("Could not send banner {} to chat {}: {}", screen.banner(), chatId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean editText(MessageRef target, Screen screen) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(target.chatId())
                .messageId(target.messageId())
                .text(screen.html())
                .parseMode(HTML)
                .replyMarkup(screen.keyboard())
                .build();
        try {
            client.execute(edit);
            return true;
        } catch (TelegramApiException e) {
            return isNotModified(e, target);
        }
    }

    private boolean editPhoto(MessageRef target, Screen screen) {
        if (!screen.fitsCaption()) {
            return false;
        }
        String path = bannerPath(screen);
        try (InputStream image = openBanner(path)) {
            InputMediaPhoto media = photoMedia(path, image);
            if (media == null) {
                return false;
            }
            media.setCaption(screen.html());
            media.setParseMode(HTML);
            Serializable result = client.execute(EditMessageMedia.builder()
                    .chatId(target.chatId())
                    .messageId(target.messageId())
                    .media(media)
                    .replyMarkup(screen.keyboard())
                    .build());
            if (result instanceof Message edited) {
                rememberBanner(path, edited);
            }
            return true;
        } catch (TelegramApiException | IOException e) {
            return e instanceof TelegramApiException apiError && isNotModified(apiError, target);
        }
    }

    private boolean isNotModified(TelegramApiException e, MessageRef target) {
        String reason = String.valueOf(e.getMessage());
        if (reason.contains(NOT_MODIFIED)) {
            return true; // The screen already shows exactly this content.
        }
        log.debug("Could not edit message {} in chat {}: {}", target.messageId(), target.chatId(), reason);
        return false;
    }

    public void delete(MessageRef target) {
        try {
            client.execute(DeleteMessage.builder()
                    .chatId(target.chatId())
                    .messageId(target.messageId())
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Could not delete message {} in chat {}: {}",
                    target.messageId(), target.chatId(), e.getMessage());
        }
    }

    /**
     * Pins a message in its chat without notifying anyone. Telegram only allows this if the bot
     * is an admin with the right to pin; otherwise nothing happens and the game goes on.
     */
    public void pin(MessageRef target) {
        try {
            client.execute(PinChatMessage.builder()
                    .chatId(target.chatId())
                    .messageId(target.messageId())
                    .disableNotification(true)
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Could not pin message {} in chat {}: {}",
                    target.messageId(), target.chatId(), e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Banners

    /**
     * Every language has its own artwork. A missing translation falls back to the default set.
     */
    private String bannerPath(Screen screen) {
        String localized = screen.banner().resourcePath(screen.language());
        return Messenger.class.getResource(localized) != null
                ? localized
                : screen.banner().resourcePath(Language.DEFAULT);
    }

    private InputStream openBanner(String path) {
        if (uploadedBanners.containsKey(path)) {
            return InputStream.nullInputStream();
        }
        InputStream image = Messenger.class.getResourceAsStream(path);
        if (image == null) {
            log.warn("Banner image {} is missing. The screen is shown without artwork.", path);
        }
        return image;
    }

    /**
     * The file name Telegram sees: "banner-home.jpg", or "fa-banner-home.jpg" for the Persian artwork.
     */
    private String uploadName(String path) {
        return path.substring("/ui/".length()).replace('/', '-');
    }

    private InputFile photoFile(String path, InputStream image) {
        String fileId = uploadedBanners.get(path);
        if (fileId != null) {
            return new InputFile(fileId);
        }
        return image == null ? null : new InputFile(image, uploadName(path));
    }

    private InputMediaPhoto photoMedia(String path, InputStream image) {
        String fileId = uploadedBanners.get(path);
        if (fileId != null) {
            return new InputMediaPhoto(fileId);
        }
        return image == null ? null : new InputMediaPhoto(image, uploadName(path));
    }

    private void rememberBanner(String path, Message message) {
        if (message == null || !message.hasPhoto()) {
            return;
        }
        List<PhotoSize> sizes = message.getPhoto();
        sizes.stream()
                .max(Comparator.comparingInt(size -> size.getWidth() == null ? 0 : size.getWidth()))
                .map(PhotoSize::getFileId)
                .ifPresent(fileId -> uploadedBanners.put(path, fileId));
    }

    // ---------------------------------------------------------------- Callbacks and bot info

    /**
     * Stops the loading animation of a pressed button. The text is optional.
     */
    public void answerCallback(String callbackId, String text, boolean asAlert) {
        try {
            client.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .text(text)
                    .showAlert(asAlert)
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Could not answer callback {}: {}", callbackId, e.getMessage());
        }
    }

    /**
     * Answers a pressed button by opening a link, for example the private chat with this bot.
     */
    public boolean answerCallbackWithUrl(String callbackId, String url) {
        try {
            client.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .url(url)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            log.debug("Could not answer callback {} with a link: {}", callbackId, e.getMessage());
            return false;
        }
    }

    /**
     * The bot's @username without the @, or an empty text if Telegram cannot be asked.
     */
    public String botUsername() {
        if (botUsername == null) {
            try {
                botUsername = client.execute(new GetMe()).getUserName();
            } catch (TelegramApiException e) {
                log.warn("Could not read the bot's username: {}", e.getMessage());
                return "";
            }
        }
        return botUsername == null ? "" : botUsername;
    }

    /**
     * Link that opens the private chat with this bot and passes a start parameter.
     */
    public Optional<String> deepLink(String startParameter) {
        String username = botUsername();
        return username.isBlank()
                ? Optional.empty()
                : Optional.of("https://t.me/" + username + "?start=" + startParameter);
    }

    /**
     * Fills Telegram's command menu, separately for private chats and groups.
     * The default language is shown to everyone; every other language is registered for
     * Telegram apps that are set to that language.
     */
    public void registerCommands() {
        List<String> privateCommands = List.of("start", "rooms", "join", "help", "roles", "stats",
                "leaderboard", "profile", "language");
        List<String> groupCommands = List.of("newgame", "room", "join", "leave", "startgame", "board",
                "lab", "accuse", "nextround", "endgame", "help");
        try {
            for (Language language : Language.values()) {
                Texts t = Texts.of(language);
                String languageCode = language == Language.DEFAULT ? null : language.code();
                client.execute(SetMyCommands.builder()
                        .commands(describe(t, "cmd.private.", privateCommands))
                        .scope(new BotCommandScopeAllPrivateChats())
                        .languageCode(languageCode)
                        .build());
                client.execute(SetMyCommands.builder()
                        .commands(describe(t, "cmd.group.", groupCommands))
                        .scope(new BotCommandScopeAllGroupChats())
                        .languageCode(languageCode)
                        .build());
            }
        } catch (TelegramApiException e) {
            log.warn("Could not register the command menu: {}", e.getMessage());
        }
    }

    private List<BotCommand> describe(Texts t, String keyPrefix, List<String> commands) {
        return commands.stream()
                .map(command -> new BotCommand(command, t.get(keyPrefix + command)))
                .toList();
    }
}
