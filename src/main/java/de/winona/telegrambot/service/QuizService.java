package de.winona.telegrambot.service;

import de.winona.telegrambot.model.Question;
import de.winona.telegrambot.model.UserQuizState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service-Klasse zur Kapselung der Quiz-Geschäftslogik und Verwaltung der Benutzer-Sessions.
 */
public class QuizService {

    private final List<Question> questions;
    private final Map<Long, UserQuizState> userSessions = new ConcurrentHashMap<>();

    public QuizService() {
        this.questions = List.of(
                new Question(
                        "Frage 1 von 3:\nWelches Schlüsselwort wird in Java verwendet, um von einer Klasse zu erben?",
                        List.of("implements", "extends", "inherits"),
                        1
                ),
                new Question(
                        "Frage 2 von 3:\nWelche Datenstruktur arbeitet nach dem LIFO-Prinzip (Last In, First Out)?",
                        List.of("Queue", "List", "Stack"),
                        2
                ),
                new Question(
                        "Frage 3 von 3:\nWofür steht die Abkürzung JVM in der Java-Entwicklung?",
                        List.of("Java Virtual Machine", "Java Variable Manager", "Java Visual Mode"),
                        0
                )
        );
    }

    /**
     * Startet oder setzt ein Quiz für eine bestimmte Chat-ID zurück.
     *
     * @param chatId Telegram Chat-ID des Benutzers
     * @return Initialisierter {@link UserQuizState}
     */
    public UserQuizState startQuiz(Long chatId) {
        UserQuizState state = userSessions.computeIfAbsent(chatId, k -> new UserQuizState());
        state.reset();
        return state;
    }

    /**
     * Liefert den aktuellen Quiz-Zustand eines Benutzers, falls vorhanden.
     *
     * @param chatId Telegram Chat-ID
     * @return Optional des {@link UserQuizState}
     */
    public Optional<UserQuizState> getSession(Long chatId) {
        return Optional.ofNullable(userSessions.get(chatId));
    }

    /**
     * Liefert die aktuell zu beantwortende Frage für den gegebenen Zustand.
     *
     * @param state Der aktuelle Benutzer-Zustand
     * @return Optional der {@link Question}, falls das Quiz noch nicht beendet ist
     */
    public Optional<Question> getCurrentQuestion(UserQuizState state) {
        if (state.getCurrentQuestionIndex() < questions.size()) {
            return Optional.of(questions.get(state.getCurrentQuestionIndex()));
        }
        return Optional.empty();
    }

    /**
     * Wertet eine abgegebene Antwort aus, erhöht ggf. den Punktestand und rückt zur nächsten Frage vor.
     *
     * @param state       Der aktuelle Benutzer-Zustand
     * @param chosenIndex Ausgewählter Antworten-Index (0-basiert)
     * @return {@code true} wenn die Antwort korrekt war, sonst {@code false}
     */
    public boolean processAnswer(UserQuizState state, int chosenIndex) {
        Question currentQuestion = questions.get(state.getCurrentQuestionIndex());
        boolean isCorrect = chosenIndex == currentQuestion.correctIndex();

        if (isCorrect) {
            state.incrementScore();
        }
        state.incrementQuestionIndex();
        return isCorrect;
    }

    /**
     * Prüft, ob das Quiz für den aktuellen Zustand beendet ist.
     *
     * @param state Der Benutzer-Zustand
     * @return {@code true} wenn alle Fragen beantwortet wurden
     */
    public boolean isQuizFinished(UserQuizState state) {
        return state.getCurrentQuestionIndex() >= questions.size();
    }

    /**
     * Liefert die Gesamtzahl der Fragen.
     */
    public int getTotalQuestions() {
        return questions.size();
    }
}
