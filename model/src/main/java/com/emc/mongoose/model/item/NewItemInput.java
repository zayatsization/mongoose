package com.emc.mongoose.model.item;

import com.emc.mongoose.common.io.Input;

import java.io.IOException;
import java.util.List;
/**
 Created by andrey on 01.12.16.
 */
public class NewItemInput<I extends Item>
implements Input<I> {

	protected final ItemFactory<I> itemFactory;
	protected final IdStringInput idInput;

	public NewItemInput(final ItemFactory<I> itemFactory, final IdStringInput idInput) {
		this.itemFactory = itemFactory;
		this.idInput = idInput;
	}

	@Override
	public I get()
	throws IOException {
		return itemFactory.getItem(idInput.get());
	}

	@Override
	public int get(final List<I> buffer, final int maxCount)
	throws IOException {
		for(int i = 0; i < maxCount; i ++) {
			buffer.add(itemFactory.getItem(idInput.get()));
		}
		return maxCount;
	}

	/**
	 * Does nothing
	 * @param itemsCount count of items which should be skipped from the beginning
	 * @throws IOException doesn't throw
	 */
	@Override
	public final long skip(final long itemsCount)
	throws IOException {
		return idInput.skip(itemsCount);
	}

	@Override
	public final void reset() {
	}

	@Override
	public final void close()
	throws IOException {
		itemFactory.close();
		idInput.close();
	}

	@Override
	public String toString() {
		return "newItemInput<" + itemFactory.getClass().getSimpleName() + ">";
	}
}