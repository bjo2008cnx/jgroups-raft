package org.jgroups.protocols.raft.message;

import org.jgroups.Address;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * 用于 ELECTION 的心跳
 * Contrary to the RAFT paper, heartbeats are not emulated with AppendEntriesRequests
 *
 * @author Bela Ban
 * @since 0.1
 */
public class HeartbeatRequest extends RaftHeader {
    public Address leader;

    public HeartbeatRequest() {
    }

    public HeartbeatRequest(int term, Address leader) {
        super(term);
        this.leader = leader;
    }

    public int size() {
        return super.size() + Util.size(leader);
    }

    public void writeTo(DataOutput out) throws Exception {
        super.writeTo(out);
        Util.writeAddress(leader, out);
    }

    public void readFrom(DataInput in) throws Exception {
        super.readFrom(in);
        leader = Util.readAddress(in);
    }

    public String toString() {
        return super.toString() + ", leader=" + leader;
    }
}
