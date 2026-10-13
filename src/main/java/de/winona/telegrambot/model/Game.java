package de.winona.telegrambot.model;

import java.util.*;

/**
 * The rules of one case.
 *
 * <p>On top of the board game this version adds two things that belong together:
 * the murderer plants a <b>red herring</b> (two cards of an innocent player), and every round
 * each suspect may send one card to the <b>forensic lab</b>. The lab finds a trace on the real
 * cards and on the planted ones alike, so only the scene tiles tell the two trails apart.
 */
public class Game {

    public static final int MAX_ROUNDS = 3;
    /**
     * Cause of death always stays on the board.
     */
    public static final int FIXED_TILES = 1;
    public static final int LAB_TESTS_PER_ROUND = 1;

    /**
     * A typed answer of the Forensic Scientist may be at most this long.
     */
    public static final int MAX_ANSWER_LENGTH = 60;

    private final Room room;
    private final boolean practice;
    private final Map<Player, Role> roles = new LinkedHashMap<>();
    private final Map<Player, Hand> hands = new LinkedHashMap<>();

    // The crime
    private Card murderWeapon;
    private Card keyEvidence;

    // The red herring
    private Player scapegoat;
    private Card plantedMeans;
    private Card plantedClue;

    // Scene tiles
    private final HintDeck hintDeck = new HintDeck();
    private final List<HintTile> hintTiles = hintDeck.drawStartTiles();
    private final List<Hint> hints = new ArrayList<>();

    // Rounds
    private int round = 1;
    private List<HintTile> offeredTiles = List.of();
    private HintTile pendingTile;
    private Integer pendingSlot;

    // Lab and accusations
    private final List<LabResult> labResults = new ArrayList<>();
    private final Map<Long, Integer> testsInCurrentRound = new HashMap<>();
    private final Set<Long> usedAccusations = new HashSet<>();
    // Turns: after the scene report every real suspect takes one turn, in seat order
    private int turnIndex = 0;

    public Game(Room room, List<Player> players, Player forcedPlayer, Role forcedRole) {
        this.room = room;
        this.practice = forcedRole != null
                || players.stream().anyMatch(player -> player.userId() < 0);

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

        // The shuffled order decides the roles. The players are stored in their seat order,
        // otherwise the murderer would always be listed first among the suspects.
        Map<Player, Role> drawn = new HashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            drawn.put(shuffled.get(i), roleForIndex(i));
        }
        for (Player player : players) {
            roles.put(player, drawn.get(player));
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

    // ---------------------------------------------------------------- Players and roles

    public Room getRoom() {
        return room;
    }

    /**
     * A practice case uses test players or a forced role and does not count for statistics.
     */
    public boolean isPractice() {
        return practice;
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

    /**
     * Everyone who has cards: the murderer and the investigators.
     */
    public List<Player> getSuspects() {
        return new ArrayList<>(hands.keySet());
    }

    public Optional<Player> getPlayer(long userId) {
        return roles.keySet().stream()
                .filter(player -> player.userId() == userId)
                .findFirst();
    }

    public Optional<Role> getRole(long userId) {
        return getPlayer(userId).map(roles::get);
    }

    // ---------------------------------------------------------------- The crime

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

    /**
     * True once the murderer has chosen the Means of Murder and the Key Evidence.
     */
    public boolean hasSolution() {
        return murderWeapon != null && keyEvidence != null;
    }

    public Card getMurderWeapon() {
        return murderWeapon;
    }

    public Card getKeyEvidence() {
        return keyEvidence;
    }

    // ---------------------------------------------------------------- The red herring

    /**
     * The players the murderer can frame: every suspect except the murderer.
     */
    public List<Player> getPossibleScapegoats() {
        List<Player> others = getSuspects();
        others.remove(getMurderer());
        return others;
    }

    public void chooseScapegoat(int index) {
        scapegoat = getPossibleScapegoats().get(index);
    }

    public boolean hasScapegoat() {
        return scapegoat != null;
    }

    public Player getScapegoat() {
        return scapegoat;
    }

    public Hand getScapegoatHand() {
        return hands.get(scapegoat);
    }

    public void plantMeans(int index) {
        plantedMeans = getScapegoatHand().weapons().get(index);
    }

    public void plantClue(int index) {
        plantedClue = getScapegoatHand().evidence().get(index);
    }

    public boolean hasPlantedMeans() {
        return plantedMeans != null;
    }

    public Card getPlantedMeans() {
        return plantedMeans;
    }

    public Card getPlantedClue() {
        return plantedClue;
    }

    /**
     * True once the solution is chosen and the red herring is planted. Only then the case can move on.
     */
    public boolean isCrimeSealed() {
        return hasSolution() && scapegoat != null && plantedMeans != null && plantedClue != null;
    }

    // ---------------------------------------------------------------- Scene tiles

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
        hints.add(new Hint(tile.title(), tile.options().get(optionIndex), tile.subject()));
    }

    public int getTileCount() {
        return hintTiles.size();
    }

    public boolean allHintsGiven() {
        return hints.size() >= hintTiles.size();
    }

    public List<Hint> getHints() {
        return Collections.unmodifiableList(hints);
    }

    // ---------------------------------------------------------------- Rounds

    public int getRound() {
        return round;
    }

    public boolean isLastRound() {
        return round >= MAX_ROUNDS;
    }

    /**
     * Starts the next round. The Forensic Scientist gets new tiles to choose from
     * and every suspect gets a fresh lab test.
     */
    public List<HintTile> startNextRound() {
        round++;
        testsInCurrentRound.clear();
        turnIndex = 0;
        List<HintTile> offer = new ArrayList<>(hintDeck.drawOffer());
        if (hints.stream().noneMatch(hint -> hint.subject() == TileSubject.MURDERER)) {
            offer.add(HintDeck.MURDERER_PROFILE);
        }
        offeredTiles = offer;
        pendingTile = null;
        pendingSlot = null;
        return offeredTiles;
    }

    /**
     * The tiles the Forensic Scientist may choose from. Empty once one is chosen.
     */
    public List<HintTile> getOfferedTiles() {
        return offeredTiles;
    }

    public void chooseOfferedTile(int index) {
        pendingTile = offeredTiles.get(index);
        offeredTiles = List.of();
    }

    /**
     * True while the Forensic Scientist still has to choose, place or answer the new tile.
     */
    public boolean isReplacementPending() {
        return !offeredTiles.isEmpty() || pendingTile != null;
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
        hints.set(pendingSlot, new Hint(pendingTile.title(), pendingTile.options().get(optionIndex),
                pendingTile.subject()));
        pendingTile = null;
        pendingSlot = null;
    }

    // ---------------------------------------------------------------- Typed answers

    /**
     * True while the Forensic Scientist has a scene tile open that they answer by typing.
     */
    public boolean isWaitingForTypedAnswer() {
        return isCrimeSealed() && (getCurrentHintTile().isPresent() || hasPendingSlot());
    }

    /**
     * The scene tile the Forensic Scientist answers right now.
     */
    public HintTile getOpenTile() {
        return hasPendingSlot() ? pendingTile : getCurrentHintTile().orElseThrow();
    }

    /**
     * Saves a typed answer: as the next answer in round 1, or in place of an old one in rounds 2 and 3.
     */
    public void addTypedAnswer(String answer) {
        if (hasPendingSlot()) {
            hintTiles.set(pendingSlot, pendingTile);
            hints.set(pendingSlot, new Hint(pendingTile.title(), answer, pendingTile.subject(), true));
            pendingTile = null;
            pendingSlot = null;
        } else {
            HintTile tile = hintTiles.get(hints.size());
            hints.add(new Hint(tile.title(), answer, tile.subject(), true));
        }
    }

    // ---------------------------------------------------------------- Forensic lab

    /**
     * The lab works on the cards once the scene report of the current round is complete.
     */
    public boolean isLabOpen() {
        return isCrimeSealed() && allHintsGiven() && !isReplacementPending();
    }

    public boolean hasTestLeft(long userId) {
        return testsInCurrentRound.getOrDefault(userId, 0) < LAB_TESTS_PER_ROUND;
    }

    public boolean canTest(long userId) {
        return getRole(userId).filter(role -> role != Role.FORENSIC_SCIENTIST).isPresent()
                && isLabOpen()
                && hasTestLeft(userId)
                && isTurnOf(userId);
    }

    public Optional<LabResult> findLabResult(Card card) {
        return labResults.stream().filter(result -> result.card().equals(card)).findFirst();
    }

    /**
     * Sends one card to the lab and uses the tester's test for this round.
     * The lab cannot tell a real trace from a planted one.
     */
    public LabResult testCard(long testerId, Player owner, Card card) {
        Player tester = getPlayer(testerId).orElseThrow();
        testsInCurrentRound.merge(testerId, 1, Integer::sum);
        LabResult result = new LabResult(round, tester, owner, card, leavesTrace(card));
        labResults.add(result);
        return result;
    }

    private boolean leavesTrace(Card card) {
        return card.equals(murderWeapon) || card.equals(keyEvidence)
                || card.equals(plantedMeans) || card.equals(plantedClue);
    }

    public List<LabResult> getLabResults() {
        return Collections.unmodifiableList(labResults);
    }

    /**
     * How many lab tests were used in the current round.
     */
    public int getTestsUsedThisRound() {
        return testsInCurrentRound.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * How many lab tests real players have per round. Test players never use the lab.
     */
    public int getTestsPerRound() {
        return (int) getSuspects().stream().filter(player -> player.userId() > 0).count() * LAB_TESTS_PER_ROUND;
    }

    // ---------------------------------------------------------------- Turns

    /**
     * The suspects who take turns, in seat order. Test players never take a turn.
     */
    public List<Player> getTurnOrder() {
        return getSuspects().stream().filter(player -> player.userId() > 0).toList();
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * True while the suspects are taking their turns: the report is out and not everyone has had a turn.
     */
    public boolean isTurnPhase() {
        return isLabOpen() && turnIndex < getTurnOrder().size();
    }

    /**
     * True once every suspect has had a turn: now the crime can be solved or the next round started.
     */
    public boolean isDiscussion() {
        return isLabOpen() && turnIndex >= getTurnOrder().size();
    }

    public Optional<Player> getCurrentTurn() {
        return isTurnPhase() ? Optional.of(getTurnOrder().get(turnIndex)) : Optional.empty();
    }

    public boolean isTurnOf(long userId) {
        return getCurrentTurn().filter(player -> player.userId() == userId).isPresent();
    }

    /**
     * The current player has tested a card or skipped: the next one is up.
     */
    public void endTurn() {
        turnIndex++;
    }
    // ---------------------------------------------------------------- Accusations

    public boolean hasAccused(long userId) {
        return usedAccusations.contains(userId);
    }

    public boolean canAccuse(long userId) {
        Optional<Player> player = getPlayer(userId);
        return player.isPresent()
                && roles.get(player.get()) != Role.FORENSIC_SCIENTIST
                && isCrimeSealed()
                && !usedAccusations.contains(userId)
                && isDiscussion();
    }

    /**
     * Judges an accusation and uses the accuser's single attempt.
     * The murderer may accuse too, so that pressing the button gives nothing away,
     * but their accusation is always wrong: they cannot convict the player they framed
     * themselves, and they cannot give up the case on purpose.
     */
    public AccusationResult accuse(long accuserId, Player suspect, Card weapon, Card evidence) {
        usedAccusations.add(accuserId);
        if (accuserId == getMurderer().userId()) {
            return AccusationResult.WRONG;
        }
        if (suspect.equals(getMurderer()) && weapon.equals(murderWeapon) && evidence.equals(keyEvidence)) {
            return AccusationResult.SOLVED;
        }
        if (suspect.equals(scapegoat) && weapon.equals(plantedMeans) && evidence.equals(plantedClue)) {
            return AccusationResult.WRONGFUL_CONVICTION;
        }
        return AccusationResult.WRONG;
    }

    public boolean allInvestigatorsDone() {
        return roles.entrySet().stream()
                .filter(entry -> entry.getValue() == Role.INVESTIGATOR)
                .map(Map.Entry::getKey)
                .filter(player -> player.userId() > 0) // Test-Spieler zählen nicht
                .allMatch(player -> usedAccusations.contains(player.userId()));
    }
}
