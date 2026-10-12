package de.winona.telegrambot.util;

import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/** Creates short room codes without letters and digits that are easy to confuse (0/O, 1/I). */
public final class RoomCodeGenerator {

    public static final int CODE_LENGTH = 4;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Random random = new Random();

    public String nextCode(Predicate<String> isTaken) {
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (isTaken.test(code));
        return code;
    }

    /**
     * Brings user input into the form codes are stored in. A code copied out of a Persian
     * message can carry invisible direction marks, and a Persian keyboard types ۷ instead of 7:
     * both still mean the same code.
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder code = new StringBuilder();
        input.codePoints().forEach(codePoint -> {
            if (Character.getType(codePoint) == Character.FORMAT) {
                return; // direction marks, zero-width joiners and similar
            }
            if (Character.isDigit(codePoint)) {
                code.append(Character.digit(codePoint, 10));
            } else {
                code.appendCodePoint(codePoint);
            }
        });
        return code.toString().trim().toUpperCase(Locale.ROOT);
    }

    public static boolean looksLikeCode(String input) {
        return normalize(input).matches("[A-Z0-9]{" + CODE_LENGTH + "}");
    }
}
