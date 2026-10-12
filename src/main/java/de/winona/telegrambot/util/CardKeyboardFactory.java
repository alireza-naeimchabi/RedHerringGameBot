package de.winona.telegrambot.util;

import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.ui.Keyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** Builds the buttons for choosing a card or a scene tile option. */
public class CardKeyboardFactory {

    /** Cards and options are shown in two columns, so four cards fit on one phone screen. */
    public static final int COLUMNS = 2;
    /** Longer labels are cut off by Telegram when two buttons share a row. */
    private static final int MAX_LABEL_LENGTH_IN_TWO_COLUMNS = 22;

    private CardKeyboardFactory() {
    }

    public static InlineKeyboardMarkup createCardKeyboard(Texts t, List<Card> cards, String callbackPrefix) {
        return Keyboard.create().grid(cardButtons(t, cards, callbackPrefix), COLUMNS).build();
    }

    /**
     * Buttons for the options of a scene tile, in the reader's language.
     * If a translation is too long for half the width of a phone, every option gets its own row.
     */
    public static InlineKeyboardMarkup createOptionKeyboard(Texts t, List<String> options, String callbackPrefix) {
        List<String> labels = options.stream().map(t::option).toList();
        boolean fitsSideBySide = labels.stream()
                .allMatch(label -> label.codePointCount(0, label.length()) <= MAX_LABEL_LENGTH_IN_TWO_COLUMNS);
        return Keyboard.create()
                .grid(textButtons(labels, callbackPrefix), fitsSideBySide ? COLUMNS : 1)
                .build();
    }

    public static List<InlineKeyboardButton> cardButtons(Texts t, List<Card> cards, String callbackPrefix) {
        return textButtons(cards.stream().map(t::card).toList(), callbackPrefix);
    }

    /** One button per label. The callback data is the prefix followed by the index, never the label. */
    public static List<InlineKeyboardButton> textButtons(List<String> labels, String callbackPrefix) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            buttons.add(Keyboard.button(labels.get(i), callbackPrefix + i));
        }
        return buttons;
    }
}
