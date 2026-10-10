package de.winona.telegrambot.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby
{

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 7;
    public static final Duration TIME_TO_LIVE = Duration.ofHours(2);

    private final Room room;
    private final List<Player> players = new ArrayList<>();
    private Integer cardMessageId;

    public Lobby(Room room) {
        this.room = room;
    }

    public Room getRoom() {
        return room;
    }

    public boolean addPlayer(Player player) {
        if (isFull() || hasPlayer(player.userId())) {
            return false;
        }
        players.add(player);
        return true;
    }

    public boolean removePlayer(long userId) {
        return players.removeIf(p -> p.userId() == userId);
    }

    public boolean hasPlayer(long userId) {
        return players.stream().anyMatch(p -> p.userId() == userId);
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public boolean hasEnoughPlayers() {
        return players.size() >= MIN_PLAYERS;
    }

    public int missingPlayers() {
        return Math.max(0, MIN_PLAYERS - players.size());
    }

    public boolean isExpired(Instant now) {
        return room.createdAt().plus(TIME_TO_LIVE).isBefore(now);
    }

    /** The message in the group that shows this lobby, so it can be updated in place. */
    public Integer getCardMessageId() {
        return cardMessageId;
    }

    public void setCardMessageId(Integer cardMessageId) {
        this.cardMessageId = cardMessageId;
    }
}
