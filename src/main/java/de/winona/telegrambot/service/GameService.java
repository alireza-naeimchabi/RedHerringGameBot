package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameService {

    private final Map<Long, Game> games = new HashMap<>();

    public Game startGame(long chatId, List<Player> players) {
        Game game = new Game(players);
        games.put(chatId, game);
        return game;
    }
}