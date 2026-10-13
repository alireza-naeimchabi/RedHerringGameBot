# Red Herring – Telegram-Spielbot

Ein Social-Deduction-Spiel für Telegram-Gruppen (4–7 Spieler), umgesetzt in reinem Java mit
`telegrambots-longpolling`. Fan-Adaption des Brettspiels *Deception: Murder in Hong Kong*,
nicht mit dem Verlag verbunden. Der Bot spricht Englisch und Persisch (Farsi); jeder Spieler wählt seine Sprache selbst.

## Was diese Version anders macht als das Brettspiel

Im Original hängt viel am Raten: Die Hinweise sind vage, die meisten Karten spielen keine Rolle,
und nur der Forensic Scientist hat etwas zu tun. Diese Version ergänzt drei Dinge:

1. **Red Herring (falsche Spur):** Der Mörder wählt nicht nur seine zwei Karten, sondern markiert
   zusätzlich eine Tatwaffe und ein Beweisstück eines unschuldigen Spielers.
2. **Labor:** Jede Runde darf jeder Verdächtige eine beliebige Karte ins Labor schicken (`/lab`).
   Das Ergebnis sehen alle: *Trace found* oder *Clean*. Eine Spur haben die zwei echten und die zwei
   untergeschobenen Karten. Es gibt also zwei Spuren, und nur der Scene Report beschreibt die echte.
3. **Fehlurteil:** Wer den hereingelegten Spieler mit beiden untergeschobenen Karten anklagt,
   verurteilt einen Unschuldigen. Der Mörder gewinnt sofort.

Dazu beschreibt jede Hinweis-Karte jetzt eindeutig eine Kartenart (🔪 Tatwaffe oder 🔍 Beweisstück),
und der Forensic Scientist wählt in Runde 2 und 3 eine von zwei neuen Hinweis-Karten.
Die Regeln stehen in `model/Game.java`, die Hinweis-Karten in `model/HintDeck.java`.

## Bedienung

**Privater Chat (Dashboard):** `/start` öffnet das Dashboard mit Buttons für
*Create Game Room*, *Join Game Room*, *My Game Rooms*, *How to Play*, *Game Roles*,
*My Statistics*, *Leaderboard*, *Profile* und *🌐 Language · زبان*. Das Menü ändert immer dieselbe
Nachricht, statt neue zu schicken.

**Gruppe (Spiel):**

| Befehl | Wirkung |
|---|---|
| `/newgame` | Raum mit 4-stelligem Code öffnen, Raumkarte posten |
| `/join`, `/leave` | Platz nehmen / Platz freigeben (auch per Button oder Code im privaten Chat) |
| `/startgame` | Fall eröffnen (nur Host, ab 4 Spielern) |
| `/lab` | Eine Karte ins Labor schicken, einmal pro Runde (auch per Button *Lab Test*) |
| `/accuse` | Anklage in 3 Schritten (auch per Button *Solve the Crime*) |
| `/nextround` | Nächste Runde (nur Forensic Scientist) |
| `/board`, `/room` | Evidence Board bzw. Raumkarte/Scene Report erneut zeigen |
| `/endgame` | Raum schließen oder laufenden Fall abbrechen |

Zum Testen allein: `/addtestplayers`, danach `/testmurderer`, `/testscientist` oder
`/testinvestigator`. Solche Übungsfälle zählen nicht für die Statistik.

## Sprachen (Englisch und Persisch)

Jeder Spieler stellt seine Sprache selbst ein: im Dashboard oder im Profil über **🌐 Language · زبان**,
oder mit `/language`. Wer nichts wählt, bekommt Englisch. Die Wahl wird in
`data/player-languages.properties` gespeichert und bleibt nach einem Neustart erhalten.

| Wo | In welcher Sprache |
|---|---|
| Privater Chat, Dashboard, geheime Rolle, Fallakte | Sprache des Spielers |
| Kurze Hinweise nach einem Button-Druck (Pop-ups) | Sprache dessen, der gedrückt hat |
| Nachrichten in der Gruppe | Alle Sprachen, die die Spieler dieses Raums gewählt haben |

Eine Gruppennachricht ist bei Telegram für alle dieselbe. Sitzen Spieler mit verschiedenen Sprachen
in einem Raum, steht der Text deshalb in beiden Sprachen untereinander, und jeder Button trägt beide
Beschriftungen (`🔬 Lab Test · آزمایش`). Spielen alle dieselbe Sprache, erscheint nur diese.

So ist es gebaut:

- `i18n/Language` – die Sprachen (Code, Name, Schreibrichtung).
- `src/main/resources/i18n/messages_en.properties` und `messages_fa.properties` – alle Texte,
  gleiche Schlüssel in beiden Dateien. Englisch ist die Standardsprache und der Rückfall.
- `i18n/Texts` – die Texte einer Sprache. Kümmert sich auch um persische Ziffern, die
  Schreibrichtung von rechts nach links und lateinische Namen in persischen Sätzen.
- `bot/Localizer` – entscheidet, in welcher Sprache etwas gezeigt wird.
- `service/LanguageService` – speichert die Wahl jedes Spielers.

Spielregeln, Raumcodes, Button-Aktionen (Callback-Daten) und Statistik kennen keine Sprache:
Karten und Hinweis-Karten werden intern mit ihrem englischen Namen geführt und erst bei der Anzeige
übersetzt (`Sleeping Pills` → Schlüssel `card.sleeping_pills`).

**Weitere Sprache hinzufügen:** Eintrag in `Language` ergänzen, `messages_<code>.properties` anlegen,
Banner mit `python3 tools/make_banners.py src/main/resources/ui/<code> <code>` erzeugen.
`MessagesTest` prüft, dass keine Übersetzung und kein Platzhalter fehlt.

## Struktur

| Paket | Aufgabe |
|---|---|
| `bot` | `RedHerringBot` verteilt Updates an `MenuController`, `RoomController`, `GameController`. `Messenger` ist die einzige Klasse, die mit Telegram spricht. |
| `ui` | Reine Darstellung: `Theme` (Icons, HTML), `Screen`, `Keyboard`, `Callbacks`, `MenuScreens`, `GameScreens`, `Banner`. Keine Spiellogik, keine festen Texte. |
| `i18n` | Übersetzungen: `Language`, `Messages`, `Texts`, `Msg`. |
| `model` | Spielregeln und Daten: `Game`, `Lobby`, `Room`, Karten, Hinweise, `PlayerStats`. |
| `service` | Zustand: `LobbyService` (Räume, Codes, Ablauf nach 2 Stunden), `GameService`, `StatsService`, `LanguageService`. |

Statistik und Rangliste werden in `data/player-stats.properties` gespeichert (nicht im Git).
Die Banner liegen in `src/main/resources/ui` (Englisch) und `src/main/resources/ui/fa` (Persisch) und
lassen sich mit `tools/make_banners.py <Ordner> [en|fa]` neu erzeugen (benötigt Python mit Playwright
und Pillow, für Persisch die Schrift Vazirmatn).

## Tests

```
mvn test
```

Die Tests spielen den Bot über einen simulierten Telegram-Client durch (Dashboard, Räume, ein
kompletter Fall, Statistik) und prüfen jede Nachricht gegen die Telegram-Limits.
`BilingualBotTest` prüft beide Sprachen: Sprachwahl, Speichern über einen Neustart, das persische
Dashboard ohne englische Reste, einen rein persischen Raum und einen gemischten Raum.

## Installation & Start

### 1. Bot bei Telegram erstellen
1. In Telegram den offiziellen Bot [@BotFather](https://t.me/BotFather) öffnen und `/newbot` senden.
2. Einen Namen und einen Benutzernamen vergeben (muss auf `bot` enden).
3. BotFather schickt ein **API-Token**. Dieses Token geheim halten, es gehört nicht ins Git.

### 2. Token als Umgebungsvariable setzen

**IntelliJ IDEA:** *Run → Edit Configurations… → Main → Environment variables*
`TELEGRAM_BOT_TOKEN=<dein Token>`

**Terminal:**
```bash
# Windows PowerShell
$env:TELEGRAM_BOT_TOKEN="<dein Token>"

# Linux / macOS
export TELEGRAM_BOT_TOKEN="<dein Token>"
```

### 3. Starten
- In der IDE: `Main.java` ausführen
- Oder per Maven:
```bash
mvn compile exec:java -Dexec.mainClass="de.winona.telegrambot.Main"
```

### 4. Spielen
1. Bot in eine Telegram-Gruppe einladen.
2. In der Gruppe `/newgame` senden, alle Spieler treten mit `/join` bei.
3. Der Host startet mit `/startgame` (ab 4 Spielern).

Allein testen: `/newgame`, dann `/addtestplayers` und `/testmurderer`, `/testscientist` oder `/testinvestigator`.

## Ablauf einer Nachricht

```
Telegram
   │  Update (Text oder Button-Klick)
   ▼
TelegramBotsLongPollingApplication   (Main.java, Long Polling)
   │
   ▼
RedHerringBot.consume()              entscheidet nur, wer zuständig ist
   │
   ├──► MenuController               Dashboard, Sprache, Statistik
   ├──► RoomController               Räume öffnen, beitreten, starten
   └──► GameController               laufender Fall
            │
            ▼
        Services & Model             Regeln und Zustand (GameService, LobbyService, Game …)
            │
            ▼
        ui (Screens, Keyboard)       baut Text und Buttons
            │
            ▼
        Messenger                    einzige Klasse, die an Telegram sendet
```

## Git-Workflow

- Jede Funktion wurde in einem eigenen Branch entwickelt, z. B.
  `feature/premium-ui`, `feature/bilingual`, `feature/typed-answers`,
  `cleanup/remove-quiz-demo`, `docs/readme`.
- Kleine, häufige Commits mit beschreibenden Nachrichten.
- Jeder Branch wurde über einen Pull Request mit Merge-Commit in `main` übernommen,
  damit die einzelnen Commits in der Historie sichtbar bleiben.

## Nützliche Links

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Java-Bibliothek TelegramBots](https://github.com/rubenlagus/TelegramBots)