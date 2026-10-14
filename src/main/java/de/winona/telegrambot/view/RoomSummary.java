package de.winona.telegrambot.view;

import de.winona.telegrambot.model.GameState;
import de.winona.telegrambot.model.LobbyState;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Room;

import java.util.List;

/**
 * What the menu needs to know about a room, whether it is still a lobby or already a running case.
 */
public record RoomSummary(Room room, List<Player> players, boolean inProgress, int round) {

    public static RoomSummary of(LobbyState lobby) {
        return new RoomSummary(lobby.getRoom(), lobby.getPlayers(), false, 0);
    }

    public static RoomSummary of(GameState game) {
        return new RoomSummary(game.getRoom(), List.copyOf(game.getRoles().keySet()), true, game.getRound());
    }

    public String code() {
        return room.code();
    }

    public boolean hasPlayer(long userId) {
        return players.stream().anyMatch(player -> player.userId() == userId);
    }

    public List<String> playerNames() {
        return players.stream().map(Player::name).toList();
    }
}
