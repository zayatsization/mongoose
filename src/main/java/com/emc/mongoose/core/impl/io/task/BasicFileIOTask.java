package com.emc.mongoose.core.impl.io.task;
//
import com.emc.mongoose.common.io.IOWorker;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.DataCorruptionException;
import com.emc.mongoose.core.api.item.data.DataSizeException;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.item.data.ContentSource;
import com.emc.mongoose.core.api.io.conf.FileIoConfig;
import com.emc.mongoose.core.api.io.task.FileIOTask;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.CANCELLED;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.FAIL_IO;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.FAIL_TIMEOUT;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.RESP_FAIL_CORRUPT;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.RESP_FAIL_NOT_FOUND;
import static com.emc.mongoose.core.api.io.task.IOTask.Status.SUCC;
//
import com.emc.mongoose.core.impl.item.data.BasicDataItem;
import static com.emc.mongoose.core.impl.item.data.BasicMutableDataItem.getRangeCount;
import static com.emc.mongoose.core.impl.item.data.BasicMutableDataItem.getRangeOffset;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
//
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
//
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
/**
 Created by kurila on 23.11.15.
 */
public class BasicFileIOTask<
	T extends FileItem, C extends Directory<T>, X extends FileIoConfig<T, C>
> extends BasicDataIOTask<T, C, X>
implements FileIOTask<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static FileSystem DEFAULT_FS = FileSystems.getDefault();
	//
	private final String parentDir;
	private final Set<OpenOption> openOptions = new HashSet<>();
	private final RunnableFuture<BasicFileIOTask<T, C, X>> future;
	//
	public BasicFileIOTask(final T item, final X ioConfig) {
		super(item, null, ioConfig);
		parentDir = ioConfig.getTargetItemPath();
		//
		openOptions.add(NOFOLLOW_LINKS);
		switch(ioType) {
			case WRITE:
				openOptions.add(WRITE);
				if(!item.hasScheduledUpdates() && !item.isAppending()) {
					openOptions.add(CREATE);
					openOptions.add(TRUNCATE_EXISTING);
				}
				break;
			case READ:
				openOptions.add(READ);
				break;
		}
		//
		future = new FutureTask<>(this, this);
	}
	//
	@Override
	public void run() {
		item.reset();
		reqTimeStart = reqTimeDone = respTimeStart = System.nanoTime() / 1000;
		try {
			if(openOptions.isEmpty()) { // delete
				runDelete();
			} else { // work w/ a content
				Files.createDirectories(DEFAULT_FS.getPath(parentDir));
				try(
					final FileChannel byteChannel = FileChannel.open(
						DEFAULT_FS.getPath(parentDir, item.getName()), openOptions
					)
				) {
					if(openOptions.contains(READ)) {
						runRead(byteChannel);
					} else {
						if(item.hasScheduledUpdates()) {
							runWriteUpdatedRanges(byteChannel);
						} else if(item.isAppending()) {
							runAppend(byteChannel);
						} else {
							runWriteFully(byteChannel);
						}
					}
				}
			}
		} catch(final ClosedByInterruptException e) {
			if(ioConfig.isClosed()) {
				status = CANCELLED;
			} else {
				status = FAIL_TIMEOUT;
			}
		} catch(final NoSuchFileException e) {
			status = RESP_FAIL_NOT_FOUND;
			LogUtil.exception(
				LOG, Level.DEBUG, e,
				"Failed to {} the file \"{}\"", ioType.name().toLowerCase(), parentDir
			);
		} catch(final IOException e) {
			status = FAIL_IO;
			LogUtil.exception(
				LOG, Level.DEBUG, e,
				"Failed to {} the file \"{}\"", ioType.name().toLowerCase(), parentDir
			);
		} finally {
			respTimeDone = System.nanoTime() / 1000;
		}
	}
	//
	protected void runDelete()
	throws IOException {
		Files.delete(DEFAULT_FS.getPath(parentDir, item.getName()));
		status = SUCC;
	}
	//
	protected void runRead(final FileChannel fileChannel)
	throws IOException {
		try {
			//
			if(ioConfig.getVerifyContentFlag()) {
				if(item.hasBeenUpdated()) {
					final int rangeCount = item.getCountRangesTotal();
					for(currRangeIdx = 0; currRangeIdx < rangeCount; currRangeIdx ++) {
						// prepare the byte range to read
						currRangeSize = item.getRangeSize(currRangeIdx);
						if(item.isCurrLayerRangeUpdated(currRangeIdx)) {
							currRange = new BasicDataItem(
								item.getOffset() + nextRangeOffset, currRangeSize,
								currDataLayerIdx + 1, ioConfig.getContentSource()
							);
						} else {
							currRange = new BasicDataItem(
								item.getOffset() + nextRangeOffset, currRangeSize,
								currDataLayerIdx, ioConfig.getContentSource()
							);
						}
						nextRangeOffset = getRangeOffset(currRangeIdx + 1);
						// read the bytes range
						if(currRangeSize > 0) {
							int n = 0;
							while(
								countBytesDone < contentSize && countBytesDone < nextRangeOffset
							) {
								//buffIn = ((IOWorker) Thread.currentThread())
								//	.getThreadLocalBuff(nextRangeOffset - countBytesDone);
								//n = currRange.readAndVerify(fileChannel, buffIn);
								n += fileChannel.transferTo(n, currRangeSize, currRange);
							}
							countBytesDone += n;
						}
					}
				} else {
					while(countBytesDone < contentSize) {
						//buffIn = ((IOWorker) Thread.currentThread())
						//	.getThreadLocalBuff(contentSize - countBytesDone);
						//n = item.readAndVerify(fileChannel, buffIn);
						countBytesDone += fileChannel.transferTo(countBytesDone, contentSize, item);
					}
				}
			} else {
				while(countBytesDone < contentSize) {
					//buffIn = ((IOWorker) Thread.currentThread())
					//	.getThreadLocalBuff(contentSize - countBytesDone);
					//n = fileChannel.read(buffIn);
					countBytesDone += fileChannel.transferTo(countBytesDone, contentSize, item);
				}
			}
			status = SUCC;
		} catch(final DataSizeException e) {
			countBytesDone += e.offset;
			LOG.warn(
				Markers.MSG,
				"{}: content size mismatch, expected: {}, actual: {}",
				item.getName(), item.getSize(), countBytesDone
			);
			status = RESP_FAIL_CORRUPT;
		} catch(final DataCorruptionException e) {
			countBytesDone += e.offset;
			LOG.warn(
				Markers.MSG,
				"{}: content mismatch @ offset {}, expected: {}, actual: {}",
				item.getName(), countBytesDone,
				String.format("\"0x%X\"", e.expected), String.format("\"0x%X\"", e.actual)
			);
			status = RESP_FAIL_CORRUPT;
		}
	}
	//
	protected void runWriteFully(final FileChannel fileChannel)
	throws IOException {
		while(countBytesDone < contentSize) {
			// countBytesDone += item.write(fileChannel, contentSize - countBytesDone);
			countBytesDone += fileChannel.transferFrom(item, countBytesDone, contentSize);
		}
		status = SUCC;
		item.resetUpdates();
	}
	//
	protected void runWriteUpdatedRanges(final FileChannel fileChannel)
	throws IOException {
		final int rangeCount = item.getCountRangesTotal();
		final ContentSource contentSource = ioConfig.getContentSource();
		for(currRangeIdx = 0; currRangeIdx < rangeCount; currRangeIdx ++) {
			if(item.isCurrLayerRangeUpdating(currRangeIdx)) {
				currRangeSize = item.getRangeSize(currRangeIdx);
				nextRangeOffset = getRangeOffset(currRangeIdx);
				currRange = new BasicDataItem(
					item.getOffset() + nextRangeOffset, currRangeSize,
					currDataLayerIdx + 1, contentSource
				);
				runWriteCurrRange(fileChannel, 0);
			}
		}
		item.commitUpdatedRanges();
		status = SUCC;
	}
	//
	protected void runWriteCurrRange(final FileChannel fileChannel, final long rangeOffset)
	throws IOException {
		fileChannel.position(nextRangeOffset);
		currRange.setRelativeOffset(rangeOffset);
		long n = 0;
		while(n < currRangeSize - rangeOffset && n < contentSize - countBytesDone) {
			//n += currRange.write(fileChannel, currRangeSize - rangeOffset - n);
			n += fileChannel.transferFrom(currRange, n, currRangeSize - rangeOffset);
		}
		countBytesDone += n;
	}
	//
	protected void runAppend(final FileChannel fileChannel)
	throws IOException {
		final long
			prevSize = item.getSize(),
			newSize = prevSize + contentSize;
		// work w/ start range if there's anything to append
		if(newSize > prevSize) {
			final int startRangeIdx = prevSize > 0 ? getRangeCount(prevSize) - 1 : 0;
			nextRangeOffset = getRangeOffset(startRangeIdx);
			currRangeSize = Math.min(
				contentSize,
				getRangeOffset(startRangeIdx + 1) - nextRangeOffset
			);
			currRange = new BasicDataItem(
				item.getOffset() + nextRangeOffset, currRangeSize,
				item.isCurrLayerRangeUpdated(startRangeIdx) ?
					currDataLayerIdx + 1 : currDataLayerIdx,
				ioConfig.getContentSource()
			);
			runWriteCurrRange(fileChannel, prevSize - nextRangeOffset);
			// work w/ remaining ranges if any
			final int lastRangeIdx = newSize > 0 ? getRangeCount(newSize) - 1 : 0;
			if(startRangeIdx < lastRangeIdx) {
				nextRangeOffset = getRangeOffset(startRangeIdx + 1);
				currRangeSize = Math.min(
					contentSize - countBytesDone,
					getRangeOffset(lastRangeIdx + 1) - nextRangeOffset
				);
				currRange = new BasicDataItem(
					item.getOffset() + nextRangeOffset, currRangeSize, currDataLayerIdx,
					ioConfig.getContentSource()
				);
				runWriteCurrRange(fileChannel, 0);
			}
		}
		item.commitAppend();
		status = SUCC;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final boolean cancel(final boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}
	//
	@Override
	public final boolean isCancelled() {
		return future.isCancelled();
	}
	//
	@Override
	public final boolean isDone() {
		return future.isDone();
	}
	//
	@Override
	public final FileIOTask<T> get()
	throws InterruptedException, ExecutionException {
		return future.get();
	}
	//
	@Override
	public final FileIOTask<T> get(final long timeout, final TimeUnit unit)
	throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(timeout, unit);
	}
}
