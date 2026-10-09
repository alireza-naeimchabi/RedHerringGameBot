package de.winona.telegrambot.model;

import java.util.List;

public record Hand(List<Card> weapons, List<Card> evidence) {
}