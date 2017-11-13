package org.jgroups.protocols.raft.role;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.protocols.raft.log.Log;
import org.jgroups.protocols.raft.log.LogEntry;
import org.jgroups.protocols.raft.message.AppendEntriesResponse;
import org.jgroups.protocols.raft.message.AppendResult;

/**
 * Base class for the different roles a RAFT node can have (follower, candidate, leader)
 *
 * @author Bela Ban
 * @since 0.1
 */
public abstract class RaftImpl {
    protected RAFT raft; // a ref to the enclosing RAFT protocol

    public RaftImpl(RAFT raft) {
        this.raft = raft;
    }

    public RAFT raft() {
        return raft;
    }

    public RaftImpl raft(RAFT r) {
        this.raft = r;
        return this;
    }

    /**
     * Called right after instantiation
     */
    public void init() {
    }

    /**
     * Called before getting destroyed (on a role change)
     */
    public void destroy() {
    }


    /**
     * Called (on a follower) when an AppendEntries request is received
     * // todo: synchronize
     * @param data         The data (command to be appended to the log)
     * @param offset       The offset
     * @param length       The length
     * @param leader       The leader's address (= the sender)
     * @param prevLogIndex The index of the previous log entry
     * @param prevLogTerm  The term of the previous log entry
     * @param entryTerm    The term of the entry
     * @param leaderCommit The leader's commit_index
     * @param internal     True if the command is an internal command
     * @return AppendResult A result (true or false), or null if the request was ignored (e.g. due to lower term)
     */
    protected AppendResult handleAppendEntriesRequest(byte[] data, int offset, int length, Address leader, int prevLogIndex, int prevLogTerm, int entryTerm,
                                                      int leaderCommit, boolean internal) {
        raft.leader(leader);
        if (data == null || length == 0) { // 一个仅包含leaderCommit的空AppendEntries消息
            handleCommitRequest(leader, leaderCommit);
            return null;
        }
        //获取前一条log 条目
        LogEntry prev = raft.logImpl.get(prevLogIndex);
        if (prev == null && prevLogIndex > 0) {// didn't find entry
            return new AppendResult(false, raft.lastAppended());
        }
        int currIndex = prevLogIndex + 1;
        int prevTerm = prev.term();
        if (prevLogIndex == 0 || prevTerm == prevLogTerm) {//前一term相同
            LogEntry existing = raft.logImpl.get(currIndex);
            if (existing != null && existing.term() != entryTerm) { //当前term不同
                // 删除当前和所有后续的条目，并用收到的条目覆盖
                raft.deleteAllLogEntriesStartingFrom(currIndex);
            }
            //附加log
            raft.append(entryTerm, currIndex, data, offset, length, internal).commitLogTo(leaderCommit);
            if (internal) raft.executeInternalCommand(null, data, offset, length);
            return new AppendResult(true, currIndex).commitIndex(raft.commitIndex());
        }
        // 如果preTerm不同，找到冲突的地方
        return new AppendResult(false, getFirstIndexOfConflictingTerm(prevLogIndex, prevTerm), prevTerm);
    }


    protected void handleAppendEntriesResponse(Address sender, int term, AppendResult result) {
    }

    protected void handleInstallSnapshotRequest(Message msg, int term, Address leader, int last_included_index, int last_included_term) {

    }


    /**
     * 查找conflictTerm开始处的第一个索引，从startIndex开始往日志的开头找
     * Finds the first index at which conflictingTerm starts, going back from startIndex towards the head of the log
     */
    protected int getFirstIndexOfConflictingTerm(int startIndex, int conflictingTerm) {
        Log log = raft.logImpl;
        int first = Math.max(1, log.firstAppended());
        int last = log.lastAppended();
        int retval = Math.min(startIndex, last);
        for (int i = retval; i >= first; i--) {
            LogEntry entry = log.get(i);
            if (entry == null || entry.term() != conflictingTerm) break;
            retval = i;
        }
        return retval;
    }

    protected void handleCommitRequest(Address sender, int leader_commit) {
        raft.commitLogTo(leader_commit);
        AppendResult result = new AppendResult(true, raft.lastAppended()).commitIndex(raft.commitIndex());
        Message msg = new Message(sender).putHeader(raft.getId(), new AppendEntriesResponse(raft.currentTerm(), result));
        raft.getDownProtocol().down(new Event(Event.MSG, msg));
    }

}
