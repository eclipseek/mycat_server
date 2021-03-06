package io.mycat.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.util.TimeUtil;

// xman. 每个前端和后端连接都有一个对应的缓冲区，对连接读写操作具体如何操作的方法和缓存方式，封装到了这个类里面。
public class NIOSocketWR extends SocketWR {
	private SelectionKey processKey;
	private static final int OP_NOT_READ = ~SelectionKey.OP_READ;
	private static final int OP_NOT_WRITE = ~SelectionKey.OP_WRITE;
	private final AbstractConnection con;
	private final SocketChannel channel;
	private final AtomicBoolean writing = new AtomicBoolean(false);

	public NIOSocketWR(AbstractConnection con) {
		this.con = con;
		this.channel = (SocketChannel) con.channel;
	}

	public void register(Selector selector) throws IOException {
		try {
			processKey = channel.register(selector, SelectionKey.OP_READ, con);
		} finally {
			if (con.isClosed.get()) {
				clearSelectionKey();
			}
		}
	}

	public void doNextWriteCheck() {
		// zhangyq | 2017/07/30/17:47 | at 江宁河定桥 STARBUCKS | CAS 代替锁
		// NIOReactor$RW 和定时器线程都会调用该方法，CAS 代替锁效率更高。
		if (!writing.compareAndSet(false, true)) {
			return;
		}

		try {
			// xman. 利用缓存队列和写缓冲记录保证写的可靠性，返回true则为全部写入成功
			boolean noMoreData = write0();
			// xman. 因为只有一个线程可以成功CAS更新writing值，所以这里不用再CAS
			writing.set(false);
			// xman. 如果全部写入成功而且写入队列为空（有可能在写入过程中又有新的Bytebuffer加入到队列），则取消注册写事件,
			// 否则，继续注册写事件
			if (noMoreData && con.writeQueue.isEmpty()) {
				if ((processKey.isValid() && (processKey.interestOps() & SelectionKey.OP_WRITE) != 0)) {
					disableWrite();
				}

			} else {

				if ((processKey.isValid() && (processKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
					enableWrite(false);
				}
			}

		} catch (IOException e) {
			if (AbstractConnection.LOGGER.isDebugEnabled()) {
				AbstractConnection.LOGGER.debug("caught err:", e);
			}
			con.close("err:" + e);
		}

	}

	private boolean write0() throws IOException {

		int written = 0;
		ByteBuffer buffer = con.writeBuffer;
		if (buffer != null) {
			// xman. 只要写缓冲记录中还有数据就不停写入，但如果写入字节为0，证明网络繁忙，则退出
			while (buffer.hasRemaining()) {
				written = channel.write(buffer);
				if (written > 0) {
					con.netOutBytes += written;
					con.processor.addNetOutBytes(written);
					con.lastWriteTime = TimeUtil.currentTimeMillis();
				} else {
					break;
				}
			}

			// xman. 如果写缓冲中还有数据证明网络繁忙，计数并退出，否则清空缓冲
			if (buffer.hasRemaining()) {
				con.writeAttempts++;
				return false;
			} else {
				con.writeBuffer = null;
				con.recycle(buffer);
			}
		}
		// xman. 读取缓存队列并写channel
		while ((buffer = con.writeQueue.poll()) != null) {
			if (buffer.limit() == 0) {
				con.recycle(buffer);
				con.close("quit send");
				return true;
			}

			buffer.flip();
			while (buffer.hasRemaining()) {
				written = channel.write(buffer);
				if (written > 0) {
					con.lastWriteTime = TimeUtil.currentTimeMillis();
					con.netOutBytes += written;
					con.processor.addNetOutBytes(written);
					con.lastWriteTime = TimeUtil.currentTimeMillis();
				} else {
					break;
				}
			}
			// xman. 如果写缓冲中还有数据证明网络繁忙，计数，记录下这次未写完的数据到写缓冲记录并退出，否则回收缓冲
			if (buffer.hasRemaining()) {
				con.writeBuffer = buffer;
				con.writeAttempts++;
				return false;
			} else {
				con.recycle(buffer);
			}
		}
		return true;
	}

	private void disableWrite() {
		try {
			SelectionKey key = this.processKey;
			key.interestOps(key.interestOps() & OP_NOT_WRITE);
		} catch (Exception e) {
			AbstractConnection.LOGGER.warn("can't disable write " + e + " con "
					+ con);
		}

	}

	private void enableWrite(boolean wakeup) {
		boolean needWakeup = false;
		try {
			SelectionKey key = this.processKey;
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
			needWakeup = true;
		} catch (Exception e) {
			AbstractConnection.LOGGER.warn("can't enable write " + e);

		}
		if (needWakeup && wakeup) {
			processKey.selector().wakeup();
		}
	}

	public void disableRead() {

		SelectionKey key = this.processKey;
		key.interestOps(key.interestOps() & OP_NOT_READ);
	}

	public void enableRead() {

		boolean needWakeup = false;
		try {
			SelectionKey key = this.processKey;
			key.interestOps(key.interestOps() | SelectionKey.OP_READ);
			needWakeup = true;
		} catch (Exception e) {
			AbstractConnection.LOGGER.warn("enable read fail " + e);
		}
		if (needWakeup) {
			processKey.selector().wakeup();
		}
	}

	private void clearSelectionKey() {
		try {
			SelectionKey key = this.processKey;
			if (key != null && key.isValid()) {
				key.attach(null);
				key.cancel();
			}
		} catch (Exception e) {
			AbstractConnection.LOGGER.warn("clear selector keys err:" + e);
		}
	}

	@Override
	public void asynRead() throws IOException {
		ByteBuffer theBuffer = con.readBuffer;
		if (theBuffer == null) {

			theBuffer = con.processor.getBufferPool().allocate(con.processor.getBufferPool().getChunkSize());

			con.readBuffer = theBuffer;
		}
		// xman. 从channel中读取数据，并且保存到对应AbstractConnection的readBuffer中，readBuffer处于write mode，返回读取了多少字节
		int got = channel.read(theBuffer);
		// xman. 调用处理读取到的数据的方法
		con.onReadData(got);
	}

}
