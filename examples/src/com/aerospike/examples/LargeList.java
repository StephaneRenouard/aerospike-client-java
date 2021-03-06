/*
 * Copyright 2012-2017 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Language;
import com.aerospike.client.Value;
import com.aerospike.client.task.RegisterTask;

public class LargeList extends Example {

	public LargeList(Console console) {
		super(console);
	}

	/**
	 * Perform operations on a Large List within a single bin.
	 */
	@Override
	public void runExample(AerospikeClient client, Parameters params) throws Exception {
		if (! params.hasLargeDataTypes) {
			console.info("Large List functions are not supported by the connected Aerospike server.");
			return;
		}
		
		runSimpleExample(client, params);
		runFilterExample(client, params);
		runWithDistinctBins(client, params);
		runWithSerializedBin(client, params);
	}

	/**
	 * Simple examples of large list functionality.
	 */
	public void runSimpleExample(AerospikeClient client, Parameters params) throws Exception {
		Key key = new Key(params.namespace, params.set, "setkey");
		String binName = params.getBinName("ListBin");
		
		// Delete record if it already exists.
		client.delete(params.writePolicy, key);
		
		// Initialize large set operator.
		com.aerospike.client.large.LargeList llist = client.getLargeList(params.writePolicy, key, binName);
		String orig1 = "llistValue1";
		String orig2 = "llistValue2";
		String orig3 = "llistValue3";
						
		// Write values.
		llist.add(Value.get(orig1));
		llist.add(Value.get(orig2));
		llist.add(Value.get(orig3));
		
		// Verify large list was created with default configuration.
		Map<?,?> map = llist.getConfig();
		
		for (Entry<?,?> entry : map.entrySet()) {
			console.info(entry.getKey().toString() + ',' + entry.getValue());
		}
		
		// Perform a Range Query -- look for "llistValue2" to "llistValue3"
		List<?> rangeList = llist.range(Value.get(orig2), Value.get(orig3));
		
		if (rangeList == null) {			
			console.error("Range returned null.");
			return;
		}
		
		if (rangeList.size() != 2) {
			console.error("Range size mismatch. Expected 2 Received " + rangeList.size());
			return;
		}
		String v2 = (String) rangeList.get(0);
		String v3 = (String) rangeList.get(1);
		
		if ( v2.equals(orig2) && v3.equals(orig3) ) {
			console.info("Range query matched: v2=" + orig2 + " v3=" + orig3);
		} else {
			console.error("Range content mismatch. Expected (" + orig2 + ":" + orig3 + ") Received (" + v2 + ":" + v3 + ")"); 
			return;
		}

		// Remove last value.
		llist.remove(Value.get(orig3));
		
		int size = llist.size();
		
		if (size != 2) {
			console.error("Size mismatch. Expected 2 Received " + size);
			return;
		}
		
		List<?> listReceived = llist.find(Value.get(orig2));
		String expected = orig2;
		
		if (listReceived == null) {
			console.error("Data mismatch: Expected " + expected + ". Received null");
			return;
		}
		
		String stringReceived = (String) listReceived.get(0);
		
		if (stringReceived != null && stringReceived.equals(expected)) {
			console.info("Data matched: namespace=%s set=%s key=%s value=%s", 
				key.namespace, key.setName, key.userKey, stringReceived);
		}
		else {
			console.error("Data mismatch: Expected " + expected + ". Received " + stringReceived);
		}
	}
	
	/**
	 * Large list filter example.
	 */
	public void runFilterExample(AerospikeClient client, Parameters params) throws Exception {
		RegisterTask task = client.register(params.policy, "udf/largelist_example.lua", "largelist_example.lua", Language.LUA);
		task.waitTillComplete();

		Key key = new Key(params.namespace, params.set, "setkey");
		String binName = params.getBinName("ListBin");
		
		// Delete record if it already exists.
		client.delete(params.writePolicy, key);
		
		// Initialize large set operator.
		com.aerospike.client.large.LargeList llist = client.getLargeList(params.writePolicy, key, binName);
		int orig1 = 1;
		int orig2 = 2;
		int orig3 = 3;
		int orig4 = 4;
						
		// Write values.
		llist.add(Value.get(orig1), Value.get(orig2), Value.get(orig3), Value.get(orig4));
		
		// Filter on values
		List<?> filterList = llist.filter("largelist_example", "my_filter_func", Value.get(orig3));
		
		if (filterList == null) {			
			console.error("Filter returned null.");
			return;
		}		
		
		if (filterList.size() != 1) {
			console.error("Filter Size mismatch. Expected 1 Received " + filterList.size());
			return;
		}
		
		Long v = (Long)filterList.get(0);
		
		if (v == orig3) {
			console.info("Filter matched: v=%d", v);
		} 
		else {
			console.error("Filter content mismatch. Expected (" + orig3 + ") Received (" + v + ")");
		}
	}

	/**
	 * Use distinct sub-bins for row in largelist bin. 
	 */
	@SuppressWarnings("unchecked")
	public void runWithDistinctBins(AerospikeClient client, Parameters params) throws AerospikeException {
		Key key = new Key(params.namespace, params.set, "accountId");

		// Delete record if it already exists.
		client.delete(params.writePolicy, key);	

		// Initialize large list operator.
		com.aerospike.client.large.LargeList list = client.getLargeList(params.writePolicy, key, "trades");

		// Write trades
		Map<String,Value> map = new HashMap<String,Value>();

		Calendar timestamp1 = new GregorianCalendar(2014, 6, 25, 12, 18, 43);	
		map.put("key", Value.get(timestamp1.getTimeInMillis()));
		map.put("ticker", Value.get("IBM"));
		map.put("qty", Value.get(100));
		map.put("price", Value.get(Double.doubleToLongBits(181.82)));
		list.add(Value.get(map));

		Calendar timestamp2 = new GregorianCalendar(2014, 6, 26, 9, 33, 17);
		map.put("key", Value.get(timestamp2.getTimeInMillis()));
		map.put("ticker", Value.get("GE"));
		map.put("qty", Value.get(500));
		map.put("price", Value.get(Double.doubleToLongBits(26.36)));
		list.add(Value.get(map));

		Calendar timestamp3 = new GregorianCalendar(2014, 6, 27, 14, 40, 19);
		map.put("key", Value.get(timestamp3.getTimeInMillis()));
		map.put("ticker", Value.get("AAPL"));
		map.put("qty", Value.get(75));
		map.put("price", Value.get(Double.doubleToLongBits(91.85)));
		list.add(Value.get(map));

		// Verify list size
		int size = list.size();

		if (size != 3) {
			throw new AerospikeException("List size mismatch. Expected 3 Received " + size);
		}

		// Filter on range of timestamps
		Calendar begin = new GregorianCalendar(2014, 6, 26);
		Calendar end = new GregorianCalendar(2014, 6, 28);
		List<Map<String,Object>> results = (List<Map<String,Object>>)list.range(Value.get(begin.getTimeInMillis()), Value.get(end.getTimeInMillis()));

		if (results == null) {			
			throw new AerospikeException("Range returned null.");
		}

		if (results.size() != 2) {
			throw new AerospikeException("Query results size mismatch. Expected 2 Received " + results.size());
		}

		// Verify data.
		validateWithDistinctBins(results, 0, timestamp2, "GE", 500, 26.36);
		validateWithDistinctBins(results, 1, timestamp3, "AAPL", 75, 91.85);
		
		console.info("Data matched.");

		console.info("Run large list scan.");
		List<Map<String,Object>> rows = (List<Map<String,Object>>)list.scan();
		for (Map<String,Object> row : rows) {
			for (@SuppressWarnings("unused") Map.Entry<String,Object> entry : row.entrySet()) {
				//console.Info(entry.Key.ToString());
				//console.Info(entry.Value.ToString());
			}
		}
		console.info("Large list scan complete.");
	}

	private void validateWithDistinctBins(List<Map<String,Object>> list, int index, Calendar expectedTime, String expectedTicker, int expectedQty, double expectedPrice) 
		throws AerospikeException {
		
		Map<String,Object> map = list.get(index);
		Calendar receivedTime = new GregorianCalendar();
		receivedTime.setTimeInMillis((Long)map.get("key"));

		if (! expectedTime.equals(receivedTime)) {
			throw new AerospikeException("Time mismatch: Expected " + expectedTime + ". Received " + receivedTime);
		}

		String receivedTicker = (String)map.get("ticker");

		if (! expectedTicker.equals(receivedTicker)) {
			throw new AerospikeException("Ticker mismatch: Expected " + expectedTicker + ". Received " + receivedTicker);
		}

		long receivedQty = (Long)map.get("qty");

		if (expectedQty != receivedQty) {
			throw new AerospikeException("Quantity mismatch: Expected " + expectedQty + ". Received " + receivedQty);
		}

		double receivedPrice = Double.longBitsToDouble((Long)map.get("price"));

		if (expectedPrice != receivedPrice) {
			throw new AerospikeException("Price mismatch: Expected " + expectedPrice + ". Received " + receivedPrice);
		}
	}

	/**
	 * Use serialized bin for row in largelist bin.
	 */
	@SuppressWarnings("unchecked")
	public void runWithSerializedBin(AerospikeClient client, Parameters params)
		throws AerospikeException, IOException {
		
		Key key = new Key(params.namespace, params.set, "accountId");

		// Delete record if it already exists.
		client.delete(params.writePolicy, key);

		// Initialize large list operator.
		com.aerospike.client.large.LargeList list = client.getLargeList(params.writePolicy, key, "trades");

		// Write trades
		Map<String, Value> map = new HashMap<String, Value>();
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(500);
		DataOutputStream writer = new DataOutputStream(byteStream);

		Calendar timestamp1 = new GregorianCalendar(2014, 6, 25, 12, 18, 43);
		map.put("key", Value.get(timestamp1.getTimeInMillis()));
		writer.writeUTF("IBM");     // ticker
		writer.writeInt(100);       // qty
		writer.writeDouble(181.82); // price
		map.put("value", Value.get(byteStream.toByteArray()));
		list.add(Value.get(map));

		Calendar timestamp2 = new GregorianCalendar(2014, 6, 26, 9, 33, 17);
		map.put("key", Value.get(timestamp2.getTimeInMillis()));
		byteStream.reset();
		writer.writeUTF("GE");     // ticker
		writer.writeInt(500);      // qty
		writer.writeDouble(26.36); // price
		map.put("value", Value.get(byteStream.toByteArray()));
		list.add(Value.get(map));

		Calendar timestamp3 = new GregorianCalendar(2014, 6, 27, 14, 40, 19);
		map.put("key", Value.get(timestamp3.getTimeInMillis()));
		byteStream.reset();
		writer.writeUTF("AAPL");   // ticker
		writer.writeInt(75);       // qty
		writer.writeDouble(91.85); // price
		map.put("value", Value.get(byteStream.toByteArray()));
		list.add(Value.get(map));

		// Verify list size
		int size = list.size();

		if (size != 3) {
			throw new AerospikeException("List size mismatch. Expected 3 Received " + size);
		}

		// Filter on range of timestamps
		Calendar begin = new GregorianCalendar(2014, 6, 26);
		Calendar end = new GregorianCalendar(2014, 6, 28);
		List<Map<String,Object>> results = (List<Map<String,Object>>)list.range(Value.get(begin.getTimeInMillis()), Value.get(end.getTimeInMillis()));

		if (results == null) {			
			throw new AerospikeException("Range returned null.");
		}

		if (results.size() != 2) {
			throw new AerospikeException("Query results size mismatch. Expected 2 Received " + results.size());
		}

		// Verify data.
		validateWithSerializedBin(results, 0, timestamp2, "GE", 500, 26.36);
		validateWithSerializedBin(results, 1, timestamp3, "AAPL", 75, 91.85);

		console.info("Data matched.");
	}

	private void validateWithSerializedBin(List<Map<String,Object>> list, int index, Calendar expectedTime, String expectedTicker, int expectedQty, double expectedPrice)
		throws AerospikeException, IOException {
		
		Map<String,Object> map = list.get(index);
		Calendar receivedTime = new GregorianCalendar();
		receivedTime.setTimeInMillis((Long)map.get("key"));

		if (! expectedTime.equals(receivedTime)) {
			throw new AerospikeException("Time mismatch: Expected " + expectedTime + ". Received " + receivedTime);
		}

		byte[] value = (byte[])map.get("value");
		ByteArrayInputStream ms = new ByteArrayInputStream(value);
		DataInputStream reader = new DataInputStream(ms);
		String receivedTicker = reader.readUTF();

		if (! expectedTicker.equals(receivedTicker)) {
			throw new AerospikeException("Ticker mismatch: Expected " + expectedTicker + ". Received " + receivedTicker);
		}

		int receivedQty = reader.readInt();

		if (expectedQty != receivedQty) {
			throw new AerospikeException("Quantity mismatch: Expected " + expectedQty + ". Received " + receivedQty);
		}

		double receivedPrice = reader.readDouble();

		if (expectedPrice != receivedPrice) {
			throw new AerospikeException("Price mismatch: Expected " + expectedPrice + ". Received " + receivedPrice);
		}
	}
}
