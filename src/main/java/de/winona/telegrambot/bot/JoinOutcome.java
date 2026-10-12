package de.winona.telegrambot.bot;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.model.Lobby;

/** What happened when a player tried to take a seat with a room code. */
public enum JoinOutcome {
    JOINED("toast.seated"),
    ALREADY_SEATED("toast.already_seated"),
    FULL("join.full"),
    IN_PROGRESS("join.in_progress"),
    EXPIRED("join.expired"),
    CLOSED("join.closed"),
    FINISHED("join.finished"),
    UNKNOWN("join.unknown"),
    NEEDS_PRIVATE_CHAT("join.needs_private");

    private final String messageKey;

    JoinOutcome(String messageKey) {
        this.messageKey = messageKey;
    }

    public boolean seated() {
        return this == JOINED || this == ALREADY_SEATED;
    }

    /** A full sentence that explains the outcome, to be translated for whoever reads it. */
    public Msg message(String code) {
        return Msg.of(messageKey, code, Lobby.MAX_PLAYERS);
    }
}
