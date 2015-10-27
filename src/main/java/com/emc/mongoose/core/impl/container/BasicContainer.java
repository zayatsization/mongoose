package com.emc.mongoose.core.impl.container;
//
import com.emc.mongoose.core.api.container.Container;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.content.ContentSource;
//
import com.emc.mongoose.core.impl.BasicItem;
/**
 Created by kurila on 20.10.15.
 */
public class BasicContainer<T extends DataItem>
extends BasicItem
implements Container<T> {
	//
	protected volatile String name;
	protected volatile ContentSource contentSrc;
	//
	public BasicContainer() {
		this.name = null;
	}
	//
	public BasicContainer(final String name) {
		this.name = name;
	}
	//
	public BasicContainer(final String name, final ContentSource contentSrc) {
		this.name = name;
		this.contentSrc = contentSrc;
	}
}
