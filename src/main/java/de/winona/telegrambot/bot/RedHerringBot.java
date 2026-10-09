package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Lobby;
import de.winona.telegrambot.model.Player;
import de.winona.telegrambot.service.LobbyService;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

public class RedHerringBot implements LongPollingSingleThreadUpdateConsumer {

    private final LobbyService lobbyService = new LobbyService();
    private final TelegramClient telegramClient;

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
        Player player = new Player(message.getFrom().getId(), message.getFrom().getFirstName());

        if (lobby.addPlayer(player)) {
            sendText(chatId, player.name() + " joined. Players: "
                    + lobby.getPlayers().size() + "/" + Lobby.MAX_PLAYERS);
        } else {
            sendText(chatId, player.name() + ", you are already in the game or the lobby is full.");
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}