package com.emc.mongoose.core.impl.data.util;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.util.DataItemInput;
//
import java.io.IOException;
import java.io.ObjectInputStream;
/**
 The data item input implementation deserializing the data items from the specified stream
 */
public class ExternItemInput<T extends DataItem>
implements DataItemInput<T> {
	//
	protected final ObjectInputStream itemsSrc;
	//
	public ExternItemInput(final ObjectInputStream itemsSrc) {
		this.itemsSrc = itemsSrc;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public T read()
	throws IOException {
		try {
			return (T) itemsSrc.readUnshared();
		} catch(final ClassNotFoundException e) {
			throw new IOException(e);
		}
	}
	//
	@Override
	public void reset()
	throws IOException {
		itemsSrc.reset();
	}
	//
	@Override
	public void close()
	throws IOException {
		itemsSrc.close();
	}
}
