package de.winona.telegrambot.view;

import java.util.ArrayList;
import java.util.List;

/**
 * Small builder for the buttons under a message, so every screen lays them out the same way.
 */
public final class Keyboard {

    private final List<List<Button>> rows = new ArrayList<>();

    private Keyboard() {
    }

    public static Keyboard create() {
        return new Keyboard();
    }

    public static Button button(String text, String callbackData) {
        return new Button(text, callbackData, null);
    }

    public static Button link(String text, String url) {
        return new Button(text, null, url);
    }

    public Keyboard row(Button... buttons) {
        if (buttons.length > 0) {
            rows.add(List.of(buttons));
        }
        return this;
    }

    /**
     * Places the buttons in rows with the given number of columns.
     */
    public Keyboard grid(List<Button> buttons, int columns) {
        for (int i = 0; i < buttons.size(); i += columns) {
            rows.add(new ArrayList<>(buttons.subList(i, Math.min(i + columns, buttons.size()))));
        }
        return this;
    }

    public ButtonGrid build() {
        return new ButtonGrid(rows);
    }
}