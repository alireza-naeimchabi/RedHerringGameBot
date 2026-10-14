package de.winona.telegrambot.view;

import de.winona.telegrambot.i18n.Language;




import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One view of the interface: optional artwork, HTML text and optional buttons.
 * A screen only describes what to show. Sending it is the job of the Messenger.
 * Screens are created through {@link de.winona.telegrambot.i18n.Texts}, which knows the language.
 */
public record Screen(Banner banner, Language language, String html, ButtonGrid keyboard) {

    /**
     * Telegram's limit for the text under a photo.
     */
    public static final int MAX_CAPTION_LENGTH = 1024;

    private static final String LANGUAGE_DIVIDER = "\n\n┄┄┄┄┄┄┄┄┄┄\n\n";
    private static final String LABEL_DIVIDER = " · ";
    /**
     * Longer labels do not fit next to each other on a phone.
     */
    private static final int MAX_LABEL_LENGTH_IN_SHARED_ROW = 20;

    public boolean hasBanner() {
        return banner != null;
    }

    /**
     * Number of characters Telegram counts: the text without HTML tags, entities counted once.
     */
    public int visibleLength() {
        return html.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .length();
    }

    /**
     * True if the text is short enough to be shown as the caption of the banner.
     */
    public boolean fitsCaption() {
        return visibleLength() <= MAX_CAPTION_LENGTH;
    }

    /**
     * Joins the same screen in several languages into one message, for a group whose players
     * read different languages. The texts follow each other and every button carries all labels.
     * All versions must have the same buttons in the same places.
     */
    public static Screen merge(List<Screen> versions) {
        if (versions.size() == 1) {
            return versions.get(0);
        }
        Screen first = versions.get(0);
        String html = versions.stream().map(Screen::html).collect(Collectors.joining(LANGUAGE_DIVIDER));
        // A shared message has no single language, so it uses the artwork of the default language.
        return new Screen(first.banner(), Language.DEFAULT, html, mergeKeyboards(versions));
    }

    private static ButtonGrid mergeKeyboards(List<Screen> versions) {
        ButtonGrid first = versions.get(0).keyboard();
        if (first == null) {
            return null;
        }
        List<List<Button>> merged = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < first.getKeyboard().size(); rowIndex++) {
            List<Button> row = new ArrayList<>();
            for (int column = 0; column < first.getKeyboard().get(rowIndex).size(); column++) {
                row.add(mergeButton(versions, rowIndex, column));
            }
            merged.add(row);
        }
        // If one pair of labels is too wide to share a row, every button gets its own row,
        // so the keyboard stays an even list instead of a ragged grid.
        boolean tooWide = merged.stream()
                .filter(row -> row.size() > 1)
                .flatMap(List::stream)
                .anyMatch(button -> button.getText().length() > MAX_LABEL_LENGTH_IN_SHARED_ROW);
        List<List<Button>> rows = new ArrayList<>();
        for (List<Button> row : merged) {
            if (tooWide) {
                row.forEach(button -> rows.add(List.of(button)));
            } else {
                rows.add(row);
            }
        }
        return new ButtonGrid(rows);
    }


    private static Button mergeButton(List<Screen> versions, int row, int column) {
        Button original = versions.get(0).keyboard().getKeyboard().get(row).get(column);
        String label = original.getText();
        for (Screen version : versions.subList(1, versions.size())) {
            ButtonGrid keyboard = version.keyboard();
            if (keyboard == null || keyboard.getKeyboard().size() <= row
                    || keyboard.getKeyboard().get(row).size() <= column) {
                continue;
            }
            String other = withoutSharedIcon(label, keyboard.getKeyboard().get(row).get(column).getText());
            // A player's name reads the same in every language and is shown once.
            if (!withoutDirectionMarks(label).contains(withoutDirectionMarks(other))) {
                label = label + LABEL_DIVIDER + other;
            }
        }
        return new Button(label, original.getCallbackData(), original.getUrl());
    }

    /**
     * Removes the invisible characters that only steer the direction of text.
     */
    private static String withoutDirectionMarks(String text) {
        return text.replaceAll("\\p{Cf}", "");
    }

    /**
     * "🔬 Lab Test" and "🔬 آزمایش" become "🔬 Lab Test · آزمایش": the icon is shown once.
     */
    private static String withoutSharedIcon(String label, String other) {
        int space = label.indexOf(' ');
        if (space > 0) {
            String icon = label.substring(0, space + 1);
            boolean isIcon = icon.codePoints().noneMatch(Character::isLetterOrDigit);
            if (isIcon && other.startsWith(icon)) {
                return other.substring(icon.length());
            }
        }
        return other;
    }
}
