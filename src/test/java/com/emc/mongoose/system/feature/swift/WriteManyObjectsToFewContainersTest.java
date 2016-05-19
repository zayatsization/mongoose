package com.emc.mongoose.system.feature.swift;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.log.appenders.RunIdFileManager;
import com.emc.mongoose.system.base.WSMockTestBase;
import com.emc.mongoose.system.tools.StdOutUtil;
import com.emc.mongoose.system.tools.BufferingOutputStream;
import com.emc.mongoose.system.tools.LogValidator;
import com.emc.mongoose.system.tools.TestConstants;
import com.emc.mongoose.run.scenario.runner.ScenarioRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 Created by andrey on 23.10.15.
 */
public class WriteManyObjectsToFewContainersTest
extends WSMockTestBase {
	//
	private static BufferingOutputStream STD_OUTPUT_STREAM;
	private static final int
		LIMIT_COUNT_OBJ = 2000,
		LIMIT_COUNT_CONTAINER = 50;
	//
	private static String RUN_ID_BASE = WriteManyObjectsToFewContainersTest.class.getCanonicalName();
	private static int countContainerCreated = 0;
	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		System.setProperty(AppConfig.KEY_RUN_ID, RUN_ID_BASE);
		System.setProperty(AppConfig.KEY_ITEM_CLASS, "container");
		System.setProperty(AppConfig.KEY_STORAGE_MOCK_CONTAINER_CAPACITY, Integer.toString(LIMIT_COUNT_OBJ));
		System.setProperty(AppConfig.KEY_STORAGE_MOCK_CONTAINER_COUNT_LIMIT, Integer.toString(LIMIT_COUNT_CONTAINER));
		System.setProperty(AppConfig.KEY_DATA_SIZE, "1KB");
		WSMockTestBase.setUpClass();
		final RunTimeConfig rtConfig = RunTimeConfig.getContext();
		rtConfig.setProperty(AppConfig.KEY_API_NAME, "swift");
		rtConfig.set(AppConfig.KEY_LOAD_LIMIT_COUNT, Integer.toString(LIMIT_COUNT_CONTAINER));
		rtConfig.set(AppConfig.KEY_SCENARIO_SINGLE_LOAD, TestConstants.LOAD_CREATE);
		rtConfig.set(AppConfig.KEY_CREATE_CONNS, "25");
		RunTimeConfig.setContext(rtConfig);
		//
		final Logger logger = LogManager.getLogger();
		logger.info(Markers.MSG, RunTimeConfig.getContext().toString());
		//
		new ScenarioRunner().run();
		TimeUnit.SECONDS.sleep(1);
		//
		RunIdFileManager.flushAll();
		//
		final File containerListFile = LogValidator.getItemsListFile(RUN_ID_BASE);
		Assert.assertTrue("items list file doesn't exist", containerListFile.exists());
		//
		String nextContainer, nextRunId;
		rtConfig.set(AppConfig.KEY_ITEM_CLASS, "data");
		rtConfig.set(AppConfig.KEY_LOAD_LIMIT_COUNT, Integer.toString(LIMIT_COUNT_OBJ));
		RunTimeConfig.setContext(rtConfig);
		try(
			final BufferedReader
				in = Files.newBufferedReader(containerListFile.toPath(), StandardCharsets.UTF_8)
		) {
			try(
				final BufferingOutputStream
					stdOutStream = StdOutUtil.getStdOutBufferingStream()
			) {
				do {
					nextContainer = in.readLine();
					nextRunId = RUN_ID_BASE + "_" + nextContainer;
					if(nextContainer == null) {
						break;
					} else {
						countContainerCreated ++;
						rtConfig.set(AppConfig.KEY_RUN_ID, nextRunId);
						rtConfig.set(AppConfig.KEY_API_SWIFT_CONTAINER, nextContainer);
						RunTimeConfig.setContext(rtConfig);
						new ScenarioRunner().run();
						TimeUnit.SECONDS.sleep(1);
						RunIdFileManager.closeAll(nextRunId);
					}
				} while(true);
				TimeUnit.SECONDS.sleep(1);
				STD_OUTPUT_STREAM = stdOutStream;
			}
		}
		//
		RunIdFileManager.flushAll();
	}
	//
	@AfterClass
	public  static void tearDownClass()
	throws Exception {
		WSMockTestBase.tearDownClass();
		System.setProperty(AppConfig.KEY_STORAGE_MOCK_CONTAINER_CAPACITY, "1000000");
		System.setProperty(AppConfig.KEY_STORAGE_MOCK_CONTAINER_COUNT_LIMIT, "1000000");
	}
	//
	@Test
	public final void checkCreatedContainerCount()
	throws Exception {
		Assert.assertEquals(LIMIT_COUNT_CONTAINER, countContainerCreated);
	}
	//
	@Test
	public final void checkThatAllContainersAlreadyWereExisting() {
		final String consoleOutput = STD_OUTPUT_STREAM.toString();
		final Pattern p = Pattern.compile("Container \"[a-z0-9]+\" already exists");
		final Matcher m = p.matcher(consoleOutput);
		int countMatch = 0;
		while(m.find()) {
			countMatch ++;
		}
		Assert.assertEquals(LIMIT_COUNT_CONTAINER, countMatch);
	}
}
