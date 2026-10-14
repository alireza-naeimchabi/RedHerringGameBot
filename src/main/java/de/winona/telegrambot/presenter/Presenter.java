package de.winona.telegrambot.presenter;

import de.winona.telegrambot.i18n.Msg;
import de.winona.telegrambot.view.ChatView;
import de.winona.telegrambot.view.GameScreens;

/**
 * Common base of the three presenters (MVP). Every presenter talks to the View only through
 * {@link ChatView} and writes in the reader's language through {@link Localizer}.
 * Subclasses add what is special about their part of the bot: the menu, the rooms or the case.
 */
public abstract class Presenter {

    protected final ChatView chatView;
    protected final Localizer localizer;

    protected Presenter(ChatView chatView, Localizer localizer) {
        this.chatView = chatView;
        this.localizer = localizer;
    }

    /**
     * Every presenter handles the buttons of its own area.
     */
    public abstract void onClick(Click click);

    /**
     * Answers in a chat with one short line, in the language of the user who asked.
     */
    protected void sendNotice(long chatId, ChatUser user, String icon, Msg message) {
        chatView.send(chatId, localizer.forUser(user.getId(), t -> GameScreens.notice(t, icon, message)));
    }
}