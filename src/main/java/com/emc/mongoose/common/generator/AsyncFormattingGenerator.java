package com.emc.mongoose.common.generator;

import java.text.ParseException;

public final class AsyncFormattingGenerator
extends AsyncValueGenerator<String>
implements ValueGenerator<String> {

	public AsyncFormattingGenerator(final String pattern)
	throws ParseException {
		this(new FormattingGenerator(pattern));
	}

	private AsyncFormattingGenerator(final FormattingGenerator innerGenerator)
	throws ParseException {
		super(
			null,
			new InitCallable<String>() {
				private final StringBuilder result = new StringBuilder();
				@Override
				public String call()
				throws Exception {
					result.setLength(0);
					return innerGenerator.format(result);
				}
				@Override
				public boolean isInitialized() {
					return true;
				}
			}
		);
	}

}
