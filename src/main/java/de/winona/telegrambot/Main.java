package de.winona.telegrambot;

import de.winona.telegrambot.bot.QuizBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

/**
 * Haupteinstiegspunkt der Telegram Bot Anwendung.
 * Liest die Konfiguration aus Umgebungsvariablen und startet die Long-Polling-Anwendung.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String ENV_BOT_TOKEN = "TELEGRAM_BOT_TOKEN";

    public static void main(String[] args) {
        String botToken = fetchBotToken();

        log.info("Starte Telegram Bot...");

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new QuizBot(botToken));
            log.info("Bot erfolgreich registriert und gestartet! Drücke Strg+C zum Beenden.");

            // Haupt-Thread am Leben halten
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("Anwendung durch Benutzer beendet.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Kritischer Fehler beim Ausführen des Bots:", e);
        }
    }

    private static String fetchBotToken() {
        String botToken = System.getenv(ENV_BOT_TOKEN);

        if (botToken == null || botToken.isBlank() || "DEIN_BOT_TOKEN_HIER".equals(botToken)) {
            log.error("FEHLER: Kein Telegram Bot Token in Umgebungsvariable '{}' gefunden!", ENV_BOT_TOKEN);
            log.error("Bitte setze die Umgebungsvariable '{}' vor dem Start.", ENV_BOT_TOKEN);
            System.exit(1);
        }

        return botToken;
    }
}
