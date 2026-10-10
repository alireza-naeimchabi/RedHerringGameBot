package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Lobby;

/** What happened when a player tried to take a seat with a room code. */
public enum JoinOutcome {
    JOINED(""),
    ALREADY_SEATED(""),
    FULL("Room %s is full. All " + Lobby.MAX_PLAYERS + " seats are taken."),
    IN_PROGRESS("The case in room %s is already underway. Wait for the next case in that group."),
    EXPIRED("Room %s has expired because the case was not opened in time. Ask the host to create a new room."),
    CLOSED("Room %s was closed by its host."),
    FINISHED("The case in room %s is finished. Ask the host to create a new room."),
    UNKNOWN("No room has the code %s. Check the code on the room card in your group."),
    NEEDS_PRIVATE_CHAT("Open a private chat with me and press Start. Then tap Join Room again.");

    private final String message;

    JoinOutcome(String message) {
        this.message = message;
    }

    public boolean seated() {
        return this == JOINED || this == ALREADY_SEATED;
    }

    /** A full sentence that explains the outcome to the player. */
    public String message(String code) {
        return String.format(message, code);
    }
}
