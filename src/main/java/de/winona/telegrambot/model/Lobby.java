package de.winona.telegrambot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby
{

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 7;

    private final List<Player> players = new ArrayList<>();

    public boolean addPlayer(Player player) {
        if (isFull() || hasPlayer(player.userId())) {
            return false;
        }
        players.add(player);
        return true;
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
}