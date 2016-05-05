package com.emc.mongoose.common.log.appenders;

import org.junit.Before;
import org.junit.Test;

import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created on 05.05.16.
 */
public class CircularArrayTest {

	private static final Random RANDOM = new Random();

	private static class FakeLogEvent {

		private final long time;

		FakeLogEvent(final long time) {
			this.time = time;
		}

		static class FleComparator implements Comparator<FakeLogEvent> {

			@Override
			public int compare(FakeLogEvent fle1, FakeLogEvent fle2) {
				return Long.compare(fle1.time, fle2.time);
			}

		}

		public long time() {
			return time;
		}

		@Override
		public String toString() {
			return String.valueOf(time());
		}
	}

	private CircularArray<FakeLogEvent> circularArray;

	@Before
	public void init() {
		circularArray = new CircularArray<>(100, new FakeLogEvent.FleComparator());
	}

	@Test
	public void shouldSearchItem() throws Exception {
		final int countLimit = 10;
		final int indexToCheck = RANDOM.nextInt(countLimit);
		FakeLogEvent fleToCheck = null;
		for (int i = 0; i < countLimit; i++) {
			FakeLogEvent tempFle = new FakeLogEvent(System.currentTimeMillis());
			circularArray.addItem(tempFle);
			TimeUnit.MILLISECONDS.sleep(100);
			if (i == indexToCheck) {
				fleToCheck = tempFle;
			}
		}
		assertEquals(indexToCheck, circularArray.searchItem(fleToCheck));
	}

	@Test
	public void shouldAddItemCircularly() throws Exception {
		final int countLimit = 400;
		for (int i = 0; i < countLimit; i++) {
			FakeLogEvent tempFle = new FakeLogEvent(System.nanoTime());
			circularArray.addItem(tempFle);
		}
		System.out.println(circularArray);
	}
}