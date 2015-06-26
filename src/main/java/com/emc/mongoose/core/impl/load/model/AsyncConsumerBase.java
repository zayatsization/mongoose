package com.emc.mongoose.core.impl.load.model;
// mongoose-common.jar
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.load.model.AsyncConsumer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 Created by kurila on 26.05.15.
 */
public abstract class AsyncConsumerBase<T>
extends Thread
implements AsyncConsumer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	// configuration params
	private final long maxCount;
	protected final int submTimeOutMilliSec, maxQueueSize;
	// states
	private final AtomicLong counterPreSubm = new AtomicLong(0);
	protected final AtomicBoolean
		isStarted = new AtomicBoolean(false),
		isShutdown = new AtomicBoolean(false),
		isAllSubm = new AtomicBoolean(false);
	// volatile
	private final BlockingQueue<T> volatileQueue;
	//
	public AsyncConsumerBase(
		final long maxCount, final int maxQueueSize, final int submTimeOutMilliSec
	) {
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		this.maxQueueSize = (int) Math.min(this.maxCount, maxQueueSize);
		this.submTimeOutMilliSec = submTimeOutMilliSec;
		volatileQueue = new ArrayBlockingQueue<>(maxQueueSize);
	}
	//
	@Override
	public void start() {
		if(isStarted.compareAndSet(false, true)) {
			LOG.debug(
				Markers.MSG,
				"{}: started, the further consuming will go through the volatile queue",
				getName()
			);
			super.start();
		}
	}
	/**
	 May block the executing thread until the queue becomes able to ingest more
	 @param item
	 @throws RemoteException
	 @throws InterruptedException
	 @throws RejectedExecutionException
	 */
	@Override
	public void submit(final T item)
	throws RemoteException, InterruptedException, RejectedExecutionException {
		if(isStarted.get()) {
			if(item == null || counterPreSubm.get() >= maxCount) {
				shutdown();
			}
			if(isShutdown.get()) {
				throw new InterruptedException("Shut down already");
			}
			if(volatileQueue.offer(item, submTimeOutMilliSec, TimeUnit.MILLISECONDS)) {
				counterPreSubm.incrementAndGet();
			} else {
				throw new RejectedExecutionException("Submit queue timeout");
			}
		} else {
			throw new RejectedExecutionException("Consuming is not started yet");
		}
	}
	/** Consumes the queue */
	@Override
	public final void run() {
		LOG.debug(
			Markers.MSG, "Determined submit queue capacity of {} for \"{}\"",
			volatileQueue.remainingCapacity(), getName()
		);
		T nextItem;
		try {
			while(volatileQueue.size() > 0 || !isShutdown.get()) {
				nextItem = volatileQueue.poll(submTimeOutMilliSec, TimeUnit.MILLISECONDS);
				if(nextItem != null) {
					submitSync(nextItem);
				}
			}
			LOG.debug(Markers.MSG, "{}: consuming finished", getName());
		} catch(final InterruptedException e) {
			LOG.debug(Markers.MSG, "{}: consuming interrupted", getName());
		} catch(final RejectedExecutionException e) {
			LOG.debug(Markers.MSG, "{}: consuming rejected", getName());
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.WARN, e, "Submit item failure");
		} finally {
			isAllSubm.set(true);
			shutdown();
		}
	}
	//
	protected abstract void submitSync(final T item)
	throws InterruptedException, RemoteException;
	//
	@Override
	public void shutdown() {
		if(!isStarted.get()) {
			throw new IllegalStateException(
				getName() + ": not started yet, but shutdown is invoked"
			);
		} else if(isShutdown.compareAndSet(false, true)) {
			LOG.debug(Markers.MSG, "{}: consumed {} items", getName(), counterPreSubm.get());
		}
	}
	//
	@Override
	public long getMaxCount() {
		return maxCount;
	}
	//
	@Override
	public synchronized void interrupt() {
		shutdown();
		if(!super.isInterrupted()) {
			super.interrupt();
		}
	}
	//
	@Override
	public void close()
	throws IOException {
		shutdown();
		final int dropCount = volatileQueue.size();
		if(dropCount > 0) {
			LOG.debug(Markers.MSG, "Dropped {} submit tasks", dropCount);
		}
		volatileQueue.clear(); // dispose
	}
}
