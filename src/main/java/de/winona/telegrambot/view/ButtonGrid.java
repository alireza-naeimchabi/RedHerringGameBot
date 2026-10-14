package de.winona.telegrambot.view;

import java.util.List;

/**
 * The buttons under a message, row by row. Immutable, so a screen cannot change after it was built.
 */
public record ButtonGrid(List<List<Button>> rows) {

    public ButtonGrid {
        rows = rows.stream().map(List::copyOf).toList();
    }

    public List<List<Button>> getKeyboard() {
        return rows;
    }
}