package de.winona.telegrambot.view;

/**
 * Points to a message that was already sent, so it can be changed in place.
 */
public record MessageRef(long chatId, int messageId, boolean photo) {
}
