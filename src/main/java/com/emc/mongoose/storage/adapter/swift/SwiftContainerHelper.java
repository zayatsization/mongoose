package com.emc.mongoose.storage.adapter.swift;
//
//
import com.emc.mongoose.core.api.container.Container;
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.data.model.ContainerHelper;
/**
 Created by kurila on 02.03.15.
 */
public interface SwiftContainerHelper<T extends WSObject, C extends Container<T>>
extends ContainerHelper<T, C> {}