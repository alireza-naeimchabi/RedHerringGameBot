package de.winona.telegrambot.bot;

/**
 * Points to a message that was already sent, so it can be changed in place.
 */
public record MessageRef(long chatId, int messageId, boolean photo) {
}
