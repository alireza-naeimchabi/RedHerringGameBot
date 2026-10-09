package de.winona.telegrambot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HintDeck {

    public static final int TILES_PER_GAME = 4;

    private static final List<HintTile> TILES = List.of(
            new HintTile("Cause of death",
                    List.of("Suffocation", "Blood loss", "Poisoning", "Heavy blow", "Accident")),
            new HintTile("Crime scene",
                    List.of("Kitchen", "Office", "Hotel room", "Park", "Garage")),
            new HintTile("Time of crime",
                    List.of("Morning", "Noon", "Afternoon", "Evening", "Night")),
            new HintTile("Motive",
                    List.of("Money", "Jealousy", "Revenge", "Fear", "Power")),
            new HintTile("Weather",
                    List.of("Sunny", "Rainy", "Stormy", "Foggy", "Snowy")),
            new HintTile("Victim's job",
                    List.of("Teacher", "Doctor", "Chef", "Artist", "Banker")));

    private HintDeck() {
    }

    public static List<HintTile> drawTiles() {
        List<HintTile> shuffled = new ArrayList<>(TILES);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, TILES_PER_GAME));
    }
}