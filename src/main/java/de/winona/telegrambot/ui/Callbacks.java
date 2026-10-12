package de.winona.telegrambot.ui;

/**
 * All callback data the buttons send, in one place.
 * Format: "namespace:action:arguments". Telegram allows at most 64 bytes.
 */
public final class Callbacks {

    public static final String SEPARATOR = ":";

    // Namespaces
    public static final String MENU = "m";
    public static final String ROOM = "r";

    // Menu
    public static final String HOME = "m:home";
    public static final String ROOMS = "m:rooms";
    public static final String CREATE_ROOM = "m:create";
    public static final String JOIN_ROOM = "m:join";
    public static final String STATISTICS = "m:stats";
    public static final String LEADERBOARD = "m:top";
    public static final String PROFILE = "m:me";
    public static final String LANGUAGE = "m:lang";
    public static final String NO_ACTION = "m:noop";

    // Game actions (the first part is kept short, the group chat id follows)
    public static final String WEAPON = "W";
    public static final String EVIDENCE = "E";
    public static final String HINT = "H";
    public static final String REPLACE_SLOT = "R";
    public static final String REPLACEMENT_HINT = "N";
    public static final String ACCUSE_START = "AC";
    public static final String ACCUSE_SUSPECT = "AP";
    public static final String ACCUSE_WEAPON = "AW";
    public static final String ACCUSE_EVIDENCE = "AE";
    public static final String ACCUSE_CANCEL = "AX";
    public static final String NEXT_ROUND = "NR";
    public static final String EVIDENCE_BOARD = "EB";
    public static final String FRAME_PLAYER = "FP";
    public static final String FRAME_MEANS = "FM";
    public static final String FRAME_CLUE = "FC";
    public static final String OFFERED_TILE = "T";
    public static final String LAB_START = "LT";
    public static final String LAB_SUSPECT = "LP";
    public static final String LAB_CARD = "LC";
    public static final String LAB_CANCEL = "LX";

    private Callbacks() {
    }

    public static String howToPlay(int page) {
        return "m:how:" + page;
    }

    public static String roles(int page) {
        return "m:roles:" + page;
    }

    /**
     * Sets the reader's language. The value is the stored language code, never a translated name.
     */
    public static String setLanguage(String languageCode) {
        return "m:setlang:" + languageCode;
    }

    public static String roomDetails(String code) {
        return "m:room:" + code;
    }

    public static String caseFile(String code) {
        return "m:case:" + code;
    }

    public static String joinRoom(String code) {
        return "r:join:" + code;
    }

    public static String leaveRoom(String code) {
        return "r:leave:" + code;
    }

    public static String startRoom(String code) {
        return "r:start:" + code;
    }

    public static String closeRoom(String code) {
        return "r:close:" + code;
    }

    /**
     * Opens a new room in the group the button is shown in.
     */
    public static String newRoomHere() {
        return "r:new";
    }

    public static String game(String action, long groupChatId, Object... arguments) {
        StringBuilder data = new StringBuilder(action).append(SEPARATOR).append(groupChatId);
        for (Object argument : arguments) {
            data.append(SEPARATOR).append(argument);
        }
        return data.toString();
    }
}
