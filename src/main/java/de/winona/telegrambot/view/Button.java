package de.winona.telegrambot.view;

/**
 * One button under a message: a label and either callback data (sent back to the bot) or a link.
 * The View's own type; only the Telegram adapter turns it into Telegram's button.
 */
public record Button(String text, String callbackData, String url) {

    public String getText() {
        return text;
    }

    public String getCallbackData() {
        return callbackData;
    }

    public String getUrl() {
        return url;
    }
}