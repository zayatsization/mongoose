package com.emc.mongoose.storage.mock.impl.web.request;

import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.data.model.DataItemContainer;
import com.emc.mongoose.core.api.io.req.WSRequestConfig;
import com.emc.mongoose.storage.adapter.atmos.SubTenant;
import com.emc.mongoose.storage.mock.api.ContainerMockException;
import com.emc.mongoose.storage.mock.api.ContainerMockNotFoundException;
import com.emc.mongoose.storage.mock.api.WSMock;
import com.emc.mongoose.storage.mock.api.WSObjectMock;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.channel.ChannelHandler.Sharable;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@Sharable
public class NagainaAtmosRequestHandler<T extends WSObjectMock> extends NagainaRequestHandlerBase<T> {

	private final static Logger LOG = LogManager.getLogger();
	private final static String
			URI_BASE_PATH = "/rest",
			OBJ_PATH = URI_BASE_PATH + "/objects",
			NS_PATH = URI_BASE_PATH + "/namespace",
			AT_PATH = URI_BASE_PATH + "/accesstokens",
			ST_PATH = URI_BASE_PATH + "/subtenant",
			STS_PATH = ST_PATH + "s/";

	public NagainaAtmosRequestHandler(RunTimeConfig rtConfig, WSMock<T> sharedStorage) {
		super(rtConfig, sharedStorage);
	}

	private String processMetaDataList(String[] metaDataList, String key) {
		if (metaDataList != null) {
			String entry[];
			for (String metaData : metaDataList) {
				entry = metaData.split("=");
				if (entry.length == 2 && entry[0].equals(key)) {
					return entry[1];
				}
			}
		}
		return null;
	}


	@Override
	protected void handleActually(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
		HttpRequest request = ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey)).get();
		String[] metaDataList = null;
		if (request.headers().contains(WSRequestConfig.KEY_EMC_TAGS)) {
			metaDataList = request.headers().get(WSRequestConfig.KEY_EMC_TAGS).split(",");
		}
		String uri = request.getUri();
		String method = request.getMethod().toString().toUpperCase();
		Long size = ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)).get();
		ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).set(true);
		if (uri.startsWith(OBJ_PATH)) {
			String[] uriParams =
					getUriParams(ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey))
							.get().getUri(), 3);
			String objId = uriParams[2];
			long offset = -1;
			if (objId == null) {
				if (method.equals(WSRequestConfig.METHOD_POST)) {
					objId = generateId();
					String processResult = processMetaDataList(metaDataList, "offset");
					try {
						if (processResult != null) {
							offset = Long.parseLong(processResult);
						}
					} catch (NumberFormatException e) {
						LogUtil.exception(
								LOG, Level.WARN, e,
								"Failed to parse offset meta tag value: \"{}\"",
								processResult
						);
					}
					handleGenericDataReq(method, getSubtenant(request.headers(), uri), objId, offset, size, ctx);
					int statusCode = response.getStatus().code();
					if (statusCode < 300 && 200 <= statusCode) {
						response.headers().set(LOCATION, OBJ_PATH + "/" + objId);
					}
				} else if (method.equals(WSRequestConfig.METHOD_GET)) {
					String subtenant = processMetaDataList(metaDataList, "subtenant");
					if (request.headers().contains(WSRequestConfig.KEY_EMC_TOKEN)) {
						objId = request.headers().get(WSRequestConfig.KEY_EMC_TOKEN);
					}
					handleContainerList(subtenant, objId, ctx);
				}
			} else {
				handleGenericDataReq(method, getSubtenant(request.headers(), uri), objId, offset, size, ctx);
			}
		} else if (uri.startsWith(NS_PATH) || (uri.startsWith(AT_PATH))) {
			setHttpResponseStatusInContext(ctx, NOT_IMPLEMENTED);
		} else if (uri.startsWith(ST_PATH)) {
			String subtenant;
			if (method.equals(WSRequestConfig.METHOD_PUT)) {
				subtenant = generateSubtenant();
			} else {
				subtenant = getSubtenant(request.headers(), uri);
			}
			handleGenericContainerReq(method, subtenant, ctx);
		} else {
			setHttpResponseStatusInContext(ctx, BAD_REQUEST);
		}
		if (ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).get()) {
			HttpResponseStatus status = ctx.attr(AttributeKey.<HttpResponseStatus>valueOf(responseStatusKey)).get();
			response.setStatus(status != null ? status : OK);
			HttpHeaders.setContentLength(response, 0);
			ctx.write(response);
		}
	}

	private final static DocumentBuilder DOM_BUILDER;
	private final static TransformerFactory TF = TransformerFactory.newInstance();

	static {
		try {
			DOM_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void handleContainerList(String subtenant, ChannelHandlerContext ctx) {
	}

	private void handleContainerList(String subtenant, String objId, ChannelHandlerContext ctx) {
		int maxCount = DataItemContainer.DEFAULT_PAGE_SIZE;
		HttpHeaders headers = ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey)).get().headers();
		if (headers.contains(WSRequestConfig.KEY_EMC_LIMIT)) {
			try {
				maxCount = Integer.parseInt(headers.get(WSRequestConfig.KEY_EMC_LIMIT));
			} catch (NumberFormatException e) {
				LOG.warn(
						Markers.ERR, "Limit header value is not a valid integer: {}",
						headers.get(WSRequestConfig.KEY_EMC_LIMIT)
				);
			}
		}

		List<T> buff = new ArrayList<>(maxCount);
		T lastObj;
		try {
			lastObj = sharedStorage.listObjects(subtenant, objId, buff, maxCount);
			if (LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
						Markers.MSG, "Bucket \"{}\": generated list of {} objects, last one is \"{}\"",
						subtenant, buff.size(), lastObj
				);
			}
		} catch (ContainerMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			return;
		} catch (ContainerMockException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.WARN, e, "Subtenant \"{}\" failure", subtenant);
			return;
		}

		Map.Entry<String, String> header = null;
		if (lastObj != null) {
			header = new AbstractMap.SimpleEntry<>(WSRequestConfig.KEY_EMC_TOKEN, lastObj.getName());
		}
		Document doc = DOM_BUILDER.newDocument();
		Element eRoot = doc.createElement("ListObjectsResponse");
		doc.appendChild(eRoot);
		//
		Element e, ee;
		for (final T dataObject : buff) {
			e = doc.createElement("Object");
			ee = doc.createElement("ObjectID");
			ee.appendChild(doc.createTextNode(dataObject.getName()));
			e.appendChild(ee);
			eRoot.appendChild(e);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		StreamResult r = new StreamResult(bos);
		try {
			TF.newTransformer().transform(new DOMSource(doc), r);
		} catch (final TransformerException ex) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.ERROR, ex, "Failed to build subtenant XML listing");
			return;
		}
		byte[] content = bos.toByteArray();
		ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).set(false);
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(content));
		response.headers().set(CONTENT_TYPE, ContentType.APPLICATION_XML.getMimeType());
		if (header != null) {
			response.headers().set(header.getKey(), header.getValue());
		}
		HttpHeaders.setContentLength(response, content.length);
		ctx.write(response);
	}

	private String getSubtenant(HttpHeaders headers, String uri) {
		if (uri.startsWith(STS_PATH) && uri.length() > STS_PATH.length()) {
			return uri.substring(STS_PATH.length());
		}
		if (headers.contains(WSRequestConfig.KEY_EMC_UID)) {
			String uid = headers.get(WSRequestConfig.KEY_EMC_UID);
			if (uid.contains("/")) {
				return uid.split("/")[0];
			}
		}
		if (headers.contains(SubTenant.KEY_SUBTENANT_ID)) {
			return headers.get(SubTenant.KEY_SUBTENANT_ID);
		}
		return null;
	}

	private String generateId() {
		final byte buff[] = new byte[22];
		ThreadLocalRandom.current().nextBytes(buff);
		return Hex.encodeHexString(buff);
	}

	public static String generateSubtenant() {
		final byte buff[] = new byte[0x10];
		ThreadLocalRandom.current().nextBytes(buff);
		return Hex.encodeHexString(buff);
	}
}
