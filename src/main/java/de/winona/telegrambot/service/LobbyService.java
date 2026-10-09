package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LobbyService
{

    private final Map<Long, Lobby> lobbies = new HashMap<>();


    public boolean createLobby(long chatId) {
        if (lobbies.containsKey(chatId)) {
            return false;
        }
        lobbies.put(chatId, new Lobby());
        return true;
    }

    public Optional<Lobby> getLobby(long chatId) {
        return Optional.ofNullable(lobbies.get(chatId));
    }

    public void removeLobby(long chatId) {
        lobbies.remove(chatId);
    }
}