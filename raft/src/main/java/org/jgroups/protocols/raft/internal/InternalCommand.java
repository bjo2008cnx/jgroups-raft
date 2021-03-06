package org.jgroups.protocols.raft.internal;

import org.jgroups.protocols.raft.role.RAFT;
import org.jgroups.util.Bits;
import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * Internal command to be added to the log, e.g. adding or removing a server
 * @author Bela Ban
 * @since  0.2
 */
public class InternalCommand implements Streamable {
    protected Type   type;
    protected String name;

    public InternalCommand() { // marshalling
    }

    public InternalCommand(Type type, String name) {
        this.type=type;
        this.name=name;
    }

    public Type type() {return type;}

    public void writeTo(DataOutput out) throws Exception {
        out.writeByte(type.ordinal());
        Bits.writeString(name, out);
    }

    public void readFrom(DataInput in) throws Exception {
        type=Type.values()[in.readByte()];
        name=Bits.readString(in);
    }

    public Object execute(RAFT raft) throws Exception {
        switch(type) {
            case addServer:
                raft._addServer(name);
                break;
            case removeServer:
                raft._removeServer(name);
                break;
        }
        return null;
    }

    @Override
    public String toString() {
        return type + (type == Type.noop? "" : "(" + name + ")");
    }

    public enum Type {addServer, removeServer, noop};

}
