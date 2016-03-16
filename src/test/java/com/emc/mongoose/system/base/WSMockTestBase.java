package com.emc.mongoose.system.base;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.impl.item.data.ContentSourceBase;
import com.emc.mongoose.storage.mock.api.StorageMock;
import com.emc.mongoose.storage.mock.api.HttpDataItemMock;
//
import com.emc.mongoose.storage.mock.impl.http.Nagaina;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.concurrent.TimeUnit;
/**
 Created by andrey on 13.08.15.
 */
public abstract class WSMockTestBase
extends LoggingTestBase {
	//
	private static StorageMock<HttpDataItemMock> WS_MOCK;
	private static Thread WS_MOCK_THREAD;
	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		LoggingTestBase.setUpClass();
		final AppConfig appConfig = BasicConfig.THREAD_CONTEXT.get();
		appConfig.setProperty(AppConfig.KEY_STORAGE_HTTP_API_TYPE, "s3"); // reset the ports beginning
		appConfig.setProperty(AppConfig.KEY_STORAGE_HTTP_MOCK_HEAD_COUNT, 5); // listen ports 9020..9024
		WS_MOCK = new Nagaina<>(appConfig);
		WS_MOCK_THREAD = new Thread(WS_MOCK, "wsMock");
		WS_MOCK_THREAD.setDaemon(true);
		WS_MOCK_THREAD.start();
		TimeUnit.SECONDS.sleep(1);
		LOG.info(Markers.MSG, "Nagaina started");
	}
	//
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		WS_MOCK_THREAD.interrupt();
		WS_MOCK.close();
		LOG.info(Markers.MSG, "Nagaina stopped");
		LoggingTestBase.tearDownClass();
		ContentSourceBase.DEFAULT = null; // reset the content source
	}
}