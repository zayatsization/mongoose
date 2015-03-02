from __future__ import print_function, absolute_import, with_statement
#
from loadbuilder import loadbuilder_init
loadbuilder_init()
#
import chain
#
from java.lang import InterruptedException, Long, Short, Throwable, NumberFormatException
#
from org.apache.logging.log4j import Level, LogManager
#
from com.emc.mongoose.run import Main
from com.emc.mongoose.util.conf import RunTimeConfig
from com.emc.mongoose.util.logging import TraceLogger, Markers
from com.emc.mongoose.run import ThreadContextMap
#
LOG = LogManager.getLogger()
LOCAL_RUN_TIME_CONFIG = Main.RUN_TIME_CONFIG.get()
#
listSizes = LOCAL_RUN_TIME_CONFIG.getStringArray("scenario.rampup.sizes")
listThreadCounts = LOCAL_RUN_TIME_CONFIG.getStringArray("scenario.rampup.thread.counts")
#
LOG.debug(Markers.MSG, "Setting the metric update period to zero for chain scenario")
LOCAL_RUN_TIME_CONFIG.set("run.metrics.period.sec", 0)
#
if __name__=="__builtin__":
	LOG.info(Markers.MSG, "Data sizes: {}", listSizes)
	LOG.info(Markers.MSG, "Thread counts: {}", listThreadCounts)
	for index, dataItemSizeStr in enumerate(listSizes):
		try:
			dataItemSize = Long(RunTimeConfig.toSize(dataItemSizeStr))
			for threadCountStr in listThreadCounts:
				try:
					threadCount = Short.valueOf(threadCountStr)
				except NumberFormatException as e:
					LOG.error(Markers.ERR, "")
				try:
					LOG.info(Markers.PERF_SUM, "---- Step {}x{} start ----", threadCount, dataItemSizeStr)
					ThreadContextMap.putValue("currentSize", dataItemSizeStr + "-" + str(index))
					ThreadContextMap.putValue("currentThreadCount", str(threadCount))
					nextChain = chain.build(
						False, True, dataItemSize, dataItemSize, threadCount
					)
					chain.execute(nextChain, False)
					LOG.debug(Markers.MSG, "---- Step {}x{} finish ----", threadCount, dataItemSizeStr)
				except InterruptedException as e:
					raise e
				except Throwable as e:
					TraceLogger.failure(LOG, Level.ERROR, e, "Chain execution failure")
		except InterruptedException:
			break
		except Throwable as e:
			TraceLogger.failure(LOG, Level.ERROR, e, "Determining the next data item size failure")
	LOG.info(Markers.MSG, "Scenario end")
