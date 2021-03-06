package org.jgroups.protocols.raft.role;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.protocols.raft.internal.StateMachine;
import org.jgroups.protocols.raft.log.Log;
import org.jgroups.protocols.raft.log.LogEntry;
import org.jgroups.protocols.raft.message.AppendEntriesResponse;
import org.jgroups.protocols.raft.message.AppendResult;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.Util;

/**
 * RAFT follower
 *
 * @author Bela Ban
 * @since 0.1
 */
public class Follower extends RaftImpl {
    public Follower(RAFT raft) {
        super(raft);
    }

    @Override
    protected void handleInstallSnapshotRequest(Message msg, int term, Address leader, int lastIncludedIndex, int lastIncludedTerm) {
        // 1. read the state (in the message's buffer) and apply it to the state machine (clear the SM before?)

        // 2. Delete the log (if it exists) and create a new log. Append a dummy entry at lastIncludedIndex with an
        //    empty buffer and term=lastIncludedTerm
        //    - first_appended=last_appended=commit_index=lastIncludedIndex

        StateMachine sm;
        if ((sm = raft.state_machine) == null) {
            raft.getLog().error("%s: no state machine set, cannot install snapshot", raft.local_addr);
            return;
        }
        Address sender = msg.src();
        try {
            ByteArrayDataInputStream in = new ByteArrayDataInputStream(msg.getRawBuffer(), msg.getOffset(), msg.getLength());
            sm.readContentFrom(in);

            raft.doSnapshot();

            // insert a dummy entry
            Log log = raft.log();
            log.append(lastIncludedIndex, true, new LogEntry(lastIncludedTerm, null));
            raft.last_appended = lastIncludedIndex;
            log.commitIndex(lastIncludedIndex);
            raft.commit_index = lastIncludedIndex;
            log.truncate(lastIncludedIndex);

            raft.getLog().debug("%s: applied snapshot (%s) from %s; last_appended=%d, commit_index=%d", raft.local_addr, Util.printBytes(msg.getLength()),
                    msg.src(), raft.lastAppended(), raft.commitIndex());

            AppendResult result = new AppendResult(true, lastIncludedIndex).commitIndex(raft.commitIndex());
            Message ack = new Message(leader).putHeader(raft.getId(), new AppendEntriesResponse(raft.currentTerm(), result));
            raft.getDownProtocol().down(new Event(Event.MSG, ack));
        } catch (Exception ex) {
            raft.getLog().error("%s: failed applying snapshot from %s: %s", raft.local_addr, sender, ex);
        }
    }
}
