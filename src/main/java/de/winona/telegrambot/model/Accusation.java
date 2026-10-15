package de.winona.telegrambot.model;

/**
 * An accusation one suspect proposes to the group: the murderer, the Means of Murder and the
 * Key Evidence. The group votes on it; only if the majority agrees is it put to the Forensic Scientist.
 *
 * @param id counts the proposals of a case, so a vote button of an older proposal cannot count
 */
public record Accusation(int id, Player proposer, Player suspect, Card weapon, Card evidence) {
}
