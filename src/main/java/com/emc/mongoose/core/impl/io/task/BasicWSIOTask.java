package com.emc.mongoose.core.impl.io.task;
// mongoose-common
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.http.IOUtils;
import com.emc.mongoose.common.net.http.content.InputChannel;
import com.emc.mongoose.common.net.http.content.OutputChannel;
import com.emc.mongoose.common.log.LogUtil;
// mongoose-core-api
import com.emc.mongoose.core.api.data.DataCorruptionException;
import com.emc.mongoose.core.api.data.DataSizeException;
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.io.req.WSRequestConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.task.WSIOTask;
// mongoose-core-impl
import com.emc.mongoose.core.api.load.executor.WSLoadExecutor;
//
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
//
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
/**
 Created by kurila on 06.06.14.
 */
public class BasicWSIOTask<T extends WSObject>
extends BasicObjectIOTask<T>
implements WSIOTask<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicWSIOTask(
		final WSLoadExecutor<T> loadExecutor, final T dataObject, final String nodeAddr
	) {
		super(loadExecutor, dataObject, nodeAddr);
	}
	/**
	 * Warning: invoked implicitly and untimely in the depths of HttpCore lib.
	 * So does nothing
	 */
	@Override
	public final void close() {}
	////////////////////////////////////////////////////////////////////////////////////////////////
	private volatile Exception exception = null;
	@SuppressWarnings("FieldCanBeLocal")
	private volatile int respStatusCode = -1;
	//
	////////////////////////////////////////////////////////////////////////////////////////////////
	// HttpAsyncRequestProducer implementation /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final HttpHost getTarget() {
		return ((WSRequestConfig<T>) reqConf).getNodeHost(nodeAddr);
	}
	//
	@Override
	public final HttpRequest generateRequest()
	throws IOException, HttpException {
		final HttpEntityEnclosingRequest httpRequest;
		try {
			 httpRequest = ((WSRequestConfig<T>) reqConf).createDataRequest(dataItem, nodeAddr);
		} catch(final URISyntaxException e) {
			throw new HttpException("Failed to generate the request", e);
		}
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(
				Markers.MSG, "I/O task #{}: generated the request: {}",
				hashCode(), httpRequest
			);
		}
		reqTimeStart = System.nanoTime() / 1000;
		return httpRequest;
	}
	//
	private final static ThreadLocal<OutputChannel>
		THRLOC_CHAN_OUT = new ThreadLocal<>();
	@Override
	public final void produceContent(final ContentEncoder out, final IOControl ioCtl)
	throws IOException {
		OutputChannel chanOut = THRLOC_CHAN_OUT.get();
		if(chanOut == null) {
			chanOut = new OutputChannel();
			THRLOC_CHAN_OUT.set(chanOut);
		}
		chanOut.setContentEncoder(out);
		try {
			switch(reqConf.getLoadType()) {
				case CREATE:
					transferSize += dataItem.writeFully(chanOut);
					break;
				case READ:
					// TODO there's a probability to specify some content in this case
					break;
				case DELETE:
					// TODO there's a probability to specify some content in this case
					break;
				case UPDATE:
					transferSize += dataItem.writeUpdatedRangesFully(chanOut);
					break;
				case APPEND:
					transferSize += dataItem.writeAugmentFully(chanOut);
					break;
			}
		} catch(final ClosedChannelException e) { // probably a manual interruption
			status = Status.CANCELLED;
			LogUtil.exception(
				LOG, Level.TRACE, e, "#{}: output channel closed during the operation", hashCode()
			);
		} catch(final IOException e) {
			status = Status.FAIL_IO;
			LogUtil.exception(
				LOG, Level.DEBUG, e, "#{}: I/O failure during the data output", hashCode()
			);
		} catch(final Exception e) {
			status = Status.FAIL_UNKNOWN;
			LogUtil.exception(LOG, Level.ERROR, e, "#{}: producing content failure", hashCode());
		} finally {
			chanOut.close();
		}
	}
	//
	@Override
	public final void requestCompleted(final HttpContext context) {
		reqTimeDone = System.nanoTime() / 1000;
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(Markers.MSG, "I/O task #{}: request sent completely", hashCode());
		}
	}
	//
	@Override
	public final boolean isRepeatable() {
		return WSObject.IS_CONTENT_REPEATABLE;
	}
	//
	@Override
	public final void resetRequest() {
		respStatusCode = -1;
		transferSize = 0;
		status = Status.FAIL_UNKNOWN;
		exception = null;
	}
	/*
	@Override @SuppressWarnings("unchecked")
	public final BasicWSIOTask<T> reuse(final Object... args) {
		return (BasicWSIOTask<T>) super.reuse(args);
	}*/
	////////////////////////////////////////////////////////////////////////////////////////////////
	// HttpAsyncResponseConsumer implementation ////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final void responseReceived(final HttpResponse response)
	throws IOException, HttpException {
		//
		respTimeStart = System.nanoTime() / 1000;
		final StatusLine status = response.getStatusLine();
		respStatusCode = status.getStatusCode();
		//
		if(respStatusCode < 200 || respStatusCode >= 300) {
			LOG.debug(Markers.ERR, "I/O task #{}: got response \"{}\"", hashCode(), status);
			//
			switch(respStatusCode) {
				case HttpStatus.SC_CONTINUE:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_BAD_REQUEST:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_UNAUTHORIZED:
				case HttpStatus.SC_FORBIDDEN:
					this.status = Status.RESP_FAIL_AUTH;
					break;
				case HttpStatus.SC_NOT_FOUND:
					this.status = Status.RESP_FAIL_NOT_FOUND;
					break;
				case HttpStatus.SC_METHOD_NOT_ALLOWED:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_CONFLICT:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_LENGTH_REQUIRED:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_REQUEST_TOO_LONG:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_REQUEST_URI_TOO_LONG:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE:
					this.status = Status.RESP_FAIL_CLIENT;
					break;
				case 429:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_INTERNAL_SERVER_ERROR:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_NOT_IMPLEMENTED:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_BAD_GATEWAY:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_SERVICE_UNAVAILABLE:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_GATEWAY_TIMEOUT:
					this.status = Status.FAIL_TIMEOUT;
					break;
				case HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED:
					this.status = Status.RESP_FAIL_SVC;
					break;
				case HttpStatus.SC_INSUFFICIENT_STORAGE:
					this.status = Status.RESP_FAIL_SPACE;
					break;
				default:
					this.status = Status.FAIL_UNKNOWN;
					break;
			}
		} else {
			this.status = Status.SUCC;
			((WSRequestConfig<T>) reqConf).applySuccResponseToObject(response, dataItem);
		}
	}
	//
	private final static ThreadLocal<InputChannel>
		THRLOC_CHAN_IN = new ThreadLocal<>();
	//
	@Override
	public final void consumeContent(final ContentDecoder in, final IOControl ioCtl) {
		try {
			if(respStatusCode < 200 || respStatusCode >= 300) { // failure, no user data is expected
				final ByteBuffer bbuff = ByteBuffer.allocate(Constants.BUFF_SIZE_LO);
				while(in.read(bbuff) >= 0 && bbuff.remaining() > 0);
				LOG.debug(
					Markers.ERR, "#{}: {}, {}", hashCode(), status.description,
					new String(bbuff.array(), 0, bbuff.position(), StandardCharsets.UTF_8)
				);
			} else {
				// check for the content corruption
				if(dataItem != null && Type.READ.equals(reqConf.getLoadType())) {
					if(reqConf.getVerifyContentFlag()) { // should verify the content
						InputChannel chanIn = THRLOC_CHAN_IN.get();
						if(chanIn == null) {
							chanIn = new InputChannel();
							THRLOC_CHAN_IN.set(chanIn);
						}
						chanIn.setContentDecoder(in);
						try {
							transferSize += dataItem.readAndVerifyFully(chanIn);
						} catch(final DataSizeException e) {
							LOG.warn(
								Markers.MSG,
								"{}: content size mismatch, expected: {}, actual: {}",
								dataItem.getId(), dataItem.getSize(), e.offset
							);
							status = Status.RESP_FAIL_CORRUPT;
						} catch(final DataCorruptionException e) {
							LOG.warn(
								Markers.MSG,
								"{}: content mismatch @ offset {}, expected: {}, actual: {}",
								dataItem.getId(), e.offset,
								String.format(
									"\"0x%X\"", e.expected), String.format("\"0x%X\"", e.actual
								)
							);
							status = Status.RESP_FAIL_CORRUPT;
						} finally {
							chanIn.close();
						}
					} else { // consume quietly
						transferSize += IOUtils.consumeQuietlyBIO(in);
					}
				}
			}
		} catch(final ClosedChannelException e) {
			status = Status.CANCELLED;
			LogUtil.exception(LOG, Level.TRACE, e, "Output channel closed during the operation");
		} catch(final IOException e) {
			if(!reqConf.isClosed()) {
				LogUtil.exception(LOG, Level.DEBUG, e, "I/O failure during content consuming");
			}
		} finally {
			IOUtils.consumeQuietlyBIO(in);
		}
	}
	//
	@Override
	public final void responseCompleted(final HttpContext context) {
		respTimeDone = System.nanoTime() / 1000;
	}
	//
	@Override
	public final IOTask.Status getResult() {
		return status;
	}
	//
	@Override
	public final Exception getException() {
		return exception;
	}
	//
	@Override
	public final boolean isDone() {
		return respTimeDone != 0 || exception != null || status.equals(Status.CANCELLED);
	}
	//
	@Override
	public final boolean cancel() {
		LOG.debug(Markers.MSG, "{}: I/O task cancel", hashCode());
		return false;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// HttpContext implementation //////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final HttpContext wrappedHttpCtx = new BasicHttpContext();
	//
	@Override
	public final Object getAttribute(final String s) {
		return wrappedHttpCtx.getAttribute(s);
	}
	@Override
	public final void setAttribute(final String s, final Object o) {
		wrappedHttpCtx.setAttribute(s, o);
	}
	@Override
	public final Object removeAttribute(final String s) {
		return wrappedHttpCtx.removeAttribute(s);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// FutureCallback<IOTask.Status> implementation ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final void completed(final IOTask.Status status) {
		complete();
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(Markers.MSG, "I/O task #{} completed", hashCode());
		}
	}
	/**
	 Overrides HttpAsyncRequestProducer.failed(Exception),
	 HttpAsyncResponseConsumer&lt;IOTask.Status&gt;.failed(Exception) and
	 FutureCallback&lt;IOTask.Status&gt;.failed(Exception)
	 @param e
	 */
	@Override
	public final void failed(final Exception e) {
		if(!reqConf.isClosed()) {
			LogUtil.exception(LOG, Level.DEBUG, e, "{}: I/O task failure", hashCode());
		}
		exception = e;
		status = Status.FAIL_UNKNOWN;
		complete();
	}
	//
	@Override
	public final void cancelled() {
		LOG.debug(Markers.MSG, "{}: I/O task canceled", hashCode());
		status = Status.CANCELLED;
		complete();
	}
}
