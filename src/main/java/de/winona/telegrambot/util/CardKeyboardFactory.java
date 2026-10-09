package de.winona.telegrambot.util;

import de.winona.telegrambot.model.Card;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class CardKeyboardFactory {

    private CardKeyboardFactory() {
    }

    public static InlineKeyboardMarkup createCardKeyboard(List<Card> cards, String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int i = 0; i < cards.size(); i++) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(cards.get(i).name())
                    .callbackData(callbackPrefix + i)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}