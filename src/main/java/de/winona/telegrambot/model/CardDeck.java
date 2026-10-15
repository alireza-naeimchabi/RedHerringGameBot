package de.winona.telegrambot.model;

import java.util.*;

/**
 * Deals the cards of one case.
 *
 * <p>The cards come in families of four look-alikes, and two families always share the same
 * answer on the most important scene tile (for means: the cause of death). Every suspect gets
 * four cards from four different families, and those four families always cover all three
 * groups. So whatever the Forensic Scientist answers on that tile, every suspect still has a
 * fitting card: a single hint never points at a single player.
 */
public class CardDeck {

    public static final int CARDS_PER_TYPE = 4;

    /*
     * 24 means: 3 groups (one per cause of death) of 2 families of 4 cards.
     */
    private static final List<List<List<String>>> WEAPON_GROUPS = List.of(
            List.of( // Bleeding wound
                    List.of("Knife", "Razor Blade", "Box Cutter", "Broken Bottle"),            // blades
                    List.of("Ice Pick", "Screwdriver", "Scissors", "Knitting Needle")),        // points
            List.of( // Blunt force
                    List.of("Hammer", "Rolling Pin", "Candlestick", "Clothes Iron"),           // one hand
                    List.of("Baseball Bat", "Shovel", "Crowbar", "Fire Poker")),               // two hands
            List.of( // No visible wound
                    List.of("Rope", "Scarf", "Necktie", "Electric Cable"),                     // cords
                    List.of("Sleeping Pills", "Rat Poison", "Bleach", "Poisoned Tea")));       // poisons

    /*
     * 24 clues: 3 groups (paper, worn things, small everyday traces) of 2 families of 4 cards.
     */
    private static final List<List<List<String>>> EVIDENCE_GROUPS = List.of(
            List.of( // Paper
                    List.of("Train Ticket", "Cinema Ticket", "Receipt", "Banknote"),           // travel or pay
                    List.of("Letter", "Notebook", "Photo", "Business Card")),                 // read or write
            List.of( // Worn
                    List.of("Earring", "Ring", "Watch", "Bracelet"),                           // jewellery
                    List.of("Glove", "Button", "Shoelace", "Muddy Shoe")),                    // clothing
            List.of( // Everyday traces
                    List.of("Key", "Coin", "Lighter", "Phone"),                                // pocket items
                    List.of("Cigarette", "Chewing Gum", "Lipstick", "Hair")));                // organic

    /** Number of families per card type. */
    public static final int FAMILIES = 6;

    private static final Map<String, Integer> FAMILY_OF_CARD = new HashMap<>();

    static {
        indexFamilies(WEAPON_GROUPS);
        indexFamilies(EVIDENCE_GROUPS);
    }

    private final Random random;
    private final List<Deque<Card>> weaponFamilies;
    private final List<Deque<Card>> evidenceFamilies;
    /** Where each next hand starts in the circle of families. Every start is used at most once. */
    private final Deque<Integer> starts;

    public CardDeck() {
        this(new Random());
    }

    CardDeck(Random random) {
        this.random = random;
        this.weaponFamilies = arrange(WEAPON_GROUPS, CardType.WEAPON);
        this.evidenceFamilies = arrange(EVIDENCE_GROUPS, CardType.EVIDENCE);
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < FAMILIES; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions, random);
        this.starts = new ArrayDeque<>(positions);
    }

    /**
     * Four means and four clues, each from a different family. Works for up to six suspects,
     * which is the most a room of seven can have.
     */
    public Hand dealHand() {
        if (starts.isEmpty()) {
            throw new IllegalStateException("A case has at most " + FAMILIES + " suspects.");
        }
        int start = starts.pop();
        return new Hand(draw(weaponFamilies, start), draw(evidenceFamilies, start));
    }

    /**
     * The family a card belongs to, 0 to 5. Means and clues are numbered separately.
     */
    public static int familyOf(Card card) {
        return FAMILY_OF_CARD.get(card.name()) % FAMILIES;
    }

    /**
     * The group of a card's family, 0 to 2: for means the cause of death.
     */
    public static int groupOf(Card card) {
        return familyOf(card) / 2;
    }

    /**
     * Puts the six families in a circle so that the three groups take turns: A B C A B C.
     * Any four neighbours in the circle then contain all three groups.
     */
    private List<Deque<Card>> arrange(List<List<List<String>>> groups, CardType type) {
        List<List<List<String>>> groupOrder = new ArrayList<>(groups);
        Collections.shuffle(groupOrder, random);
        List<List<String>> first = new ArrayList<>();
        List<List<String>> second = new ArrayList<>();
        for (List<List<String>> group : groupOrder) {
            List<List<String>> pair = new ArrayList<>(group);
            Collections.shuffle(pair, random);
            first.add(pair.get(0));
            second.add(pair.get(1));
        }
        List<Deque<Card>> circle = new ArrayList<>();
        for (List<String> family : first) {
            circle.add(shuffled(family, type));
        }
        for (List<String> family : second) {
            circle.add(shuffled(family, type));
        }
        return circle;
    }

    private Deque<Card> shuffled(List<String> names, CardType type) {
        List<Card> cards = new ArrayList<>();
        for (String name : names) {
            cards.add(new Card(name, type));
        }
        Collections.shuffle(cards, random);
        return new ArrayDeque<>(cards);
    }

    /**
     * One card from each of the four families that follow the start. Each family is drawn by at
     * most four hands, so its four cards are always enough. The hand is shuffled, so the order of
     * the cards does not give the families away.
     */
    private List<Card> draw(List<Deque<Card>> circle, int start) {
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < CARDS_PER_TYPE; i++) {
            hand.add(circle.get((start + i) % FAMILIES).pop());
        }
        Collections.shuffle(hand, random);
        return hand;
    }

    private static void indexFamilies(List<List<List<String>>> groups) {
        int family = 0;
        for (List<List<String>> group : groups) {
            for (List<String> names : group) {
                for (String name : names) {
                    FAMILY_OF_CARD.put(name, family);
                }
                family++;
            }
        }
    }
}
