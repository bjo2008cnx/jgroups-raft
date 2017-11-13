package org.jgroups.protocols.raft.protocol;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.raft.log.LogEntry;
import org.jgroups.protocols.raft.message.HeartbeatRequest;
import org.jgroups.protocols.raft.message.RaftHeader;
import org.jgroups.protocols.raft.message.VoteRequest;
import org.jgroups.protocols.raft.message.VoteResponse;
import org.jgroups.protocols.raft.role.RAFT;
import org.jgroups.protocols.raft.role.Role;
import org.jgroups.stack.Protocol;
import org.jgroups.util.MessageBatch;
import org.jgroups.util.TimeScheduler;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 启动选举计时器，并在计时器关闭并且没有收到心跳时开始选举。
 * Starts an election timer on connect and starts an election when the timer goes off and no heartbeats have been received. Runs a heartbeat task when leader.
 *
 * @author Bela Ban
 * @since 0.1
 */
@MBean(description = "Protocol performing leader election according to the RAFT paper")
public class ELECTION extends Protocol {
    // when moving to JGroups -> add to jg-protocol-ids.xml
    protected static final short ELECTION_ID = 520;

    // When moving to JGroups -> add to jg-magic-map.xml
    protected static final short voteReq = 3000;
    protected static final short VOTE_RSP = 3001;
    protected static final short HEARTBEAT_REQ = 3002;

    //初始化ClassConfigurator
    static {
        ClassConfigurator.addProtocol(ELECTION_ID, ELECTION.class);
        ClassConfigurator.add(voteReq, VoteRequest.class);
        ClassConfigurator.add(VOTE_RSP, VoteResponse.class);
        ClassConfigurator.add(HEARTBEAT_REQ, HeartbeatRequest.class);
    }

    //领导者发出心跳的间隔（以毫秒为单位）
    @Property(description = "Interval (in ms) at which a leader sends out heartbeats")
    protected long heartbeat_interval = 30;

    //最小选举间隔（毫秒）
    @Property(description = "Min election interval (ms)")
    protected long election_min_interval = 150;

    //最大选举间隔（毫秒）。 实际的选举间隔是在[election_min_interval..election_max_interval]范围中作为随机值计算的
    @Property(description = "Max election interval (ms). The actual election interval is computed as a random value in " + "range " +
            "[election_min_interval..election_max_interval]")
    protected long election_max_interval = 300;

    /**
     * 此节点投票的候选人的地址
     * The address of the candidate this node voted for in the current term
     */
    protected Address votedFor;

    /**
     * 本期为我收集的投票（如果是候选人）
     * Votes collected for me in the current term (if candidate)
     */
    @ManagedAttribute(description = "Number of votes this candidate received in the current term")
    protected int currentVotes;

    /**
     * 如果真的话，选举将不会开始; 这个节点将永远是一个追随者。 仅用于测试，可能会被删除。 不要使用
     */
    @ManagedAttribute(description = "No election will ever be started if true; this node will always be a follower. Used only for testing and may get " +
            "removed" + ". Don't use !")
    protected boolean noElections;


    /**
     * 在这次选举超时之前是否收到心跳，如果是false，那么跟随者将成为候选人并开始新的选举
     * Whether a heartbeat has been received before this election timeout kicked in. If false, the follower becomes candidate and starts a new election
     */
    protected volatile boolean heartbeatReceived = true;

    protected RAFT raft; // direct ref instead of events
    protected Address localAddr;
    protected TimeScheduler timer;
    protected Future<?> electionTask; //选举任务
    protected Future<?> heartbeatTask; //心跳任务
    protected Role role = Role.Follower;

    public long heartbeatInterval() {
        return heartbeat_interval;
    }

    public ELECTION heartbeatInterval(long val) {
        heartbeat_interval = val;
        return this;
    }

    public long electionMinInterval() {
        return election_min_interval;
    }

    public ELECTION electionMinInterval(long val) {
        election_min_interval = val;
        return this;
    }

    public long electionMaxInterval() {
        return election_max_interval;
    }

    public ELECTION electionMaxInterval(long val) {
        election_max_interval = val;
        return this;
    }

    public boolean noElections() {
        return noElections;
    }

    public ELECTION noElections(boolean flag) {
        noElections = flag;
        return this;
    }


    @ManagedAttribute(description = "The current role")
    public String role() {
        return role.toString();
    }

    @ManagedAttribute(description = "Is the heartbeat task running")
    public synchronized boolean isHeartbeatTaskRunning() {
        return heartbeatTask != null && !heartbeatTask.isDone();
    }

    @ManagedAttribute(description = "Is the election ttimer running")
    public synchronized boolean isElectionTimerRunning() {
        return electionTask != null && !electionTask.isDone();
    }

    /**
     * 初始化
     *
     * @throws Exception
     */
    public void init() throws Exception {
        super.init();
        if (election_min_interval >= election_max_interval)
            throw new Exception("election_min_interval (" + election_min_interval + ") needs to be smaller than " + "election_max_interval (" +
                    election_max_interval + ")");
        timer = getTransport().getTimer();
        raft = findProtocol(RAFT.class);
    }

    public Object down(Event evt) {
        switch (evt.getType()) {
            case Event.CONNECT:
            case Event.CONNECT_USE_FLUSH:
            case Event.CONNECT_WITH_STATE_TRANSFER:
            case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH:
                Object retval = down_prot.down(evt); // connect first
                startElectionTimer();
                return retval;
            case Event.DISCONNECT:
                changeRole(Role.Follower);
                stopElectionTimer();
                break;
            case Event.SET_LOCAL_ADDRESS:
                localAddr = (Address) evt.getArg();
                break;
        }
        return down_prot.down(evt);
    }


    public Object up(Event evt) {
        switch (evt.getType()) {
            case Event.MSG:
                Message msg = (Message) evt.getArg();
                RaftHeader hdr = (RaftHeader) msg.getHeader(id);
                if (hdr == null) break;
                handleEvent(msg, hdr);
                return null;
        }
        return up_prot.up(evt);
    }


    public void up(MessageBatch batch) {
        for (Message msg : batch) {
            RaftHeader hdr = (RaftHeader) msg.getHeader(id);
            if (hdr != null) {
                batch.remove(msg);
                handleEvent(msg, hdr);
            }
        }
        if (!batch.isEmpty()) up_prot.up(batch);
    }


    protected void handleEvent(Message msg, RaftHeader hdr) {
        // drop the message if hdr.term < raft.current_term, else accept
        // if hdr.term > raft.current_term -> change to follower
        int rc = raft.currentTerm(hdr.term());
        if (rc < 0) return;
        if (rc > 0) { // a new term was set
            changeRole(Role.Follower);
            voteFor(null); // so we can vote again in this term
        }

        if (hdr instanceof HeartbeatRequest) {
            HeartbeatRequest hb = (HeartbeatRequest) hdr;
            handleHeartbeat(hb.term(), hb.leader);
        } else if (hdr instanceof VoteRequest) {
            VoteRequest header = (VoteRequest) hdr;
            handleVoteRequest(msg.src(), header.term(), header.lastLogTerm(), header.lastLogIndex());
        } else if (hdr instanceof VoteResponse) {
            VoteResponse rsp = (VoteResponse) hdr;
            handleVoteResponse(rsp.term());
        }
    }


    protected synchronized void handleHeartbeat(int term, Address leader) {
        if (Objects.equals(localAddr, leader)) return;
        heartbeatReceived(true);
        if (role != Role.Follower || raft.updateTermAndLeader(term, leader)) {
            changeRole(Role.Follower);
            voteFor(null);
        }
    }

    protected void handleVoteRequest(Address sender, int term, int lastLogTerm, int lastLogIndex) {
        if (Objects.equals(localAddr, sender)) return;
        if (log.isTraceEnabled())
            log.trace("%s: received VoteRequest from %s: term=%d, my term=%d, lastLogTerm=%d, lastLogIndex=%d", localAddr, sender, term, raft.currentTerm(),
                    lastLogTerm, lastLogIndex);
        boolean sendVoteRsp = false;
        synchronized (this) {
            if (voteFor(sender)) {
                if (sameOrNewer(lastLogTerm, lastLogIndex)) sendVoteRsp = true;
                else log.trace("%s: dropped VoteRequest from %s as my log is more up-to-date", localAddr, sender);
            } else log.trace("%s: already voted for %s in term %d; skipping vote", localAddr, sender, term);
        }
        if (sendVoteRsp) sendVoteResponse(sender, term); // raft.current_term);
    }

    protected synchronized void handleVoteResponse(int term) {
        if (role == Role.Candidate && term == raft.currentTerm()) {
            if (++currentVotes >= raft.majority()) {
                // we've got the majority: become leader
                log.trace("%s: collected %d votes (majority=%d) in term %d -> becoming leader", localAddr, currentVotes, raft.majority(), term);
                changeRole(Role.Leader);
            }
        }
    }

    /**
     * 处理选举超时
     */
    protected synchronized void handleElectionTimeout() {
        log.trace("%s: election timeout", localAddr);
        switch (role) {
            case Follower:
                changeRole(Role.Candidate);
                startElection();
                break;
            case Candidate:
                startElection();
                break;
        }
    }

    /**
     * Returns true if lastLogTerm >= my own last log term, or lastLogIndex >= my own index
     *
     * @param lastLogTerm
     * @param lastLogIndex
     * @return
     */
    protected boolean sameOrNewer(int lastLogTerm, int lastLogIndex) {
        int myLastLogIndex = raft.log().lastAppended();
        LogEntry entry = raft.log().get(myLastLogIndex);
        int myLastLogTerm = entry != null ? entry.term() : 0;
        int comp = Integer.compare(myLastLogTerm, lastLogTerm);
        return comp <= 0 && (comp < 0 || Integer.compare(myLastLogIndex, lastLogIndex) <= 0);
    }


    protected synchronized boolean heartbeatReceived(final boolean flag) {
        boolean retval = heartbeatReceived;
        heartbeatReceived = flag;
        return retval;
    }

    protected void sendHeartbeat(int term, Address leader) {
        Message req = new Message(null).putHeader(id, new HeartbeatRequest(term, leader)).setFlag(Message.Flag.OOB, Message.Flag.INTERNAL, Message.Flag
                .NO_RELIABILITY, Message.Flag.NO_FC).setTransientFlag(Message.TransientFlag.DONT_LOOPBACK);
        down_prot.down(new Event(Event.MSG, req));
    }

    protected void sendVoteRequest(int term) {
        int lastLogIndex = raft.log().lastAppended();
        LogEntry entry = raft.log().get(lastLogIndex);
        int lastLogTerm = entry != null ? entry.term() : 0;
        VoteRequest req = new VoteRequest(term, lastLogTerm, lastLogIndex);
        log.trace("%s: sending %s", localAddr, req);
        Message voteReq = new Message(null).putHeader(id, req).setFlag(Message.Flag.OOB, Message.Flag.INTERNAL, Message.Flag.NO_RELIABILITY, Message.Flag
                .NO_FC).setTransientFlag(Message.TransientFlag.DONT_LOOPBACK);
        down_prot.down(new Event(Event.MSG, voteReq));
    }

    protected void sendVoteResponse(Address dest, int term) {
        VoteResponse rsp = new VoteResponse(term, true);
        log.trace("%s: sending %s", localAddr, rsp);
        Message vote_rsp = new Message(dest).putHeader(id, rsp).setFlag(Message.Flag.OOB, Message.Flag.INTERNAL, Message.Flag.NO_RELIABILITY, Message.Flag
                .NO_FC);
        down_prot.down(new Event(Event.MSG, vote_rsp));
    }

    protected void changeRole(Role new_role) {
        if (role == new_role) return;
        if (role != Role.Leader && new_role == Role.Leader) {
            raft.leader(localAddr);
            // send a first heartbeat immediately after the election so other candidates step down
            sendHeartbeat(raft.currentTerm(), raft.leader());
            stopElectionTimer();
            startHeartbeatTimer();
        } else if (role == Role.Leader && new_role != Role.Leader) {
            stopHeartbeatTimer();
            startElectionTimer();
            raft.leader(null);
        }
        role = new_role;
        raft.changeRole(role);
    }

    protected void startElection() {
        int newTerm;

        synchronized (this) {
            newTerm = raft.createNewTerm();
            voteFor(null);
            currentVotes = 0;
            // Vote for self - return if I already voted for someone else
            if (!voteFor(localAddr)) return;
            currentVotes++; // vote for myself
        }

        sendVoteRequest(newTerm); // Send VoteRequest message; responses are received asynchronously. If majority -> become leader
    }

    @ManagedAttribute(description = "Vote cast for a candidate in the current term")
    public synchronized String votedFor() {
        return votedFor != null ? votedFor.toString() : null;
    }

    protected boolean voteFor(final Address addr) {
        if (addr == null) {
            votedFor = null;
            return true;
        }
        if (votedFor == null) {
            votedFor = addr;
            return true;
        }
        return votedFor.equals(addr); // a vote for the same candidate in the same term is ok
    }


    protected void startElectionTimer() {
        if (!noElections && (electionTask == null || electionTask.isDone())) {
            electionTask = timer.scheduleWithDynamicInterval(new ElectionTask());
        }
    }

    protected void stopElectionTimer() {
        if (electionTask != null) electionTask.cancel(true);
    }

    protected void startHeartbeatTimer() {
        if (heartbeatTask == null || heartbeatTask.isDone())
            heartbeatTask = timer.scheduleAtFixedRate(new HeartbeatTask(), heartbeat_interval, heartbeat_interval, TimeUnit.MILLISECONDS);
    }

    protected void stopHeartbeatTimer() {
        if (heartbeatTask != null) heartbeatTask.cancel(true);
    }

    protected <T extends Protocol> T findProtocol(Class<T> clazz) {
        for (Protocol p = up_prot; p != null; p = p.getUpProtocol()) {
            if (p.getClass().equals(clazz)) return (T) p;
        }
        throw new IllegalStateException(clazz.getSimpleName() + " not found above " + this.getClass().getSimpleName());
    }


    protected class ElectionTask implements TimeScheduler.Task {
        public long nextInterval() {
            return computeElectionTimeout(election_min_interval, election_max_interval);
        }

        public void run() {
            if (!heartbeatReceived(false)) {
                handleElectionTimeout();
            }
        }

        protected long computeElectionTimeout(long min, long max) {
            long diff = max - min;
            return (int) ((Math.random() * 100000) % diff) + min;
        }
    }

    protected class HeartbeatTask implements Runnable {
        public void run() {
            sendHeartbeat(raft.currentTerm(), raft.leader());
        }
    }

}
