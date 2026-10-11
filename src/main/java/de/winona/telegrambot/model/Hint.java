package de.winona.telegrambot.model;

/**
 * One answer on the scene report.
 *
 * @param typed true if the Forensic Scientist typed the answer, false if it is one of the tile's options
 */
public record Hint(String title, String choice, TileSubject subject, boolean typed)
{

    /** An answer chosen from the tile's options. */
    public Hint(String title, String choice, TileSubject subject)
    {
        this(title, choice, subject, false);
    }
}
