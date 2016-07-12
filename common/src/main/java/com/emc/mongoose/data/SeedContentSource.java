package com.emc.mongoose.data;

import com.emc.mongoose.log.Markers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
/**
 Created by kurila on 23.07.14.
 A uniform data source for producing uniform data items.
 Implemented as finite buffer of pseudorandom bytes.
 */
public final class SeedContentSource
extends ContentSourceBase
implements ContentSource {
	//
	private final static Logger LOG = LogManager.getLogger();
	////////////////////////////////////////////////////////////////////////////////////////////////
	public SeedContentSource() {
		super();
	}
	//
	public SeedContentSource(final long seed, final long size) {
		super(ByteBuffer.allocateDirect((int) size));
		this.seed = seed;
		generateData(zeroByteLayer, seed);
		LOG.debug(Markers.MSG, "New ring buffer instance #{}", hashCode());
	}
	//
	public SeedContentSource(final SeedContentSource anotherContentSource) {
		super(anotherContentSource);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Human readable "serialization" implementation ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final String toString() {
		return Long.toHexString(seed) + ',' + Integer.toHexString(zeroByteLayer.capacity());
	}
	//
	public static SeedContentSource fromString(final String metaInfo)
		throws IllegalArgumentException, IOException {
		final String values[] = metaInfo.split(",");
		if(values.length == 2) {
			return new SeedContentSource(
				Long.parseLong(values[0], 0x10), Integer.parseInt(values[1], 0x10)
			);
		} else {
			throw new IllegalArgumentException();
		}
	}
}