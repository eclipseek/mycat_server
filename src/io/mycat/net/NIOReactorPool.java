package io.mycat.net;

import java.io.IOException;

// xman. 一般高性能网络通信框架采用多Reactor（多dispatcher）模式，这里将NIOReactor池化；
public class NIOReactorPool {
	private final NIOReactor[] reactors;
	private volatile int nextReactor;

	public NIOReactorPool(String name, int poolSize) throws IOException {
		reactors = new NIOReactor[poolSize];
		for (int i = 0; i < poolSize; i++) {
			NIOReactor reactor = new NIOReactor(name + "-" + i);
			reactors[i] = reactor;
			reactor.startup();
		}
	}

	// xman. 每次NIOConnector接受一个连接或者NIOAcceptor请求一个连接，都会封装成AbstractConnection，
	// 同时请求NIOReactorPool每次轮询出一个NIOReactor，之后AbstractConnection与这个NIOReactor绑定
	public NIOReactor getNextReactor() {
//		if (++nextReactor == reactors.length) {
//			nextReactor = 0;
//		}
//		return reactors[nextReactor];

        int i = ++nextReactor;
        if (i >= reactors.length) {
            i=nextReactor = 0;
        }
        return reactors[i];
	}
}
