package de.winona.telegrambot.presenter;

/**
 * The person who sent a message or pressed a button, as the presenters see them.
 * The Telegram adapter turns Telegram's own user object into this, so no presenter depends on Telegram.
 */
public record ChatUser(long id, String firstName, String lastName, String userName) {

    // The same method names as Telegram's user, so the presenters only change the type, not the calls.
    public long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserName() {
        return userName;
    }
}