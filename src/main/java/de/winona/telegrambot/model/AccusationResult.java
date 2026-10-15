package de.winona.telegrambot.model;

public enum AccusationResult {
    /**
     * Murderer, Means of Murder and Key Evidence are all correct.
     */
    SOLVED,
    /**
     * Something is wrong. The group has used its single accusation and the murderer wins.
     */
    WRONG,
    /**
     * The scapegoat was accused with both planted cards: an innocent is convicted.
     */
    WRONGFUL_CONVICTION
}
