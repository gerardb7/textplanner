package edu.upf.taln.textplanning.core.corpora;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.*;

/**
 *  Class optimized to efficiently store frequencies in memory using a ByteBuffer to store counts and Trove collections
 *  to store indexes to the byte buffer.
 *
 * Inspired by code in http://java-performance.info/use-case-optimizing-memory-footprint-of-read-only-csv-file-trove-unsafe-bytebuffer-data-compression/
 */
public class CompactFrequencies implements Corpus, Serializable
{
	private final int num_docs;
	private static final int BUFFER_SIZE_STEP = 10 * 1024 * 1024;
	private TObjectIntMap<String> meaning_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap meaning_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1 );
	transient private ByteBuffer meaning_counts = ByteBuffer.allocate(BUFFER_SIZE_STEP);

	private TObjectIntMap<String> form_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap form_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1 );
	transient private ByteBuffer form_counts = ByteBuffer.allocate(BUFFER_SIZE_STEP);

	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger();

	public CompactFrequencies(int num_docs, Map<String, Integer> total_counts, Map<String, Integer> doc_counts, Map<String, Map<String, Integer>> form_counts)
	{
		this.num_docs = num_docs;
		addCounts(total_counts, doc_counts, form_counts);
		pack();
	}

	private void addCounts(Map<String, Integer> total_counts, Map<String, Integer> doc_counts, Map<String, Map<String, Integer>> f_counts)
	{
		total_counts.keySet().forEach(meaning ->
		{
			//increase buffer size if necessary
			if (meaning_counts.remaining() < Integer.BYTES * 2)
			{
				final ByteBuffer newBuf = ByteBuffer.allocate(meaning_counts.capacity() + BUFFER_SIZE_STEP);
				meaning_counts.flip();
				newBuf.put(meaning_counts);
				meaning_counts = newBuf;
			}

			// save offset of the first byte in this record
			meaning_index.put(meaning, meaning_counts.position());

			// add bytes to main buffer
			final int total_count = total_counts.get(meaning);
			final int docs_count = doc_counts.get(meaning);
			meaning_counts.putInt(total_count).putInt(docs_count);
		});

		f_counts.keySet().forEach(form ->
		{
			Map<String, Integer> meanings = f_counts.get(form);
			final List<byte[]> bytes_list = new ArrayList<>(meanings.size() - 1);
			int bytes_length = Short.BYTES; // to store number of meaning counts

			//generate byte representations for all (meaning, count) pairs
			for (Map.Entry<String, Integer> e : meanings.entrySet())
			{
				final byte[] bytes_meaning_string = e.getKey().getBytes(Charsets.UTF_8);
				final byte[] bytes_count = Ints.toByteArray(e.getValue());
				byte[] bytes = ArrayUtils.addAll(bytes_meaning_string, bytes_count);

				bytes_length += Byte.BYTES + bytes.length; // to store length of bytes + bytes
				bytes_list.add(bytes);
			}

			//increase buffer size if necessary
			if (this.form_counts.remaining() < bytes_length)
			{
				final ByteBuffer newBuf = ByteBuffer.allocate(this.form_counts.capacity() + BUFFER_SIZE_STEP);
				this.form_counts.flip();
				newBuf.put(this.form_counts);
				this.form_counts = newBuf;
			}

			form_index.put(form, form_counts.position());

			// add an integer indicating number of meaning counts
			this.form_counts.putShort((short)meanings.size()); // a short with max value 32767 should suffice

			// and now add the bytes for each (meaning, count) pair
			for (final byte[] bytes : bytes_list)
			{
				this.form_counts.put((byte) bytes.length); // using a single byte to store length unsigned int (max value 127)
				this.form_counts.put(bytes); // bytes has both the meaning string and the count int
			}
		});
	}

	private void pack()
	{
		{
			// find all hash codes in the key set, calculate their frequency
			final TIntIntMap freq = new TIntIntHashMap(meaning_index.size());
			meaning_index.forEachKey(meaning ->
			{
				final int hashCode = meaning.hashCode();
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
			meaning_index.forEachEntry((k, v) ->
			{
				final int hash = k.hashCode();
				if (duplicates.contains(hash))
					newMap.put(k, v);
				else
					meaning_hash_index.put(hash, v);
				return true;
			});
			meaning_index = newMap;

			log.info("meaning hash map size = " + meaning_hash_index.size());
			log.info("meaning String map size = " + meaning_index.size());
			log.info("meaning binary data size = " + meaning_counts.limit());
		}

		{
			// find all hash codes in the key set, calculate their frequency
			final TIntIntMap freq = new TIntIntHashMap(form_index.size());
			form_index.forEachKey(meaning ->
			{
				final int hashCode = meaning.hashCode();
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

			log.info("Form hash map size = " + form_hash_index.size());
			log.info("Form String map size = " + form_index.size());
			log.info("Form binary data size = " + form_counts.limit());
		}
	}

	@Override
	public OptionalInt getMeaningCount(String meaning)
	{
		return getMeaningCounts(meaning).getLeft();
	}

	@Override
	public OptionalInt getMeaningDocumentCount(String meaning)
	{
		return getMeaningCounts(meaning).getRight();
	}

	private Pair<OptionalInt, OptionalInt> getMeaningCounts(String meaning)
	{
		int pos = getMeaningPos(meaning).orElse(-1);
		if (pos == -1)
			return Pair.of(OptionalInt.empty(), OptionalInt.empty());

		meaning_counts.position(pos);

		final int count = meaning_counts.getInt();
		final int docs_count = meaning_counts.getInt();

		return Pair.of(OptionalInt.of(count), OptionalInt.of(docs_count));
	}

	private OptionalInt getMeaningPos(String meaning)
	{
		int pos = meaning_hash_index.get(meaning.hashCode());
		if (pos == -1)
		{
			pos = meaning_index.get(meaning);
		}

		if (pos == -1)
			return OptionalInt.empty();
		else
			return OptionalInt.of(pos);
	}

	@Override
	public OptionalInt getFormMeaningCount(String form, String meaning)
	{
		Map<String, Integer> counts = getFormCounts(form);
		if (counts.isEmpty())
			return OptionalInt.empty();

		return counts.entrySet().stream()
				.filter(e -> e.getKey().equals(meaning))
				.findFirst()
				.map(Map.Entry::getValue)
				.map(OptionalInt::of)
				.orElseGet(OptionalInt::empty);
	}

	@Override
	public OptionalInt getFormCount(String form)
	{
		Map<String, Integer> counts = getFormCounts(form);
		if (counts.isEmpty())
			return OptionalInt.empty();

		int count = counts.values().stream()
				.mapToInt(c -> c)
				.sum();
		return OptionalInt.of(count);
	}

	private Map<String, Integer> getFormCounts(final String form)
	{
		int pos = getFormPos(form).orElse(-1);
		if (pos == -1)
			return new HashMap<>();

		form_counts.position(pos);
		int num_meanings = form_counts.getShort();
		final Map<String, Integer> meaning_counts = new HashMap<>(num_meanings);

		for (int i=0; i < num_meanings; ++i)
		{
			final int len = form_counts.get() & 0xFF; // standard way of read unsigned byte
			final int num_bytes_meaning_string = len - Integer.BYTES;
			//reading straight from ByteBuffer array in order not to allocate a temp copy
			final String meaning = new String(form_counts.array(), form_counts.position(), num_bytes_meaning_string, Charsets.UTF_8);
			form_counts.position(form_counts.position() + num_bytes_meaning_string);
			int count = form_counts.getInt();
			meaning_counts.put(meaning, count);
		}

		return meaning_counts;
	}

	private OptionalInt getFormPos(String form)
	{
		int pos = form_hash_index.get(form.hashCode());
		if (pos == -1)
		{
			pos = form_index.get(form);
		}

		if (pos == -1)
			return OptionalInt.empty();
		else
			return OptionalInt.of(pos);
	}

	@Override
	public int getNumDocs()
	{
		return num_docs;
	}


	// Serializes byte arrays wrapped with ByteBuffer objects.
	// See https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		// write default properties
		out.defaultWriteObject();
		// write buffer capacity and data
		out.writeInt(meaning_counts.capacity());
		out.write(meaning_counts.array());
		out.writeInt(form_counts.capacity());
		out.write(form_counts.array());
	}

	// Deserializes byte arrays and wraps them with ByteBuffer objects.
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		log.info("Loading frequencies from serialized binary file");
		Stopwatch timer = Stopwatch.createStarted();
		//read default properties
		in.defaultReadObject();

		//read buffer data and wrap with ByteBuffer
		int bufferSize = in.readInt();
		byte[] buffer = new byte[bufferSize];
		//noinspection ResultOfMethodCallIgnored
		in.readFully(buffer, 0, bufferSize);
		meaning_counts = ByteBuffer.wrap(buffer, 0, bufferSize);

		bufferSize = in.readInt();
		buffer = new byte[bufferSize];
		//noinspection ResultOfMethodCallIgnored
		in.readFully(buffer, 0, bufferSize);
		form_counts = ByteBuffer.wrap(buffer, 0, bufferSize);
		log.info("Frequencies loaded in " + timer.stop());
	}
}