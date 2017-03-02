package com.emc.mongoose.storage.driver.builder;

import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.storage.StorageDriver;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig;

import java.rmi.RemoteException;
import java.util.regex.Pattern;

/**
 Created by andrey on 05.10.16.
 */
public interface StorageDriverBuilder<
	I extends Item, O extends IoTask<I>, T extends StorageDriver<I, O>
> {

	String RES_PREFIX = "META-INF/services/";
	String PKG_PREFIX = "pkgPrefix";
	String STORAGE_DRIVER_TYPE = "storageDriverType";
	Pattern PATTERN_IMPL_FQCN = Pattern.compile(
		"(?<" + PKG_PREFIX + ">[a-zA-Z_][\\\\.\\w]*\\.)?mongoose\\.storage\\.driver\\.(?<" +
			STORAGE_DRIVER_TYPE + ">[a-zA-Z_][\\\\.\\w]*)"
	);

	ItemConfig getItemConfig()
	throws RemoteException;

	LoadConfig getLoadConfig()
	throws RemoteException;

	StorageConfig getStorageConfig()
	throws RemoteException;

	StorageDriverBuilder<I, O, T> setJobName(final String runId)
	throws RemoteException;

	StorageDriverBuilder<I, O, T> setItemConfig(final ItemConfig itemConfig)
	throws RemoteException;

	StorageDriverBuilder<I, O, T> setLoadConfig(final LoadConfig loadConfig)
	throws RemoteException;

	StorageDriverBuilder<I, O, T> setStorageConfig(final StorageConfig storageConfig)
	throws RemoteException;

	T build()
	throws RemoteException, UserShootHisFootException;
}
