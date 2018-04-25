package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Class optimized to efficiently store frequencies in memory using a ByteBuffer to store counts and Trove collections
 *  to store indexes to the byte buffer.
 *
 * Inspired by code in http://java-performance.info/use-case-optimizing-memory-footprint-of-read-only-csv-file-trove-unsafe-bytebuffer-data-compression/
 */
public class CompactFrequencies implements Serializable
{
	private static final int BUFFER_SIZE_STEP = 10 * 1024 * 1024;
	private TObjectIntMap<String> sense_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap sense_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1 );
	transient private ByteBuffer sense_counts = ByteBuffer.allocate(BUFFER_SIZE_STEP);

	private TObjectIntMap<String> form_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap form_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1 );
	transient private ByteBuffer form_counts = ByteBuffer.allocate(BUFFER_SIZE_STEP);

	private final static long serialVersionUID = 1L;

	public CompactFrequencies(Map<String, Pair<Integer, Integer>> sense_counts, Map<String, Map<String, Integer>> form_counts)
	{
		addCounts(sense_counts, form_counts);
		pack();
	}

	public Pair<Integer, Integer> getSenseCounts(final String sense)
	{
		int pos = sense_hash_index.get(sense.hashCode());
		if (pos == -1)
		{
			pos = sense_index.get(sense);
			if (pos == -1)
				return null;
		}

		sense_counts.position(pos);

		final int count = sense_counts.getInt();
		final int docs_count = sense_counts.getInt();

		return Pair.of(count, docs_count);
	}

	public Map<String, Integer> getFormCounts(final String form)
	{
		int pos = form_hash_index.get(form.hashCode());
		if (pos == -1)
		{
			pos = form_index.get(form);
			if (pos == -1)
				return null;
		}

		form_counts.position(pos);
		int num_senses = form_counts.getInt();
		final Map<String, Integer> sense_counts = new HashMap<>(num_senses);

		for (int i=0; i < num_senses; ++i)
		{
			final int len = form_counts.get() & 0xFF; // standard way of read unsigned byte
			final int num_bytes_sense_string = len - Integer.BYTES;
			//reading straight from ByteBuffer array in order not to allocate a temp copy
			final String sense = new String(form_counts.array(), form_counts.position(), num_bytes_sense_string, Charsets.UTF_8);
			form_counts.position(form_counts.position() + num_bytes_sense_string);
			int count = form_counts.getInt();
			sense_counts.put(sense, count);
		}

		return sense_counts;
	}

	private void addCounts(Map<String, Pair<Integer, Integer>> s_counts, Map<String, Map<String, Integer>> f_counts)
	{
		s_counts.keySet().forEach(sense ->
		{
			//increase buffer size if necessary
			if (sense_counts.remaining() < Integer.BYTES * 2)
			{
				final ByteBuffer newBuf = ByteBuffer.allocate(sense_counts.capacity() + BUFFER_SIZE_STEP);
				sense_counts.flip();
				newBuf.put(sense_counts);
				sense_counts = newBuf;
			}

			// save offset of the first byte in this record
			sense_index.put(sense, sense_counts.position());

			// add bytes to main buffer
			final int total_count = s_counts.get(sense).getLeft();
			final int docs_count = s_counts.get(sense).getRight();
			sense_counts.putInt(total_count).putInt(docs_count);
		});

		f_counts.keySet().forEach(form ->
		{
			Map<String, Integer> senses = f_counts.get(form);
			final List<byte[]> bytes_list = new ArrayList<>(senses.size() - 1);
			int bytes_length = Integer.BYTES; // to store number of sense counts

			//generate byte representations for all (sense, count) pairs
			for (Map.Entry<String, Integer> e : senses.entrySet())
			{
				final byte[] bytes_sense_string = e.getKey().getBytes(Charsets.UTF_8);
				final byte[] bytes_count = Ints.toByteArray(e.getValue());
				byte[] bytes = ArrayUtils.addAll(bytes_sense_string, bytes_count);

				bytes_length += Integer.BYTES + bytes.length; // to store length of bytes + bytes
				bytes_list.add(bytes);
			}

			//increase buffer size if necessary
			if (form_counts.remaining() < bytes_length)
			{
				final ByteBuffer newBuf = ByteBuffer.allocate(form_counts.capacity() + BUFFER_SIZE_STEP);
				form_counts.flip();
				newBuf.put(form_counts);
				form_counts = newBuf;
			}

			form_index.put(form, form_counts.position());

			// add an integer indicating number of sense counts
			form_counts.putInt(senses.size());

			// and now add the bytes for each (sense, count) pair
			for (final byte[] bytes : bytes_list)
			{
				form_counts.put((byte) bytes.length); // using a single byte to store length unsigned int (max value 127)
				form_counts.put(bytes); // bytes has both the sense string and the count int
			}
		});
	}

	private void pack()
	{
		{
			// find all hash codes in the key set, calculate their frequency
			final TIntIntMap freq = new TIntIntHashMap(sense_index.size());
			sense_index.forEachKey(sense ->
			{
				final int hashCode = sense.hashCode();
				freq.adjustOrPutValue(hashCode, 1, 1);
				return true;
			});

			// now keep records with freq > 1
			final TIntSet duplicates = new TIntHashSet(100);
			freq.forEachEntry((k, v) ->
			{
				if (v > 1)
					duplicates.add(k);
				return true;
			});

			// now generate 2 actual maps
			final TObjectIntMap<String> newMap = new TObjectIntHashMap<>(100);
			sense_index.forEachEntry((k, v) ->
			{
				final int hash = k.hashCode();
				if (duplicates.contains(hash))
					newMap.put(k, v);
				else
					sense_hash_index.put(hash, v);
				return true;
			});
			sense_index = newMap;

			System.out.println("Sense hash map size = " + sense_hash_index.size());
			System.out.println("Sense String map size = " + sense_index.size());
			System.out.println("Sense binary data size = " + sense_counts.limit());
		}

		{
			// find all hash codes in the key set, calculate their frequency
			final TIntIntMap freq = new TIntIntHashMap(form_index.size());
			form_index.forEachKey(sense ->
			{
				final int hashCode = sense.hashCode();
				freq.adjustOrPutValue(hashCode, 1, 1);
				return true;
			});

			// now keep records with freq > 1
			final TIntSet duplicates = new TIntHashSet(100);
			freq.forEachEntry((k, v) ->
			{
				if (v > 1)
					duplicates.add(k);
				return true;
			});

			// now generate 2 actual maps
			final TObjectIntMap<String> newMap = new TObjectIntHashMap<>(100);
			form_index.forEachEntry((k, v) ->
			{
				final int hash = k.hashCode();
				if (duplicates.contains(hash))
					newMap.put(k, v);
				else
					form_hash_index.put(hash, v);
				return true;
			});
			form_index = newMap;

			System.out.println("Form hash map size = " + form_hash_index.size());
			System.out.println("Form String map size = " + form_index.size());
			System.out.println("Form binary data size = " + form_counts.limit());
		}
	}

	// Serializes byte arrays wrapped with ByteBuffer objects.
	// See https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		// write default properties
		out.defaultWriteObject();
		// write buffer capacity and data
		out.writeInt(sense_counts.capacity());
		out.write(sense_counts.array());
		out.writeInt(form_counts.capacity());
		out.write(form_counts.array());
	}

	// Deserializes byte arrays and wraps them with ByteBuffer objects.
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		//read default properties
		in.defaultReadObject();

		//read buffer data and wrap with ByteBuffer
		int bufferSize = in.readInt();
		byte[] buffer = new byte[bufferSize];
		//noinspection ResultOfMethodCallIgnored
		in.read(buffer, 0, bufferSize);
		sense_counts = ByteBuffer.wrap(buffer, 0, bufferSize);

		bufferSize = in.readInt();
		buffer = new byte[bufferSize];
		//noinspection ResultOfMethodCallIgnored
		in.read(buffer, 0, bufferSize);
		form_counts = ByteBuffer.wrap(buffer, 0, bufferSize);
	}
}