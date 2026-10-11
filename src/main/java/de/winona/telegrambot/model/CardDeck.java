package de.winona.telegrambot.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class CardDeck {

    public static final int CARDS_PER_TYPE = 4;

    /*
     * 24 means in 6 families of 4 look-alike cards. Two families share each cause of death,
     * so "Cause of death" never points at a single card: the investigators always have to
     * tell look-alikes apart with the other tiles and the lab.
     */
    private static final List<String> WEAPON_NAMES = List.of(
            // Bleeding wound: blades
            "Knife", "Razor Blade", "Box Cutter", "Broken Bottle",
            // Bleeding wound: points
            "Ice Pick", "Screwdriver", "Scissors", "Knitting Needle",
            // Blunt force: one hand
            "Hammer", "Rolling Pin", "Candlestick", "Clothes Iron",
            // Blunt force: two hands
            "Baseball Bat", "Shovel", "Crowbar", "Fire Poker",
            // No visible wound: cords
            "Rope", "Scarf", "Necktie", "Electric Cable",
            // No visible wound: poisons
            "Sleeping Pills", "Rat Poison", "Bleach", "Poisoned Tea");

    /* 24 clues in 6 families of 4 small, everyday things that anyone could have dropped. */
    private static final List<String> EVIDENCE_NAMES = List.of(
            // Paper slips: travel or pay
            "Train Ticket", "Cinema Ticket", "Receipt", "Banknote",
            // Written paper: read or write
            "Letter", "Notebook", "Photo", "Business Card",
            // Jewellery: wear, metal
            "Earring", "Ring", "Watch", "Bracelet",
            // Clothing: wear, fabric or leather
            "Glove", "Button", "Shoelace", "Muddy Shoe",
            // Pocket items: open, switch on or pay
            "Key", "Coin", "Lighter", "Phone",
            // Body and habit traces: organic
            "Cigarette", "Chewing Gum", "Lipstick", "Hair");

    private final Deque<Card> weapons;
    private final Deque<Card> evidence;

    public CardDeck() {
        this.weapons = shuffledCards(WEAPON_NAMES, CardType.WEAPON);
        this.evidence = shuffledCards(EVIDENCE_NAMES, CardType.EVIDENCE);
    }

    public Hand dealHand() {
        return new Hand(draw(weapons), draw(evidence));
    }

    private Deque<Card> shuffledCards(List<String> names, CardType type) {
        List<Card> cards = new ArrayList<>();
        for (String name : names) {
            cards.add(new Card(name, type));
        }
        Collections.shuffle(cards);
        return new ArrayDeque<>(cards);
    }

    private List<Card> draw(Deque<Card> pile) {
        List<Card> drawn = new ArrayList<>();
        for (int i = 0; i < CARDS_PER_TYPE; i++) {
            drawn.add(pile.pop());
        }
        return drawn;
    }
}