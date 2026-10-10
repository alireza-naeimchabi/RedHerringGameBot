package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.AccusationResult;
import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.model.CardDeck;
import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Hand;
import de.winona.telegrambot.model.Hint;
import de.winona.telegrambot.model.HintTile;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.model.Room;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.ui.Callbacks;
import de.winona.telegrambot.ui.GameScreens;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.ui.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Runs a case from the moment it opens until the verdict:
 * roles, the murderer's choice, the scene tiles, the rounds and the accusations.
 * The rules themselves live in {@link Game}; this class connects them to the chat.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final Messenger messenger;
    private final GameService gameService;
    private final StatsService statsService;
    private final Random random = new Random();

    public GameController(Messenger messenger, GameService gameService, StatsService statsService) {
        this.messenger = messenger;
        this.gameService = gameService;
        this.statsService = statsService;
    }

    // ---------------------------------------------------------------- Opening the case

    public void openCase(Room room, List<Player> players, Player forcedPlayer, Role forcedRole) {
        long chatId = room.groupChatId();
        Game game = gameService.startGame(room, players, forcedPlayer, forcedRole);

        game.getRoles().forEach((player, role) ->
                sendPrivately(player, GameScreens.roleBriefing(game, role)));

        messenger.send(chatId, GameScreens.caseOpened(game));
        messenger.send(chatId, GameScreens.evidenceBoard(game));
        startMurdererChoice(game);
    }

    private void startMurdererChoice(Game game) {
        Player murderer = game.getMurderer();

        if (isTestPlayer(murderer)) {
            game.chooseWeapon(random.nextInt(CardDeck.CARDS_PER_TYPE));
            game.chooseEvidence(random.nextInt(CardDeck.CARDS_PER_TYPE));
            announceCrime(game);
            return;
        }
        messenger.send(murderer.userId(), GameScreens.chooseMeans(game));
    }

    private void announceCrime(Game game) {
        if (game.isPractice()) {
            // Only practice cases print the solution, so the host cannot peek in a real case.
            log.info("[PRACTICE] Case {}: {} / {} + {}", game.getRoom().code(), game.getMurderer().name(),
                    game.getMurderWeapon().name(), game.getKeyEvidence().name());
        }
        sendPrivately(game.getForensicScientist(), GameScreens.solutionForScientist(game));
        messenger.send(game.getRoom().groupChatId(), GameScreens.crimeCommitted());
        askForNextTile(game, null);
    }

    // ---------------------------------------------------------------- Buttons

    /** Handles every button that belongs to a running case. */
    public void onClick(Click click) {
        long groupChatId = click.longPart(1);
        Optional<Game> gameOpt = gameService.getGame(groupChatId);
        if (gameOpt.isEmpty()) {
            click.alert("This case is closed.");
            return;
        }
        Game game = gameOpt.get();

        switch (click.part(0)) {
            case Callbacks.WEAPON -> onMeansChosen(click, game);
            case Callbacks.EVIDENCE -> onKeyEvidenceChosen(click, game);
            case Callbacks.HINT -> onTileAnswered(click, game);
            case Callbacks.REPLACE_SLOT -> onReplaceSlotChosen(click, game);
            case Callbacks.REPLACEMENT_HINT -> onReplacementAnswered(click, game);
            case Callbacks.NEXT_ROUND -> nextRound(game, click.user(), click.inPrivateChat() ? click.message() : null)
                    .ifPresent(click::alert);
            case Callbacks.EVIDENCE_BOARD -> messenger.send(groupChatId, GameScreens.evidenceBoard(game));
            case Callbacks.ACCUSE_START -> startAccusation(game, click.user()).ifPresent(click::alert);
            case Callbacks.ACCUSE_SUSPECT -> onAccusationStep(click, game);
            case Callbacks.ACCUSE_WEAPON -> onAccusationStep(click, game);
            case Callbacks.ACCUSE_EVIDENCE -> onAccusationFinished(click, game);
            case Callbacks.ACCUSE_CANCEL -> onAccusationCancelled(click, game);
            default -> click.alert("This button is no longer valid.");
        }
    }

    // ---------------------------------------------------------------- Murderer

    private void onMeansChosen(Click click, Game game) {
        int index = click.intPart(2);
        if (game.getMurderer().userId() != click.userId() || game.hasWeapon() || !isCardIndex(index)) {
            click.toast("This choice is already made.");
            return;
        }
        game.chooseWeapon(index);
        messenger.show(click.message(), GameScreens.chooseKeyEvidence(game));
    }

    private void onKeyEvidenceChosen(Click click, Game game) {
        int index = click.intPart(2);
        if (game.getMurderer().userId() != click.userId() || !game.hasWeapon() || game.hasSolution()
                || !isCardIndex(index)) {
            click.toast("This choice is already made.");
            return;
        }
        game.chooseEvidence(index);
        messenger.show(click.message(), GameScreens.crimeSealed(game));
        announceCrime(game);
    }

    private boolean isCardIndex(int index) {
        return index >= 0 && index < CardDeck.CARDS_PER_TYPE;
    }

    // ---------------------------------------------------------------- Scene tiles

    /**
     * Asks the Forensic Scientist for the next answer, or publishes the scene report
     * when all tiles are answered.
     *
     * @param inPlace the private message to reuse, or null to send a new one
     */
    private void askForNextTile(Game game, MessageRef inPlace) {
        Player scientist = game.getForensicScientist();
        Optional<HintTile> tileOpt = game.getCurrentHintTile();

        while (tileOpt.isPresent() && isTestPlayer(scientist)) {
            game.addHint(random.nextInt(tileOpt.get().options().size()));
            tileOpt = game.getCurrentHintTile();
        }

        if (tileOpt.isEmpty()) {
            messenger.send(game.getRoom().groupChatId(), GameScreens.sceneReport(game));
            presentPrivately(scientist, inPlace, GameScreens.tilesPlaced(game));
            return;
        }
        presentPrivately(scientist, inPlace, GameScreens.sceneTile(game, tileOpt.get()));
    }

    private void onTileAnswered(Click click, Game game) {
        int tileIndex = click.intPart(2);
        int optionIndex = click.intPart(3);
        Optional<HintTile> tile = game.getCurrentHintTile();

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("Only the Forensic Scientist answers the scene tiles.");
            return;
        }
        if (!game.hasSolution() || tile.isEmpty() || tileIndex != game.getCurrentHintIndex()
                || optionIndex < 0 || optionIndex >= tile.get().options().size()) {
            click.toast("This tile is already answered.");
            return;
        }

        game.addHint(optionIndex);
        Hint placed = game.getHints().get(game.getHints().size() - 1);
        messenger.send(game.getRoom().groupChatId(), GameScreens.tileAnswered(game, placed));
        askForNextTile(game, click.message());
    }

    // ---------------------------------------------------------------- Rounds

    public void nextRoundByCommand(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            messenger.send(groupChatId, noCaseHere());
            return;
        }
        nextRound(game.get(), user, null)
                .ifPresent(problem -> messenger.send(groupChatId,
                        Screen.text(Theme.ICON_WRONG + " " + Theme.esc(problem))));
    }

    /**
     * Starts the next round, or closes the case after the final round.
     *
     * @return empty if it worked, otherwise the reason why not
     */
    private Optional<String> nextRound(Game game, User user, MessageRef inPlace) {
        long chatId = game.getRoom().groupChatId();
        Player scientist = game.getForensicScientist();

        // If the Forensic Scientist is a test player, anyone at the table may move the case on.
        boolean allowed = isTestPlayer(scientist)
                ? game.getPlayer(user.getId()).isPresent()
                : scientist.userId() == user.getId();
        if (!allowed) {
            return Optional.of("Only the Forensic Scientist, " + scientist.name() + ", can do that.");
        }
        if (!game.allHintsGiven() || game.isReplacementPending()) {
            return Optional.of("The current round is not finished yet.");
        }
        if (game.isLastRound()) {
            if (inPlace != null) {
                messenger.show(inPlace, Screen.text(Theme.ICON_OK + " You closed the case."));
            }
            closeCase(game, false, null, "Round " + Game.MAX_ROUNDS + " ended without a correct accusation.");
            return Optional.empty();
        }

        game.startNextRound();
        game.chooseOfferedTile(0);
        HintTile newTile = game.getPendingTile();
        messenger.send(chatId, GameScreens.roundStarted(game));

        if (isTestPlayer(scientist)) {
            int replaceable = game.getHints().size() - Game.FIXED_TILES;
            game.chooseSlotToReplace(Game.FIXED_TILES + random.nextInt(replaceable));
            game.addReplacementHint(random.nextInt(newTile.options().size()));
            messenger.send(chatId, GameScreens.sceneReport(game));
            return Optional.empty();
        }
        presentPrivately(scientist, inPlace, GameScreens.chooseTileToReplace(game, newTile));
        return Optional.empty();
    }

    private void onReplaceSlotChosen(Click click, Game game) {
        int round = click.intPart(2);
        int slot = click.intPart(3);

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("Only the Forensic Scientist replaces scene tiles.");
            return;
        }
        if (round != game.getRound() || !game.isReplacementPending() || game.hasPendingSlot()
                || slot < Game.FIXED_TILES || slot >= game.getHints().size()) {
            click.toast("This tile is already replaced.");
            return;
        }
        game.chooseSlotToReplace(slot);
        messenger.show(click.message(), GameScreens.replacementTile(game, game.getPendingTile()));
    }

    private void onReplacementAnswered(Click click, Game game) {
        int round = click.intPart(2);
        int optionIndex = click.intPart(3);

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("Only the Forensic Scientist answers the scene tiles.");
            return;
        }
        if (round != game.getRound() || !game.hasPendingSlot()
                || optionIndex < 0 || optionIndex >= game.getPendingTile().options().size()) {
            click.toast("This tile is already answered.");
            return;
        }
        game.addReplacementHint(optionIndex);
        messenger.send(game.getRoom().groupChatId(), GameScreens.sceneReport(game));
        messenger.show(click.message(), GameScreens.tilesPlaced(game));
    }

    public void showSceneReport(long groupChatId) {
        gameService.getGame(groupChatId).ifPresentOrElse(
                game -> messenger.send(groupChatId, game.getHints().isEmpty()
                        ? GameScreens.evidenceBoard(game)
                        : GameScreens.sceneReport(game)),
                () -> messenger.send(groupChatId, noCaseHere()));
    }

    public void showEvidenceBoard(long groupChatId) {
        gameService.getGame(groupChatId).ifPresentOrElse(
                game -> messenger.send(groupChatId, GameScreens.evidenceBoard(game)),
                () -> messenger.send(groupChatId, noCaseHere()));
    }

    // ---------------------------------------------------------------- Accusation

    public void accuseByCommand(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            messenger.send(groupChatId, noCaseHere());
            return;
        }
        startAccusation(game.get(), user)
                .ifPresent(problem -> messenger.send(groupChatId,
                        Screen.text(Theme.ICON_WRONG + " " + Theme.esc(problem))));
    }

    private Optional<String> startAccusation(Game game, User user) {
        Optional<String> problem = whyCannotAccuse(game, user.getId());
        if (problem.isPresent()) {
            return problem;
        }
        Player accuser = game.getPlayer(user.getId()).orElseThrow();
        messenger.send(game.getRoom().groupChatId(), GameScreens.accuseChooseSuspect(game, accuser));
        return Optional.empty();
    }

    private Optional<String> whyCannotAccuse(Game game, long userId) {
        Optional<Role> role = game.getRole(userId);
        if (role.isEmpty()) {
            return Optional.of("You are not part of this case.");
        }
        if (role.get() == Role.FORENSIC_SCIENTIST) {
            return Optional.of("The Forensic Scientist cannot solve the crime. You already know the answer.");
        }
        if (!game.hasSolution()) {
            return Optional.of("The crime has not been committed yet. Wait for the murderer's choice.");
        }
        if (game.hasAccused(userId)) {
            return Optional.of("You have already used your attempt to solve the crime.");
        }
        return Optional.empty();
    }

    /** Checks that the person pressing the button is the one who started this accusation. */
    private Optional<Player> accuserOf(Click click, Game game) {
        long accuserId = click.longPart(2);
        Optional<Player> accuser = game.getPlayer(accuserId);
        if (accuser.isEmpty() || accuserId != click.userId()) {
            click.alert("This accusation belongs to " + accuser.map(Player::name).orElse("someone else") + ".");
            return Optional.empty();
        }
        Optional<String> problem = whyCannotAccuse(game, accuserId);
        if (problem.isPresent()) {
            click.alert(problem.get());
            return Optional.empty();
        }
        return accuser;
    }

    private void onAccusationStep(Click click, Game game) {
        Optional<Player> accuser = accuserOf(click, game);
        if (accuser.isEmpty()) {
            return;
        }
        int suspectIndex = click.intPart(3);
        if (!isSuspectIndex(game, suspectIndex)) {
            click.alert("This button is no longer valid.");
            return;
        }

        if (click.part(0).equals(Callbacks.ACCUSE_SUSPECT)) {
            messenger.show(click.message(), GameScreens.accuseChooseMeans(game, accuser.get(), suspectIndex));
            return;
        }
        int weaponIndex = click.intPart(4);
        if (!isCardIndex(weaponIndex)) {
            click.alert("This button is no longer valid.");
            return;
        }
        messenger.show(click.message(),
                GameScreens.accuseChooseKeyEvidence(game, accuser.get(), suspectIndex, weaponIndex));
    }

    private void onAccusationFinished(Click click, Game game) {
        Optional<Player> accuserOpt = accuserOf(click, game);
        if (accuserOpt.isEmpty()) {
            return;
        }
        int suspectIndex = click.intPart(3);
        int weaponIndex = click.intPart(4);
        int evidenceIndex = click.intPart(5);
        if (!isSuspectIndex(game, suspectIndex) || !isCardIndex(weaponIndex) || !isCardIndex(evidenceIndex)) {
            click.alert("This button is no longer valid.");
            return;
        }

        Player accuser = accuserOpt.get();
        Player suspect = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(suspect);
        Card weapon = hand.weapons().get(weaponIndex);
        Card evidence = hand.evidence().get(evidenceIndex);

        boolean correct = game.accuse(accuser.userId(), suspect, weapon, evidence) == AccusationResult.SOLVED;

        if (correct) {
            messenger.show(click.message(), GameScreens.accusationFinal(accuser, suspect, weapon, evidence, true));
            closeCase(game, true, accuser, null);
        } else if (game.allInvestigatorsDone()) {
            messenger.show(click.message(), GameScreens.accusationFinal(accuser, suspect, weapon, evidence, false));
            closeCase(game, false, null, "Every investigator has used their attempt.");
        } else {
            messenger.show(click.message(), GameScreens.accusationWrong(accuser, suspect, weapon, evidence));
        }
    }

    private void onAccusationCancelled(Click click, Game game) {
        long accuserId = click.longPart(2);
        Optional<Player> accuser = game.getPlayer(accuserId);
        if (accuser.isEmpty() || accuserId != click.userId()) {
            click.alert("This accusation belongs to " + accuser.map(Player::name).orElse("someone else") + ".");
            return;
        }
        messenger.show(click.message(), GameScreens.accusationWithdrawn(accuser.get()));
    }

    private boolean isSuspectIndex(Game game, int index) {
        return index >= 0 && index < game.getSuspects().size();
    }

    // ---------------------------------------------------------------- Ending the case

    /** Publishes the verdict, counts the result and frees the group for a new room. */
    private void closeCase(Game game, boolean investigatorsWon, Player solver, String reasonIfUnsolved) {
        long chatId = game.getRoom().groupChatId();
        messenger.send(chatId, investigatorsWon
                ? GameScreens.caseSolved(game, solver)
                : GameScreens.murdererEscaped(game, reasonIfUnsolved));
        statsService.recordGame(game, investigatorsWon, solver == null ? null : solver.userId());
        gameService.endGame(chatId);
    }

    /**
     * /endgame during a case: the host or a player ends it without a verdict.
     *
     * @return false if no case is running in this group
     */
    public boolean abandonCase(long groupChatId, User user) {
        Optional<Game> gameOpt = gameService.getGame(groupChatId);
        if (gameOpt.isEmpty()) {
            return false;
        }
        Game game = gameOpt.get();
        if (!game.getRoom().isHost(user.getId()) && game.getPlayer(user.getId()).isEmpty()) {
            messenger.send(groupChatId, Screen.text(Theme.ICON_WRONG
                    + " Only the host or a player of this case can end it."));
            return true;
        }
        gameService.endGame(groupChatId);
        messenger.send(groupChatId, GameScreens.caseAbandoned(game, user.getFirstName()));
        return true;
    }

    /** The private overview of a running case for one of its players ("Rejoin Game"). */
    public Optional<Screen> caseFile(String code, long userId) {
        return gameService.findByCode(code)
                .filter(game -> game.getPlayer(userId).isPresent())
                .map(game -> GameScreens.caseFile(game, userId));
    }

    // ---------------------------------------------------------------- Helpers

    private Screen noCaseHere() {
        return Screen.text(Theme.ICON_ROOMS + " There is no running case in this group. Send /newgame to create a room.");
    }

    private boolean isTestPlayer(Player player) {
        return player.userId() < 0;
    }

    private void sendPrivately(Player player, Screen screen) {
        if (!isTestPlayer(player)) {
            messenger.send(player.userId(), screen);
        }
    }

    /** Shows a screen in the player's private chat, reusing their last message if there is one. */
    private void presentPrivately(Player player, MessageRef inPlace, Screen screen) {
        if (isTestPlayer(player)) {
            return;
        }
        if (inPlace != null && inPlace.chatId() == player.userId()) {
            messenger.show(inPlace, screen);
        } else {
            messenger.send(player.userId(), screen);
        }
    }
}
