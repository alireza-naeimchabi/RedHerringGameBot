package de.winona.telegrambot.model;

import java.util.*;

public class Game
{

    private final Map<Player, Role> roles = new LinkedHashMap<>();
    private final Map<Player, Hand> hands = new LinkedHashMap<>();

    private Card murderWeapon;
    private Card keyEvidence;

    private final HintDeck hintDeck = new HintDeck();
    private final List<HintTile> hintTiles = hintDeck.drawStartTiles();
    private final List<Hint> hints = new ArrayList<>();


    public static final int MAX_ROUNDS = 3;
    public static final int FIXED_TILES = 2; // Cause of death + Location bleiben immer

    private int round = 1;
    private HintTile pendingTile;
    private Integer pendingSlot;

    private final Set<Long> usedAccusations = new HashSet<>();

    public Game(List<Player> players, Player forcedPlayer, Role forcedRole) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        if (forcedPlayer != null && shuffled.remove(forcedPlayer)) {
            int index = switch (forcedRole) {
                case FORENSIC_SCIENTIST -> 0;
                case MURDERER -> 1;
                case INVESTIGATOR -> 2;
            };
            shuffled.add(index, forcedPlayer);
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



    public Optional<HintTile> getCurrentHintTile() {
        if (allHintsGiven()) {
            return Optional.empty();
        }
        return Optional.of(hintTiles.get(hints.size()));
    }

    public int getCurrentHintIndex() {
        return hints.size();
    }

    public void addHint(int optionIndex) {
        HintTile tile = hintTiles.get(hints.size());
        hints.add(new Hint(tile.title(), tile.options().get(optionIndex)));
    }

    public boolean allHintsGiven() {
        return hints.size() >= hintTiles.size();
    }

    public List<Hint> getHints() {
        return Collections.unmodifiableList(hints);
    }


    public List<Player> getSuspects() {
        return new ArrayList<>(hands.keySet());
    }

    public Optional<Player> getPlayer(long userId) {
        return roles.keySet().stream()
                .filter(player -> player.userId() == userId)
                .findFirst();
    }

    public boolean canAccuse(long userId) {
        Optional<Player> player = getPlayer(userId);
        return player.isPresent()
                && roles.get(player.get()) != Role.FORENSIC_SCIENTIST
                && hasSolution()
                && !usedAccusations.contains(userId);
    }

    public boolean accuse(long accuserId, Player suspect, Card weapon, Card evidence) {
        usedAccusations.add(accuserId);
        return suspect.equals(getMurderer())
                && weapon.equals(murderWeapon)
                && evidence.equals(keyEvidence);
    }

    public boolean allInvestigatorsDone() {
        return roles.entrySet().stream()
                .filter(entry -> entry.getValue() == Role.INVESTIGATOR)
                .map(Map.Entry::getKey)
                .filter(player -> player.userId() > 0) // Test-Spieler zählen nicht
                .allMatch(player -> usedAccusations.contains(player.userId()));
    }

    public int getRound() {
        return round;
    }

    public boolean isLastRound() {
        return round >= MAX_ROUNDS;
    }

    public HintTile startNextRound() {
        round++;
        pendingTile = hintDeck.drawTile();
        pendingSlot = null;
        return pendingTile;
    }

    public boolean isReplacementPending() {
        return pendingTile != null;
    }

    public HintTile getPendingTile() {
        return pendingTile;
    }

    public boolean hasPendingSlot() {
        return pendingSlot != null;
    }

    public void chooseSlotToReplace(int slot) {
        pendingSlot = slot;
    }

    public void addReplacementHint(int optionIndex) {
        hintTiles.set(pendingSlot, pendingTile);
        hints.set(pendingSlot, new Hint(pendingTile.title(), pendingTile.options().get(optionIndex)));
        pendingTile = null;
        pendingSlot = null;
    }
}