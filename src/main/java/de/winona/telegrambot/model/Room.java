package de.winona.telegrambot.model;

import java.time.Instant;

/**
 * A game room: the link between a Telegram group and a short code players can share.
 * The same room first holds a {@link Lobby} and later a running {@link Game}.
 */
public record Room(String code, long groupChatId, String groupTitle, Player host, Instant createdAt) {

    public boolean isHost(long userId) {
        return host.userId() == userId;
    }
}
