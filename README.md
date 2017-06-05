##示例演示：

启动CounterServiceDemo(参数-name A)

启动CounterServiceDemo(参数-name B)

启动CounterServiceDemo(参数-name C)

在Console中可以看到A或B成为Leader

停掉A，可以看到B或C成为Leader


jgroups-raft
============

Implementation of the RAFT [1] consensus algorithm in JGroups. For the design, look into `doc`.

To generate the manual execute `ant manual` (requires `asciidoctor`).

Discussions on design and implementation are at [2].

The web page is here: [3].

There's an IRC at #jgroups-raft.

[1] http://raftconsensus.github.io/

[2] https://groups.google.com/forum/#!forum/jgroups-raft

[3] http://belaban.github.io/jgroups-raft

