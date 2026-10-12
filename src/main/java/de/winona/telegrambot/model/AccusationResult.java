package de.winona.telegrambot.model;

public enum AccusationResult {
    /**
     * Murderer, Means of Murder and Key Evidence are all correct.
     */
    SOLVED,
    /**
     * Something is wrong. The accuser has used their attempt.
     */
    WRONG,
    /**
     * The scapegoat was accused with both planted cards: an innocent is convicted.
     */
    WRONGFUL_CONVICTION
}
