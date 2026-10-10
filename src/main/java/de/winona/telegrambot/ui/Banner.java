package de.winona.telegrambot.ui;

import de.winona.telegrambot.i18n.Language;

/**
 * The artwork shown above a screen. The words on a banner are part of the picture,
 * so every language has its own set: src/main/resources/ui for the default language
 * and src/main/resources/ui/&lt;code&gt; for the others.
 */
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

    public String resourcePath(Language language) {
        if (language == null || language == Language.DEFAULT) {
            return "/ui/" + fileName;
        }
        return "/ui/" + language.code() + "/" + fileName;
    }
}
