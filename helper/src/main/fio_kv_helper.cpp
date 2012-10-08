/**
 * Copyright (C) 2012 Turn, Inc.  All Rights Reserved.
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
 * - Neither the name Turn, Inc. nor the names of its contributors may be used
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
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include "fio_kv_helper.h"

/**
 * This is a glue library over FusionIO's key/value store API. It provides an
 * easier to use API that manages the details of FusionIO's API usage like
 * memory alignment, device management, etc into an all-encompassing and more
 * symetric API.
 *
 * It can be compiled into a shared library with the following command:
 *
 *   $ gcc -ansi -pedantic -pedantic-errors -fPIC -shared -Werror -lrt -ldl
 *       -lvsldpexp -L/usr/lib/fio -o libfio_kv_helper.so fio_kv_helper.cpp
 *
 * Of course the FusionIO's development kit must be installed first.
 */

/**
 * Open a Fusion-IO device for key/value store access.
 *
 * The user executing this program needs read-write access to the given device.
 *
 * Args:
 *	 device (const char *): Path to the Fusion-IO device file.
 * Returns:
 *	 Returns a fio_kv_store_t structure containing the file descriptor
 *	 associated with the open device and the KV store ID used by the KV SDK
 *	 API.
 */
fio_kv_store_t *fio_kv_open(const char *device, int pool_id)
{
	fio_kv_store_t *store;

	assert(device != NULL);
	assert(pool_id >= 0);

	store	= (fio_kv_store_t *)calloc(1, sizeof(fio_kv_store_t));

	store->fd = open(device, O_RDWR);
	if (store->fd < 0) {
		free(store);
		return NULL;
	}

	store->kv = kv_create(store->fd, FIO_KV_API_VERSION, FIO_KV_MAX_POOLS, false);
	if (store->kv <= 0) {
		free(store);
		return NULL;
	}

	store->pool = pool_id;
	return store;
}

/**
 * Closes a key/value store.
 *
 * Closes and clears information in the fio_kv_store_t structure. It is the
 * responsibility of the caller to free to memory backing the structure.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store to close.
 */
void fio_kv_close(fio_kv_store_t *store)
{
	assert(store != NULL);

	// TODO: check return value of close(). See close(2).
	close(store->fd);
	store->fd = 0;
	store->kv = 0;
}

/**
 * Allocate sector-aligned memory to hold the given number of bytes.
 *
 * Args:
 *	 length (uint32_t): Size of the memory to allocate.
 * Returns:
 *	 A pointer to sector-aligned, allocated memory that can hold up to length
 *	 bytes.
 */
void *fio_kv_alloc(uint32_t length)
{
	void *p;

	posix_memalign(&p, FIO_SECTOR_ALIGNMENT,
			length + FIO_SECTOR_ALIGNMENT -
				(length % FIO_SECTOR_ALIGNMENT));

	return p;
}

/**
 * Frees the memory allocated for the 'data' field of a fio_kv_value_t
 * structure.
 *
 * It is still the responsibility of the caller to manage the memory of the
 * other fields of that structure, in particular the 'info' field.
 *
 * Args:
 *	 value (fio_kv_value_t *): The value structure to free.
 */
void fio_kv_free_value(fio_kv_value_t *value)
{
	assert(value != NULL);

	free(value->data);
	value->data = NULL;
}

/**
 * Retrieves the value associated with a given key in a key/value store.
 *
 * Note that you must provide sector-aligned memory that will can contain the
 * requested number of bytes. Such memory can be obtained by calling
 * fio_kv_alloc().
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store.
 *	 key (fio_kv_key_t *): The key.
 *	 value (fio_kv_value_t *): An allocated value structure to hold the
 *		 result.
 * Returns:
 *	 The number of bytes read, or -1 if the read failed.
 */
int fio_kv_get(fio_kv_store_t *store, fio_kv_key_t *key,
		fio_kv_value_t *value)
{
	int ret;

	assert(store != NULL);
	assert(key != NULL);
	assert(key->length >= 1 && key->length <= 128);
	assert(key->bytes != NULL);
	assert(value != NULL);
	assert(value->data != NULL);
	assert(value->info != NULL);

	ret = kv_get(store->kv, store->pool,
			key->bytes, key->length,
			value->data, value->info->value_len,
			value->info);
	if (ret < 0) {
		return ret;
	}

	return ret;
}

/**
 * Insert (or replace) a key/value pair into the store.
 *
 * Note that the value's data must be sector-aligned memory, which can be
 * obtained from the fio_kv_alloc() function.
 *
 * Args:
 *	 store (fio_kv_store_t *): A pointer to a opened KV store.
 *	 key (fio_kv_key_t): The key.
 *	 value (fio_kv_value_t): The value.
 * Returns:
 *	 The number of bytes written, or -1 in case of an error.
 */
int fio_kv_put(fio_kv_store_t *store, fio_kv_key_t *key,
		fio_kv_value_t *value)
{
	assert(store != NULL);
	assert(key != NULL);
	assert(key->length >= 1 && key->length <= 128);
	assert(key->bytes != NULL);
	assert(value != NULL);
	assert(value->data != NULL);
	assert(value->info != NULL);

	return kv_put(store->kv, store->pool,
			key->bytes, key->length,
			value->data, value->info->value_len,
			value->info->expiry, true, 0);
}

/**
 * Tells whether a specific key exists in a key/value store.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store.
 *	 key (fio_kv_key_t *): The key of the mapping to check the existence of.
 * Returns:
 *	 Returns true if a mapping for the given key exists in the key/value store.
 */
bool fio_kv_exists(fio_kv_store_t *store, fio_kv_key_t *key)
{
	assert(store != NULL);
	assert(key != NULL);
	assert(key->length >= 1 && key->length <= 128);
	assert(key->bytes != NULL);

	return kv_exists(store->kv, store->pool, key->bytes, key->length);
}

/**
 * Removes a key/value pair from the key/value store.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store.
 *	 key (fio_kv_key_t *): The key to the mapping to remove.
 * Returns:
 *	 Returns true if the mapping was successfuly removed, false otherwise.
 */
bool fio_kv_delete(fio_kv_store_t *store, fio_kv_key_t *key)
{
	assert(store != NULL);
	assert(key != NULL);
	assert(key->length >= 1 && key->length <= 128);
	assert(key->bytes != NULL);

	return kv_delete(store->kv, store->pool, key->bytes, key->length) == 0;
}

/**
 * Returns the last errno value.
 *
 * This method is obviously absolutely not thread-safe and cannot guarantee
 * that the returned value matches the last error.
 *
 * Returns:
 *	 Returns the last errno value.
 */
int fio_kv_get_last_error(void)
{
	return errno;
}