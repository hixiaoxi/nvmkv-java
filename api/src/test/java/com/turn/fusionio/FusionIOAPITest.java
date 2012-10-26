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
package com.turn.fusionio;

import java.nio.ByteBuffer;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for the low-level FusionIO API wrapping.
 *
 * <p>
 * These tests are marked as broken by default because they need FusionIO
 * hardware installed in the machine that runs them.
 * </p>
 *
 * @author mpetazzoni
 */
@Test
public class FusionIOAPITest {

	private static final String DEVICE_NAME = "/dev/fct0";
	private static final int POOL_ID = 0;
	private static final int BATCH_SIZE = 10;
	private static final int BIG_VALUE_SIZE = 4096;

	private static final Key[] TEST_KEYS = new Key[BATCH_SIZE];
	private static final Value[] TEST_VALUES = new Value[BATCH_SIZE];
	private static final Value[] TEST_READBACK = new Value[BATCH_SIZE];

	// "hello, world"
	private static final byte[] TEST_DATA = new byte[] {
		0x68, 0x65, 0x6c, 0x6c, 0x6f,
		0x2c, 0x20,
		0x77, 0x6f, 0x72, 0x6c, 0x64,
		0x00,
	};

	private FusionIOAPI api = null;

	@BeforeClass
	public void setUp() throws FusionIOException {
		this.api = new FusionIOAPI(DEVICE_NAME, POOL_ID);
		for (int i=0; i<BATCH_SIZE; i++) {
			TEST_KEYS[i] = Key.get(i);
			TEST_VALUES[i] = Value.get(TEST_DATA.length);
			TEST_VALUES[i].getByteBuffer().put(TEST_DATA);
			TEST_VALUES[i].getByteBuffer().rewind();
			TEST_READBACK[i] = Value.get(TEST_DATA.length);
		}
	}

	@BeforeMethod
	public void clean() throws FusionIOException {
		this.api.remove(TEST_KEYS);
		for (int i=0; i<BATCH_SIZE; i++) {
			TEST_READBACK[i]
				.free()
				.allocate(TEST_DATA.length);
		}
	}

	@AfterClass
	public void tearDown() throws FusionIOException {
		for (int i=0; i<BATCH_SIZE; i++) {
			TEST_VALUES[i].free();
			TEST_READBACK[i].free();
		}
		this.api.close();
	}

	public void testOpenClose() throws FusionIOException {
		this.api.open();
		assert this.api.isOpened()
			: "FusionIO API was not opened as expected";
		this.api.close();
		assert !this.api.isOpened()
			: "FusionIO API should be closed now";
	}

	public void testGet() throws FusionIOException {
		this.api.put(TEST_KEYS[0], TEST_VALUES[0]);
		assert this.api.exists(TEST_KEYS[0])
			: "Key/value mapping should exist";
		this.api.get(TEST_KEYS[0], TEST_READBACK[0]);
		Assert.assertEquals(
			TEST_READBACK[0].getByteBuffer(),
			TEST_VALUES[0].getByteBuffer());
	}

	public void testPut() throws FusionIOException {
		this.api.put(TEST_KEYS[0], TEST_VALUES[0]);
		assert this.api.exists(TEST_KEYS[0])
			: "Key/value mapping should exist";
	}

	public void testRemove() throws FusionIOException {
		this.api.put(TEST_KEYS[0], TEST_VALUES[0]);
		assert this.api.exists(TEST_KEYS[0])
			: "Key/value mapping should exist";

		this.api.remove(TEST_KEYS[0]);
		assert !this.api.exists(TEST_KEYS[0])
			: "Key/value mapping should have been removed";
	}

	public void testBigValue() throws FusionIOException {
		Value value = Value.get(BIG_VALUE_SIZE);
		Value readback = Value.get(BIG_VALUE_SIZE);
		ByteBuffer data = value.getByteBuffer();
		while (data.remaining() > 0) {
			data.put((byte) 0x42);
		}

		this.api.put(TEST_KEYS[0], value);
		assert this.api.exists(TEST_KEYS[0])
			: "Key/value mapping should exist";
		this.api.get(TEST_KEYS[0], readback);

		data.rewind();
		Assert.assertEquals(readback.getByteBuffer(), data);
	}

	public void testBatchPut() throws FusionIOException {
		this.api.put(TEST_KEYS, TEST_VALUES);

		for (int i=0; i<BATCH_SIZE; i++) {
			this.api.get(TEST_KEYS[i], TEST_READBACK[i]);
			Assert.assertEquals(
				TEST_READBACK[i].getByteBuffer(),
				TEST_VALUES[i].getByteBuffer(),
				"Value #" + i + "'s data does not match!");
		}
	}

	public void testBatchRemove() throws FusionIOException {
		this.api.put(TEST_KEYS, TEST_VALUES);
		for (int i=0; i<BATCH_SIZE; i++) {
			assert this.api.exists(TEST_KEYS[i]);
		}

		this.api.remove(TEST_KEYS);
		for (int i=0; i<BATCH_SIZE; i++) {
			assert !this.api.exists(TEST_KEYS[i]);
		}
	}

	public void testIteration() throws FusionIOException {
		this.api.put(TEST_KEYS, TEST_VALUES);

		int count = 0;
		for (Map.Entry<Key, Value> pair : this.api) {
			assert count < TEST_VALUES.length;
			Assert.assertEquals(
				pair.getValue().getByteBuffer(),
				TEST_VALUES[(int)pair.getKey().longValue()].getByteBuffer(),
				"Value data mismatch for key " + count + "!");
			count++;
		}
		assert count == TEST_VALUES.length;
	}
}
