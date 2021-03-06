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

/**
 * Low-level access API to a FusionIO device key/value store functionality.
 *
 * <p>
 * This class provides a convenient Java facade to FusionIO's key/value store
 * API. It makes uses of an intermediary helper library which is then mapped to
 * Java via JNI.
 * </p>
 *
 * @author mpetazzoni
 */
public class FusionIOAPI {

	/**
	 * The name of the helper library name.
	 *
	 * <p>
	 * This shared library needs to be in the library path of the executing
	 * JVM, either by using the <tt>LD_LIBRARY_PATH</tt> environment variable
	 * or by setting <tt>-Djava.library.path</tt>.
	 * </p>
	 */
	private static final String LIBRARY_NAME = "fio_kv_helper";

	static {
		try {
			System.loadLibrary(LIBRARY_NAME);
			fio_kv_init_jni_cache();
		} catch (Exception e) {
			System.err.println("Could not initialize FusionIO binding!");
			e.printStackTrace(System.err);
		}
	};

	/**
	 * Retrieve an instanciated FusionIO {@link Store} using the given FusionIO
	 * device or directFS file as the backing storage.
	 *
	 * @param path The FusionIO device file or directFS file path.
	 * @param version The user-defined FusionIO store version. Opening a
	 *	previously created store with a different version number will fail.
	 * @param expiryMode The key/value pair expiration policy.
	 * @param expiryTime Only used for {@link ExpiryMode.GLOBAL_EXPIRY},
	 *	defines the key/value pair TTL in seconds after insertion.
	 * @return Returns an initialized {@link Store} instance usable for native
	 *	FusionIO key/value store operations.
	 */
	public static Store get(String path, int version, ExpiryMode expiryMode,
			int expiryTime) {
		return new Store(path, version, expiryMode, expiryTime);
	}

	/**
	 * Expiry modes fore the FusionIO store.
	 *
	 * <p>
	 * This enum matches the nvm_kv_expiry_t enum from the directKV API.
	 * </p>
	 *
	 * @author mpetazzoni
	 */
	public enum ExpiryMode {
		NO_EXPIRY,
		ARBITRARY_EXPIRY,
		GLOBAL_EXPIRY;
	};

	static native void fio_kv_init_jni_cache();

	static native boolean fio_kv_open(Store store, int version,
		ExpiryMode expiryMode, int expiryTime);
	static native void fio_kv_close(Store store);
	static native StoreInfo fio_kv_get_store_info(Store store);

	static native Pool fio_kv_get_or_create_pool(Store store, String tag);
	static native Pool[] fio_kv_get_all_pools(Store store);
	static native boolean fio_kv_delete_pool(Pool pool);
	static native boolean fio_kv_delete_all_pools(Store store);

	static native ByteBuffer fio_kv_alloc(int length);
	static native void fio_kv_free_value(Value value);

	static native int fio_kv_get_value_len(Pool pool, Key key);
	static native KeyValueInfo fio_kv_get_key_info(Pool pool, Key key);

	static native int fio_kv_get(Pool pool, Key key, Value value);
	static native int fio_kv_put(Pool pool, Key key, Value value);
	static native boolean fio_kv_exists(Pool pool, Key key, KeyValueInfo info);
	static native boolean fio_kv_delete(Pool pool, Key key);
	static native boolean fio_kv_delete_all(Store store);

	static native boolean fio_kv_batch_put(Pool pool, Key[] keys, Value[] values);

	static native int fio_kv_iterator(Pool pool);
	static native boolean fio_kv_next(Pool pool, int iterator);
	static native boolean fio_kv_get_current(Pool pool, int iterator, Key key, Value value);
	static native boolean fio_kv_end_iteration(Pool pool, int iterator);

	static native int fio_kv_get_last_error();
}
