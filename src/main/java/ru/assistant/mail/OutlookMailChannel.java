package ru.assistant.mail;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComFailException;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges to a locally running Outlook desktop client via JACOB/COM.
 * Not unit-testable: requires a real Outlook installation + native jacob DLL
 * on the PATH, neither of which is available on this development machine.
 * The contract is exercised through {@link MockMailChannel} instead; this
 * class is verified manually on the exam machine (see README).
 */
public class OutlookMailChannel implements MailChannel {

    private static final Logger log = LoggerFactory.getLogger(OutlookMailChannel.class);

    private static final int OL_FOLDER_INBOX = 6;

    private final String folderName;

    public OutlookMailChannel(String folderName) {
        this.folderName = folderName;
    }

    @Override
    public List<Msg> fetchUnread() throws MailChannelException {
        ActiveXComponent outlookApp = null;
        try {
            outlookApp = new ActiveXComponent("Outlook.Application");
            Dispatch namespace = Dispatch.call(outlookApp, "GetNamespace", "MAPI").toDispatch();
            Dispatch folder = resolveFolder(namespace);
            Dispatch items = Dispatch.get(folder, "Items").toDispatch();
            Dispatch unreadItems = Dispatch.call(items, "Restrict", "[Unread] = true").toDispatch();

            List<Msg> result = new ArrayList<>();
            int count = Dispatch.get(unreadItems, "Count").getInt();
            for (int i = 1; i <= count; i++) {
                Dispatch mailItem = Dispatch.call(unreadItems, "Item", i).toDispatch();
                result.add(toMsg(mailItem));
            }
            return result;
        } catch (ComFailException e) {
            throw new MailChannelException("Failed to fetch unread messages from Outlook", e);
        } finally {
            if (outlookApp != null) {
                outlookApp.safeRelease();
            }
        }
    }

    @Override
    public void reply(Msg original, String body) throws MailChannelException {
        ActiveXComponent outlookApp = null;
        try {
            outlookApp = new ActiveXComponent("Outlook.Application");
            Dispatch namespace = Dispatch.call(outlookApp, "GetNamespace", "MAPI").toDispatch();
            Dispatch folder = resolveFolder(namespace);
            Dispatch mailItem = findByEntryId(folder, original.getId());
            if (mailItem == null) {
                throw new MailChannelException("Original message not found in Outlook folder, id masked");
            }
            Dispatch replyItem = Dispatch.call(mailItem, "Reply").toDispatch();
            Dispatch.put(replyItem, "Body", body);
            Dispatch.call(replyItem, "Send");
        } catch (ComFailException e) {
            throw new MailChannelException("Failed to send reply via Outlook", e);
        } finally {
            if (outlookApp != null) {
                outlookApp.safeRelease();
            }
        }
    }

    private Dispatch resolveFolder(Dispatch namespace) {
        if (folderName == null || folderName.isEmpty() || "Inbox".equalsIgnoreCase(folderName)) {
            return Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch();
        }
        Dispatch inbox = Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch();
        Dispatch folders = Dispatch.get(inbox, "Folders").toDispatch();
        return Dispatch.call(folders, "Item", folderName).toDispatch();
    }

    private Dispatch findByEntryId(Dispatch folder, String entryId) {
        Dispatch items = Dispatch.get(folder, "Items").toDispatch();
        int count = Dispatch.get(items, "Count").getInt();
        for (int i = 1; i <= count; i++) {
            Dispatch candidate = Dispatch.call(items, "Item", i).toDispatch();
            String candidateId = Dispatch.get(candidate, "EntryID").getString();
            if (entryId.equals(candidateId)) {
                return candidate;
            }
        }
        return null;
    }

    private Msg toMsg(Dispatch mailItem) {
        String id = Dispatch.get(mailItem, "EntryID").getString();
        String from = getStringSafe(mailItem, "SenderEmailAddress");
        String subject = getStringSafe(mailItem, "Subject");
        String body = getStringSafe(mailItem, "Body");
        String receivedAtIso = getReceivedIso(mailItem);
        return new Msg(id, from, subject, body, receivedAtIso);
    }

    private String getStringSafe(Dispatch item, String property) {
        try {
            Variant value = Dispatch.get(item, property);
            return value == null ? "" : value.getString();
        } catch (ComFailException e) {
            log.warn("Failed to read Outlook property {}, defaulting to empty string", property);
            return "";
        }
    }

    private String getReceivedIso(Dispatch mailItem) {
        try {
            Variant received = Dispatch.get(mailItem, "ReceivedTime");
            return received.getJavaDate().toInstant().toString();
        } catch (RuntimeException e) {
            log.warn("Failed to read Outlook ReceivedTime, defaulting to empty string");
            return "";
        }
    }
}
