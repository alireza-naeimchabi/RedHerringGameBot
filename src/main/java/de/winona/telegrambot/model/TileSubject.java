package de.winona.telegrambot.model;

/** What a scene tile talks about. Every clue points at one kind of card. */
public enum TileSubject {
    MEANS,
    KEY_EVIDENCE,
    /** A general detail about the murderer as a person, not about a card. */
    MURDERER
}
