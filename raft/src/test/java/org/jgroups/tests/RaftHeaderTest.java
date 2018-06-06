package org.jgroups.tests;

import org.jgroups.Global;
import org.jgroups.protocols.raft.protocol.REDIRECT;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Util;
import org.testng.annotations.Test;

/**
 * @author Bela Ban
 * @since  0.1
 */
@Test(groups=Global.FUNCTIONAL,singleThreaded=false)
public class RaftHeaderTest {

    public void testVoteRequestHeader() throws Exception {
        org.jgroups.protocols.raft.message.VoteRequest hdr=new org.jgroups.protocols.raft.message.VoteRequest(22, 3, 7);
        _testSize(hdr, org.jgroups.protocols.raft.message.VoteRequest.class);
    }

    public void testVoteResponseHeader() throws Exception {
        org.jgroups.protocols.raft.message.VoteResponse rsp=new org.jgroups.protocols.raft.message.VoteResponse(22, true);
        _testSize(rsp, org.jgroups.protocols.raft.message.VoteResponse.class);
    }

    public void testHeatbeatHeader() throws Exception {
        org.jgroups.protocols.raft.message.HeartbeatRequest hb=new org.jgroups.protocols.raft.message.HeartbeatRequest(22, Util.createRandomAddress("A"));
        _testSize(hb, org.jgroups.protocols.raft.message.HeartbeatRequest.class);
    }

    public void testAppendEntriesRequest() throws Exception {
        org.jgroups.protocols.raft.message.AppendEntriesRequest req=new org.jgroups.protocols.raft.message.AppendEntriesRequest(22, Util.createRandomAddress("A"), 4, 21, 22, 18, true);
        _testSize(req, org.jgroups.protocols.raft.message.AppendEntriesRequest.class);
    }

    public void testAppendEntriesResponse() throws Exception {
        org.jgroups.protocols.raft.message.AppendEntriesResponse rsp=new org.jgroups.protocols.raft.message.AppendEntriesResponse(22, new org.jgroups.protocols.raft.message.AppendResult(false, 22, 5));
        _testSize(rsp, org.jgroups.protocols.raft.message.AppendEntriesResponse.class);
    }

    public void testInstallSnapshotHeader() throws Exception {
        org.jgroups.protocols.raft.message.InstallSnapshotRequest hdr=new org.jgroups.protocols.raft.message.InstallSnapshotRequest(5);
        _testSize(hdr, org.jgroups.protocols.raft.message.InstallSnapshotRequest.class);

        hdr=new org.jgroups.protocols.raft.message.InstallSnapshotRequest(5, Util.createRandomAddress("A"), 5, 4);
        _testSize(hdr, org.jgroups.protocols.raft.message.InstallSnapshotRequest.class);
    }

    public static void testRedirectHeader() throws Exception {
        REDIRECT.RedirectHeader hdr=new REDIRECT.RedirectHeader(REDIRECT.RequestType.SET_REQ, 22, true);
        _testSize(hdr, REDIRECT.RedirectHeader.class);

        hdr=new REDIRECT.RedirectHeader(REDIRECT.RequestType.RSP, 322649, false);
        _testSize(hdr, REDIRECT.RedirectHeader.class);
    }


    protected static <T extends org.jgroups.protocols.raft.message.RaftHeader> void _testSize(T hdr, Class<T> clazz) throws Exception {
        int size=hdr.size();
        ByteArrayDataOutputStream out=new ByteArrayDataOutputStream(size);
        hdr.writeTo(out);
        System.out.println(clazz.getSimpleName() + ": size=" + size);
        assert out.position() == size;

        org.jgroups.protocols.raft.message.RaftHeader hdr2=(org.jgroups.protocols.raft.message.RaftHeader)Util.streamableFromByteBuffer(clazz, out.buffer(), 0, out.position());
        assert hdr2 != null;
        assert hdr.term() == hdr2.term();
    }


    protected static <T extends REDIRECT.RedirectHeader> void _testSize(T hdr, Class<T> clazz) throws Exception {
        int size=hdr.size();
        ByteArrayDataOutputStream out=new ByteArrayDataOutputStream(size);
        hdr.writeTo(out);
        System.out.println(clazz.getSimpleName() + ": size=" + size);
        assert out.position() == size;

        REDIRECT.RedirectHeader hdr2=(REDIRECT.RedirectHeader)Util.streamableFromByteBuffer(clazz, out.buffer(), 0, out.position());
        assert hdr2 != null;
        assert hdr.size() == hdr2.size();
    }
}
