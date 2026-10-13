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

    public String resourcePath(Language language) {
        String defaultPath = "/ui/" + fileName;
        if (language == null || language == Language.DEFAULT) {
            return defaultPath;
        }
        String localizedPath = "/ui/" + language.code() + "/" + fileName;
        // Fall back to the default banner if this language has no own artwork yet.
        return Banner.class.getResource(localizedPath) != null ? localizedPath : defaultPath;
    }
}
