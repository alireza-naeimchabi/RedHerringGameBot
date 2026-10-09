package de.winona.telegrambot.model;

public enum Role {

    FORENSIC_SCIENTIST("Forensic Scientist",
            "You know the solution, but you cannot speak. You give hints with buttons."),
    MURDERER("Murderer",
            "Choose your weapon and your evidence in secret. Do not get caught."),
    INVESTIGATOR("Investigator",
            "Find the murderer, the weapon and the key evidence.");

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