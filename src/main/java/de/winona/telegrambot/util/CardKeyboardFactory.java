package de.winona.telegrambot.util;

import de.winona.telegrambot.model.Card;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

import java.util.Map;

public class CardKeyboardFactory {

    private CardKeyboardFactory() {
    }

    public static InlineKeyboardMarkup createCardKeyboard(List<Card> cards, String callbackPrefix) {
        List<String> names = cards.stream().map(Card::name).toList();
        return createTextKeyboard(names, callbackPrefix);
    }

    public static InlineKeyboardMarkup createTextKeyboard(List<String> texts, String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(texts.get(i))
                    .callbackData(callbackPrefix + i)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup createKeyboard(Map<String, String> buttons) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        buttons.forEach((text, data) -> rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(text)
                        .callbackData(data)
                        .build())));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}