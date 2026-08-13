package ru.assistant.mail;

import java.util.List;

public interface MailChannel {

    List<Msg> fetchUnread() throws MailChannelException;

    void reply(Msg original, String body) throws MailChannelException;
}
