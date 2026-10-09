package de.winona.telegrambot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Game {

    private final Map<Player, Role> roles = new LinkedHashMap<>();

    public Game(List<Player> players) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            roles.put(shuffled.get(i), roleForIndex(i));
        }
    }

    private Role roleForIndex(int index) {
        return switch (index) {
            case 0 -> Role.FORENSIC_SCIENTIST;
            case 1 -> Role.MURDERER;
            default -> Role.INVESTIGATOR;
        };
    }

    public Map<Player, Role> getRoles() {
        return Collections.unmodifiableMap(roles);
    }
}