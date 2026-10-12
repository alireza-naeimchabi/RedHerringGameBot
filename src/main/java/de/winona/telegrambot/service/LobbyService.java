package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.model.RoomEnd;
import de.winona.telegrambot.util.RoomCodeGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

public class LobbyService {

    private static final int REMEMBERED_ENDED_ROOMS = 500;

    private final Map<Long, Lobby> lobbies = new HashMap<>();
    private final Map<String, RoomEnd> endedRooms = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RoomEnd> eldest) {
            return size() > REMEMBERED_ENDED_ROOMS;
        }
    };
    private final RoomCodeGenerator codeGenerator = new RoomCodeGenerator();
    private final Clock clock;

    public LobbyService() {
        this(Clock.systemUTC());
    }

    public LobbyService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Opens a room for a group. Empty if the group already has an open room.
     */
    public Optional<Lobby> createLobby(long chatId, String groupTitle, Player host) {
        if (getLobby(chatId).isPresent()) {
            return Optional.empty();
        }
        String code = codeGenerator.nextCode(candidate ->
                endedRooms.containsKey(candidate) || findByCode(candidate).isPresent());
        Lobby lobby = new Lobby(new Room(code, chatId, groupTitle, host, Instant.now(clock)));
        lobbies.put(chatId, lobby);
        return Optional.of(lobby);
    }

    public Optional<Lobby> getLobby(long chatId) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby != null && lobby.isExpired(Instant.now(clock))) {
            removeLobby(chatId, RoomEnd.EXPIRED);
            return Optional.empty();
        }
        return Optional.ofNullable(lobby);
    }

    public Optional<Lobby> findByCode(String code) {
        return List.copyOf(lobbies.keySet()).stream()
                .map(this::getLobby)
                .flatMap(Optional::stream)
                .filter(lobby -> lobby.getRoom().code().equals(code))
                .findFirst();
    }

    public List<Lobby> lobbiesOf(long userId) {
        return List.copyOf(lobbies.keySet()).stream()
                .map(this::getLobby)
                .flatMap(Optional::stream)
                .filter(lobby -> lobby.hasPlayer(userId) || lobby.getRoom().isHost(userId))
                .toList();
    }

    public void removeLobby(long chatId, RoomEnd reason) {
        Lobby removed = lobbies.remove(chatId);
        if (removed != null) {
            endedRooms.put(removed.getRoom().code(), reason);
        }
    }

    /**
     * Tells why a code that once existed can no longer be joined.
     */
    public Optional<RoomEnd> endOf(String code) {
        return Optional.ofNullable(endedRooms.get(code));
    }
}
