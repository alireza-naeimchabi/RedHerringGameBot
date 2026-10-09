package de.winona.telegrambot.bot;

import de.winona.telegrambot.model.Question;
import de.winona.telegrambot.model.UserQuizState;
import de.winona.telegrambot.service.QuizService;
import de.winona.telegrambot.util.KeyboardFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

/**
 * Controller/Consumer für Telegram Updates im Quiz-Bot.
 * Nimmt eingehende Befehle und Button-Klicks entgegen, steuert den Quiz-Ablauf über den {@link QuizService}
 * und sendet Antworten über den {@link TelegramClient}.
 */
public class QuizBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuizBot.class);

    private static final String COMMAND_START = "/start";
    private static final String COMMAND_QUIZ = "/quiz";
    private static final String COMMAND_HELP = "/help";
    private static final String CALLBACK_ANSWER_PREFIX = "ANS_";

    private final TelegramClient telegramClient;
    private final QuizService quizService;

    /**
     * Erstellt eine neue Instanz des {@link QuizBot} mit dem angegebenen Bot-Token.
     * Erzeugt intern einen neuen {@link QuizService}.
     *
     * @param botToken HTTP API Token von Telegram BotFather
     */
    public QuizBot(String botToken) {
        this(botToken, new QuizService());
    }

    /**
     * Erstellt eine neue Instanz des {@link QuizBot} mit Injection des {@link QuizService}.
     *
     * @param botToken    HTTP API Token von Telegram BotFather
     * @param quizService Service-Instanz zur Verwaltung der Quiz-Logik
     */
    public QuizBot(String botToken, QuizService quizService) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.quizService = quizService;
    }

    /**
     * Hauptmethode des {@link LongPollingSingleThreadUpdateConsumer} Interfaces.
     * Verarbeitet jedes von Telegram empfangene {@link Update}.
     *
     * @param update Enthält Nachrichten-, Chat- oder Callback-Informationen
     */
    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        }
    }

    /**
     * Verarbeitet Interaktionen mit Inline-Keyboard-Buttons (CallbackQueries).
     *
     * @param update Das Telegram Update-Objekt mit CallbackQuery-Daten
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackId = update.getCallbackQuery().getId();

        acknowledgeCallbackQuery(callbackId);

        if (callbackData.startsWith(CALLBACK_ANSWER_PREFIX)) {
            try {
                int chosenIndex = Integer.parseInt(callbackData.substring(CALLBACK_ANSWER_PREFIX.length()));
                evaluateAndAdvanceQuiz(chatId, chosenIndex);
            } catch (NumberFormatException e) {
                log.warn("Ungültiges Callback-Datenformat empfangen: {}", callbackData);
            }
        }
    }

    /**
     * Verarbeitet eingehende Textnachrichten und Befehle wie /start, /quiz oder /help.
     *
     * @param update Das Telegram Update-Objekt mit der Textnachricht
     */
    private void handleTextMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        String senderName = update.getMessage().getFrom().getFirstName();

        log.info("Nachricht empfangen von {} (ChatId {}): {}", senderName, chatId, text);

        if (text.equalsIgnoreCase(COMMAND_START) || text.equalsIgnoreCase(COMMAND_QUIZ)) {
            startQuizFlow(chatId, senderName);
        } else if (text.equalsIgnoreCase(COMMAND_HELP)) {
            sendHelpMessage(chatId);
        } else if (text.matches("[1-3]")) {
            int chosenIndex = Integer.parseInt(text) - 1;
            evaluateAndAdvanceQuiz(chatId, chosenIndex);
        } else {
            sendTextMessage(chatId, "Bitte klicke auf einen Button oder antworte mit **1**, **2** oder **3**.\nStarte das Quiz jederzeit neu mit `/quiz`.");
        }
    }

    /**
     * Initalisiert ein neues Quiz für den angegebenen Benutzer und sendet die erste Frage.
     *
     * @param chatId     Telegram Chat-ID des Benutzers
     * @param senderName Vorname des Benutzers für die persönliche Begrüßung
     */
    private void startQuizFlow(Long chatId, String senderName) {
        UserQuizState state = quizService.startQuiz(chatId);

        sendTextMessage(chatId, "Hallo " + senderName + "! 🧠 Willkommen zum **Java-Quiz**!\n"
                + "Es erwarten dich " + quizService.getTotalQuestions() + " Fragen. Wähle jeweils die richtige Antwort aus.");

        sendNextQuestion(chatId, state);
    }

    /**
     * Baut und sendet die nächste Quizfrage inklusive des Inline-Keyboards.
     *
     * @param chatId Telegram Chat-ID
     * @param state  Aktueller Quiz-Zustand des Benutzers
     */
    private void sendNextQuestion(Long chatId, UserQuizState state) {
        Optional<Question> questionOpt = quizService.getCurrentQuestion(state);
        if (questionOpt.isEmpty()) {
            finishQuizFlow(chatId, state);
            return;
        }

        Question question = questionOpt.get();
        InlineKeyboardMarkup keyboard = KeyboardFactory.createQuestionKeyboard(question, CALLBACK_ANSWER_PREFIX);

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(question.text())
                .replyMarkup(keyboard)
                .build();

        executeTelegramMethod(message);
    }

    /**
     * Wertet eine abgegebene Antwort aus, gibt Feedback und schaltet zur nächsten Frage weiter.
     *
     * @param chatId      Telegram Chat-ID
     * @param chosenIndex Ausgewählter Antworten-Index (0-basiert)
     */
    private void evaluateAndAdvanceQuiz(Long chatId, int chosenIndex) {
        Optional<UserQuizState> sessionOpt = quizService.getSession(chatId);
        if (sessionOpt.isEmpty() || quizService.isQuizFinished(sessionOpt.get())) {
            sendTextMessage(chatId, "Kein aktives Quiz gefunden. Sende `/quiz` um ein neues Quiz zu starten!");
            return;
        }

        UserQuizState state = sessionOpt.get();
        Optional<Question> questionBeforeOpt = quizService.getCurrentQuestion(state);

        if (questionBeforeOpt.isEmpty()) {
            return;
        }

        Question currentQuestion = questionBeforeOpt.get();
        boolean isCorrect = quizService.processAnswer(state, chosenIndex);

        if (isCorrect) {
            sendTextMessage(chatId, "✅ *Richtig!* (" + currentQuestion.options().get(chosenIndex) + ")");
        } else {
            String correctAnswerText = currentQuestion.options().get(currentQuestion.correctIndex());
            sendTextMessage(chatId, "❌ *Falsch!* Die richtige Antwort war: *" + correctAnswerText + "*");
        }

        if (quizService.isQuizFinished(state)) {
            finishQuizFlow(chatId, state);
        } else {
            sendNextQuestion(chatId, state);
        }
    }

    /**
     * Beendet das Quiz für den Benutzer, wertet die Punkte aus und sendet die Abschlussmeldung.
     *
     * @param chatId Telegram Chat-ID
     * @param state  Aktueller Quiz-Zustand des Benutzers
     */
    private void finishQuizFlow(Long chatId, UserQuizState state) {
        int score = state.getScore();
        int total = quizService.getTotalQuestions();

        String feedback = (score == total) ? " 🏆 Perfekt! Du hast alle Fragen richtig beantwortet!" :
                (score > 0) ? " 👍 Gut gemacht!" : " 😅 Besser beim nächsten Mal!";

        sendTextMessage(chatId, "🎉 *Quiz beendet!*\n\n"
                + "Dein Ergebnis: *" + score + " von " + total + " Punkten*" + feedback + "\n\n"
                + "Möchtest du nochmal spielen? Sende einfach `/quiz`!");
    }

    /**
     * Sendet eine Übersicht der verfügbaren Befehle und Bedienungshinweise.
     *
     * @param chatId Telegram Chat-ID
     */
    private void sendHelpMessage(Long chatId) {
        sendTextMessage(chatId, "🤖 *Quiz-Bot Hilfemenü*\n\n"
                + "• `/quiz` oder `/start` - Startet ein neues Quiz (" + quizService.getTotalQuestions() + " Fragen)\n"
                + "• `/help` - Zeigt diese Hilfe an\n\n"
                + "Antworte während des Quiz entweder per Klick auf die Buttons oder durch Eingabe von 1, 2 oder 3.");
    }

    /**
     * Bestätigt den Empfang einer CallbackQuery bei Telegram, um das Lade-Icon auf dem Button zu beenden.
     *
     * @param callbackId Eindeutige ID der Callback-Anfrage
     */
    private void acknowledgeCallbackQuery(String callbackId) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .build();
        executeTelegramMethod(answer);
    }

    /**
     * Hilfsmethode zum Formatisieren und Senden einer reinen Textnachricht (Markdown).
     *
     * @param chatId Telegram Chat-ID
     * @param text   Textinhalt der Nachricht
     */
    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();

        executeTelegramMethod(message);
    }

    /**
     * Führt eine Methode der Telegram Bot API über den {@link TelegramClient} aus und fängt Ausnahmen ab.
     *
     * @param method Telegram API-Methode (z.B. SendMessage, AnswerCallbackQuery)
     */
    private void executeTelegramMethod(org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod<?> method) {
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e) {
            log.error("Fehler bei Telegram API-Aufruf (Methode {}):", method.getMethod(), e);
        }
    }
}
