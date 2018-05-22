package org.jgroups.protocols.raft.role;

import org.jgroups.Address;
import org.jgroups.protocols.raft.message.AppendResult;
import org.jgroups.protocols.raft.util.RequestTable;
import org.jgroups.util.ExtendedUUID;
import org.jgroups.util.Util;

/**
 * Leader
 * @author Bela Ban
 * @since  0.1
 */
public class Leader extends RaftImpl {
    public Leader(RAFT raft) {
        super(raft);
    }

    public void init() {
        super.init();
        raft.createRequestTable();
        raft.createCommitTable();
        raft.startResendTask();
    }

    public void destroy() {
        super.destroy();
        raft.stopResendTask();
        raft.request_table=null;
        raft.commit_table=null;
    }


    @Override
    protected void handleAppendEntriesResponse(Address sender, int term, AppendResult result) {
        RequestTable<String> reqtab=raft.request_table;
        if(reqtab == null)
            throw new IllegalStateException("request table cannot be null in leader");
        ExtendedUUID uuid=(ExtendedUUID)sender;
        String sender_raft_id=Util.bytesToString(uuid.get(RAFT.raft_id_key));
        raft.getLog().trace("%s: received AppendEntries response from %s for term %d: %s", raft.local_addr, sender, term, result);
        if(result.success()) {
            raft.commit_table.update(sender, result.getIndex(), result.getIndex()+1, result.commitIndex(), false);
            if(reqtab.add(result.index(), sender_raft_id, raft.majority()))
                raft.handleCommit(result.index());
        }
        else
            raft.commit_table.update(sender, 0, result.getIndex(), result.commitIndex(), true);
    }

}
