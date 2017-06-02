package org.jgroups.protocols.raft;

import java.util.concurrent.CompletableFuture;

/**
 * 增删成员的接口 (RAFT.members).
 * @author Bela Ban
 * @since  0.2
 */
public interface DynamicMembership {
    CompletableFuture<byte[]> addServer(String name) throws Exception;
    CompletableFuture<byte[]> removeServer(String name) throws Exception;
}
