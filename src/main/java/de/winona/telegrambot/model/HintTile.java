package de.winona.telegrambot.model;

import java.util.List;

public record HintTile(String title, TileSubject subject, List<String> options) {
}
