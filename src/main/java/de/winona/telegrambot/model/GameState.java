package de.winona.telegrambot.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Read-only view of a running case (MVP): everything a screen may read, nothing it could change.
 * Presenters work with {@link Game} itself; the View only ever gets this interface.
 */

public interface GameState {
    int MAX_ROUNDS = 3;
    /**
     * Cause of death always stays on the board.
     */
    int FIXED_TILES = 1;
    int LAB_TESTS_PER_ROUND = 1;
    /**
     * A typed answer of the Forensic Scientist may be at most this long.
     */
    int MAX_ANSWER_LENGTH = 60;


    Room getRoom();

    boolean isPractice();

    Map<Player, Role> getRoles();

    Map<Player, Hand> getHands();

    Player getMurderer();

    Player getForensicScientist();


    List<Player> getSuspects();

    Optional<Player> getPlayer(long userId);

    Optional<Role> getRole(long userId);

    Hand getMurdererHand();

    boolean hasWeapon();

    boolean hasSolution();

    Card getMurderWeapon();

    Card getKeyEvidence();

    List<Player> getPossibleScapegoats();

    boolean hasScapegoat();

    Player getScapegoat();

    Hand getScapegoatHand();

    boolean hasPlantedMeans();

    Card getPlantedMeans();

    Card getPlantedClue();

    boolean isCrimeSealed();

    Optional<HintTile> getCurrentHintTile();

    int getCurrentHintIndex();

    int getTileCount();

    boolean allHintsGiven();

    List<Hint> getHints();

    int getRound();

    boolean isLastRound();

    List<HintTile> getOfferedTiles();

    boolean isReplacementPending();

    HintTile getPendingTile();

    boolean hasPendingSlot();

    boolean isWaitingForTypedAnswer();

    HintTile getOpenTile();

    boolean isLabOpen();

    boolean hasTestLeft(long userId);

    boolean canTest(long userId);

    Optional<LabResult> findLabResult(Card card);


    List<LabResult> getLabResults();

    int getTestsUsedThisRound();

    int getTestsPerRound();

    List<Player> getTurnOrder();

    int getTurnIndex();

    boolean isTurnPhase();

    boolean isDiscussion();

    Optional<Player> getCurrentTurn();

    boolean isTurnOf(long userId);

    boolean hasAccused(long userId);

    boolean canAccuse(long userId);

    boolean allInvestigatorsDone();
}
