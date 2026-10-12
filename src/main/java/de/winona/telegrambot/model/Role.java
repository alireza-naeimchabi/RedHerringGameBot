package de.winona.telegrambot.model;

public enum Role {

    FORENSIC_SCIENTIST("Forensic Scientist",
            "You know the solution, but you may never talk about it. You answer only through the scene tiles."),
    MURDERER("Murderer",
            "Choose your Means of Murder and your Key Evidence in secret. Do not get caught."),
    INVESTIGATOR("Investigator",
            "Find the murderer, the Means of Murder and the Key Evidence.");

    private final String title;
    private final String description;

    Role(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
