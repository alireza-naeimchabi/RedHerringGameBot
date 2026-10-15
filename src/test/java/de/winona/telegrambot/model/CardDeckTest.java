package de.winona.telegrambot.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The deal keeps the suspects alike, so no single hint can point at one player.
 */
class CardDeckTest {

    private static final int MOST_SUSPECTS = 6;

    @RepeatedTest(200)
    void everySuspectHasFourDifferentFamiliesCoveringAllGroups() {
        CardDeck deck = new CardDeck();
        Set<Card> dealt = new HashSet<>();
        for (int suspect = 0; suspect < MOST_SUSPECTS; suspect++) {
            Hand hand = deck.dealHand();
            for (List<Card> cards : List.of(hand.weapons(), hand.evidence())) {
                assertEquals(CardDeck.CARDS_PER_TYPE, cards.size());
                assertEquals(4, cards.stream().map(CardDeck::familyOf).distinct().count(),
                        "four different families: " + cards);
                assertEquals(3, cards.stream().map(CardDeck::groupOf).distinct().count(),
                        "every group is there, so a hint never rules a player out: " + cards);
                dealt.addAll(cards);
            }
        }
        assertEquals(2 * MOST_SUSPECTS * CardDeck.CARDS_PER_TYPE, dealt.size(), "no card is dealt twice");
    }

    @Test
    void theDealChangesFromCaseToCase() {
        Set<String> firstHands = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            firstHands.add(new CardDeck().dealHand().weapons().stream()
                    .map(Card::name).sorted().collect(Collectors.joining(",")));
        }
        assertTrue(firstHands.size() > 1);
    }

    @Test
    void aSeventhSuspectIsRefused() {
        CardDeck deck = new CardDeck();
        for (int i = 0; i < MOST_SUSPECTS; i++) {
            deck.dealHand();
        }
        assertThrows(IllegalStateException.class, deck::dealHand);
    }
}
