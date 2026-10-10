package de.winona.telegrambot.util;

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

    private CardKeyboardFactory() {
    }

    public static InlineKeyboardMarkup createCardKeyboard(List<Card> cards, String callbackPrefix) {
        return Keyboard.create().grid(cardButtons(cards, callbackPrefix), COLUMNS).build();
    }

    public static InlineKeyboardMarkup createTextKeyboard(List<String> texts, String callbackPrefix) {
        return Keyboard.create().grid(textButtons(texts, callbackPrefix), COLUMNS).build();
    }

    public static List<InlineKeyboardButton> cardButtons(List<Card> cards, String callbackPrefix) {
        return textButtons(cards.stream().map(Card::name).toList(), callbackPrefix);
    }

    /** Still used by the old RedHerringBot until it is split into controllers. */
    public static InlineKeyboardMarkup createKeyboard(java.util.Map<String, String> buttons) {
        Keyboard keyboard = Keyboard.create();
        buttons.forEach((text, data) -> keyboard.row(Keyboard.button(text, data)));
        return keyboard.build();
    }

    /** One button per text. The callback data is the prefix followed by the index of the text. */
    public static List<InlineKeyboardButton> textButtons(List<String> texts, String callbackPrefix) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            buttons.add(Keyboard.button(texts.get(i), callbackPrefix + i));
        }
        return buttons;
    }
}
