package de.winona.telegrambot.telegram;

import de.winona.telegrambot.telegram.FakeTelegram.Shown;
import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.view.Banner;
import de.winona.telegrambot.view.ChatView;
import de.winona.telegrambot.view.Screen;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How screens in one or two languages reach a chat.
 */
class MessengerTest {

    private static final long CHAT = -500L;
    private static final Texts EN = Texts.of(Language.ENGLISH);
    private static final Texts FA = Texts.of(Language.PERSIAN);

    private final FakeTelegram telegram = new FakeTelegram();
    private final ChatView chatView = new Messenger(telegram.client());

    @Test
    void everyLanguageGetsItsOwnArtwork() {
        chatView.send(CHAT, EN.banner(Banner.HOME, "Home", null));
        chatView.send(CHAT, FA.banner(Banner.HOME, "خانه", null));

        List<Shown> shown = telegram.chat(CHAT);
        assertEquals("banner-home.jpg", shown.get(0).banner);
        assertEquals("fa-banner-home.jpg", shown.get(1).banner);
        assertEquals(List.of(), telegram.problems());
    }

    @Test
    void aShortScreenInTwoLanguagesStaysOneMessageWithSharedButtons() {
        Screen merged = Screen.merge(List.of(
                EN.banner(Banner.CASE_OPEN, "The case is open.", keyboard("🔬 Lab Test", "⚖️ Solve the Crime")),
                FA.banner(Banner.CASE_OPEN, "پرونده باز است.", keyboard("🔬 آزمایش", "⚖️ حل پرونده"))));

        chatView.send(CHAT, merged);

        Shown message = telegram.last(CHAT);
        assertEquals(1, telegram.chat(CHAT).size());
        assertTrue(message.photo);
        assertEquals("banner-case-open.jpg", message.banner, "a shared message uses the default artwork");
        assertTrue(message.html.contains("The case is open.") && message.html.contains("پرونده باز است."));
        assertEquals(List.of("🔬 Lab Test · آزمایش", "⚖️ Solve the Crime · حل پرونده"),
                message.buttons().stream().map(InlineKeyboardButton::getText).toList());
        assertEquals("action0", message.buttons().get(0).getCallbackData(), "the action behind a button is the same");
        assertEquals(List.of(), telegram.problems());
    }

    @Test
    void aScreenTooLongForACaptionIsSentAsArtworkFollowedByText() {
        String english = "A long verdict. ".repeat(40);
        String persian = "یک حکم طولانی. ".repeat(40);
        Screen merged = Screen.merge(List.of(
                EN.banner(Banner.CASE_SOLVED, english, keyboard("🗝 New Case")),
                FA.banner(Banner.CASE_SOLVED, persian, keyboard("🗝 پرونده‌ی جدید"))));
        assertFalse(merged.fitsCaption());

        chatView.send(CHAT, merged);

        List<Shown> shown = telegram.chat(CHAT);
        assertEquals(2, shown.size());
        assertTrue(shown.get(0).photo);
        assertEquals("", shown.get(0).html, "the artwork carries no text");
        assertTrue(shown.get(0).buttons().isEmpty());
        assertFalse(shown.get(1).photo);
        assertTrue(shown.get(1).html.contains("A long verdict.") && shown.get(1).html.contains("یک حکم طولانی."));
        assertEquals("🗝 New Case · پرونده‌ی جدید", shown.get(1).buttons().get(0).getText());
        assertEquals(List.of(), telegram.problems());
    }

    private InlineKeyboardMarkup keyboard(String... labels) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (int i = 0; i < labels.length; i++) {
            row.add(InlineKeyboardButton.builder().text(labels[i]).callbackData("action" + i).build());
        }
        return InlineKeyboardMarkup.builder().keyboardRow(row).build();
    }
}
