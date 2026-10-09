package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameService
{

    private final Map<Long, Game> games = new HashMap<>();

    public Game startGame(long chatId, List<Player> players, Player forcedPlayer, Role forcedRole) {
        Game game = new Game(players, forcedPlayer, forcedRole);
        games.put(chatId, game);
        return game;
    }

    public Optional<Game> getGame(long chatId)
    {
        return Optional.ofNullable(games.get(chatId));
    }
}