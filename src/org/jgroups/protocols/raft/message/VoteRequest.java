package org.jgroups.protocols.raft.message;

import org.jgroups.util.Bits;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * @author Bela Ban
 * @since 0.1
 */
public class VoteRequest extends RaftHeader {
    //protected int term; term定义在父类中
    protected int lastLogTerm;
    protected int lastLogIndex;

    public VoteRequest() {
    }

    public VoteRequest(int term, int lastLogTerm, int lastLogIndex) {
        super(term);
        this.lastLogTerm = lastLogTerm;
        this.lastLogIndex = lastLogIndex;
    }

    public int lastLogTerm() {
        return lastLogTerm;
    }

    public int lastLogIndex() {
        return lastLogIndex;
    }


    public int size() {
        return super.size() + Bits.size(lastLogTerm) + Bits.size(lastLogIndex);
    }

    public void writeTo(DataOutput out) throws Exception {
        super.writeTo(out);
        Bits.writeInt(lastLogTerm, out);
        Bits.writeInt(lastLogIndex, out);
    }

    public void readFrom(DataInput in) throws Exception {
        super.readFrom(in);
        lastLogTerm = Bits.readInt(in);
        lastLogIndex = Bits.readInt(in);
    }

    public String toString() {
        return super.toString() + ", lastLogTerm=" + lastLogTerm + ", lastLogIndex=" + lastLogIndex;
    }
}
