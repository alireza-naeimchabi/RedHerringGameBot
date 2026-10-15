package de.winona.telegrambot.model;

/**
 * Where a vote on an accusation stands after one more vote.
 */
public enum VoteOutcome {
    /** Neither side has a majority yet. */
    OPEN,
    /** More than half of the voters agree: the accusation is judged. */
    APPROVED,
    /** At least half of the voters disagree: the accusation is dropped and the case goes on. */
    REJECTED
}
