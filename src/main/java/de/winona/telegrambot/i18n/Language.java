package de.winona.telegrambot.i18n;

import java.util.Arrays;

/**
 * The languages the bot speaks. The code is what gets stored, so it must never change.
 */
public enum Language {
    ENGLISH("en", "English", "🇬🇧", false),
    GERMAN("de", "Deutsch", "🇩🇪", false),
    PERSIAN("fa", "فارسی", "🇮🇷", true);

    /**
     * Used for everyone who has not chosen a language.
     */
    public static final Language DEFAULT = ENGLISH;

    private final String code;
    private final String nativeName;
    private final String flag;
    private final boolean rightToLeft;

    Language(String code, String nativeName, String flag, boolean rightToLeft) {
        this.code = code;
        this.nativeName = nativeName;
        this.flag = flag;
        this.rightToLeft = rightToLeft;
    }

    public String code() {
        return code;
    }

    /**
     * The name of the language in the language itself.
     */
    public String nativeName() {
        return nativeName;
    }

    public String flag() {
        return flag;
    }

    public boolean isRightToLeft() {
        return rightToLeft;
    }

    /**
     * Finds a language by its stored code. Unknown codes give the default language.
     */
    public static Language fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(DEFAULT);
    }
}
