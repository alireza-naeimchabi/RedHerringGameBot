package de.winona.telegrambot.ui;

/** The artwork shown above a screen. Files live in src/main/resources/ui. */
public enum Banner {
    HOME("home"),
    ROOMS("rooms"),
    BRIEFING("briefing"),
    ROLES("roles"),
    RECORDS("records"),
    CASE_OPEN("case-open"),
    CASE_SOLVED("case-solved"),
    CASE_COLD("case-cold");

    private final String fileName;

    Banner(String name) {
        this.fileName = "banner-" + name + ".jpg";
    }

    public String fileName() {
        return fileName;
    }

    public String resourcePath() {
        return "/ui/" + fileName;
    }
}
