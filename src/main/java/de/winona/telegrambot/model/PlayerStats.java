package de.winona.telegrambot.model;

/**
 * The record of one detective across all closed cases.
 */
public class PlayerStats {

    private final long userId;
    private String name;
    private int games;
    private int wins;
    private int asForensicScientist;
    private int asMurderer;
    private int asInvestigator;
    private int casesSolved;
    private int escapes;

    public PlayerStats(long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public PlayerStats(long userId, String name, int games, int wins, int asForensicScientist,
                       int asMurderer, int asInvestigator, int casesSolved, int escapes) {
        this(userId, name);
        this.games = games;
        this.wins = wins;
        this.asForensicScientist = asForensicScientist;
        this.asMurderer = asMurderer;
        this.asInvestigator = asInvestigator;
        this.casesSolved = casesSolved;
        this.escapes = escapes;
    }

    /**
     * Adds one closed case to this record.
     */
    public void addGame(String currentName, Role role, boolean won, boolean solvedTheCase) {
        name = currentName;
        games++;
        if (won) {
            wins++;
        }
        switch (role) {
            case FORENSIC_SCIENTIST -> asForensicScientist++;
            case MURDERER -> asMurderer++;
            case INVESTIGATOR -> asInvestigator++;
        }
        if (solvedTheCase) {
            casesSolved++;
        }
        if (role == Role.MURDERER && won) {
            escapes++;
        }
    }

    public int winRatePercent() {
        return games == 0 ? 0 : Math.round(wins * 100f / games);
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getGames() {
        return games;
    }

    public int getWins() {
        return wins;
    }

    public int getAsForensicScientist() {
        return asForensicScientist;
    }

    public int getAsMurderer() {
        return asMurderer;
    }

    public int getAsInvestigator() {
        return asInvestigator;
    }

    public int getCasesSolved() {
        return casesSolved;
    }

    public int getEscapes() {
        return escapes;
    }
}
