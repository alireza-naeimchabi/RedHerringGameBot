package de.winona.telegrambot.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby implements LobbyState {

    private final Room room;
    private final List<Player> players = new ArrayList<>();
    private Integer cardMessageId;

    public Lobby(Room room) {
        this.room = room;
    }

    @Override
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

    @Override
    public boolean hasPlayer(long userId) {
        return players.stream().anyMatch(p -> p.userId() == userId);
    }

    @Override
    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    @Override
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    @Override
    public boolean hasEnoughPlayers() {
        return players.size() >= MIN_PLAYERS;
    }

    @Override
    public int missingPlayers() {
        return Math.max(0, MIN_PLAYERS - players.size());
    }

    @Override
    public boolean isExpired(Instant now) {
        return room.createdAt().plus(TIME_TO_LIVE).isBefore(now);
    }

    /**
     * The message in the group that shows this lobby, so it can be updated in place.
     */
    @Override
    public Integer getCardMessageId() {
        return cardMessageId;
    }

    public void setCardMessageId(Integer cardMessageId) {
        this.cardMessageId = cardMessageId;
    }
}
