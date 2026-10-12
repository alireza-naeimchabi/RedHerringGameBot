package de.winona.telegrambot.bot;

import org.telegram.telegrambots.meta.api.objects.User;

/**
 * One press on an inline button: who pressed it, on which message, and with which data.
 * Every press must be answered exactly once, otherwise the button keeps showing a spinner.
 */
public class Click {

    private final Messenger messenger;
    private final String callbackId;
    private final User user;
    private final MessageRef message;
    private final String chatTitle;
    private final String[] parts;
    private boolean answered;

    public Click(Messenger messenger, String callbackId, User user, MessageRef message,
                 String chatTitle, String[] parts) {
        this.messenger = messenger;
        this.callbackId = callbackId;
        this.user = user;
        this.message = message;
        this.chatTitle = chatTitle;
        this.parts = parts;
    }

    /** Title of the group the button was pressed in. Null in a private chat. */
    public String chatTitle() {
        return chatTitle;
    }

    public User user() {
        return user;
    }

    public long userId() {
        return user.getId();
    }

    /** The message the button belongs to. Null if Telegram no longer provides it. */
    public MessageRef message() {
        return message;
    }

    public boolean inPrivateChat() {
        return message != null && message.chatId() == userId();
    }

    public int partCount() {
        return parts.length;
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

    /** Short note at the top of the chat. */
    public void toast(String text) {
        answer(text, false);
    }

    /** Pop-up the user has to confirm. Use it when a button cannot do what was asked. */
    public void alert(String text) {
        answer(text, true);
    }

    /** Opens a link instead of showing text. Returns false if Telegram refused the link. */
    public boolean openLink(String url) {
        if (answered) {
            return false;
        }
        answered = messenger.answerCallbackWithUrl(callbackId, url);
        return answered;
    }

    /** Answers silently if no handler answered before. */
    public void finish() {
        answer(null, false);
    }

    private void answer(String text, boolean asAlert) {
        if (answered) {
            return;
        }
        answered = true;
        messenger.answerCallback(callbackId, text, asAlert);
    }
}
