package org.jgroups.raft.demox;

import org.jgroups.Channel;
import org.jgroups.protocols.raft.StateMachine;
import org.jgroups.protocols.raft.role.RAFT;
import org.jgroups.protocols.raft.role.Role;
import org.jgroups.raft.RaftHandle;
import org.jgroups.util.AsciiString;
import org.jgroups.util.Bits;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Distribute counter service based on Raft consensus algorithm.
 */
public class CounterService implements StateMachine, RAFT.RoleChange {

    private RaftHandle raft;

    private final Map<String, Long> counters;

    private enum Command {
        CREATE, INCREMENT_AND_GET, DECREMENT_AND_GET, GET, SET
    }

    public CounterService(Channel ch) {
        this.raft = new RaftHandle(ch, this);
        this.counters = new HashMap<>();

        raft.raftId(ch.getName())
            .addRoleListener(this);
    }

    // ===========================================
    //              Raft Status API
    // ===========================================

    public int lastApplied() {
        return raft.lastApplied();
    }

    public int commitIndex() {
        return raft.commitIndex();
    }

    public int logSize() {
        return raft.logSize();
    }

    public void dumpLog() {
        System.out.println("\nindex (term): command\n---------------------");
        raft.logEntries((entry, index) -> {
            StringBuilder log = new StringBuilder()
                    .append(index)
                    .append(" (").append(entry.term()).append("): ");

            if (entry.command() == null ) {
                System.out.println(log.append("<marker record>"));
                return;
            } else if (entry.internal()) {
                System.out.println(log.append("<internal command>"));
                return;
            }

            ByteArrayDataInputStream in = new ByteArrayDataInputStream(
                    entry.command(), entry.offset(), entry.length()
            );
            try {
                Command cmd = Command.values()[in.readByte()];
                String name = Bits.readAsciiString(in).toString();
                switch (cmd) {
                    case CREATE:
                        log.append(cmd)
                            .append("(").append(name).append(", ")
                            .append(Bits.readLong(in))
                            .append(")");
                        break;
                    case GET:
                    case INCREMENT_AND_GET:
                    case DECREMENT_AND_GET:
                        log.append(cmd)
                            .append("(").append(name).append(")");
                        break;
                    default:
                        throw new IllegalArgumentException("Command " + cmd + "is unknown");
                }
                System.out.println(log);
            }
            catch (IOException e) {
                throw new IllegalStateException("Error when dump log", e);
            }
        });
        System.out.println();
    }

    public void snapshot() {
        try {
            raft.snapshot();
        } catch (Exception e) {
            throw new IllegalStateException("Error when snapshot", e);
        }
    }

    public boolean isLeaderExist() {
        return raft.leader() != null;
    }

    // ===========================================
    //              Raft API
    // ===========================================

    @Override
    public void roleChanged(Role role) {
        System.out.println("roleChanged to: " + role);
    }

    @Override
    public byte[] apply(byte[] data, int offset, int length) throws Exception {
        ByteArrayDataInputStream in = new ByteArrayDataInputStream(data, offset, length);
        Command cmd = Command.values()[in.readByte()];
        String name = Bits.readAsciiString(in).toString();
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date())
                + "] Apply: cmd=[" + cmd + "]");

        long v1, retVal;
        switch (cmd) {
            case CREATE:
                v1 = Bits.readLong(in);
                retVal = create0(name, v1);
                return Util.objectToByteBuffer(retVal);
            case GET:
                retVal = get0(name);
                return Util.objectToByteBuffer(retVal);
            case INCREMENT_AND_GET:
                retVal = add0(name, 1L);
                return Util.objectToByteBuffer(retVal);
            case DECREMENT_AND_GET:
                retVal = add0(name, -1L);
                return Util.objectToByteBuffer(retVal);
            default:
                throw new IllegalArgumentException("Command " + cmd + "is unknown");
        }
    }

    @Override
    public void readContentFrom(DataInput in) throws Exception {
        int size = in.readInt();
        System.out.println("ReadContentFrom: size=[" + size + "]");
        for (int i = 0; i < size; i++) {
            AsciiString name = Bits.readAsciiString(in);
            Long value = Bits.readLong(in);
            counters.put(name.toString(), value);
        }
    }

    @Override
    public void writeContentTo(DataOutput out) throws Exception {
        synchronized (counters) {
            int size = counters.size();
            System.out.println("WriteContentFrom: size=[" + size + "]");
            out.writeInt(size);
            for (Map.Entry<String, Long> entry : counters.entrySet()) {
                AsciiString name = new AsciiString(entry.getKey());
                Long value = entry.getValue();
                Bits.writeAsciiString(name, out);
                Bits.writeLong(value, out);
            }
        }
    }

    // ===========================================
    //              Counter API
    // ===========================================

    public void getOrCreateCounter(String name, long initVal) {
        Object retVal = invoke(Command.CREATE, name, false, initVal);
        counters.put(name, (Long) retVal);
    }

    public long incrementAndGet(String name) {
        return (long) invoke(Command.INCREMENT_AND_GET, name, false);
    }

    public long decrementAndGet(String name) {
        return (long) invoke(Command.DECREMENT_AND_GET, name, false);
    }

    public long get(String name) {
        return (long) invoke(Command.GET, name, false);
    }

    private Object invoke(Command cmd, String name, boolean ignoreRetVal, long... values) {
        ByteArrayDataOutputStream out = new ByteArrayDataOutputStream(256);
        try {
            out.writeByte(cmd.ordinal());
            Bits.writeAsciiString(new AsciiString(name), out);
            for (long val : values) {
                Bits.writeLong(val, out);
            }

            byte[] rsp = raft.set(out.buffer(), 0, out.position());
            return ignoreRetVal ? null : Util.objectFromByteBuffer(rsp);
        }
        catch (IOException ex) {
            throw new RuntimeException("Serialization failure (cmd="
                    + cmd + ", name=" + name + ")", ex);
        }
        catch (Exception ex) {
            throw new RuntimeException("Raft set failure (cmd="
                    + cmd + ", name=" + name + ")", ex);
        }
    }

    // ===========================================
    //              Counter Native API
    // ===========================================

    public synchronized Long create0(String name, long initVal) {
        counters.putIfAbsent(name, initVal);
        return counters.get(name);
    }

    public synchronized Long get0(String name) {
        return counters.getOrDefault(name, 0L);
    }

    public synchronized Long add0(String name, long delta) {
        Long oldVal = counters.getOrDefault(name, 0L);
        return counters.put(name, oldVal + delta);
    }
}