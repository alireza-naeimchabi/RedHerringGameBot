package de.winona.telegrambot.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class HintDeck {

    public static final int RANDOM_TILES_AT_START = 4;

    private static final HintTile CAUSE_OF_DEATH = new HintTile("Cause of death",
            List.of("Suffocation", "Blood loss", "Poisoning", "Heavy blow", "Illness", "Accident"));

    private static final List<HintTile> LOCATION_TILES = List.of(
            new HintTile("Location (indoors)",
                    List.of("Kitchen", "Bedroom", "Office", "Hotel room", "Garage", "Restaurant")),
            new HintTile("Location (outdoors)",
                    List.of("Park", "Parking lot", "Beach", "Forest", "Street", "Construction site")));

    private static final List<HintTile> SCENE_TILES = List.of(
            new HintTile("Time of crime",
                    List.of("Early morning", "Morning", "Noon", "Afternoon", "Evening", "Midnight")),
            new HintTile("Motive",
                    List.of("Money", "Jealousy", "Revenge", "Fear", "Power", "Love")),
            new HintTile("Weather",
                    List.of("Sunny", "Rainy", "Stormy", "Foggy", "Snowy", "Hot")),
            new HintTile("Duration of crime",
                    List.of("Seconds", "Minutes", "About an hour", "Several hours", "Days", "Unclear")),
            new HintTile("State of the scene",
                    List.of("Tidy", "Messy", "Burned", "Wet", "Dark", "Locked")),
            new HintTile("Murderer's character",
                    List.of("Calm", "Angry", "Greedy", "Clever", "Nervous", "Cold")),
            new HintTile("Trace on the body",
                    List.of("Bruise", "Cut", "Burn", "Bite", "Rash", "No trace")),
            new HintTile("Evidence is made of",
                    List.of("Metal", "Paper", "Fabric", "Plastic", "Glass", "Organic")));

    private final Deque<HintTile> drawPile;

    public HintDeck() {
        List<HintTile> shuffled = new ArrayList<>(SCENE_TILES);
        Collections.shuffle(shuffled);
        this.drawPile = new ArrayDeque<>(shuffled);
    }

    public List<HintTile> drawStartTiles() {
        List<HintTile> tiles = new ArrayList<>();
        tiles.add(CAUSE_OF_DEATH);
        tiles.add(LOCATION_TILES.get(new Random().nextInt(LOCATION_TILES.size())));

        for (int i = 0; i < RANDOM_TILES_AT_START; i++) {
            tiles.add(drawPile.pop());
        }
        return tiles;
    }

    public HintTile drawTile() {
        return drawPile.pop();
    }
}