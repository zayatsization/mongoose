package com.emc.mongoose.tests.system;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.tests.system.base.FileStorageDistributedScenarioTestBase;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import static com.emc.mongoose.common.Constants.KEY_JOB_NAME;
import static com.emc.mongoose.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 Created by andrey on 04.03.17.
 Covered use cases:
 Covered use cases:
 * 2.1.1.1.2. Small Data Items (4KB)
 * 2.2.1. Items Input File
 * 2.3.2. Items Output File
 * 2.3.3.1. Constant Items Destination Path
 * 4.2. Small Concurrency Level (2-10)
 * 6.1. Load Jobs Naming
 * 6.2.2. Limit Load Job by Processed Item Count
 * 6.2.6. Limit Load Job by End of Items Input
 * 8.2.1. Create New Items
 * 8.2.2. Copy Mode
 * 9.3. Custom Scenario File
 * 9.4.1. Override Default Configuration in the Scenario
 * 9.4.2. Job Configuration Inheritance
 * 9.4.3. Reusing The Items in the Scenario
 * 9.5.2. Load Job
 * 9.5.3. Precondition Load Job
 * 9.5.5. Sequential Job
 * 10.1.2. Many Local Separate Storage Driver Services (at different ports)
 * 10.2.2. Destination Path Precondition Hook
 * 10.3. Filesystem Storage Driver
 */
public class CopyFilesUsingItemsInputFileTest
extends FileStorageDistributedScenarioTestBase {

	private static final Path SCENARIO_PATH = Paths.get(
		getBaseDir(), DIR_SCENARIO, "copy", "file.json"
	);
	private static final SizeInBytes EXPECTED_ITEM_DATA_SIZE = new SizeInBytes("4KB");
	private static final int EXPECTED_CONCURRENCY = 10;
	private static final long EXPECTED_COUNT = 1000;
	private static final String ITEM_INPUT_FILE = "files2copy.csv";
	private static final String ITEM_INPUT_PATH = "/tmp/src-dir";
	private static final String ITEM_OUTPUT_PATH = "/tmp/dst-dir";

	private static String STD_OUTPUT;
	private static boolean FINISHED_IN_TIME;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		JOB_NAME = CopyFilesUsingItemsInputFileTest.class.getSimpleName();
		try {
			Files.delete(Paths.get(ITEM_INPUT_FILE));
		} catch(final Exception ignored) {
		}
		try {
			FileUtils.deleteDirectory(new File(ITEM_INPUT_PATH));
		} catch(final Exception ignored) {
		}
		try {
			FileUtils.deleteDirectory(new File(ITEM_OUTPUT_PATH));
		} catch(final Exception ignored) {
		}
		ThreadContext.put(KEY_JOB_NAME, JOB_NAME);
		CONFIG_ARGS.add("--test-scenario-file=" + SCENARIO_PATH.toString());
		FileStorageDistributedScenarioTestBase.setUpClass();
		final Thread runner = new Thread(
			() -> {
				try {
					STD_OUT_STREAM.startRecording();
					SCENARIO.run();
					STD_OUTPUT = STD_OUT_STREAM.stopRecording();
				} catch(final Throwable t) {
					LogUtil.exception(LOG, Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		runner.start();
		TimeUnit.SECONDS.timedJoin(runner, 20);
		FINISHED_IN_TIME = !runner.isAlive();
		runner.interrupt();
		LoadJobLogFileManager.flush(JOB_NAME);
		TimeUnit.SECONDS.sleep(10);
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		FileStorageDistributedScenarioTestBase.tearDownClass();
	}

	@Test
	public final void testFinishedInTime() {
		assertTrue(FINISHED_IN_TIME);
	}

	@Test
	public void testMetricsLogFile()
	throws Exception {
		final List<CSVRecord> metricsLogRecords = getMetricsLogRecords();
		assertTrue(
			"There should be more than 0 metrics records in the log file",
			metricsLogRecords.size() > 0
		);
		testMetricsLogRecords(
			metricsLogRecords, IoType.CREATE, EXPECTED_CONCURRENCY, STORAGE_DRIVERS_COUNT,
			EXPECTED_ITEM_DATA_SIZE,
			EXPECTED_COUNT, 0, CONFIG.getTestConfig().getStepConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test
	public void testTotalMetricsLogFile()
	throws Exception {
		final List<CSVRecord> totalMetrcisLogRecords = getMetricsTotalLogRecords();
		assertEquals(
			"There should be 1 total metrics records in the log file", 1,
			totalMetrcisLogRecords.size()
		);
		testTotalMetricsLogRecords(
			totalMetrcisLogRecords.get(0), IoType.CREATE, EXPECTED_CONCURRENCY, STORAGE_DRIVERS_COUNT,
			EXPECTED_ITEM_DATA_SIZE, EXPECTED_COUNT, 0
		);
	}

	@Test
	public void testMetricsStdout()
	throws Exception {
		testMetricsStdout(
			STD_OUTPUT.replaceAll("[\r\n]+", " "),
			IoType.CREATE, EXPECTED_CONCURRENCY, STORAGE_DRIVERS_COUNT, EXPECTED_ITEM_DATA_SIZE,
			CONFIG.getTestConfig().getStepConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test
	public void testIoTraceLogFile()
	throws Exception {
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		assertEquals(
			"There should be " + EXPECTED_COUNT + " records in the I/O trace log file",
			EXPECTED_COUNT, ioTraceRecords.size()
		);
		String nextItemPath, nextItemId;
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.CREATE.ordinal(), EXPECTED_ITEM_DATA_SIZE);
			nextItemPath = ioTraceRecord.get("ItemPath");
			Assert.assertTrue(
				"File \"" + nextItemPath + "\" doesn't exist", Files.exists(Paths.get(nextItemPath))
			);
			nextItemId = nextItemPath.substring(nextItemPath.lastIndexOf(File.separatorChar) + 1);
			Assert.assertTrue(
				"File \"" + ITEM_INPUT_PATH + File.separatorChar + nextItemId + "\" doesn't exist",
				Files.exists(Paths.get(ITEM_INPUT_PATH, nextItemId))
			);
		}
	}
}