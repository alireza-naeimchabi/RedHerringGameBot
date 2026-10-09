package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Game;
import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.service.GameService;
import de.winona.telegrambot.service.LobbyService;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import de.winona.telegrambot.model.Card;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import java.util.Optional;

public class RedHerringBot implements LongPollingSingleThreadUpdateConsumer
{

    private final LobbyService lobbyService = new LobbyService();
    private final TelegramClient telegramClient;
    private final GameService gameService = new GameService();


    public RedHerringBot(String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        String command = message.getText().split(" ")[0].split("@")[0];

        switch (command) {
            case "/start" -> sendText(chatId, "Welcome to Red Herring! 🕵️ Add me to a group and send /newgame.");
            case "/newgame" -> handleNewGame(message);
            case "/join" -> handleJoin(message);
            case "/startgame" -> handleStartGame(message);
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

    private void handleStartGame(Message message)
    {
        long chatId = message.getChatId();
        Optional<Lobby> lobbyOpt = lobbyService.getLobby(chatId);

        if (lobbyOpt.isEmpty())
        {
            sendText(chatId, "No game yet. Send /newgame first.");
            return;
        }

        Lobby lobby = lobbyOpt.get();
        if (!lobby.hasEnoughPlayers()) {
            sendText(chatId, "Not enough players. You need at least " + Lobby.MIN_PLAYERS + ".");
            return;
        }

        Game game = gameService.startGame(chatId, lobby.getPlayers());
        lobbyService.removeLobby(chatId);

        game.getRoles().forEach((player, role) ->
                sendText(player.userId(), "Your role: " + role.getTitle() + "\n" + role.getDescription()));

        sendText(chatId, "The game has started! I sent everyone a secret role in private chat. 🤫");
        sendText(chatId, buildCardOverview(game));
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
}