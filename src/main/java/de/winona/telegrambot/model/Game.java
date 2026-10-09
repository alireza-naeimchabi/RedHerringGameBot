package de.winona.telegrambot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Game
{

    private final Map<Player, Role> roles = new LinkedHashMap<>();
    private final Map<Player, Hand> hands = new LinkedHashMap<>();

    private Card murderWeapon;
    private Card keyEvidence;

    public Game(List<Player> players, Player forcedMurderer)
    {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        if (forcedMurderer != null && shuffled.remove(forcedMurderer))
        {
            shuffled.add(1, forcedMurderer);
        }

        for (int i = 0; i < shuffled.size(); i++)
        {
            roles.put(shuffled.get(i), roleForIndex(i));
        }

        CardDeck deck = new CardDeck();
        roles.forEach((player, role) -> {
            if (role != Role.FORENSIC_SCIENTIST) {
                hands.put(player, deck.dealHand());
            }
        });
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

    public Map<Player, Hand> getHands() {
        return Collections.unmodifiableMap(hands);
    }

    public Player getMurderer() {
        return findPlayer(Role.MURDERER);
    }

    public Player getForensicScientist() {
        return findPlayer(Role.FORENSIC_SCIENTIST);
    }

    private Player findPlayer(Role role) {
        return roles.entrySet().stream()
                .filter(entry -> entry.getValue() == role)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    //Murderacts

    public Hand getMurdererHand() {
        return hands.get(getMurderer());
    }

    public void chooseWeapon(int index) {
        murderWeapon = getMurdererHand().weapons().get(index);
    }

    public void chooseEvidence(int index) {
        keyEvidence = getMurdererHand().evidence().get(index);
    }

    public boolean hasWeapon() {
        return murderWeapon != null;
    }

    public boolean hasSolution() {
        return murderWeapon != null && keyEvidence != null;
    }

    public Card getMurderWeapon() {
        return murderWeapon;
    }

    public Card getKeyEvidence() {
        return keyEvidence;
    }
}