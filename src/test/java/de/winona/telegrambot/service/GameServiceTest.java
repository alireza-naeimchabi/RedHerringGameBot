package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Room;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roles are dealt completely at random: earlier cases in the group never matter.
 */
class GameServiceTest {

    private static final Player HOST = new Player(1, "Host");
    private static final List<Player> PLAYERS = List.of(HOST, new Player(2, "Sara"), new Player(3, "Ben"),
            new Player(4, "Clara"), new Player(5, "Dana"));

    private Game playCase(GameService service) {
        Game game = service.startGame(new Room("TEST", -1, "Group", HOST, Instant.now()), PLAYERS, null, null);
        service.endGame(-1);
        return game;
    }

    @Test
    void everyoneCanGetEveryRole() {
        GameService service = new GameService();
        Set<Player> scientists = new HashSet<>();
        Set<Player> murderers = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            Game game = playCase(service);
            scientists.add(game.getForensicScientist());
            murderers.add(game.getMurderer());
        }
        assertEquals(PLAYERS.size(), scientists.size());
        assertEquals(PLAYERS.size(), murderers.size());
    }

    @Test
    void thePreviousCaseDoesNotLimitTheNextOne() {
        GameService service = new GameService();
        Game last = playCase(service);
        boolean sameScientist = false;
        boolean sameMurderer = false;
        for (int i = 0; i < 300; i++) {
            Game next = playCase(service);
            sameScientist |= next.getForensicScientist().equals(last.getForensicScientist());
            sameMurderer |= next.getMurderer().equals(last.getMurderer());
            last = next;
        }
        assertTrue(sameScientist, "the same Forensic Scientist may come twice in a row");
        assertTrue(sameMurderer, "the same murderer may come twice in a row");
    }
}
