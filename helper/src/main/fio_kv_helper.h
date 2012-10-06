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
#ifndef __FIO_KV_HELPER_H__
#define __FIO_KV_HELPER_H__

#include <vsl_dp_experimental/kv.h>

#define FIO_KV_API_VERSION          1
#define FIO_KV_MAX_POOLS            1024

#define FIO_SECTOR_ALIGNMENT        512

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
	int fd;
	int64_t kv;
	int pool;
} fio_kv_store_t;

typedef struct {
	int length;
	kv_key_t *bytes;
} fio_kv_key_t;

typedef struct {
	void *data;
	kv_key_info_t *info;
} fio_kv_value_t;


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
fio_kv_store_t *fio_kv_open(const char *device, int pool_id);

/**
 * Closes a key/value store.
 *
 * Closes and clears information in the fio_kv_store_t structure. It is the
 * responsibility of the caller to free to memory backing the structure.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store to close.
 */
void fio_kv_close(fio_kv_store_t *store);

/**
 * Allocate sector-aligned memory to hold the given number of bytes.
 *
 * Args:
 *	 length (uint32_t): Size of the memory to allocate.
 * Returns:
 *	 A pointer to sector-aligned, allocated memory that can hold up to length
 *	 bytes.
 */
void *fio_kv_alloc(uint32_t length);

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
void fio_kv_free_value(fio_kv_value_t *value);

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
		fio_kv_value_t *value);

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
		fio_kv_value_t *value);

/**
 * Tells whether a specific key exists in a key/value store.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store.
 *	 key (fio_kv_key_t *): The key of the mapping to check the existence of.
 * Returns:
 *	 Returns true if a mapping for the given key exists in the key/value store.
 */
bool fio_kv_exists(fio_kv_store_t *store, fio_kv_key_t *key);

/**
 * Removes a key/value pair from the key/value store.
 *
 * Args:
 *	 store (fio_kv_store_t *): The key/value store.
 *	 key (fio_kv_key_t *): The key to the mapping to remove.
 * Returns:
 *	 Returns true if the mapping was successfuly removed, false otherwise.
 */
bool fio_kv_delete(fio_kv_store_t *store, fio_kv_key_t *key);

/**
 * Returns the last errno value.
 *
 * This method is obviously absolutely not thread-safe and cannot guarantee
 * that the returned value matches the last error.
 *
 * Returns:
 *	 Returns the last errno value.
 */
int fio_kv_get_last_error(void);

#ifdef __cplusplus
}
#endif

#endif /* __FIO_KV_HELPER_H__ */
