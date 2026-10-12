package de.winona.telegrambot.i18n;

import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.model.CardDeck;
import de.winona.telegrambot.model.CardType;
import de.winona.telegrambot.model.Hand;
import de.winona.telegrambot.model.HintDeck;
import de.winona.telegrambot.model.HintTile;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.ui.Screen;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextsTest {

    private static final String RIGHT_TO_LEFT_MARK = "‏";
    private static final String ISOLATE_START = "⁨";
    private static final String ISOLATE_END = "⁩";

    private final Texts english = Texts.of(Language.ENGLISH);
    private final Texts persian = Texts.of(Language.PERSIAN);

    @Test
    void valuesAreInsertedAndNumbersUseTheDigitsOfTheLanguage() {
        assertEquals("Round 2 of 3", english.get("case.round", 2, 3));
        assertEquals("دور ۲ از ۳", persian.get("case.round", 2, 3));
        assertEquals("67%", english.get("common.percent", 67));
        assertEquals("۶۷٪", persian.get("common.percent", 67));
        assertEquals("۱۰۴", persian.number(104));
    }

    @Test
    void roomCodesAndOtherTextValuesAreNeverChanged() {
        assertEquals("ROOM K7Q2", english.get("room.title", "K7Q2"));
        assertEquals("اتاق K7Q2", persian.get("room.title", "K7Q2"));
    }

    @Test
    void countsChooseTheRightForm() {
        assertEquals("1 room", english.plural("rooms.count", 1));
        assertEquals("3 rooms", english.plural("rooms.count", 3));
        assertEquals("۳ اتاق", persian.plural("rooms.count", 3));
    }

    @Test
    void nullOrUnknownLanguageFallsBackToEnglish() {
        assertEquals(Language.ENGLISH, Texts.of(null).language());
        assertEquals(Language.ENGLISH, Language.fromCode("xx"));
        assertEquals(Language.PERSIAN, Language.fromCode("fa"));
        assertEquals("no.such.key", english.get("no.such.key"), "a missing key is visible, not a crash");
    }

    @Test
    void aMessageWithoutReaderIsTranslatedWhenItIsRead() {
        Msg problem = Msg.of("err.lab.tested", Texts.cardMsg(new Card("Knife", CardType.WEAPON)));

        assertEquals("The lab already tested Knife. Pick another card.", english.get(problem));
        assertTrue(persian.get(problem).contains("چاقو"));
        assertFalse(persian.get(problem).contains("Knife"));
    }

    @Test
    void everyCardTileAndOptionOfTheGameHasAPersianName() {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Hand hand = new CardDeck().dealHand();
            List<Card> cards = new ArrayList<>(hand.weapons());
            cards.addAll(hand.evidence());
            for (Card card : cards) {
                assertEquals(card.name(), english.card(card));
                if (persian.card(card).equals(card.name())) {
                    missing.add(card.name());
                }
            }
        }
        for (int i = 0; i < 20; i++) {
            HintDeck deck = new HintDeck();
            List<HintTile> tiles = new ArrayList<>(deck.drawStartTiles());
            tiles.addAll(deck.drawOffer());
            tiles.addAll(deck.drawOffer());
            tiles.add(HintDeck.MURDERER_PROFILE);
            for (HintTile tile : tiles) {
                assertEquals(tile.title(), english.tile(tile.title()));
                if (persian.tile(tile.title()).equals(tile.title())) {
                    missing.add(tile.title());
                }
                for (String option : tile.options()) {
                    assertEquals(option, english.option(option));
                    if (persian.option(option).equals(option)) {
                        missing.add(option);
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void rolesAndRanksAreTranslated() {
        assertEquals("Forensic Scientist", english.role(Role.FORENSIC_SCIENTIST));
        assertEquals("قاتل", persian.role(Role.MURDERER));
        assertEquals("Recruit", english.rank(0));
        assertEquals("Detective", english.rank(3));
        assertNotEquals(english.rank(30), persian.rank(30));
    }

    @Test
    void namesAreEscapedShortenedAndKeptTogetherInTheOtherDirection() {
        assertEquals("Tom &amp; Jerry", english.name("Tom & Jerry", 22));
        assertEquals("A very long gr…", english.name("A very long group title", 15));
        assertEquals("Unknown", english.name(" ", 22));
        assertEquals("ناشناس", persian.name(null, 22));

        // A Latin name inside Persian text, and a Persian name inside English text, are isolated.
        assertEquals(ISOLATE_START + "Sara" + ISOLATE_END, persian.name("Sara", 22));
        assertEquals(ISOLATE_START + "سارا" + ISOLATE_END, english.name("سارا", 22));
        // Names in the direction of the language need nothing.
        assertEquals("سارا", persian.name("سارا", 22));
        assertEquals("Sara", english.name("Sara", 22));
    }

    @Test
    void everyLineOfAPersianScreenStartsRightToLeft() {
        Screen screen = persian.text("K7Q2 اتاق\n\nSara\nمتن");

        assertEquals(RIGHT_TO_LEFT_MARK + "K7Q2 اتاق\n\n" + RIGHT_TO_LEFT_MARK + "Sara\n" + RIGHT_TO_LEFT_MARK + "متن",
                screen.html());
        assertEquals(Language.PERSIAN, screen.language());
        assertEquals("<blockquote><b>" + RIGHT_TO_LEFT_MARK + "متن</b></blockquote>",
                persian.text("<blockquote><b>متن</b></blockquote>").html(), "the mark stays inside the block");
        assertEquals(RIGHT_TO_LEFT_MARK + "<code>K7Q2</code>", persian.text("<code>K7Q2</code>").html(),
                "a room code is copied without the mark");
        assertEquals("Room\n\nSara", english.text("Room\n\nSara").html(), "English screens are left as they are");
    }
}
