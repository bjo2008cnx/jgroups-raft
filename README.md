重构jgroups-raft，具有更好的代码可读性

##示例演示：

启动CounterServiceDemo(参数-name A)

启动CounterServiceDemo(参数-name B)

启动CounterServiceDemo(参数-name C)

在Console中可以看到A或B成为Leader

停掉A，可以看到B或C成为Leader


JGroups:
 JGroups当中，udp是比较推荐的通信方式，其特点是不需要知道另一个节点的ip，通过多播网络发现就可以“找到”相应的节点，而tcp则需要在配置文件中固定配置。
 JGroups实现了“单播（对点）”、“组播（对组）”和“广播（对所有）”的三种通信方式
 JGroups为我们提供了这样一个通知或发现的功能，我们可以很轻松的知道集群里的某个worker是否在运行。

raft 优化：
自动识别,不需要配置
如果只有1台存活，自动成为master
如果只有2台存活，编号小的为master

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

