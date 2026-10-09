package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameService
{

    private final Map<Long, Game> games = new HashMap<>();

    public Game startGame(long chatId, List<Player> players, Player forcedMurderer) {
        Game game = new Game(players, forcedMurderer);
        games.put(chatId, game);
        return game;
    }

    public Optional<Game> getGame(long chatId)
    {
        return Optional.ofNullable(games.get(chatId));
    }
}