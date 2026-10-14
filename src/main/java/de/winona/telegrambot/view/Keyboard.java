package de.winona.telegrambot.view;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Small builder for inline keyboards, so every screen lays out its buttons the same way.
 */
public final class Keyboard {

    private final List<InlineKeyboardRow> rows = new ArrayList<>();

    private Keyboard() {
    }

    public static Keyboard create() {
        return new Keyboard();
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    public static InlineKeyboardButton link(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }

    public Keyboard row(InlineKeyboardButton... buttons) {
        if (buttons.length > 0) {
            rows.add(new InlineKeyboardRow(buttons));
        }
        return this;
    }

    /**
     * Places the buttons in rows with the given number of columns.
     */
    public Keyboard grid(List<InlineKeyboardButton> buttons, int columns) {
        for (int i = 0; i < buttons.size(); i += columns) {
            rows.add(new InlineKeyboardRow(buttons.subList(i, Math.min(i + columns, buttons.size()))));
        }
        return this;
    }

    public InlineKeyboardMarkup build() {
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
