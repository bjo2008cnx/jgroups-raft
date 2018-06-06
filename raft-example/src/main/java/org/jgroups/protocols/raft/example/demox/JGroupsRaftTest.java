package org.jgroups.protocols.raft.example.demox;

import org.jgroups.JChannel;
import org.jgroups.protocols.raft.role.RAFT;
import org.jgroups.util.Util;

/**
 * Test jgroups raft algorithm implementation.
 */
public class JGroupsRaftTest {

    private static final String CLUSTER_NAME = "ctr-cluster";
    private static final String COUNTER_NAME = "counter";
    private static final String RAFT_XML = "raft.xml";

    public static void main(String[] args) throws Exception {
        JChannel ch = new JChannel(RAFT_XML).name(args[0]);
        CounterService counter = new CounterService(ch);

        try {
            doConnect(ch, CLUSTER_NAME);
            doLoop(ch, counter);
        } finally {
            Util.close(ch);
        }
    }

    private static void doConnect(JChannel ch, String clusterName) throws Exception {
        ch.connect(clusterName);
    }

    private static void doLoop(JChannel ch, CounterService counter) {
        boolean looping = true;
        while (looping) {
            int key = Util.keyPress("\n[0] Create [1] Increment [2] Decrement [3] Dump log [4] Snapshot [x] Exit\n" +
                    "first-applied=" + ((RAFT) ch.getProtocolStack().findProtocol(RAFT.class)).log().firstAppended() +
                    ", last-applied=" + counter.lastApplied() +
                    ", commit-index=" + counter.commitIndex() +
                    ", log size=" + Util.printBytes(counter.logSize()) + ": ");

            if ((key == '0' || key == '1' || key == '2') && !counter.isLeaderExist()) {
                System.out.println("Cannot perform cause there is no leader by now");
                continue;
            }

            long val;
            switch (key) {
                case '0':
                    counter.getOrCreateCounter(COUNTER_NAME, 1L);
                    break;
                case '1':
                    val = counter.incrementAndGet(COUNTER_NAME);
                    System.out.printf("%s: %s\n", COUNTER_NAME, val);
                    break;
                case '2':
                    val = counter.decrementAndGet(COUNTER_NAME);
                    System.out.printf("%s: %s\n", COUNTER_NAME, val);
                    break;
                case '3':
                    counter.dumpLog();
                    break;
                case '4':
                    counter.snapshot();
                    break;
                case 'x':
                    looping = false;
                    break;
                case '\n':
                    System.out.println(COUNTER_NAME + ": " + counter.get(COUNTER_NAME) + "\n");
                    break;
            }
        }
    }

}