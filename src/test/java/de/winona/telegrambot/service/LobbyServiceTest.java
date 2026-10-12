package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.RoomEnd;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LobbyServiceTest {

    private static final Player HOST = new Player(1, "Host");

    /**
     * A clock the test can move forward.
     */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void aGroupHasOnlyOneOpenRoomAndEveryRoomItsOwnCode() {
        LobbyService service = new LobbyService();

        Lobby first = service.createLobby(-1, "Group one", HOST).orElseThrow();
        Lobby second = service.createLobby(-2, "Group two", HOST).orElseThrow();

        assertTrue(service.createLobby(-1, "Group one", HOST).isEmpty());
        assertNotEquals(first.getRoom().code(), second.getRoom().code());
        assertTrue(first.getRoom().code().matches("[A-HJ-NP-Z2-9]{4}"), first.getRoom().code());
        assertEquals(Optional.of(second), service.findByCode(second.getRoom().code()));
        assertEquals(2, service.lobbiesOf(HOST.userId()).size());
    }

    @Test
    void aRoomThatIsNotOpenedInTimeExpires() {
        MovableClock clock = new MovableClock();
        LobbyService service = new LobbyService(clock);
        String code = service.createLobby(-1, "Group", HOST).orElseThrow().getRoom().code();

        clock.advance(Lobby.TIME_TO_LIVE.minusMinutes(1));
        assertTrue(service.findByCode(code).isPresent());

        clock.advance(Duration.ofMinutes(2));
        assertTrue(service.findByCode(code).isEmpty());
        assertEquals(Optional.of(RoomEnd.EXPIRED), service.endOf(code));
        assertTrue(service.createLobby(-1, "Group", HOST).isPresent(), "the group can open a new room");
    }

    @Test
    void theReasonARoomEndedIsRemembered() {
        LobbyService service = new LobbyService();
        String code = service.createLobby(-1, "Group", HOST).orElseThrow().getRoom().code();

        service.removeLobby(-1, RoomEnd.CLOSED_BY_HOST);

        assertEquals(Optional.of(RoomEnd.CLOSED_BY_HOST), service.endOf(code));
        assertTrue(service.endOf("NONE").isEmpty());
    }
}
