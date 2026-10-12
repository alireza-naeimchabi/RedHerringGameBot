package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameService {

    private final Map<Long, Game> games = new HashMap<>();

    public Game startGame(Room room, List<Player> players, Player forcedPlayer, Role forcedRole) {
        Game game = new Game(room, players, forcedPlayer, forcedRole);
        games.put(room.groupChatId(), game);
        return game;
    }

    public Optional<Game> getGame(long chatId) {
        return Optional.ofNullable(games.get(chatId));
    }

    public Optional<Game> findByCode(String code) {
        return games.values().stream()
                .filter(game -> game.getRoom().code().equals(code))
                .findFirst();
    }

    public List<Game> gamesOf(long userId) {
        return games.values().stream()
                .filter(game -> game.getPlayer(userId).isPresent())
                .toList();
    }

    public void endGame(long chatId) {
        games.remove(chatId);
    }
}
