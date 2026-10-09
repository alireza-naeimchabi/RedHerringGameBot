package de.winona.telegrambot.util;

import de.winona.telegrambot.model.Question;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility-Klasse zur Erstellung von Telegram Keyboards.
 */
public final class KeyboardFactory {

    private KeyboardFactory() {
        // Utility-Klasse: keine Instanziierung erlaubt
    }

    /**
     * Baut ein {@link InlineKeyboardMarkup} für eine Quizfrage mit nummerierten Antwort-Buttons.
     *
     * @param question       Die darzustellende Quizfrage
     * @param callbackPrefix Das Präfix für Callback-Daten (z.B. "ANS_")
     * @return Fertiges Inline-Keyboard für das SendMessage-Objekt
     */
    public static InlineKeyboardMarkup createQuestionKeyboard(Question question, String callbackPrefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<String> options = question.options();

        for (int i = 0; i < options.size(); i++) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text((i + 1) + ") " + options.get(i))
                    .callbackData(callbackPrefix + i)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
