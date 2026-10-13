package de.winona.telegrambot.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules of the red herring and the forensic lab, without any chat around them.
 */
class GameTest {

    private static final Player HOST = new Player(1, "Host");
    private final List<Player> players = List.of(HOST, new Player(2, "Sara"), new Player(3, "Ben"),
            new Player(4, "Clara"), new Player(5, "Dana"));

    private Game game;
    private Player murderer;
    private Player scapegoat;
    private Player bystander;

    @BeforeEach
    void commitACrimeWithARedHerring() {
        game = new Game(new Room("TEST", -1, "Group", HOST, Instant.now()), players, null, null);
        murderer = game.getMurderer();

        assertFalse(game.getPossibleScapegoats().contains(murderer));
        assertFalse(game.getPossibleScapegoats().contains(game.getForensicScientist()));

        game.chooseWeapon(0);
        game.chooseEvidence(1);
        assertFalse(game.isCrimeSealed(), "the red herring is part of the crime");
        game.chooseScapegoat(0);
        game.plantMeans(2);
        game.plantClue(3);
        assertTrue(game.isCrimeSealed());

        scapegoat = game.getScapegoat();
        bystander = game.getPossibleScapegoats().get(1);
        answerOpenTiles();
    }

    private void answerOpenTiles() {
        while (game.getCurrentHintTile().isPresent()) {
            game.addHint(0);
        }
    }

    @Test
    void theSceneReportStartsWithCauseOfDeathAndCoversBothCardTypes() {
        List<Hint> hints = game.getHints();

        assertEquals(6, hints.size());
        assertEquals("Cause of death", hints.get(0).title());
        assertEquals(3, hints.stream().filter(hint -> hint.subject() == TileSubject.MEANS).count());
        assertEquals(3, hints.stream().filter(hint -> hint.subject() == TileSubject.KEY_EVIDENCE).count());
        assertEquals(6, hints.stream().map(Hint::title).distinct().count());
    }

    @Test
    void theLabFindsATraceOnRealAndPlantedCardsAndNowhereElse() {
        List<Card> withTrace = new ArrayList<>();
        int round = 1;
        // Five suspects' worth of cards would need many rounds, so one tester asks again every round.
        for (Player owner : game.getSuspects()) {
            Hand hand = game.getHands().get(owner);
            List<Card> cards = new ArrayList<>(hand.weapons());
            cards.addAll(hand.evidence());
            for (Card card : cards) {
                if (game.testCard(murderer.userId(), owner, card).trace()) {
                    withTrace.add(card);
                }
            }
        }

        assertEquals(List.of(game.getMurderWeapon(), game.getKeyEvidence(),
                        game.getPlantedMeans(), game.getPlantedClue()).stream().sorted(this::byName).toList(),
                withTrace.stream().sorted(this::byName).toList());
        assertEquals(round, game.getRound());
    }

    private int byName(Card a, Card b) {
        return a.name().compareTo(b.name());
    }

    @Test
    void suspectsTestOneCardEachInSeatOrderAndTheForensicScientistNever() {
        List<Player> order = game.getTurnOrder();
        assertEquals(game.getSuspects(), order, "turns follow the seat order");
        assertTrue(game.isLabOpen());
        assertTrue(game.isTurnPhase());
        assertFalse(game.isDiscussion());

        Player first = order.get(0);
        Player second = order.get(1);
        assertTrue(game.canTest(first.userId()));
        assertFalse(game.canTest(second.userId()), "only the player on turn may test");
        assertFalse(game.canTest(game.getForensicScientist().userId()));
        assertFalse(game.canTest(999), "strangers have no test");

        // Index 3 is never a real or a planted card (those are 0 and 1, 2 and 3 only as clue).
        LabResult result = game.testCard(first.userId(), second, game.getHands().get(second).weapons().get(3));
        assertFalse(result.trace());
        assertEquals(1, result.round());
        game.endTurn();
        assertFalse(game.canTest(first.userId()), "one test per round");
        assertTrue(game.canTest(second.userId()), "now the next suspect is up");
        assertEquals(1, game.getTestsUsedThisRound());
        assertEquals(4, game.getTestsPerRound());
        assertTrue(game.findLabResult(result.card()).isPresent());

        for (int i = 1; i < order.size(); i++) {
            game.endTurn();
        }
        assertTrue(game.isDiscussion(), "every suspect had a turn");
        assertTrue(game.getCurrentTurn().isEmpty());

        List<HintTile> offered = game.startNextRound();
        assertFalse(game.isLabOpen(), "the lab waits for the new scene report");
        assertEquals(3, offered.size(), "one means tile, one evidence tile and the murderer profile");
        assertNotEquals(offered.get(0).subject(), offered.get(1).subject());
        assertEquals(HintDeck.MURDERER_PROFILE, offered.get(2));

        game.chooseOfferedTile(1);
        assertTrue(game.isReplacementPending());
        game.chooseSlotToReplace(Game.FIXED_TILES);
        game.addReplacementHint(0);

        assertTrue(game.isLabOpen());
        assertTrue(game.isTurnOf(first.userId()), "a new round starts again with the first seat");
        assertTrue(game.canTest(first.userId()), "a new round brings a new test");
        assertEquals(0, game.getTestsUsedThisRound());
        assertEquals("Cause of death", game.getHints().get(0).title());
        assertEquals(offered.get(1).title(), game.getHints().get(Game.FIXED_TILES).title());
    }

    @Test
    void theMurdererProfileCanBeGivenOncePerCase() {
        List<HintTile> round2 = game.startNextRound();
        assertEquals(HintDeck.MURDERER_PROFILE, round2.get(2));

        game.chooseOfferedTile(2);
        game.chooseSlotToReplace(Game.FIXED_TILES);
        game.addReplacementHint(1);
        Hint profile = game.getHints().get(Game.FIXED_TILES);
        assertEquals(TileSubject.MURDERER, profile.subject());
        assertEquals("Has been quiet", profile.choice());

        List<HintTile> round3 = game.startNextRound();
        assertEquals(2, round3.size());
        assertFalse(round3.contains(HintDeck.MURDERER_PROFILE), "the profile is used up");
    }

    @Test
    void playersKeepTheirSeatOrderSoNoListGivesARoleAway() {
        assertEquals(players, new ArrayList<>(game.getRoles().keySet()));
        List<Player> seatsWithoutScientist = new ArrayList<>(players);
        seatsWithoutScientist.remove(game.getForensicScientist());
        assertEquals(seatsWithoutScientist, game.getSuspects());

        Set<Integer> murdererPositions = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Game another = new Game(new Room("TEST", -1, "Group", HOST, Instant.now()), players, null, null);
            murdererPositions.add(another.getSuspects().indexOf(another.getMurderer()));
        }
        assertEquals(4, murdererPositions.size(), "the murderer can sit anywhere in the suspect list");
    }

    @Test
    void theMurdererCanAccuseButNeverDecidesTheCase() {
        Hand real = game.getMurdererHand();
        Hand framed = game.getScapegoatHand();

        assertFalse(game.canAccuse(murderer.userId()), "nobody accuses before every suspect had a turn");
        game.getTurnOrder().forEach(player -> game.endTurn());
        assertTrue(game.canAccuse(murderer.userId()), "the button gives nothing away");
        assertEquals(AccusationResult.WRONG,
                game.accuse(murderer.userId(), scapegoat, framed.weapons().get(2), framed.evidence().get(3)),
                "the murderer cannot convict the player they framed");
        assertTrue(game.hasAccused(murderer.userId()));
        assertFalse(game.allInvestigatorsDone(), "the murderer's attempt does not count");
        assertEquals(AccusationResult.SOLVED,
                game.accuse(bystander.userId(), murderer, real.weapons().get(0), real.evidence().get(1)));
    }

    @Test
    void theMurdererCannotGiveUpTheCaseOnPurpose() {
        Hand real = game.getMurdererHand();
        assertEquals(AccusationResult.WRONG,
                game.accuse(murderer.userId(), murderer, real.weapons().get(0), real.evidence().get(1)));
    }

    @Test
    void anAccusationIsSolvedWrongOrAWrongfulConviction() {
        Hand real = game.getMurdererHand();
        Hand framed = game.getScapegoatHand();

        assertEquals(AccusationResult.WRONG,
                game.accuse(bystander.userId(), murderer, real.weapons().get(0), real.evidence().get(0)));
        assertEquals(AccusationResult.WRONG,
                game.accuse(bystander.userId(), scapegoat, framed.weapons().get(2), framed.evidence().get(0)),
                "one planted card is not enough for a conviction");
        assertEquals(AccusationResult.WRONGFUL_CONVICTION,
                game.accuse(bystander.userId(), scapegoat, framed.weapons().get(2), framed.evidence().get(3)));
        assertEquals(AccusationResult.SOLVED,
                game.accuse(scapegoat.userId(), murderer, real.weapons().get(0), real.evidence().get(1)));
        assertTrue(game.hasAccused(bystander.userId()));
        assertFalse(game.canAccuse(bystander.userId()));
    }
}
