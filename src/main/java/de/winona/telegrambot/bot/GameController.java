package de.winona.telegrambot.bot;

import de.winona.telegrambot.i18n.Language;
import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.i18n.Texts;
import de.winona.telegrambot.model.*;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.StatsService;
import de.winona.telegrambot.ui.Callbacks;
import de.winona.telegrambot.ui.GameScreens;
import de.winona.telegrambot.ui.Screen;
import de.winona.telegrambot.ui.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Runs a case from the moment it opens until the verdict:
 * roles, the murderer's choice and red herring, the scene tiles, the lab, the rounds
 * and the accusations.
 * The rules themselves live in {@link Game}; this class connects them to the chat.
 *
 * <p>Languages: what goes to the group is written for all players of the case, what goes to
 * one player uses that player's language, see {@link Localizer}.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final Messenger messenger;
    private final Localizer localizer;
    private final GameService gameService;
    private final StatsService statsService;
    private final Random random = new Random();

    public GameController(Messenger messenger, Localizer localizer, GameService gameService,
                          StatsService statsService) {
        this.messenger = messenger;
        this.localizer = localizer;
        this.gameService = gameService;
        this.statsService = statsService;
    }

    // ---------------------------------------------------------------- Opening the case

    public void openCase(Room room, List<Player> players, Player forcedPlayer, Role forcedRole) {
        Game game = gameService.startGame(room, players, forcedPlayer, forcedRole);

        game.getRoles().forEach((player, role) ->
                sendPrivately(player, t -> GameScreens.roleBriefing(t, game, role)));

        sendToGroup(game, t -> GameScreens.caseOpened(t, game));
        sendToGroup(game, t -> GameScreens.evidenceBoard(t, game));
        startMurdererChoice(game);
    }

    private void startMurdererChoice(Game game) {
        Player murderer = game.getMurderer();

        if (isTestPlayer(murderer)) {
            game.chooseWeapon(random.nextInt(CardDeck.CARDS_PER_TYPE));
            game.chooseEvidence(random.nextInt(CardDeck.CARDS_PER_TYPE));
            game.chooseScapegoat(random.nextInt(game.getPossibleScapegoats().size()));
            game.plantMeans(random.nextInt(CardDeck.CARDS_PER_TYPE));
            game.plantClue(random.nextInt(CardDeck.CARDS_PER_TYPE));
            announceCrime(game);
            return;
        }
        sendPrivately(murderer, t -> GameScreens.chooseMeans(t, game));
    }

    private void announceCrime(Game game) {
        if (game.isPractice()) {
            // Only practice cases print the solution, so the host cannot peek in a real case.
            log.info("[PRACTICE] Case {}: {} / {} + {}. Red herring on {}: {} + {}",
                    game.getRoom().code(), game.getMurderer().name(),
                    game.getMurderWeapon().name(), game.getKeyEvidence().name(),
                    game.getScapegoat().name(), game.getPlantedMeans().name(), game.getPlantedClue().name());
        }
        sendPrivately(game.getForensicScientist(), t -> GameScreens.solutionForScientist(t, game));
        sendToGroup(game, GameScreens::crimeCommitted);
        askForNextTile(game, null);
    }

    // ---------------------------------------------------------------- Buttons

    /**
     * Handles every button that belongs to a running case.
     */
    public void onClick(Click click) {
        long groupChatId = click.longPart(1);
        Optional<Game> gameOpt = gameService.getGame(groupChatId);
        if (gameOpt.isEmpty()) {
            click.alert("err.case_closed");
            return;
        }
        Game game = gameOpt.get();

        switch (click.part(0)) {
            case Callbacks.WEAPON -> onMeansChosen(click, game);
            case Callbacks.EVIDENCE -> onKeyEvidenceChosen(click, game);
            case Callbacks.FRAME_PLAYER -> onScapegoatChosen(click, game);
            case Callbacks.FRAME_MEANS -> onMeansPlanted(click, game);
            case Callbacks.FRAME_CLUE -> onCluePlanted(click, game);
            case Callbacks.HINT -> onTileAnswered(click, game);
            case Callbacks.OFFERED_TILE -> onNewTileChosen(click, game);
            case Callbacks.REPLACE_SLOT -> onReplaceSlotChosen(click, game);
            case Callbacks.REPLACEMENT_HINT -> onReplacementAnswered(click, game);
            case Callbacks.NEXT_ROUND -> nextRound(game, click.user(), click.inPrivateChat() ? click.message() : null)
                    .ifPresent(click::alert);
            case Callbacks.EVIDENCE_BOARD -> sendToGroup(game, t -> GameScreens.evidenceBoard(t, game));
            case Callbacks.LAB_START -> startLabTest(game, click.user()).ifPresent(click::alert);
            case Callbacks.LAB_SUSPECT -> onLabSuspectChosen(click, game);
            case Callbacks.LAB_CARD -> onLabCardChosen(click, game);
            case Callbacks.LAB_CANCEL -> onLabCancelled(click, game);
            case Callbacks.ACCUSE_START -> startAccusation(game, click.user()).ifPresent(click::alert);
            case Callbacks.ACCUSE_SUSPECT -> onAccusationStep(click, game);
            case Callbacks.ACCUSE_WEAPON -> onAccusationStep(click, game);
            case Callbacks.ACCUSE_EVIDENCE -> onAccusationFinished(click, game);
            case Callbacks.ACCUSE_CANCEL -> onAccusationCancelled(click, game);
            default -> click.alert("err.button_invalid");
        }
    }

    // ---------------------------------------------------------------- Murderer

    private void onMeansChosen(Click click, Game game) {
        int index = click.intPart(2);
        if (game.getMurderer().userId() != click.userId() || game.hasWeapon() || !isCardIndex(index)) {
            click.toast("err.choice_made");
            return;
        }
        game.chooseWeapon(index);
        showToClicker(click, t -> GameScreens.chooseKeyEvidence(t, game));
    }

    private void onKeyEvidenceChosen(Click click, Game game) {
        int index = click.intPart(2);
        if (game.getMurderer().userId() != click.userId() || !game.hasWeapon() || game.hasSolution()
                || !isCardIndex(index)) {
            click.toast("err.choice_made");
            return;
        }
        game.chooseEvidence(index);
        showToClicker(click, t -> GameScreens.chooseScapegoat(t, game));
    }

    private boolean isCardIndex(int index) {
        return index >= 0 && index < CardDeck.CARDS_PER_TYPE;
    }

    // ---------------------------------------------------------------- Red herring

    private boolean mayPlant(Click click, Game game) {
        return game.getMurderer().userId() == click.userId() && game.hasSolution() && !game.isCrimeSealed();
    }

    private void onScapegoatChosen(Click click, Game game) {
        int index = click.intPart(2);
        if (!mayPlant(click, game) || game.hasScapegoat()
                || index < 0 || index >= game.getPossibleScapegoats().size()) {
            click.toast("err.choice_made");
            return;
        }
        game.chooseScapegoat(index);
        showToClicker(click, t -> GameScreens.choosePlantedMeans(t, game));
    }

    private void onMeansPlanted(Click click, Game game) {
        int index = click.intPart(2);
        if (!mayPlant(click, game) || !game.hasScapegoat() || game.hasPlantedMeans() || !isCardIndex(index)) {
            click.toast("err.choice_made");
            return;
        }
        game.plantMeans(index);
        showToClicker(click, t -> GameScreens.choosePlantedClue(t, game));
    }

    private void onCluePlanted(Click click, Game game) {
        int index = click.intPart(2);
        if (!mayPlant(click, game) || !game.hasPlantedMeans() || !isCardIndex(index)) {
            click.toast("err.choice_made");
            return;
        }
        game.plantClue(index);
        showToClicker(click, t -> GameScreens.crimeSealed(t, game));
        announceCrime(game);
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
            sendToGroup(game, t -> GameScreens.sceneReport(t, game));
            presentPrivately(scientist, inPlace, t -> GameScreens.tilesPlaced(t, game));
            return;
        }
        HintTile tile = tileOpt.get();
        presentPrivately(scientist, inPlace, t -> GameScreens.sceneTile(t, game, tile));
    }

    private void onTileAnswered(Click click, Game game) {
        int tileIndex = click.intPart(2);
        int optionIndex = click.intPart(3);
        Optional<HintTile> tile = game.getCurrentHintTile();

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("err.only_scientist_tiles");
            return;
        }
        if (!game.isCrimeSealed() || tile.isEmpty() || tileIndex != game.getCurrentHintIndex()
                || optionIndex < 0 || optionIndex >= tile.get().options().size()) {
            click.toast("err.tile_answered");
            return;
        }

        game.addHint(optionIndex);
        Hint placed = game.getHints().get(game.getHints().size() - 1);
        sendToGroup(game, t -> GameScreens.tileAnswered(t, game, placed));
        askForNextTile(game, click.message());
    }

    // ---------------------------------------------------------------- Typed answers

    /**
     * Text the Forensic Scientist typed in the private chat. If a scene tile is open,
     * the text is the answer to it.
     *
     * @return false if the text is not an answer, so the dashboard can handle it
     */
    public boolean onPrivateText(User user, String text) {
        Optional<Game> gameOpt = gameService.gamesOf(user.getId()).stream()
                .filter(game -> game.getForensicScientist().userId() == user.getId())
                .filter(Game::isWaitingForTypedAnswer)
                .findFirst();
        if (gameOpt.isEmpty()) {
            return false;
        }
        Game game = gameOpt.get();
        Player scientist = game.getForensicScientist();
        boolean replacesOldAnswer = game.hasPendingSlot();

        // One of the suggested ideas, in any language: saved as that option, so every
        // player reads it in their own language. Only free text stays as it was typed.
        OptionalInt option = matchingOption(game.getOpenTile(), text);
        if (option.isPresent()) {
            if (replacesOldAnswer) {
                game.addReplacementHint(option.getAsInt());
            } else {
                game.addHint(option.getAsInt());
            }
        } else {
            Optional<Msg> problem = whyAnswerIsRejected(game, text);
            if (problem.isPresent()) {
                sendPrivately(scientist, t -> GameScreens.notice(t, Theme.ICON_WRONG, problem.get()));
                return true;
            }
            game.addTypedAnswer(text);
        }

        if (replacesOldAnswer) {
            sendToGroup(game, t -> GameScreens.sceneReport(t, game));
            sendPrivately(scientist, t -> GameScreens.tilesPlaced(t, game));
        } else {
            Hint placed = game.getHints().get(game.getHints().size() - 1);
            sendToGroup(game, t -> GameScreens.tileAnswered(t, game, placed));
            askForNextTile(game, null);
        }
        return true;
    }

    /**
     * The option of the tile the text stands for, in any language, or empty for free text.
     */
    private static OptionalInt matchingOption(HintTile tile, String text) {
        String typed = comparable(text);
        for (int i = 0; i < tile.options().size(); i++) {
            for (Language language : Language.values()) {
                if (comparable(Texts.of(language).option(tile.options().get(i))).equals(typed)) {
                    return OptionalInt.of(i);
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Only letters and digits, in lower case, with the Arabic forms of ی and ک written the Persian way,
     * so "Silent." matches "silent" and "بی صدا" matches "بی‌صدا".
     */
    private static String comparable(String text) {
        StringBuilder letters = new StringBuilder();
        text.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .map(c -> c == 'ي' ? 'ی' : c == 'ك' ? 'ک' : c)
                .forEach(letters::appendCodePoint);
        return letters.toString();
    }

    /**
     * A typed answer must be short and must not name a player or a card on the table.
     */
    private Optional<Msg> whyAnswerIsRejected(Game game, String answer) {
        if (answer.codePointCount(0, answer.length()) > Game.MAX_ANSWER_LENGTH) {
            return Optional.of(Msg.of("err.answer.too_long", Game.MAX_ANSWER_LENGTH));
        }
        List<String> forbidden = new ArrayList<>();
        game.getRoles().keySet().forEach(player -> forbidden.add(player.name()));
        for (Hand hand : game.getHands().values()) {
            List<Card> cards = new ArrayList<>(hand.weapons());
            cards.addAll(hand.evidence());
            for (Card card : cards) {
                for (Language language : Language.values()) {
                    forbidden.add(Texts.of(language).card(card));
                }
            }
        }
        boolean namesSomething = forbidden.stream().anyMatch(word -> mentions(answer, word));
        return namesSomething ? Optional.of(Msg.of("err.answer.forbidden")) : Optional.empty();
    }

    /**
     * True if the text contains the word on its own, not as part of a longer word. Ignores case.
     */
    private static boolean mentions(String text, String word) {
        return Pattern.compile("(?iU)(?<!\\w)" + Pattern.quote(word) + "(?!\\w)").matcher(text).find();
    }

    // ---------------------------------------------------------------- Rounds

    public void nextRoundByCommand(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            sendNoCaseHere(groupChatId, user);
            return;
        }
        nextRound(game.get(), user, null).ifPresent(problem -> sendProblem(groupChatId, user, problem));
    }

    /**
     * Starts the next round, or closes the case after the final round.
     *
     * @return empty if it worked, otherwise the reason why not
     */
    private Optional<Msg> nextRound(Game game, User user, MessageRef inPlace) {
        Player scientist = game.getForensicScientist();

        // If the Forensic Scientist is a test player, anyone at the table may move the case on.
        boolean allowed = isTestPlayer(scientist)
                ? game.getPlayer(user.getId()).isPresent()
                : scientist.userId() == user.getId();
        if (!allowed) {
            return Optional.of(Msg.of("err.only_scientist", scientist.name()));
        }
        if (!game.allHintsGiven() || game.isReplacementPending()) {
            return Optional.of(Msg.of("err.round_not_finished"));
        }
        if (game.isLastRound()) {
            if (inPlace != null) {
                messenger.show(inPlace, localizer.forUser(user.getId(), GameScreens::caseClosedByScientist));
            }
            closeCase(game, false, null, Msg.of("verdict.reason.rounds", Game.MAX_ROUNDS));
            return Optional.empty();
        }

        List<HintTile> offered = game.startNextRound();
        sendToGroup(game, t -> GameScreens.roundStarted(t, game));

        if (isTestPlayer(scientist)) {
            game.chooseOfferedTile(random.nextInt(offered.size()));
            int replaceable = game.getHints().size() - Game.FIXED_TILES;
            game.chooseSlotToReplace(Game.FIXED_TILES + random.nextInt(replaceable));
            game.addReplacementHint(random.nextInt(game.getPendingTile().options().size()));
            sendToGroup(game, t -> GameScreens.sceneReport(t, game));
            return Optional.empty();
        }
        presentPrivately(scientist, inPlace, t -> GameScreens.chooseNewTile(t, game));
        return Optional.empty();
    }

    private void onNewTileChosen(Click click, Game game) {
        int round = click.intPart(2);
        int index = click.intPart(3);

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("err.only_scientist_chooses");
            return;
        }
        if (round != game.getRound() || index < 0 || index >= game.getOfferedTiles().size()) {
            click.toast("err.tile_chosen");
            return;
        }
        game.chooseOfferedTile(index);
        showToClicker(click, t -> GameScreens.chooseTileToReplace(t, game, game.getPendingTile()));
    }

    private void onReplaceSlotChosen(Click click, Game game) {
        int round = click.intPart(2);
        int slot = click.intPart(3);

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("err.only_scientist_replaces");
            return;
        }
        if (round != game.getRound() || game.getPendingTile() == null || game.hasPendingSlot()
                || slot < Game.FIXED_TILES || slot >= game.getHints().size()) {
            click.toast("err.tile_replaced");
            return;
        }
        game.chooseSlotToReplace(slot);
        showToClicker(click, t -> GameScreens.replacementTile(t, game, game.getPendingTile()));
    }

    private void onReplacementAnswered(Click click, Game game) {
        int round = click.intPart(2);
        int optionIndex = click.intPart(3);

        if (game.getForensicScientist().userId() != click.userId()) {
            click.alert("err.only_scientist_tiles");
            return;
        }
        if (round != game.getRound() || !game.hasPendingSlot()
                || optionIndex < 0 || optionIndex >= game.getPendingTile().options().size()) {
            click.toast("err.tile_answered");
            return;
        }
        game.addReplacementHint(optionIndex);
        sendToGroup(game, t -> GameScreens.sceneReport(t, game));
        showToClicker(click, t -> GameScreens.tilesPlaced(t, game));
    }

    public void showSceneReport(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            sendNoCaseHere(groupChatId, user);
            return;
        }
        sendToGroup(game.get(), t -> game.get().getHints().isEmpty()
                ? GameScreens.evidenceBoard(t, game.get())
                : GameScreens.sceneReport(t, game.get()));
    }

    public void showEvidenceBoard(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            sendNoCaseHere(groupChatId, user);
            return;
        }
        sendToGroup(game.get(), t -> GameScreens.evidenceBoard(t, game.get()));
    }

    // ---------------------------------------------------------------- Forensic lab

    public void labTestByCommand(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            sendNoCaseHere(groupChatId, user);
            return;
        }
        startLabTest(game.get(), user).ifPresent(problem -> sendProblem(groupChatId, user, problem));
    }

    private Optional<Msg> startLabTest(Game game, User user) {
        Optional<Msg> problem = whyCannotTest(game, user.getId());
        if (problem.isPresent()) {
            return problem;
        }
        Player tester = game.getPlayer(user.getId()).orElseThrow();
        sendToGroup(game, t -> GameScreens.labChooseSuspect(t, game, tester));
        return Optional.empty();
    }

    private Optional<Msg> whyCannotTest(Game game, long userId) {
        Optional<Role> role = game.getRole(userId);
        if (role.isEmpty()) {
            return Optional.of(Msg.of("err.not_in_case"));
        }
        if (role.get() == Role.FORENSIC_SCIENTIST) {
            return Optional.of(Msg.of("err.lab.scientist"));
        }
        if (!game.isLabOpen()) {
            return Optional.of(Msg.of("err.lab.closed"));
        }
        if (!game.hasTestLeft(userId)) {
            return Optional.of(Msg.of("err.lab.used"));
        }
        return Optional.empty();
    }

    /**
     * Checks that the person pressing the button started this lab test and may still test.
     */
    private Optional<Player> testerOf(Click click, Game game) {
        long testerId = click.longPart(2);
        Optional<Player> tester = game.getPlayer(testerId);
        if (tester.isEmpty() || testerId != click.userId()) {
            click.alert("err.lab.belongs", ownerName(tester));
            return Optional.empty();
        }
        Optional<Msg> problem = whyCannotTest(game, testerId);
        if (problem.isPresent()) {
            click.alert(problem.get());
            return Optional.empty();
        }
        return tester;
    }

    /**
     * The name of the player a flow belongs to, or "someone else" in the reader's language.
     */
    private Object ownerName(Optional<Player> owner) {
        return owner.<Object>map(Player::name).orElse(Msg.of("err.someone_else"));
    }

    private void onLabSuspectChosen(Click click, Game game) {
        Optional<Player> tester = testerOf(click, game);
        if (tester.isEmpty()) {
            return;
        }
        int suspectIndex = click.intPart(3);
        if (!isSuspectIndex(game, suspectIndex)) {
            click.alert("err.button_invalid");
            return;
        }
        showToGroup(click, game, t -> GameScreens.labChooseCard(t, game, tester.get(), suspectIndex));
    }

    private void onLabCardChosen(Click click, Game game) {
        Optional<Player> tester = testerOf(click, game);
        if (tester.isEmpty()) {
            return;
        }
        int suspectIndex = click.intPart(3);
        int cardIndex = click.intPart(4);
        if (!isSuspectIndex(game, suspectIndex) || cardIndex < 0 || cardIndex >= 2 * CardDeck.CARDS_PER_TYPE) {
            click.alert("err.button_invalid");
            return;
        }

        Player owner = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(owner);
        List<Card> cards = new ArrayList<>(hand.weapons());
        cards.addAll(hand.evidence());
        Card card = cards.get(cardIndex);

        if (game.findLabResult(card).isPresent()) {
            click.alert("err.lab.tested", Texts.cardMsg(card));
            return;
        }
        LabResult result = game.testCard(tester.get().userId(), owner, card);
        showToGroup(click, game, t -> GameScreens.labResult(t, game, result));
    }

    private void onLabCancelled(Click click, Game game) {
        long testerId = click.longPart(2);
        Optional<Player> tester = game.getPlayer(testerId);
        if (tester.isEmpty() || testerId != click.userId()) {
            click.alert("err.lab.belongs", ownerName(tester));
            return;
        }
        showToGroup(click, game, t -> GameScreens.labCancelled(t, tester.get()));
    }

    // ---------------------------------------------------------------- Accusation

    public void accuseByCommand(long groupChatId, User user) {
        Optional<Game> game = gameService.getGame(groupChatId);
        if (game.isEmpty()) {
            sendNoCaseHere(groupChatId, user);
            return;
        }
        startAccusation(game.get(), user).ifPresent(problem -> sendProblem(groupChatId, user, problem));
    }

    private Optional<Msg> startAccusation(Game game, User user) {
        Optional<Msg> problem = whyCannotAccuse(game, user.getId());
        if (problem.isPresent()) {
            return problem;
        }
        Player accuser = game.getPlayer(user.getId()).orElseThrow();
        sendToGroup(game, t -> GameScreens.accuseChooseSuspect(t, game, accuser));
        return Optional.empty();
    }

    private Optional<Msg> whyCannotAccuse(Game game, long userId) {
        Optional<Role> role = game.getRole(userId);
        if (role.isEmpty()) {
            return Optional.of(Msg.of("err.not_in_case"));
        }
        if (role.get() == Role.FORENSIC_SCIENTIST) {
            return Optional.of(Msg.of("err.accuse.scientist"));
        }
        if (!game.isCrimeSealed()) {
            return Optional.of(Msg.of("err.accuse.not_sealed"));
        }
        if (game.hasAccused(userId)) {
            return Optional.of(Msg.of("err.accuse.used"));
        }
        return Optional.empty();
    }

    /**
     * Checks that the person pressing the button is the one who started this accusation.
     */
    private Optional<Player> accuserOf(Click click, Game game) {
        long accuserId = click.longPart(2);
        Optional<Player> accuser = game.getPlayer(accuserId);
        if (accuser.isEmpty() || accuserId != click.userId()) {
            click.alert("err.accuse.belongs", ownerName(accuser));
            return Optional.empty();
        }
        Optional<Msg> problem = whyCannotAccuse(game, accuserId);
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
            click.alert("err.button_invalid");
            return;
        }

        if (click.part(0).equals(Callbacks.ACCUSE_SUSPECT)) {
            showToGroup(click, game, t -> GameScreens.accuseChooseMeans(t, game, accuser.get(), suspectIndex));
            return;
        }
        int weaponIndex = click.intPart(4);
        if (!isCardIndex(weaponIndex)) {
            click.alert("err.button_invalid");
            return;
        }
        showToGroup(click, game,
                t -> GameScreens.accuseChooseKeyEvidence(t, game, accuser.get(), suspectIndex, weaponIndex));
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
            click.alert("err.button_invalid");
            return;
        }

        Player accuser = accuserOpt.get();
        Player suspect = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(suspect);
        Card weapon = hand.weapons().get(weaponIndex);
        Card evidence = hand.evidence().get(evidenceIndex);

        AccusationResult result = game.accuse(accuser.userId(), suspect, weapon, evidence);

        if (result == AccusationResult.SOLVED) {
            showToGroup(click, game,
                    t -> GameScreens.accusationFinal(t, accuser, suspect, weapon, evidence, true));
            closeCase(game, true, accuser, null);
        } else if (result == AccusationResult.WRONGFUL_CONVICTION) {
            showToGroup(click, game,
                    t -> GameScreens.accusationOfScapegoat(t, accuser, suspect, weapon, evidence));
            closeCase(game, false, null, Msg.of("verdict.reason.framed", suspect.name()));
        } else if (game.allInvestigatorsDone()) {
            showToGroup(click, game,
                    t -> GameScreens.accusationFinal(t, accuser, suspect, weapon, evidence, false));
            closeCase(game, false, null, Msg.of("verdict.reason.attempts"));
        } else {
            showToGroup(click, game, t -> GameScreens.accusationWrong(t, accuser, suspect, weapon, evidence));
        }
    }

    private void onAccusationCancelled(Click click, Game game) {
        long accuserId = click.longPart(2);
        Optional<Player> accuser = game.getPlayer(accuserId);
        if (accuser.isEmpty() || accuserId != click.userId()) {
            click.alert("err.accuse.belongs", ownerName(accuser));
            return;
        }
        showToGroup(click, game, t -> GameScreens.accusationWithdrawn(t, accuser.get()));
    }

    private boolean isSuspectIndex(Game game, int index) {
        return index >= 0 && index < game.getSuspects().size();
    }

    // ---------------------------------------------------------------- Ending the case

    /**
     * Publishes the verdict, counts the result and frees the group for a new room.
     */
    private void closeCase(Game game, boolean investigatorsWon, Player solver, Msg reasonIfUnsolved) {
        sendToGroup(game, t -> investigatorsWon
                ? GameScreens.caseSolved(t, game, solver)
                : GameScreens.murdererEscaped(t, game, reasonIfUnsolved));
        statsService.recordGame(game, investigatorsWon, solver == null ? null : solver.userId());
        gameService.endGame(game.getRoom().groupChatId());
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
            sendProblem(groupChatId, user, Msg.of("err.end.not_allowed"));
            return true;
        }
        gameService.endGame(groupChatId);
        sendToGroup(game, t -> GameScreens.caseAbandoned(t, game, user.getFirstName()));
        return true;
    }

    /**
     * The private overview of a running case for one of its players ("Rejoin Game").
     */
    public Optional<Screen> caseFile(String code, long userId) {
        return gameService.findByCode(code)
                .filter(game -> game.getPlayer(userId).isPresent())
                .map(game -> localizer.forUser(userId, t -> GameScreens.caseFile(t, game, userId)));
    }

    // ---------------------------------------------------------------- Who sees what, in which language

    /**
     * A new message in the group, written for all players of the case.
     */
    private void sendToGroup(Game game, Function<Texts, Screen> view) {
        messenger.send(game.getRoom().groupChatId(), localizer.forPlayers(game.getRoles().keySet(), view));
    }

    /**
     * Changes the group message a button belongs to, written for all players of the case.
     */
    private void showToGroup(Click click, Game game, Function<Texts, Screen> view) {
        messenger.show(click.message(), localizer.forPlayers(game.getRoles().keySet(), view));
    }

    /**
     * Changes the private message a button belongs to, in the language of the player who pressed it.
     */
    private void showToClicker(Click click, Function<Texts, Screen> view) {
        messenger.show(click.message(), view.apply(click.texts()));
    }

    private void sendPrivately(Player player, Function<Texts, Screen> view) {
        if (!isTestPlayer(player)) {
            messenger.send(player.userId(), localizer.forUser(player.userId(), view));
        }
    }

    /**
     * Shows a screen in the player's private chat, reusing their last message if there is one.
     */
    private void presentPrivately(Player player, MessageRef inPlace, Function<Texts, Screen> view) {
        if (isTestPlayer(player)) {
            return;
        }
        Screen screen = localizer.forUser(player.userId(), view);
        if (inPlace != null && inPlace.chatId() == player.userId()) {
            messenger.show(inPlace, screen);
        } else {
            messenger.send(player.userId(), screen);
        }
    }

    /**
     * Answers a command that could not be carried out, in the language of the player who sent it.
     */
    private void sendProblem(long groupChatId, User user, Msg problem) {
        messenger.send(groupChatId,
                localizer.forUser(user.getId(), t -> GameScreens.notice(t, Theme.ICON_WRONG, problem)));
    }

    private void sendNoCaseHere(long groupChatId, User user) {
        messenger.send(groupChatId,
                localizer.forUser(user.getId(), t -> GameScreens.notice(t, Theme.ICON_ROOMS, Msg.of("err.no_case"))));
    }

    private boolean isTestPlayer(Player player) {
        return player.userId() < 0;
    }
}
