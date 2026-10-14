package de.winona.telegrambot.telegram;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.view.ChatView;
import de.winona.telegrambot.view.MessageRef;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * One press on an inline button: who pressed it, on which message, and with which data.
 * Every press must be answered exactly once, otherwise the button keeps showing a spinner.
 * Answers are only seen by the person who pressed, so they use that person's language.
 */
public class Click {

    private final ChatView chatView;
    private final String callbackId;
    private final User user;
    private final Texts texts;
    private final MessageRef message;
    private final String chatTitle;
    private final String[] parts;
    private boolean answered;

    public Click(ChatView chatView, String callbackId, User user, Texts texts, MessageRef message,
                 String chatTitle, String[] parts) {
        this.chatView = chatView;
        this.callbackId = callbackId;
        this.user = user;
        this.texts = texts;
        this.message = message;
        this.chatTitle = chatTitle;
        this.parts = parts;
    }

    public User user() {
        return user;
    }

    public long userId() {
        return user.getId();
    }

    /**
     * The texts in the language of the person who pressed the button.
     */
    public Texts texts() {
        return texts;
    }

    /**
     * The message the button belongs to. Null if Telegram no longer provides it.
     */
    public MessageRef message() {
        return message;
    }

    /**
     * Title of the group the button was pressed in. Null in a private chat.
     */
    public String chatTitle() {
        return chatTitle;
    }

    public boolean inPrivateChat() {
        return message != null && message.chatId() == userId();
    }

    public String part(int index) {
        return index < parts.length ? parts[index] : "";
    }

    public int intPart(int index) {
        return Integer.parseInt(part(index));
    }

    public long longPart(int index) {
        return Long.parseLong(part(index));
    }

    /**
     * Short note at the top of the chat.
     */
    public void toast(Msg message) {
        answer(texts.get(message), false);
    }

    public void toast(String key, Object... args) {
        toast(Msg.of(key, args));
    }

    /**
     * Pop-up the user has to confirm. Use it when a button cannot do what was asked.
     */
    public void alert(Msg message) {
        answer(texts.get(message), true);
    }

    public void alert(String key, Object... args) {
        alert(Msg.of(key, args));
    }

    /**
     * Opens a link instead of showing text. Returns false if Telegram refused the link.
     */
    public boolean openLink(String url) {
        if (answered) {
            return false;
        }
        answered = chatView.answerCallbackWithUrl(callbackId, url);
        return answered;
    }

    /**
     * Answers with a text that is already translated, for example right after a language change.
     */
    public void toastText(String text) {
        answer(text, false);
    }

    /**
     * Answers silently if no handler answered before.
     */
    public void finish() {
        answer(null, false);
    }

    private void answer(String text, boolean asAlert) {
        if (answered) {
            return;
        }
        answered = true;
        chatView.answerCallback(callbackId, text, asAlert);
    }
}
