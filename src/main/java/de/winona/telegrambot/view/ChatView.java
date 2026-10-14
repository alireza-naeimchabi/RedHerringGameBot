package de.winona.telegrambot.view;

import java.util.List;
import java.util.Optional;

/**
 * The View as the presenters see it (MVP): it shows screens and answers button presses.
 * Presenters only know this interface, never Telegram. {@link de.winona.telegrambot.telegram.Messenger}
 * implements it as a Facade over the Telegram API; tests replace Telegram with a fake.
 */

public interface ChatView {
    Optional<MessageRef> send(long chatId, Screen screen);

    Optional<MessageRef> show(MessageRef target, Screen screen);

    void delete(MessageRef target);

    void pin(MessageRef target);

    void answerCallback(String callbackId, String text, boolean asAlert);

    boolean answerCallbackWithUrl(String callbackId, String url);

    String botUsername();

    Optional<String> deepLink(String startParameter);

    /**
     * Shows the frames one after another in the same message, with a short pause between them.
     * Used for small animations, for example while the lab analyses a card.
     */
    Optional<MessageRef> animate(MessageRef target, List<Screen> frames);

    /**
     * Sends Telegram's rolling dice animation and lets it roll before the next message.
     */
    void sendDice(long chatId);
}
