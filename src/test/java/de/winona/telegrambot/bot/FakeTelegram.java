package de.winona.telegrambot.bot;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for Telegram. It remembers every message the bot shows, lets a test press buttons,
 * and checks each message against Telegram's limits, so mistakes show up without a real chat.
 */
public class FakeTelegram {

    public static final String BOT_USERNAME = "TestCaseBot";

    /**
     * One message as a chat currently shows it.
     */
    public static final class Shown {
        public final long chatId;
        public final int messageId;
        public String html;
        public InlineKeyboardMarkup keyboard;
        public boolean photo;
        public String banner;

        Shown(long chatId, int messageId) {
            this.chatId = chatId;
            this.messageId = messageId;
        }

        public List<InlineKeyboardButton> buttons() {
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            if (keyboard != null) {
                keyboard.getKeyboard().forEach(buttons::addAll);
            }
            return buttons;
        }

        public Optional<InlineKeyboardButton> button(String labelPart) {
            return buttons().stream().filter(button -> button.getText().contains(labelPart)).findFirst();
        }
    }

    /**
     * How the bot answered the last button press.
     */
    public record Answer(String text, boolean alert, String url) {
    }

    private static final Set<String> ALLOWED_TAGS = Set.of("b", "i", "s", "code", "pre", "blockquote");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z]+)[^>]*>");

    private final Map<String, Shown> messages = new LinkedHashMap<>();
    private final Set<Long> blockedChats = new HashSet<>();
    private final List<String> problems = new ArrayList<>();
    private final TelegramClient client;
    private int nextMessageId = 100;
    private int nextCallbackId = 1;
    private Answer lastAnswer;

    public FakeTelegram() {
        client = (TelegramClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{TelegramClient.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("execute")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    try {
                        return execute(args[0]);
                    } catch (TelegramApiException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw new InvocationTargetException(e);
                    }
                });
    }

    public TelegramClient client() {
        return client;
    }

    /**
     * A user who never started the bot: private messages to them fail, as in real Telegram.
     */
    public void blockPrivateChat(long userId) {
        blockedChats.add(userId);
    }

    public void allowPrivateChat(long userId) {
        blockedChats.remove(userId);
    }

    public List<String> problems() {
        return problems;
    }

    public Answer lastAnswer() {
        return lastAnswer;
    }

    // ---------------------------------------------------------------- What the bot calls

    private Object execute(Object method) throws TelegramApiException {
        if (method instanceof GetMe) {
            User bot = new User(1L, "Bot", true);
            bot.setUserName(BOT_USERNAME);
            return bot;
        }
        if (method instanceof SendMessage send) {
            long chatId = Long.parseLong(send.getChatId());
            failIfBlocked(chatId);
            Shown shown = newMessage(chatId);
            shown.html = send.getText();
            shown.keyboard = (InlineKeyboardMarkup) send.getReplyMarkup();
            check(shown, send.getParseMode());
            return toMessage(shown);
        }
        if (method instanceof SendPhoto send) {
            long chatId = Long.parseLong(send.getChatId());
            failIfBlocked(chatId);
            Shown shown = newMessage(chatId);
            shown.photo = true;
            shown.banner = send.getPhoto().isNew() ? send.getPhoto().getMediaName() : send.getPhoto().getAttachName();
            shown.html = send.getCaption() == null ? "" : send.getCaption();
            shown.keyboard = (InlineKeyboardMarkup) send.getReplyMarkup();
            if (send.getPhoto().isNew() && send.getPhoto().getNewMediaStream() == null) {
                problems.add("Photo without image data in chat " + chatId);
            }
            if (!shown.html.isEmpty()) {
                check(shown, send.getParseMode());
            }
            return toMessage(shown);
        }
        if (method instanceof EditMessageText edit) {
            Shown shown = existing(edit.getChatId(), edit.getMessageId());
            if (shown.photo) {
                throw new TelegramApiException("Bad Request: there is no text in the message to edit");
            }
            if (edit.getText().equals(shown.html) && sameKeyboard(shown.keyboard, edit.getReplyMarkup())) {
                throw new TelegramApiException("Bad Request: message is not modified");
            }
            shown.html = edit.getText();
            shown.keyboard = edit.getReplyMarkup();
            check(shown, edit.getParseMode());
            return toMessage(shown);
        }
        if (method instanceof EditMessageMedia edit) {
            Shown shown = existing(edit.getChatId(), edit.getMessageId());
            shown.photo = true;
            shown.banner = edit.getMedia().isNewMedia() ? edit.getMedia().getMediaName() : edit.getMedia().getMedia();
            shown.html = edit.getMedia().getCaption();
            shown.keyboard = edit.getReplyMarkup();
            check(shown, edit.getMedia().getParseMode());
            return toMessage(shown);
        }
        if (method instanceof DeleteMessage delete) {
            messages.remove(key(Long.parseLong(delete.getChatId()), delete.getMessageId()));
            return true;
        }
        if (method instanceof AnswerCallbackQuery answer) {
            if (lastAnswer != null) {
                problems.add("A button press was answered twice.");
            }
            lastAnswer = new Answer(answer.getText(), Boolean.TRUE.equals(answer.getShowAlert()), answer.getUrl());
            return true;
        }
        return true; // SetMyCommands and other calls that need no simulation
    }

    private void failIfBlocked(long chatId) throws TelegramApiException {
        if (blockedChats.contains(chatId)) {
            throw new TelegramApiException("Forbidden: bot can't initiate conversation with a user");
        }
    }

    private Shown newMessage(long chatId) {
        Shown shown = new Shown(chatId, nextMessageId++);
        messages.put(key(chatId, shown.messageId), shown);
        return shown;
    }

    private Shown existing(String chatId, Integer messageId) throws TelegramApiException {
        Shown shown = messages.get(key(Long.parseLong(chatId), messageId));
        if (shown == null) {
            throw new TelegramApiException("Bad Request: message to edit not found");
        }
        return shown;
    }

    private boolean sameKeyboard(InlineKeyboardMarkup a, InlineKeyboardMarkup b) {
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private Message toMessage(Shown shown) {
        Message message = new Message();
        message.setMessageId(shown.messageId);
        message.setChat(chat(shown.chatId, null));
        if (shown.photo) {
            PhotoSize size = new PhotoSize();
            size.setFileId("file-" + shown.banner);
            size.setWidth(1280);
            message.setPhoto(List.of(size));
        } else {
            message.setText(shown.html);
        }
        return message;
    }

    // ---------------------------------------------------------------- Telegram's rules

    private void check(Shown shown, String parseMode) {
        String where = "chat " + shown.chatId + " message " + shown.messageId + ": ";
        if (!"HTML".equals(parseMode)) {
            problems.add(where + "parse mode is not HTML");
        }
        if (shown.html == null || shown.html.isBlank()) {
            problems.add(where + "empty text");
            return;
        }
        String visible = TAG.matcher(shown.html).replaceAll("")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        int limit = shown.photo ? 1024 : 4096;
        if (visible.length() > limit) {
            problems.add(where + "text has " + visible.length() + " characters, limit is " + limit);
        }

        Deque<String> open = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(shown.html);
        while (matcher.find()) {
            String tag = matcher.group(2);
            if (!ALLOWED_TAGS.contains(tag)) {
                problems.add(where + "tag <" + tag + "> is not used by this bot's design");
            } else if (matcher.group(1).isEmpty()) {
                open.push(tag);
            } else if (open.isEmpty() || !open.pop().equals(tag)) {
                problems.add(where + "closing tag </" + tag + "> does not match: " + shown.html);
            }
        }
        if (!open.isEmpty()) {
            problems.add(where + "unclosed tags " + open + ": " + shown.html);
        }
        if (Pattern.compile("&(?!lt;|gt;|amp;)").matcher(shown.html).find()) {
            problems.add(where + "unescaped & in: " + shown.html);
        }

        for (InlineKeyboardButton button : shown.buttons()) {
            if (button.getText() == null || button.getText().isBlank()) {
                problems.add(where + "button without label");
            }
            boolean hasData = button.getCallbackData() != null;
            boolean hasUrl = button.getUrl() != null;
            if (hasData == hasUrl) {
                problems.add(where + "button '" + button.getText() + "' needs exactly one action");
            }
            if (hasData && button.getCallbackData().getBytes(StandardCharsets.UTF_8).length > 64) {
                problems.add(where + "callback data too long: " + button.getCallbackData());
            }
        }
        if (shown.keyboard != null) {
            shown.keyboard.getKeyboard().forEach(row -> {
                if (row.size() > 4) {
                    problems.add(where + "more than 4 buttons in one row");
                }
            });
        }
    }

    // ---------------------------------------------------------------- What a test does

    public static User user(long id, String firstName) {
        User user = new User(id, firstName, false);
        user.setUserName(firstName.toLowerCase());
        return user;
    }

    private static Chat chat(long chatId, String title) {
        Chat chat = new Chat(chatId, chatId > 0 ? "private" : "supergroup");
        chat.setTitle(title);
        return chat;
    }

    /**
     * A text message from a user. Use the user's own id as chat id for a private chat.
     */
    public static Update text(User from, long chatId, String groupTitle, String text) {
        Message message = new Message();
        message.setMessageId(1);
        message.setFrom(from);
        message.setChat(chat(chatId, groupTitle));
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    /**
     * A press on a button with the given callback data under the given message.
     */
    public Update press(User from, Shown shown, String groupTitle, String data) {
        lastAnswer = null;
        CallbackQuery callback = new CallbackQuery();
        callback.setId("cb" + nextCallbackId++);
        callback.setFrom(from);
        callback.setData(data);
        Message message = toMessage(shown);
        message.setChat(chat(shown.chatId, groupTitle));
        callback.setMessage(message);
        Update update = new Update();
        update.setCallbackQuery(callback);
        return update;
    }

    // ---------------------------------------------------------------- What a test looks at

    public List<Shown> chat(long chatId) {
        return messages.values().stream().filter(shown -> shown.chatId == chatId).toList();
    }

    public Shown last(long chatId) {
        List<Shown> shown = chat(chatId);
        if (shown.isEmpty()) {
            throw new AssertionError("No message in chat " + chatId);
        }
        return shown.get(shown.size() - 1);
    }

    /**
     * The newest message in a chat whose text contains the given part.
     */
    public Optional<Shown> find(long chatId, String textPart) {
        List<Shown> shown = chat(chatId);
        for (int i = shown.size() - 1; i >= 0; i--) {
            if (shown.get(i).html != null && shown.get(i).html.contains(textPart)) {
                return Optional.of(shown.get(i));
            }
        }
        return Optional.empty();
    }

    private static String key(long chatId, int messageId) {
        return chatId + "/" + messageId;
    }
}
