#!/usr/bin/env python
from __future__ import print_function, absolute_import, with_statement
from sys import exit
#
from loadbuilder import INSTANCE as loadBuilder
#
from com.emc.mongoose.base.api import Request
from com.emc.mongoose.util.conf import RunTimeConfig
from com.emc.mongoose.util.logging import Markers
#
from org.apache.logging.log4j import Level, LogManager
#
from java.lang import IllegalArgumentException
from java.util import NoSuchElementException
#
LOG = LogManager.getLogger()
#
try:
	loadType = Request.Type.valueOf(RunTimeConfig.getString("scenario.single.load").upper())
	LOG.debug(Markers.MSG, "Using load type: {}", loadType.name())
	loadBuilder.setLoadType(loadType)
except NoSuchElementException:
	LOG.error(Markers.ERR, "No load type specified, try arg -Dscenario.single.load=<VALUE> to override")
except IllegalArgumentException:
	LOG.error(Markers.ERR, "No such load type, it should be a constant from Load.Type enumeration")
	exit()
#
from java.lang import Exception
from com.emc.mongoose.util.logging import ExceptionHandler
load = None
try:
	load = loadBuilder.build()
except Exception as e:
	ExceptionHandler.trace(LOG, Level.FATAL, e, "Failed to instantiate the load executor")
#
from java.lang import Integer
from java.util.concurrent import TimeUnit
timeOut = None  # tuple of (value, unit)
try:
	timeOut = RunTimeConfig.getString("run.time")
	timeOut = timeOut.split('.')
	timeOut = Integer.valueOf(timeOut[0]), TimeUnit.valueOf(timeOut[1].upper())
	LOG.debug(Markers.MSG, "Using time limit: {} {}", timeOut[0], timeOut[1].name().lower())
except NoSuchElementException:
	LOG.error(Markers.ERR, "No timeout specified, try arg -Drun.time=<INTEGER>.<UNIT> to override")
except IllegalArgumentException:
	LOG.error(Markers.ERR, "Timeout unit should be a name of a constant from TimeUnit enumeration")
	exit()
except IndexError:
	LOG.error(Markers.ERR, "Time unit should be specified with timeout value (following after \".\" separator)")
	exit()
#
load.start()
load.join(timeOut[1].toMillis(timeOut[0]))
load.close()
LOG.info(Markers.MSG, "Scenario end")
