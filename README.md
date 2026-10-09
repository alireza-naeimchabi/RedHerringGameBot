# Java Telegram-Bot Demo

Dieses Demo-Projekt veranschaulicht die grundlegende Funktionsweise eines **Telegram Bots** in **reinem Java** (Standard Main-Methode ohne Spring Boot Framework) unter Verwendung der offiziellen Java-Bibliothek `telegrambots-longpolling`.

---

## 🚀 Schritt-für-Schritt Anleitung zur Inbetriebnahme

### 1. Bot bei Telegram erstellen (via BotFather)
1. Öffne die Telegram-App und suche nach dem offiziellen Bot **[@BotFather](https://t.me/BotFather)**.
2. Starte den Chat mit `/start` und sende den Befehl `/newbot`.
3. Vergib einen **Namen** für deinen Bot (z.B. `Mein Demo Bot`).
4. Vergib einen **Benutzernamen** (Username), der zwingend auf `bot` enden muss (z.B. `mein_demo_java_bot`).
5. **Wichtig:** BotFather erzeugt ein **HTTP API Token** (z.B. `123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ`). Speichere dieses Token sicher ab.
   - 📖 *Offizielle Dokumentation:* [Telegram BotFather Anleitung](https://core.telegram.org/bots/features#botfather)

---

### 2. Bot-Token als Umgebungsvariable konfigurieren

Setze die Umgebungsvariable `TELEGRAM_BOT_TOKEN` in deinem System oder in IntelliJ IDEA:

#### In IntelliJ IDEA hinterlegen:
1. Klicke in der Menüleiste oben auf **Run** ➔ **Edit Configurations...** (oder klicke droppdown neben dem grünen Play-Button oben rechts).
2. Wähle die Konfiguration deiner Hauptklasse [`Main`](src/main/java/de/winona/telegrambot/Main.java) aus.
3. Suche das Feld **Environment variables** (falls unsichtbar: klicke auf *Modify options* ➔ *Environment variables*).
4. Füge folgende Variable hinzu:
   * **Name:** `TELEGRAM_BOT_TOKEN`
   * **Value:** `DEIN_BOT_TOKEN_VON_BOTFATHER`
5. Bestätige mit **Apply** und **OK**.

#### Über das Terminal (Powershell / Bash):
```bash
# Windows PowerShell:
$env:TELEGRAM_BOT_TOKEN="123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ"

# Linux / macOS:
export TELEGRAM_BOT_TOKEN="123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ"
```

---

### 3. Anwendung starten
- Starte die Anwendung über die [`Main.java`](src/main/java/de/winona/telegrambot/Main.java) Hauptklasse in deiner IDE
- Oder starte über die Konsole (Maven Installation vorausgesetzt):

```bash
mvn compile exec:java -Dexec.mainClass="de.winona.telegrambot.Main"
```

---

## 🧩 Grundlegende Architektur & Komponenten

```
┌────────────────────────────────┐       1. getUpdates (Long Polling)      ┌───────────────────────────┐
│  TelegramBotsLongPollingApp    ├────────────────────────────────────────►│                           │
│  (in Main.java)                │                                         │   Telegram API Server     │
│                                │◄────────────────────────────────────────┤   (https://api.telegram.org)│
│  QuizBot (Consumer)            │       2. Update (Nachrichten/Commands)  │                           │
│                                ├────────────────────────────────────────►│                           │
└────────────────────────────────┘       3. execute(SendMessage)           └───────────────────────────┘
```

### 1. Funktionsweise in Plain Java
Der Lebenszyklus des Bots in Java wird manuell gesteuert:

1. **`TelegramBotsLongPollingApplication`**: Erstellt und verwaltet den Long-Polling-Thread für das Abholen neuer Nachrichten. Über `try-with-resources` wird sichergestellt, dass Ressourcen beim Herunterfahren sauber geschlossen werden.
2. **`registerBot(...)`**: Verknüpft dein Bot-Token mit deiner Consumer-Implementierung ([`DemoBot.java`](src/main/java/de/winona/telegrambot/bot/QuizBot.java)).
3. **`Thread.currentThread().join()`**: Hält den Haupt-Thread aktiv, damit die Anwendung nicht sofort beendet wird.

---

### 2. Hauptkomponenten im Quellcode

| Komponente / Klasse | Beschreibung |
| :--- | :--- |
| **`TelegramBotsLongPollingApplication`** (in `Main.java`)| Die zentrale Anwendungsklasse von TelegramBots, die den Polling-Loop steuert.<br> |
| **`QuizBot`** | Controller/Consumer [`QuizBot`](src/main/java/de/winona/telegrambot/bot/QuizBot.java): Empfängt Updates, verarbeitet Befehle und steuert den Ablauf. |
| **`LongPollingSingleThreadUpdateConsumer`** (von `QuizBot.java` implementiert) | Interface zur Verarbeitung eingehender Nachrichten. Die Methode `consume(Update update)` wird für jede Nachricht aufgerufen. |
| **`QuizService`** | Service [`QuizService`](src/main/java/de/winona/telegrambot/service/QuizService.java): Kapselt die Quiz-Geschäftslogik, Session-Verwaltung und Antwort-Auswertung (Single Responsibility). |
| **`KeyboardFactory`** | Utility [`KeyboardFactory`](src/main/java/de/winona/telegrambot/util/KeyboardFactory.java): Erstellt Inline-Keyboards für Fragen. |
| **`Question`** | Record [`Question`](src/main/java/de/winona/telegrambot/model/Question.java) speichert den Fragetext, die Antwortoptionen und den Index der richtigen Antwort. |
| **`UserQuizState`** | Klasse [`UserQuizState`](src/main/java/de/winona/telegrambot/model/UserQuizState.java) verwaltet den Fortschritt (Frage-Index & Punkte) pro `chatId`. |

---

## 🧠 Quiz-Architektur & Ablauf 

Das Projekt ist nach dem **Single Responsibility Principle (SRP)** strukturiert:

```
                  ┌───────────────────────────┐
                  │         Main.java         │
                  └─────────────┬─────────────┘
                                │ Startet Application & Bot
                                ▼
                  ┌───────────────────────────┐
                  │        QuizBot.java       │  (Controller / Routing)
                  └──────┬─────────────┬──────┘
                         │             │
       Delegiert Logik   │             │ Baut UI Keyboards
                         ▼             ▼
  ┌─────────────────────────┐       ┌─────────────────────────┐
  │    QuizService.java     │       │   KeyboardFactory.java  │
  └────────────┬────────────┘       └─────────────────────────┘
               │ Verwaltet Sessions
               ▼
  ┌─────────────────────────┐
  │   UserQuizState.java    │
  └─────────────────────────┘
```

```
User sendet /quiz oder /start
         │
         ▼
  [UserQuizState.reset()]  ──►  Sende Frage 1 + InlineKeyboard (Buttons)
                                        │
┌───────────────────────────────────────┴──────────────────────────────────────┐
│ User klickt Button (CallbackQuery)  ODER  User tippt Zahl (1, 2, 3)          │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        ▼
                         [handleAnswer(chatId, choice)]
                                        │
           ┌────────────────────────────┴────────────────────────────┐
           ▼                                                         ▼
  Ist Antwort richtig?                                    Gibt es weitere Fragen?
  ├─ Ja  ➔ Score + 1, "Richtig!"                         ├─ Ja  ➔ SendQuestion(next)
  └─ Nein ➔ "Falsch! Richtig war..."                      └─ Nein ➔ FinishQuiz(Score)
```

---

## 📚 Nützliche Links & Dokumentation

* 🌐 **Telegram Bot API Referenz:** [https://core.telegram.org/bots/api](https://core.telegram.org/bots/api)
* 🤖 **Telegram Bots Offizieller Leitfaden:** [https://core.telegram.org/bots](https://core.telegram.org/bots)
* 📦 **Java TelegramBots Library (GitHub):** [https://github.com/rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots)
  * 📖  **Zugehöriges Wiki / Tutorials**: [https://rubenlagus.github.io/TelegramBotsDocumentation/telegram-bots.html](https://rubenlagus.github.io/TelegramBotsDocumentation/telegram-bots.html)

---

## 🚀 Deine Aufgabe 
- Entwickle einen Telegram-Chatbot
- Der Bot sollte in der Lage sein, eine Art Spiel mit dir zu spielen (mögliche Ideen findest du unten)
- Mach dich mit der Struktur der [Telegram-Bot-API](https://core.telegram.org/bots) vertraut
- Erstelle eine sinnvolle, auf OOP basierende Struktur für dein Java-Projekt
- Präsentiere deinen Bot in 6–9 Minuten (idealerweise: Lass die Besucher deinen Bot selbst ausprobieren)
- Überlege dir interessante Erweiterungen der grundlegenden Bot-API (z. B. einen Multiplayer-Modus)
- Stelle deinen Code auf GitHub ein und befolge die Grundregeln für die Verwendung von Git:
    - Kleine und häufige Commits
    - Erstelle Branches, wann immer es sinnvoll ist
     
---

## 💡 Ideen für chatbasierte Spiele (mit freundlicher Unterstützung von ChatGPT) 
1. Krimi-Detektiv: Die Spieler schlüpfen in die Rolle von Detektiven und führen ein Gespräch mit einem KI-gesteuerten Verdächtigen. Sie müssen Fragen stellen, Hinweise sammeln und Schlussfolgerungen ziehen, um ein mysteriöses Verbrechen aufzuklären.
2. Storytelling-Abenteuer: Die Spieler nehmen an einem interaktiven Storytelling-Abenteuer teil, bei dem sie über Chat-Befehle Entscheidungen treffen. Die KI reagiert auf ihre Befehle und treibt die Geschichte basierend auf ihren Entscheidungen voran, was zu unterschiedlichen Verläufen und Enden führt.
3. Quiz-Herausforderung: Die KI stellt den Spielern Quizfragen aus verschiedenen Kategorien, und diese geben ihre Antworten über den Chat ein. Die KI führt den Punktestand und gibt Feedback zu richtigen und falschen Antworten.
4. Rollenspiel (RPG): Die Spieler erstellen ihre Charaktere und begeben sich in einem chatbasierten Rollenspiel auf eine epische Quest. Sie führen Dialoge mit Nicht-Spieler-Charakteren, treffen Entscheidungen und bestreiten Kämpfe – alles über Chat-Befehle.
5. Worträtsel: Die KI stellt den Spielern Worträtsel wie Anagramme, Rätsel oder Wortassoziationen vor. Die Spieler versuchen, die Rätsel zu lösen, indem sie ihre Antworten über den Chat eingeben, und die KI gibt Feedback zur Richtigkeit ihrer Antworten.
6. Persönlichkeitstest: Die KI stellt den Spielern eine Reihe von Fragen, um ihre Persönlichkeit einzuschätzen. Anhand ihrer Antworten erstellt die KI ein individuelles Profil oder gibt Einblicke in ihre Charaktereigenschaften.
7. Virtuelles Haustier: Die Spieler adoptieren ein virtuelles Haustier, das ausschließlich innerhalb der Chat-Oberfläche existiert. Sie können über Chat-Befehle mit dem Haustier interagieren, es füttern, mit ihm spielen und sich um seine Bedürfnisse kümmern.
8. "You don’t know Jack“: Mein persönliches Lieblingsquizspiel! Schaut euch die [JS-basierte Demo](https://de.ydkj.eu/) an.




