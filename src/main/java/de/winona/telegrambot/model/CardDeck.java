package de.winona.telegrambot.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class CardDeck {

    public static final int CARDS_PER_TYPE = 4;

    private static final List<String> WEAPON_NAMES = List.of(
            "Knife", "Poison", "Rope", "Hammer", "Pistol", "Scissors",
            "Pillow", "Axe", "Wrench", "Sleeping Pills", "Broken Bottle", "Electric Cable",
            "Brick", "Screwdriver", "Crossbow", "Frying Pan", "Ice Pick", "Scarf",
            "Baseball Bat", "Dumbbell", "Razor Blade", "Golf Club", "Plastic Bag", "Shovel");


    private static final List<String> EVIDENCE_NAMES = List.of(
            "Lipstick", "Train Ticket", "Glove", "Key", "Receipt", "Cigarette",
            "Button", "Phone", "Coffee Cup", "Perfume", "Muddy Shoe", "Letter",
            "Photo", "Coin", "Watch", "Hair", "Umbrella",
            "Napkin", "Earring", "Bus Ticket", "Lighter", "Notebook", "Sunglasses", "Chewing Gum");

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