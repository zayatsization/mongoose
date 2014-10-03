package com.emc.mongoose.object.api.provider.s3;
//
import com.emc.mongoose.object.api.WSRequestConfig;
import com.emc.mongoose.object.api.provider.s3.Bucket;
import com.emc.mongoose.object.api.provider.s3.WSRequestConfigImpl;
import com.emc.mongoose.object.data.WSObject;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
/**
 Created by kurila on 02.10.14.
 */
public final class WSBucket<T extends WSObject>
implements Bucket<T>{
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	final WSRequestConfigImpl reqConf;
	final String name;
	//
	public WSBucket(final WSRequestConfigImpl reqConf, final String name) {
		this.reqConf = reqConf;
		//
		if(name == null || name.length() == 0) {
			final Date
				dt = Calendar.getInstance(
					TimeZone.getTimeZone("GMT+0"), Locale.ROOT
				).getTime();
			this.name = "mongoose-" + WSRequestConfig.FMT_DT.format(dt);
		} else {
			this.name = name;
		}
	}
	//
	@Override
	public final String getName() {
		return name;
	}
	//
	@Override
	public final boolean exists()
	throws IllegalStateException {
		boolean flagExists = false;
		//
		final HttpRequestBase createReq = new HttpHead("/" + name);
		reqConf.applyHeadersFinally(createReq);
		final CloseableHttpClient httpClient = reqConf.getClient();
		//
		CloseableHttpResponse httpResp = null;
		if(httpClient != null) {
			try {
				httpResp = httpClient.execute(
					new HttpHost(reqConf.getAddr(), reqConf.getPort(), reqConf.getScheme()),
					createReq
				);
			} catch(final IOException e) {
				ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to check bucket \""+name+"\"");
			}
		} else {
			throw new IllegalStateException(
				"Failed to check the bucket, no HTTP client was specified"
			);
		}
		//
		if(httpResp != null) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					flagExists = true;
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.debug(
						Markers.MSG, "Check bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
		}
		//
		return flagExists;
	}
	//
	@Override
	public final void create()
	throws IllegalStateException {
		final HttpRequestBase createReq = new HttpPut("/" + name);
		reqConf.applyHeadersFinally(createReq);
		final CloseableHttpClient httpClient = reqConf.getClient();
		//
		CloseableHttpResponse httpResp = null;
		if(httpClient != null) {
			try {
				httpResp = httpClient.execute(
					new HttpHost(reqConf.getAddr(), reqConf.getPort(), reqConf.getScheme()),
					createReq
				);
			} catch(final IOException e) {
				ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to create bucket \""+name+"\"");
			}
		} else {
			throw new IllegalStateException(
				"Failed to create the bucket, no HTTP client was specified"
			);
		}
		//
		if(httpResp != null) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					LOG.info(Markers.MSG, "Bucket \"{}\" created", name);
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.warn(
						Markers.ERR, "Create bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
		}
	}
	//
	@Override
	public final void delete()
	throws IllegalStateException {
		final HttpRequestBase createReq = new HttpDelete("/" + name);
		reqConf.applyHeadersFinally(createReq);
		final CloseableHttpClient httpClient = reqConf.getClient();
		//
		CloseableHttpResponse httpResp = null;
		if(httpClient != null) {
			try {
				httpResp = httpClient.execute(createReq);
			} catch(final IOException e) {
				ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to delete bucket \""+name+"\"");
			}
		} else {
			throw new IllegalStateException(
				"Failed to delete the bucket, no HTTP client was specified"
			);
		}
		//
		if(httpResp != null) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					LOG.info(Markers.MSG, "Bucket \"{}\" deleted", name);
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.warn(
						Markers.ERR, "Delete bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
		}
	}
	//
	@Override
	public final List<T> list() {
		return Collections.emptyList();
	}
}
