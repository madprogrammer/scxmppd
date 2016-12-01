# scxmppd

SCXMPPd is a (trying to be) modular and extensible XMPP server written in Scala. The server is still in very early stage of development, and the code is missing stuff like tests and error checking, however development slowly continues and I hope these issues will be eventually resolved.

##### SCXMPPd aims to be
- Scalable. Ability to cluster multiple nodes to handle more simultaneous connections.
- Modular. Many of the additional features of the XMPP protocol (XEP) should be implemented as plug-in modules.
- Storage agnostic. It should support multiple options for data storage backend with an ability to easily add a new one.
- Ligthweight. Memory and CPU usage should be kept low, in order to increase per-node capacity.

##### What's NOT in priority right now
- 100% standard compliance. It's fine as long as most popular clients can successfully work with it.
- 100% feature completeness. Some non-essential XMPP protocol additions or features will be added after the core is stabilized.
- Server-to-Server comminucation is one of such features

##### Supported features
- Basic clustering using Akka's distributed PubSub and clustering support
- Basic XMPP message passing, including between different nodes of the cluster
- Legacy SSL connections
- ETCd as key-value storage backend
- XEP-0049: Private XML Storage (https://xmpp.org/extensions/xep-0049.html)
- XEP-0012: Last Activity (https://xmpp.org/extensions/xep-0012.html)
- XEP-0160: Offline Messages (https://xmpp.org/extensions/xep-0160.html)
- XEP-0199: XMPP Ping (https://xmpp.org/extensions/xep-0199.html)
- XEP-0030: Service Discovery (https://xmpp.org/extensions/xep-0030.html)
- Modular HTTP(S) server able to serve static or dynamic content
- BOSH support (in progress)

##### License
The code is distributed under GPLv2, see LICENSE for details.
