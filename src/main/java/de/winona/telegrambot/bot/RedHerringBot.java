package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.*;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import de.winona.telegrambot.util.CardKeyboardFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;
import java.util.stream.Collectors;


public class RedHerringBot implements LongPollingSingleThreadUpdateConsumer
{

    private final LobbyService lobbyService = new LobbyService();
    private final TelegramClient telegramClient;
    private final GameService gameService = new GameService();


    public RedHerringBot(String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update)
    {
        if (update.hasCallbackQuery())
        {
            handleCallback(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText())
        {
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        String command = message.getText().split(" ")[0].split("@")[0];

        switch (command) {
            case "/nextround" -> handleNextRound(message);
            case "/start" -> sendText(chatId, "Welcome to Red Herring! 🕵️ Add me to a group and send /newgame.");
            case "/newgame" -> handleNewGame(message);
            case "/join" -> handleJoin(message);
            case "/startgame" -> handleStartGame(message, null);
            case "/testmurderer" -> handleStartGame(message, Role.MURDERER);
            case "/testscientist" -> handleStartGame(message, Role.FORENSIC_SCIENTIST);
            case "/addtestplayers" -> handleAddTestPlayers(message);
            case "/testinvestigator" -> handleStartGame(message, Role.INVESTIGATOR);
            case "/accuse" -> handleAccuse(message);
            case "/endgame" -> handleEndGame(message);
            default -> { }
        }
    }

    private void handleNewGame(Message message) {
        long chatId = message.getChatId();

        if (!message.getChat().isGroupChat() && !message.getChat().isSuperGroupChat()) {
            sendText(chatId, "This game only works in a group.");
            return;
        }
        if (gameService.getGame(chatId).isPresent()) {
            sendText(chatId, "A game is already running. Send /endgame to stop it.");
            return;
        }

        Player host = new Player(message.getFrom().getId(), message.getFrom().getFirstName());
        if (lobbyService.createLobby(chatId, message.getChat().getTitle(), host).isPresent()) {
            sendText(chatId, "New game created! Send /join to take part.");
        } else {
            sendText(chatId, "There is already a game in this group.");
        }
    }

    private void handleJoin(Message message) {
        long chatId = message.getChatId();
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(chatId);

        if (lobbyOpt.isEmpty()) {
            sendText(chatId, "No game yet. Send /newgame first.");
            return;
        }

        Lobby lobby = lobbyOpt.get();
        long userId = message.getFrom().getId();
        String name = message.getFrom().getFirstName();

        if (lobby.hasPlayer(userId)) {
            sendText(chatId, name + ", you are already in the game.");
            return;
        }
        if (lobby.isFull()) {
            sendText(chatId, "The lobby is full.");
            return;
        }
        if (!sendText(userId, "You joined a Red Herring game. I will send your secret role here.")) {
            sendText(chatId, name + ", please open a private chat with me and press Start. Then send /join again.");
            return;
        }

        lobby.addPlayer(new Player(userId, name));
        sendText(chatId, name + " joined. Players: "
                + lobby.getPlayers().size() + "/" + Lobby.MAX_PLAYERS);
    }

    private boolean sendText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
            return true;
        } catch (TelegramApiException e) {
            return false;
        }
    }

    private void handleStartGame(Message message, Role forcedRole)
    {
        long chatId = message.getChatId();
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(chatId);

        if (lobbyOpt.isEmpty())
        {
            sendText(chatId, "No game yet. Send /newgame first.");
            return;
        }

        Lobby lobby = lobbyOpt.get();
        if (!lobby.hasEnoughPlayers())
        {
            sendText(chatId, "Not enough players. You need at least " + Lobby.MIN_PLAYERS + ".");
            return;
        }

        Player forcedPlayer = (forcedRole != null)
                ? new Player(message.getFrom().getId(), message.getFrom().getFirstName())
                : null;
        Game game = gameService.startGame(chatId, lobby.getPlayers(), forcedPlayer, forcedRole);
        lobbyService.removeLobby(chatId, RoomEnd.CASE_STARTED);

        game.getRoles().forEach((player, role) ->
                sendText(player.userId(), "Your role: " + role.getTitle() + "\n" + role.getDescription()));

        sendText(chatId, "The game has started! I sent everyone a secret role in private chat. 🤫");
        sendText(chatId, "🧪 The Forensic Scientist is " + game.getForensicScientist().name()
                + ". They know the solution, but they cannot talk about it.");
        sendText(chatId, buildCardOverview(game));
        startMurdererChoice(chatId, game);
    }

    private void handleAddTestPlayers(Message message) {
        long chatId = message.getChatId();
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(chatId);

        if (lobbyOpt.isEmpty()) {
            sendText(chatId, "No game yet. Send /newgame first.");
            return;
        }

        Lobby lobby = lobbyOpt.get();
        lobby.addPlayer(new Player(-1, "Test Anna"));
        lobby.addPlayer(new Player(-2, "Test Ben"));
        lobby.addPlayer(new Player(-3, "Test Clara"));
        sendText(chatId, "Added 3 test players. Players: "
                + lobby.getPlayers().size() + "/" + Lobby.MAX_PLAYERS);
    }

    private String buildCardOverview(Game game) {
        StringBuilder text = new StringBuilder("🗂 Cards on the table\n");

        game.getHands().forEach((player, hand) -> {
            text.append("\n👤 ").append(player.name()).append("\n");
            text.append("🔪 ").append(cardNames(hand.weapons())).append("\n");
            text.append("🔍 ").append(cardNames(hand.evidence())).append("\n");
        });

        return text.toString();
    }

    private String cardNames(List<Card> cards) {
        return cards.stream()
                .map(Card::name)
                .collect(Collectors.joining(", "));
    }


    private void startMurdererChoice(long groupChatId, Game game) {
        Player murderer = game.getMurderer();

        if (murderer.userId() < 0) {
            Random random = new Random();
            game.chooseWeapon(random.nextInt(CardDeck.CARDS_PER_TYPE));
            game.chooseEvidence(random.nextInt(CardDeck.CARDS_PER_TYPE));
            announceSolutionChosen(groupChatId, game);
            return;
        }

        sendKeyboard(murderer.userId(), "🔪 Choose your murder weapon:",
                CardKeyboardFactory.createCardKeyboard(
                        game.getMurdererHand().weapons(), "W:" + groupChatId + ":"));
    }

    private void handleCallback(CallbackQuery callback)
    {
        answerCallback(callback.getId());

        String[] parts = callback.getData().split(":");
        String type = parts[0];
        long groupChatId = Long.parseLong(parts[1]);
        long userId = callback.getFrom().getId();

        Optional<Game> gameOpt = gameService.getGame(groupChatId);
        if (gameOpt.isEmpty()) {
            return;
        }
        Game game = gameOpt.get();

        switch (type) {
            case "W" -> handleWeaponChoice(game, groupChatId, userId, Integer.parseInt(parts[2]));
            case "E" -> handleEvidenceChoice(game, groupChatId, userId, Integer.parseInt(parts[2]));
            case "H" -> handleHintChoice(game, groupChatId, userId,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            case "AP" -> handleAccuseSuspect(game, groupChatId, userId, parts);
            case "AW" -> handleAccuseWeapon(game, groupChatId, userId, parts);
            case "AE" -> handleAccuseEvidence(game, groupChatId, userId, parts);
            case "R" -> handleReplaceSlot(game, groupChatId, userId,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            case "N" -> handleReplacementHint(game, groupChatId, userId,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            default -> { }
        }
    }

    private void handleWeaponChoice(Game game, long groupChatId, long userId, int index) {
        if (game.getMurderer().userId() != userId || game.hasWeapon()) {
            return;
        }
        game.chooseWeapon(index);
        sendKeyboard(userId, "🔍 Choose the evidence you left behind:",
                CardKeyboardFactory.createCardKeyboard(
                        game.getMurdererHand().evidence(), "E:" + groupChatId + ":"));
    }

    private void handleEvidenceChoice(Game game, long groupChatId, long userId, int index)
    {
        if (game.getMurderer().userId() != userId || !game.hasWeapon() || game.hasSolution()) {
            return;
        }
        game.chooseEvidence(index);
        announceSolutionChosen(groupChatId, game);
    }

    private void handleHintChoice(Game game, long groupChatId, long userId, int tileIndex, int optionIndex) {
        if (game.getForensicScientist().userId() != userId) {
            return;
        }
        if (!game.hasSolution() || tileIndex != game.getCurrentHintIndex()) {
            return;
        }
        game.addHint(optionIndex);
        askForNextHint(groupChatId, game);
    }

    private void announceSolutionChosen(long groupChatId, Game game) {
        String solution = game.getMurderWeapon().name() + " + " + game.getKeyEvidence().name();
        System.out.println("[TEST] Solution: " + game.getMurderer().name() + " / " + solution);

        sendText(game.getMurderer().userId(), "Your choice: " + solution);
        sendText(game.getForensicScientist().userId(),
                "The murderer is " + game.getMurderer().name() + ".\nSolution: " + solution);
        sendText(groupChatId, "🔪 The murderer has made a choice. The investigation begins!");
        askForNextHint(groupChatId, game);

    }

    private void sendKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void answerCallback(String callbackId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askForNextHint(long groupChatId, Game game) {
        Optional<HintTile> tileOpt = game.getCurrentHintTile();

        if (tileOpt.isEmpty()) {
            sendText(groupChatId, buildHintOverview(game));
            return;
        }

        HintTile tile = tileOpt.get();
        Player scientist = game.getForensicScientist();

        if (scientist.userId() < 0) {
            game.addHint(new Random().nextInt(tile.options().size()));
            askForNextHint(groupChatId, game);
            return;
        }

        String prefix = "H:" + groupChatId + ":" + game.getCurrentHintIndex() + ":";
        sendKeyboard(scientist.userId(), "🧪 " + tile.title() + " – choose one hint:",
                CardKeyboardFactory.createTextKeyboard(tile.options(), prefix));
    }

    private String buildHintOverview(Game game) {
        StringBuilder text = new StringBuilder("🧪 Hints – Round " + game.getRound()
                + " of " + Game.MAX_ROUNDS + "\n\n");

        game.getHints().forEach(hint ->
                text.append("• ").append(hint.title()).append(": ").append(hint.choice()).append("\n"));

        text.append("\nDiscuss! Use /accuse to solve the crime.\n");
        text.append(game.isLastRound()
                ? "Forensic Scientist: send /nextround to close the case."
                : "Forensic Scientist: send /nextround for a new hint.");
        return text.toString();
    }


    private void handleAccuse(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        String name = message.getFrom().getFirstName();

        Optional<Game> gameOpt = gameService.getGame(chatId);
        if (gameOpt.isEmpty()) {
            sendText(chatId, "There is no running game.");
            return;
        }

        Game game = gameOpt.get();
        if (!game.canAccuse(userId)) {
            sendText(chatId, name + ", you cannot accuse right now.");
            return;
        }

        Map<String, String> buttons = new LinkedHashMap<>();
        List<Player> suspects = game.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            if (suspects.get(i).userId() != userId) {
                buttons.put(suspects.get(i).name(), "AP:" + chatId + ":" + userId + ":" + i);
            }
        }

        sendKeyboard(chatId, "⚖️ " + name + ", who is the murderer?",
                CardKeyboardFactory.createKeyboard(buttons));
    }

    private void handleAccuseSuspect(Game game, long groupChatId, long userId, String[] parts) {
        long accuserId = Long.parseLong(parts[2]);
        int suspectIndex = Integer.parseInt(parts[3]);

        if (userId != accuserId || !game.canAccuse(userId)) {
            return;
        }

        Player suspect = game.getSuspects().get(suspectIndex);
        String prefix = "AW:" + groupChatId + ":" + userId + ":" + suspectIndex + ":";
        sendKeyboard(groupChatId, "🔪 Which weapon did " + suspect.name() + " use?",
                CardKeyboardFactory.createCardKeyboard(game.getHands().get(suspect).weapons(), prefix));
    }

    private void handleAccuseWeapon(Game game, long groupChatId, long userId, String[] parts) {
        long accuserId = Long.parseLong(parts[2]);
        int suspectIndex = Integer.parseInt(parts[3]);
        int weaponIndex = Integer.parseInt(parts[4]);

        if (userId != accuserId || !game.canAccuse(userId)) {
            return;
        }

        Player suspect = game.getSuspects().get(suspectIndex);
        String prefix = "AE:" + groupChatId + ":" + userId + ":" + suspectIndex + ":" + weaponIndex + ":";
        sendKeyboard(groupChatId, "🔍 Which evidence did " + suspect.name() + " leave behind?",
                CardKeyboardFactory.createCardKeyboard(game.getHands().get(suspect).evidence(), prefix));
    }

    private void handleAccuseEvidence(Game game, long groupChatId, long userId, String[] parts) {
        long accuserId = Long.parseLong(parts[2]);
        int suspectIndex = Integer.parseInt(parts[3]);
        int weaponIndex = Integer.parseInt(parts[4]);
        int evidenceIndex = Integer.parseInt(parts[5]);

        if (userId != accuserId || !game.canAccuse(userId)) {
            return;
        }

        Player suspect = game.getSuspects().get(suspectIndex);
        Hand hand = game.getHands().get(suspect);
        Card weapon = hand.weapons().get(weaponIndex);
        Card evidence = hand.evidence().get(evidenceIndex);
        String accuserName = game.getPlayer(userId).map(Player::name).orElse("Someone");

        boolean correct = game.accuse(userId, suspect, weapon, evidence);
        String accusation = "⚖️ " + accuserName + " accuses " + suspect.name()
                + " with " + weapon.name() + " + " + evidence.name() + ".\n\n";

        if (correct) {
            sendText(groupChatId, accusation + "✅ Correct! The investigators win! 🎉\n"
                    + buildSolutionText(game));
            gameService.endGame(groupChatId);
        } else if (game.allInvestigatorsDone()) {
            sendText(groupChatId, accusation + "❌ Wrong! Nobody can accuse anymore. The murderer wins! 🔪\n"
                    + buildSolutionText(game));
            gameService.endGame(groupChatId);
        } else {
            sendText(groupChatId, accusation + "❌ Wrong! The investigation continues.");
        }
    }

    private void handleEndGame(Message message) {
        long chatId = message.getChatId();
        gameService.endGame(chatId);
        lobbyService.removeLobby(chatId, RoomEnd.CLOSED_BY_HOST);
        sendText(chatId, "Game stopped. Send /newgame to start again.");
    }

    private String buildSolutionText(Game game) {
        return "The murderer was " + game.getMurderer().name() + " with "
                + game.getMurderWeapon().name() + " + " + game.getKeyEvidence().name() + ".";
    }

    private void handleNextRound(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();

        Optional<Game> gameOpt = gameService.getGame(chatId);
        if (gameOpt.isEmpty()) {
            sendText(chatId, "There is no running game.");
            return;
        }

        Game game = gameOpt.get();
        Player scientist = game.getForensicScientist();

        if (scientist.userId() > 0 && scientist.userId() != userId) {
            sendText(chatId, "Only the Forensic Scientist can start the next round.");
            return;
        }
        if (!game.allHintsGiven() || game.isReplacementPending()) {
            sendText(chatId, "The current round is not finished yet.");
            return;
        }
        if (game.isLastRound()) {
            sendText(chatId, "⌛ The final round is over and nobody solved the crime. "
                    + "The murderer wins! 🔪\n" + buildSolutionText(game));
            gameService.endGame(chatId);
            return;
        }

        HintTile newTile = game.startNextRound();
        sendText(chatId, "🔔 Round " + game.getRound()
                + " begins. The Forensic Scientist replaces one hint.");

        if (scientist.userId() < 0) {
            Random random = new Random();
            int replaceable = game.getHints().size() - Game.FIXED_TILES;
            game.chooseSlotToReplace(Game.FIXED_TILES + random.nextInt(replaceable));
            game.addReplacementHint(random.nextInt(newTile.options().size()));
            sendText(chatId, buildHintOverview(game));
            return;
        }

        Map<String, String> buttons = new LinkedHashMap<>();
        List<Hint> hints = game.getHints();
        for (int i = Game.FIXED_TILES; i < hints.size(); i++) {
            buttons.put(hints.get(i).title() + ": " + hints.get(i).choice(),
                    "R:" + chatId + ":" + game.getRound() + ":" + i);
        }

        sendKeyboard(scientist.userId(), "🔔 Round " + game.getRound() + ". New tile: "
                        + newTile.title() + "\nWhich old hint do you replace?",
                CardKeyboardFactory.createKeyboard(buttons));
    }

    private void handleReplaceSlot(Game game, long groupChatId, long userId, int round, int slot) {
        if (game.getForensicScientist().userId() != userId) {
            return;
        }
        if (round != game.getRound() || !game.isReplacementPending() || game.hasPendingSlot()) {
            return;
        }

        game.chooseSlotToReplace(slot);
        HintTile tile = game.getPendingTile();
        sendKeyboard(userId, "🧪 " + tile.title() + " – choose one hint:",
                CardKeyboardFactory.createTextKeyboard(tile.options(),
                        "N:" + groupChatId + ":" + round + ":"));
    }

    private void handleReplacementHint(Game game, long groupChatId, long userId, int round, int optionIndex) {
        if (game.getForensicScientist().userId() != userId) {
            return;
        }
        if (round != game.getRound() || !game.hasPendingSlot()) {
            return;
        }

        game.addReplacementHint(optionIndex);
        sendText(groupChatId, buildHintOverview(game));
    }
}