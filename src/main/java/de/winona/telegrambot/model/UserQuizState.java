package de.winona.telegrambot.model;

/**
 * Kapselt den aktuellen Fortschritt und Punktestand eines Benutzers im Quiz.
 */
public class UserQuizState {
    private int currentQuestionIndex = 0;
    private int score = 0;

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void incrementQuestionIndex() {
        this.currentQuestionIndex++;
    }

    public int getScore() {
        return score;
    }

    public void incrementScore() {
        this.score++;
    }

    public void reset() {
        this.currentQuestionIndex = 0;
        this.score = 0;
    }
}
