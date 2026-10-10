package de.winona.telegrambot.model;

/**
 * One card that was sent to the forensic lab.
 * A trace means: the card belongs to the real crime, or the murderer planted it.
 */
public record LabResult(int round, Player tester, Player owner, Card card, boolean trace) {
}
