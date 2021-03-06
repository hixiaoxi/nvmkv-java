/**
 * Copyright (C) 2012-2013 Turn Inc.  All Rights Reserved.
 * Proprietary and confidential.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name Turn Inc. nor the names of its contributors may be used
 *   to endorse or promote products derived from this software without specific
 *   prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.turn.fusionio;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * An iterator over the key/value pairs in a FusionIO store.
 *
 * @author mpetazzoni
 */
public class FusionIOStoreIterator implements Iterator<Map.Entry<Key, Value>> {

	private final Pool pool;
	private final int iterator;

	private final KeyValuePair allocated;
	private KeyValuePair next;
	private boolean first;

	/**
	 * Instantiate a new iterator on the given key/value store.
	 *
	 * @param pool The key/value store pool to iterate over.
	 * @throws FusionIOException If the native store iterator could not be
	 *	obtained.
	 */
	public FusionIOStoreIterator(Pool pool) throws FusionIOException {
		this.pool = pool;
		this.iterator = FusionIOAPI.fio_kv_iterator(this.pool);
		if (this.iterator < 0) {
			throw new FusionIOException("Could not create iterator on " +
				this.pool + "!", FusionIOAPI.fio_kv_get_last_error());
		}

		this.allocated = new KeyValuePair(
			Key.get(Key.FUSION_IO_MAX_KEY_SIZE),
			Value.get(Value.FUSION_IO_MAX_VALUE_SIZE));
		this.next = null;
		this.first = true;
	}

	@Override
	protected final void finalize() throws Throwable {
		this.allocated.getValue().free();
		FusionIOAPI.fio_kv_end_iteration(this.pool, this.iterator);
		super.finalize();
	}

	@Override
	public boolean hasNext() {
		this.next = this.advance();
		return this.next != null;
	}

	@Override
	public Map.Entry<Key, Value> next() {
		if (this.next == null) {
			this.next = this.advance();
		}

		if (this.next == null) {
			throw new NoSuchElementException();
		}

		KeyValuePair next = this.next;
		this.next = null;
		return next;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	private KeyValuePair advance() {
		if (!this.first && !FusionIOAPI.fio_kv_next(this.pool, this.iterator)) {
			return null;
		}

		this.first = false;

		if (!FusionIOAPI.fio_kv_get_current(this.pool, this.iterator,
				allocated.getKey(), allocated.getValue())) {
			return null;
		}

		ByteBuffer data = allocated.getValue().getByteBuffer();
		data.rewind();
		data.limit(allocated.getValue().size());
		return allocated;
	}
}
