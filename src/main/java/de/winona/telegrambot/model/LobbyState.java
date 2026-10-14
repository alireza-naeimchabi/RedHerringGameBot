package de.winona.telegrambot.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Read-only view of a room that is still waiting for players. The View only ever gets this interface.
 */
public interface LobbyState {
    int MIN_PLAYERS = 4;
    int MAX_PLAYERS = 7;
    Duration TIME_TO_LIVE = Duration.ofHours(2);

    Room getRoom();

    boolean hasPlayer(long userId);

    boolean isFull();

    List<Player> getPlayers();

    boolean hasEnoughPlayers();

    int missingPlayers();

    boolean isExpired(Instant now);

    Integer getCardMessageId();
}
