package de.winona.telegrambot.model;

import java.util.List;

/**
 * Repräsentiert eine einzelne Quizfrage mit Text, Antwortmöglichkeiten und dem Index der richtigen Antwort.
 */
public record Question(String text, List<String> options, int correctIndex) {
}
