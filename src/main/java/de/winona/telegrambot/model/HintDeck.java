package de.winona.telegrambot.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static de.winona.telegrambot.model.TileSubject.KEY_EVIDENCE;
import static de.winona.telegrambot.model.TileSubject.MEANS;
import static de.winona.telegrambot.model.TileSubject.MURDERER;

/**
 * The scene tiles the Forensic Scientist answers.
 * Every tile asks about a property that each means card or each clue card obviously has,
 * so every answer rules cards in or out instead of only adding atmosphere.
 */
public class HintDeck {

    public static final int MEANS_TILES_AT_START = 2;
    public static final int EVIDENCE_TILES_AT_START = 3;
    public static final int TILES_OFFERED_PER_ROUND = 2;

    /** Deliberately general: each answer fits two card families, about a third of all means. */
    private static final HintTile CAUSE_OF_DEATH = new HintTile("Cause of death", MEANS,
            List.of("Bleeding wound", "Blunt force", "No visible wound"));

    private static final List<HintTile> MEANS_TILES = List.of(
            new HintTile("The means comes from", MEANS,
                    List.of("Kitchen", "Workshop or garage", "Bathroom",
                            "Living room", "Bedroom or wardrobe", "Garden or shed")),
            new HintTile("The means is made of", MEANS,
                    List.of("Metal", "Wood", "Cloth or cord", "Glass or ceramic", "Liquid or powder", "Several materials")),
            new HintTile("Size of the means", MEANS,
                    List.of("Fits in a pocket", "Fits in one hand", "Fits in a bag",
                            "Needs two hands", "Longer than an arm", "Soft and foldable")),
            new HintTile("Sound of the crime", MEANS,
                    List.of("Silent", "A dull thud", "A struggle",
                            "A short cry", "Something fell over", "A door slammed")),
            new HintTile("Blood at the scene", MEANS,
                    List.of("None", "A few drops", "A trail", "A pool", "On the walls", "Wiped away")),
            new HintTile("The attack", MEANS,
                    List.of("In the victim's sleep", "From behind", "Face to face",
                            "Without a fight", "Slow and patient", "In sudden anger")));

    private static final List<HintTile> EVIDENCE_TILES = List.of(
            new HintTile("The key evidence is made of", KEY_EVIDENCE,
                    List.of("Metal", "Paper", "Fabric or leather", "Plastic",
                            "Several materials", "Something organic")),
            new HintTile("The key evidence is something you", KEY_EVIDENCE,
                    List.of("Wear", "Read or write", "Travel or pay with",
                            "Eat, drink or smoke", "Open or switch on", "Lose without noticing")),
            new HintTile("Size of the key evidence", KEY_EVIDENCE,
                    List.of("Smaller than a coin", "Fits in a wallet", "Fits in a pocket",
                            "Fits in one hand", "Fits in a bag", "Too big for a bag")),
            new HintTile("The key evidence was found", KEY_EVIDENCE,
                    List.of("In the victim's hand", "On the floor", "On a table",
                            "In the bin", "By the door", "Outside")),
            new HintTile("The key evidence tells us about", KEY_EVIDENCE,
                    List.of("A journey", "Appearance", "A habit", "A relationship", "Money", "Work")));

    /**
     * One general detail about the murderer as a person. Offered from round 2 on,
     * until the Forensic Scientist has used it once in this case.
     */
    public static final HintTile MURDERER_PROFILE = new HintTile("Profile of the murderer", MURDERER,
            List.of("Has spoken a lot", "Has been quiet", "Has used the lab",
                    "Has not used the lab", "Has blamed someone", "Has defended someone"));

    private final Deque<HintTile> meansPile = shuffled(MEANS_TILES);
    private final Deque<HintTile> evidencePile = shuffled(EVIDENCE_TILES);

    private static Deque<HintTile> shuffled(List<HintTile> tiles) {
        List<HintTile> copy = new ArrayList<>(tiles);
        Collections.shuffle(copy);
        return new ArrayDeque<>(copy);
    }

    /** Cause of death always comes first, then tiles about the means, then about the key evidence. */
    public List<HintTile> drawStartTiles() {
        List<HintTile> tiles = new ArrayList<>();
        tiles.add(CAUSE_OF_DEATH);
        for (int i = 0; i < MEANS_TILES_AT_START; i++) {
            tiles.add(meansPile.pop());
        }
        for (int i = 0; i < EVIDENCE_TILES_AT_START; i++) {
            tiles.add(evidencePile.pop());
        }
        return tiles;
    }

    /**
     * New tiles for the next round. The Forensic Scientist picks one of them.
     * If possible, one tile is about the means and one about the key evidence.
     */
    public List<HintTile> drawOffer() {
        List<HintTile> offer = new ArrayList<>();
        if (!meansPile.isEmpty()) {
            offer.add(meansPile.pop());
        }
        if (!evidencePile.isEmpty()) {
            offer.add(evidencePile.pop());
        }
        while (offer.size() < TILES_OFFERED_PER_ROUND && !meansPile.isEmpty()) {
            offer.add(meansPile.pop());
        }
        return offer;
    }
}
