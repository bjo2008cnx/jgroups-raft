package org.jgroups.protocols.raft.message;

import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.util.Bits;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * 用来给集群发给AppendEntries 消息
 * The log entries are contained in actual payload of the message,
 * not in this header.
 *
 * @author Bela Ban
 * @since 0.1
 */
public class AppendEntriesRequest extends RaftHeader {
    public Address leader;         // probably not needed as msg.src() contains the leader's address already
    public int prevLogTerm;
    public int prevLogIndex;
    public int entryTerm;     // term of the given entry, e.g. when relaying a log to a late joiner
    public int leaderCommit;  // the commit_index of the leader
    public boolean internal;

    public AppendEntriesRequest() {
    }

    public AppendEntriesRequest(int term, Address leader, int prevLogIndex, int prevLogTerm, int entryTerm,
                                int leaderCommit, boolean internal) {
        super(term);
        this.leader = leader;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entryTerm = entryTerm;
        this.leaderCommit = leaderCommit;
        this.internal = internal;
    }


    @Override
    public int size() {
        return super.size() + Util.size(leader) + Bits.size(prevLogIndex) + Bits.size(prevLogTerm) +
                Bits.size(entryTerm) + Bits.size(leaderCommit) + Global.BYTE_SIZE;
    }

    @Override
    public void writeTo(DataOutput out) throws Exception {
        super.writeTo(out);
        Util.writeAddress(leader, out);
        Bits.writeInt(prevLogIndex, out);
        Bits.writeInt(prevLogTerm, out);
        Bits.writeInt(entryTerm, out);
        Bits.writeInt(leaderCommit, out);
        out.writeBoolean(internal);
    }

    @Override
    public void readFrom(DataInput in) throws Exception {
        super.readFrom(in);
        leader = Util.readAddress(in);
        prevLogIndex = Bits.readInt(in);
        prevLogTerm = Bits.readInt(in);
        entryTerm = Bits.readInt(in);
        leaderCommit = Bits.readInt(in);
        internal = in.readBoolean();
    }

    @Override
    public String toString() {
        return super.toString() + ", leader=" + leader + ", prevLogIndex=" + prevLogIndex +
                ", prevLogTerm=" + prevLogTerm + ", entryTerm=" + entryTerm + ", leaderCommit=" + leaderCommit +
                ", internal=" + internal;
    }
}
