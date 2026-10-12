package de.winona.telegrambot.ui;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The design tokens of the interface: names, icons and text helpers.
 * Every screen uses these, so wording and symbols stay the same everywhere.
 */
public final class Theme {

    // Brand
    public static final String TITLE = "DECEPTION";
    public static final String SUBTITLE = "MURDER IN HONG KONG";
    public static final String HEADLINE = "Every clue hides a secret.";
    public static final String TAGLINE = "Gather your friends, investigate the evidence, "
            + "and uncover the truth before the murderer escapes.";
    public static final String FAN_NOTE = "Fan-made adaptation of the board game "
            + "Deception: Murder in Hong Kong. Not affiliated with its publisher.";

    // Icons: one meaning per icon
    public static final String ICON_HOME = "🏠";
    public static final String ICON_ROOMS = "🗂";
    public static final String ICON_CREATE = "🗝";
    public static final String ICON_JOIN = "🚪";
    public static final String ICON_HOW_TO = "📖";
    public static final String ICON_ROLES = "🎭";
    public static final String ICON_STATS = "📊";
    public static final String ICON_LEADERBOARD = "🏆";
    public static final String ICON_PROFILE = "👤";
    public static final String ICON_MEANS = "🔪";
    public static final String ICON_CLUE = "🔍";
    public static final String ICON_SCIENTIST = "🧪";
    public static final String ICON_ACCUSE = "⚖️";
    public static final String ICON_ROUND = "🔔";
    public static final String ICON_SECRET = "🤫";
    public static final String ICON_OK = "✅";
    public static final String ICON_WRONG = "❌";
    public static final String ICON_WAITING = "🟡";
    public static final String ICON_LIVE = "🔴";

    private static final String SEAT_TAKEN = "●";
    private static final String SEAT_FREE = "○";

    private Theme() {
    }

    /** Makes any text safe for Telegram's HTML mode. Use it for names and titles from users. */
    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String bold(String text) {
        return "<b>" + esc(text) + "</b>";
    }

    public static String italic(String text) {
        return "<i>" + esc(text) + "</i>";
    }

    public static String code(String text) {
        return "<code>" + esc(text) + "</code>";
    }

    /** A highlighted block. The content must already be valid HTML. */
    public static String note(String html) {
        return "<blockquote>" + html + "</blockquote>";
    }

    /** Screen heading: a bold title and an optional quiet line below it. */
    public static String heading(String icon, String title, String subline) {
        String heading = icon + " " + bold(title);
        return subline == null ? heading : heading + "\n" + italic(subline);
    }

    /** Seats as dots, for example ●●●○○○○. */
    public static String seats(int taken, int capacity) {
        return SEAT_TAKEN.repeat(Math.max(0, taken)) + SEAT_FREE.repeat(Math.max(0, capacity - taken));
    }

    /** One circled initial per name, for example Ⓐ Ⓢ Ⓑ. */
    public static String initials(List<String> names) {
        return names.stream().map(Theme::initial).collect(Collectors.joining(" "));
    }

    private static String initial(String name) {
        if (name == null || name.isBlank()) {
            return "◍";
        }
        int first = Character.toUpperCase(name.strip().codePointAt(0));
        if (first >= 'A' && first <= 'Z') {
            return Character.toString(0x24B6 + (first - 'A'));
        }
        return "(" + esc(Character.toString(first)) + ")";
    }

    /** Rank title earned by the number of won cases. */
    public static String rankTitle(int wins) {
        if (wins >= 30) {
            return "Superintendent";
        }
        if (wins >= 15) {
            return "Chief Inspector";
        }
        if (wins >= 8) {
            return "Inspector";
        }
        if (wins >= 3) {
            return "Detective";
        }
        if (wins >= 1) {
            return "Constable";
        }
        return "Recruit";
    }

    public static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
