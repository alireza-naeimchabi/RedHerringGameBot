package de.winona.telegrambot.i18n;

import de.winona.telegrambot.model.Card;
import de.winona.telegrambot.model.Role;
import de.winona.telegrambot.ui.Banner;
import de.winona.telegrambot.ui.Screen;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Locale;

/**
 * The texts of one language. Screens ask this class for every word they show,
 * so no screen needs to know which languages exist.
 *
 * <p>It also takes care of what makes Persian different: digits, text direction,
 * and names in another script inside a sentence.
 */
public final class Texts {

    private static final char RIGHT_TO_LEFT_MARK = '‏';
    private static final char FIRST_STRONG_ISOLATE = '⁨';
    private static final char POP_DIRECTIONAL_ISOLATE = '⁩';
    private static final String PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

    private final Language language;
    private final Messages messages;

    private Texts(Language language) {
        this.language = language;
        this.messages = Messages.get();
    }

    public static Texts of(Language language) {
        return new Texts(language == null ? Language.DEFAULT : language);
    }

    public Language language() {
        return language;
    }

    public boolean isRightToLeft() {
        return language.isRightToLeft();
    }

    // ---------------------------------------------------------------- Looking up texts

    /**
     * The text for a key with {0}, {1}, ... replaced by the arguments.
     * Whole numbers are written with the digits of the language. Everything else is inserted as is,
     * so text from users must be passed through {@link #name} or {@link #dyn} first.
     */
    public String get(String key, Object... args) {
        String text = messages.text(language, key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", format(args[i]));
        }
        return text;
    }

    public String get(Msg message) {
        return get(message.key(), message.args());
    }

    /**
     * A text that depends on a count. Looks up "key.one" for exactly one and "key.other" otherwise.
     * {0} is the count, further arguments follow.
     */
    public String plural(String key, int count, Object... moreArgs) {
        Object[] args = new Object[moreArgs.length + 1];
        args[0] = count;
        System.arraycopy(moreArgs, 0, args, 1, moreArgs.length);
        return get(key + (count == 1 ? ".one" : ".other"), args);
    }

    private String format(Object arg) {
        if (arg instanceof Integer || arg instanceof Long) {
            return number(((Number) arg).longValue());
        }
        if (arg instanceof Msg message) {
            return get(message);
        }
        return String.valueOf(arg);
    }

    /**
     * A whole number in the digits of the language, for example 12 or ۱۲.
     */
    public String number(long value) {
        String digits = Long.toString(value);
        if (!language.isRightToLeft()) {
            return digits;
        }
        StringBuilder localized = new StringBuilder();
        for (char digit : digits.toCharArray()) {
            localized.append(Character.isDigit(digit) ? PERSIAN_DIGITS.charAt(digit - '0') : digit);
        }
        return localized.toString();
    }

    // ---------------------------------------------------------------- Game terms

    public String card(Card card) {
        return term("card.", card.name());
    }

    /**
     * A card name that is translated later, for a message whose reader is not known yet.
     */
    public static Msg cardMsg(Card card) {
        return Msg.of("card." + keyOf(card.name()));
    }

    public String tile(String title) {
        return term("tile.", title);
    }

    public String option(String option) {
        return term("option.", option);
    }

    public String role(Role role) {
        return get("role." + role.name().toLowerCase(Locale.ROOT) + ".title");
    }

    public String roleDescription(Role role) {
        return get("role." + role.name().toLowerCase(Locale.ROOT) + ".description");
    }

    /**
     * Rank title earned by the number of won cases.
     */
    public String rank(int wins) {
        String rank = wins >= 30 ? "superintendent"
                : wins >= 15 ? "chief_inspector"
                  : wins >= 8 ? "inspector"
                    : wins >= 3 ? "detective"
                      : wins >= 1 ? "constable"
                        : "recruit";
        return get("rank." + rank);
    }

    /**
     * Cards, tiles and options keep their English name in the game data. That name is turned
     * into a key, for example "Sleeping Pills" into "card.sleeping_pills".
     */
    private String term(String prefix, String englishName) {
        String key = prefix + keyOf(englishName);
        return messages.has(language, key) || messages.has(Language.DEFAULT, key)
                ? get(key)
                : englishName;
    }

    public static String keyOf(String englishName) {
        return englishName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    // ---------------------------------------------------------------- Text from users

    /**
     * Makes any text safe for Telegram's HTML mode.
     */
    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A name or title typed by a user, ready for an HTML message: shortened, escaped and,
     * if it is written in the other direction than this language, kept together as one unit.
     */
    public String name(String raw, int maxLength) {
        return dyn(shorten(raw, maxLength));
    }

    /**
     * Like {@link #name} without shortening.
     */
    public String dyn(String raw) {
        return isolate(raw, esc(raw));
    }

    /**
     * A user's name for a button or a pop-up, where HTML is not allowed.
     */
    public String plainName(String raw, int maxLength) {
        String shortened = shorten(raw, maxLength);
        return isolate(shortened, shortened);
    }

    private String isolate(String raw, String prepared) {
        return runsAgainstLanguage(raw)
                ? FIRST_STRONG_ISOLATE + prepared + POP_DIRECTIONAL_ISOLATE
                : prepared;
    }

    /**
     * True if the text contains letters of the opposite direction, for example Latin in Persian.
     */
    private boolean runsAgainstLanguage(String text) {
        if (text == null) {
            return false;
        }
        return text.codePoints().anyMatch(codePoint -> {
            byte direction = Character.getDirectionality(codePoint);
            boolean rightToLeft = direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
            boolean leftToRight = direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT;
            return language.isRightToLeft() ? leftToRight : rightToLeft;
        });
    }

    /**
     * Cuts long names so one long title cannot break a screen.
     */
    public String shorten(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return get("common.unknown");
        }
        String clean = text.strip();
        if (clean.codePointCount(0, clean.length()) <= maxLength) {
            return clean;
        }
        int end = clean.offsetByCodePoints(0, maxLength - 1);
        return clean.substring(0, end).stripTrailing() + "…";
    }

    // ---------------------------------------------------------------- Screens

    public Screen text(String html) {
        return new Screen(null, language, direct(html), null);
    }

    public Screen text(String html, InlineKeyboardMarkup keyboard) {
        return new Screen(null, language, direct(html), keyboard);
    }

    public Screen banner(Banner banner, String html, InlineKeyboardMarkup keyboard) {
        return new Screen(banner, language, direct(html), keyboard);
    }

    /**
     * Telegram decides the direction of every line by its first letter. A Persian line that
     * starts with a room code or a Latin name would flip to left-to-right, so each line of a
     * right-to-left language starts with an invisible mark that fixes its direction.
     */
    private String direct(String html) {
        if (!language.isRightToLeft()) {
            return html;
        }
        StringBuilder directed = new StringBuilder();
        for (String line : html.split("\n", -1)) {
            if (!directed.isEmpty()) {
                directed.append('\n');
            }
            if (line.isBlank()) {
                directed.append(line);
                continue;
            }
            // The mark belongs to the text: in front of a tag like <blockquote> it would
            // stand alone outside the block.
            int textStart = endOfLeadingTags(line);
            directed.append(line, 0, textStart).append(RIGHT_TO_LEFT_MARK).append(line, textStart, line.length());
        }
        return directed.toString();
    }

    private int endOfLeadingTags(String line) {
        int position = 0;
        while (position < line.length() && line.charAt(position) == '<') {
            int end = line.indexOf('>', position);
            // Not inside <code>: the mark would be copied together with a room code.
            if (end < 0 || line.startsWith("<code", position) || line.startsWith("<pre", position)) {
                break;
            }
            position = end + 1;
        }
        return position;
    }
}
