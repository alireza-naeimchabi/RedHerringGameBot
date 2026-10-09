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

import java.util.List;
import java.util.Optional;
import java.util.Random;
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
            case "/start" -> sendText(chatId, "Welcome to Red Herring! 🕵️ Add me to a group and send /newgame.");
            case "/newgame" -> handleNewGame(message);
            case "/join" -> handleJoin(message);
            case "/startgame" -> handleStartGame(message, false);
            case "/testmurderer" -> handleStartGame(message, true);
            case "/addtestplayers" -> handleAddTestPlayers(message);
            default -> { }
        }
    }

    private void handleNewGame(Message message) {
        long chatId = message.getChatId();

        if (!message.getChat().isGroupChat() && !message.getChat().isSuperGroupChat()) {
            sendText(chatId, "This game only works in a group.");
            return;
        }

        if (lobbyService.createLobby(chatId)) {
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

    private void handleStartGame(Message message, boolean senderIsMurderer)
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

        Player forcedMurderer = senderIsMurderer
                ? new Player(message.getFrom().getId(), message.getFrom().getFirstName())
                : null;
        Game game = gameService.startGame(chatId, lobby.getPlayers(), forcedMurderer);
        lobbyService.removeLobby(chatId);

        game.getRoles().forEach((player, role) ->
                sendText(player.userId(), "Your role: " + role.getTitle() + "\n" + role.getDescription()));

        sendText(chatId, "The game has started! I sent everyone a secret role in private chat. 🤫");
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

    private void handleCallback(CallbackQuery callback) {
        answerCallback(callback.getId());

        String[] parts = callback.getData().split(":");
        String type = parts[0];
        long groupChatId = Long.parseLong(parts[1]);
        int index = Integer.parseInt(parts[2]);
        long userId = callback.getFrom().getId();

        Optional<Game> gameOpt = gameService.getGame(groupChatId);
        if (gameOpt.isEmpty()) {
            return;
        }

        Game game = gameOpt.get();
        if (game.getMurderer().userId() != userId) {
            return;
        }

        if (type.equals("W") && !game.hasWeapon()) {
            game.chooseWeapon(index);
            sendKeyboard(userId, "🔍 Choose the evidence you left behind:",
                    CardKeyboardFactory.createCardKeyboard(
                            game.getMurdererHand().evidence(), "E:" + groupChatId + ":"));
        } else if (type.equals("E") && game.hasWeapon() && !game.hasSolution()) {
            game.chooseEvidence(index);
            announceSolutionChosen(groupChatId, game);
        }
    }

    private void announceSolutionChosen(long groupChatId, Game game) {
        String solution = game.getMurderWeapon().name() + " + " + game.getKeyEvidence().name();

        sendText(game.getMurderer().userId(), "Your choice: " + solution);
        sendText(game.getForensicScientist().userId(),
                "The murderer is " + game.getMurderer().name() + ".\nSolution: " + solution);
        sendText(groupChatId, "🔪 The murderer has made a choice. The investigation begins!");
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

}