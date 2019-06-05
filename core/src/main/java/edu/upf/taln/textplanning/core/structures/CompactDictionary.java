package edu.upf.taln.textplanning.core.structures;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Shorts;
import com.ibm.icu.util.ULocale;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 *  Stores dictionary entries in a space-efficient manner. Used a ByteBuffer and Trove collections.
 *
 * Inspired by code in http://java-performance.info/use-case-optimizing-memory-footprint-of-read-only-csv-file-trove-unsafe-bytebuffer-data-compression/
 */
public class CompactDictionary implements Serializable
{
	private static class Entry
	{
		final String form;
		final String pos;
		final List<String> meanings;

		public Entry(String form, String pos)
		{
			this(form, pos, null);
		}

		public Entry(String form, String pos, List<String> meanings)
		{
			this.form = form;
			this.pos = pos;
			this.meanings = meanings;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Entry that = (Entry) o;

			if (!form.equals(that.form)) return false;
			return pos.equals(that.pos);
		}

		@Override
		public int hashCode()
		{
			int result = form.hashCode();
			result = 31 * result + pos.hashCode();
			return result;
		}
	}

	private static final int BUFFER_SIZE_STEP = 10 * 1024 * 1024;
	private TObjectIntMap<Entry> word_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap words_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer meanings_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private TObjectIntMap<String> meanings_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap meanings_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer glosses_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private final ULocale language;
	private int num_added_words = 0;
	private int num_added_meanings = 0;
	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger();

	public CompactDictionary(ULocale language)
	{
		this.language = language;
	}

	public ULocale getLanguage()
	{
		return language;
	}

	public boolean contains(String form, String pos)
	{
		final Entry e = new Entry(form, pos);
		return word_index.containsKey(e) || words_hash_index.containsKey(e.hashCode());
	}

	public boolean contains(String meaning)
	{
		return meanings_index.containsKey(meaning) || meanings_hash_index.containsKey(meaning.hashCode());
	}

	public void addWord(String word, String pos, List<String> meanings)
	{
		Entry entry = new Entry(word, pos, meanings);
		if (word_index.containsKey(entry) || words_hash_index.containsKey(entry.hashCode()))
			return;

		// create byte arrays for meanings
		final List<byte[]> bytes_list = new ArrayList<>(entry.meanings.size() - 1);
		entry.meanings.forEach(m ->
		{
			final byte[] bytes_string = m.getBytes(Charsets.UTF_8);
			final byte[] bytes_count = Shorts.toByteArray((short) bytes_string.length);

			byte[] bytes = ArrayUtils.addAll(bytes_count, bytes_string);
			bytes_list.add(bytes);
		});

		// increase buffer size if necessary
		int num_bytes = Short.BYTES * 2 + bytes_list.size();
		if (meanings_data.remaining() < num_bytes)
		{
			final ByteBuffer newBuf = ByteBuffer.allocate(meanings_data.capacity() + BUFFER_SIZE_STEP);
			meanings_data.flip();
			newBuf.put(meanings_data);
			meanings_data = newBuf;
		}

		// save offset of the first byte in this record
		final int position = meanings_data.position();

		// add an integer indicating number of meanings
		meanings_data.putShort((short) entry.meanings.size()); // a short with max value 32767 should suffice

		// store the bytes for each meaning
		for (final byte[] bytes : bytes_list)
		{
			meanings_data.put(bytes);
		}

		updateIndexes(entry, position);
		++num_added_words;
	}

	private void updateIndexes(Entry entry, int meanings_pos)
	{
		final int hash = entry.hashCode();
		if (words_hash_index.containsKey(hash))
			word_index.put(entry, meanings_pos);
		else
			words_hash_index.put(hash, meanings_pos);
	}

	public void addMeaning(String meaning, String label, List<String> glosses)
	{
		// create byte arrays for label and glosses
		final List<byte[]> bytes_list = new ArrayList<>(glosses.size());
		{
			final byte[] bytes_string = label.getBytes(Charsets.UTF_8);
			final byte[] bytes_count = Shorts.toByteArray((short) bytes_string.length);

			byte[] bytes = ArrayUtils.addAll(bytes_count, bytes_string);
			bytes_list.add(bytes);
		}

		glosses.forEach(m ->
		{
			final byte[] bytes_string = m.getBytes(Charsets.UTF_8);
			final byte[] bytes_count = Shorts.toByteArray((short) bytes_string.length);

			byte[] bytes = ArrayUtils.addAll(bytes_count, bytes_string);
			bytes_list.add(bytes);
		});

		//increase buffer size if necessary
		int num_bytes = Short.BYTES * 2 + bytes_list.size();
		if (glosses_data.remaining() < num_bytes)
		{
			final ByteBuffer newBuf = ByteBuffer.allocate(glosses_data.capacity() + BUFFER_SIZE_STEP);
			glosses_data.flip();
			newBuf.put(glosses_data);
			glosses_data = newBuf;
		}

		// save offset of the first byte in this record
		final int position = glosses_data.position();

		// add an integer indicating number of glosses
		glosses_data.putShort((short) glosses.size()); // a short with max value 32767 should suffice

		// store the bytes for each gloss
		for (final byte[] bytes : bytes_list)
		{
			glosses_data.put(bytes);
		}

		updateIndexes(meaning, position);
		++num_added_meanings;
	}

	private void updateIndexes(String meaning, int glosses_pos)
	{
		final int hash = meaning.hashCode();
		if (meanings_hash_index.containsKey(hash))
			meanings_index.put(meaning, glosses_pos);
		else
			meanings_hash_index.put(hash, glosses_pos);
	}

	public List<String> getMeanings(String word, String pos)
	{
		int position = getMeaningsPosition(new Entry(word, pos)).orElse(-1);
		if (position == -1)
			return List.of();

		meanings_data.position(position);

		final int num_meanings = meanings_data.getShort();
		return IntStream.range(0, num_meanings)
				.mapToObj(i ->
				{
					final int num_bytes = meanings_data.getInt();
					byte[] bytes = new byte[num_bytes];
					meanings_data.get(bytes);
					return new String(bytes);
				})
				.collect(toList());
	}

	public Optional<String> getLabel(String meaning)
	{
		int position = getGlossesPosition(meaning).orElse(-1);
		if (position == -1)
			return Optional.empty();

		glosses_data.position(position);

		// skip number of glosses
		glosses_data.getShort();

		final int num_bytes = glosses_data.getInt();
		byte[] bytes = new byte[num_bytes];
		glosses_data.get(bytes);

		return Optional.of(new String(bytes));
	}

	public List<String> getGlosses(String meaning)
	{
		int position = getGlossesPosition(meaning).orElse(-1);
		if (position == -1)
			return List.of();

		glosses_data.position(position);

		final int num_glosses = glosses_data.getShort();
		{
			// Skip label
			final int num_bytes = glosses_data.getInt();
			byte[] bytes = new byte[num_bytes];
			glosses_data.get(bytes);
		}

		return IntStream.range(0, num_glosses)
				.mapToObj(i ->
				{
					final int num_bytes = glosses_data.getInt();
					byte[] bytes = new byte[num_bytes];
					glosses_data.get(bytes);
					return new String(bytes);
				})
				.collect(toList());
	}

	private OptionalInt getMeaningsPosition(Entry entry)
	{
		// First check if the entry 'as is' is indexed
		if (word_index.containsKey(entry))
			return OptionalInt.of(word_index.get(entry));

		// then check if it's indexed via its hash code
		final int hash = entry.hashCode();
		if (words_hash_index.containsKey(hash))
			return OptionalInt.of(words_hash_index.get(hash));

		return OptionalInt.empty();
	}

	private OptionalInt getGlossesPosition(String meaning)
	{
		// First check if the entry 'as is' is indexed
		if (meanings_index.containsKey(meaning))
			return OptionalInt.of(meanings_index.get(meaning));

		// then check if it's indexed via its hash code
		final int hash = meaning.hashCode();
		if (meanings_hash_index.containsKey(hash))
			return OptionalInt.of(meanings_hash_index.get(hash));

		return OptionalInt.empty();
	}

	// Serializes byte arrays wrapped with ByteBuffer objects.
	// See https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		log.info("Dictionary contains " + words_hash_index.size() + word_index.size() + " words and " +
					meanings_hash_index.size() + meanings_index.size() + " meanings");
		log.info(num_added_words + " words and " + num_added_meanings + " meanings have been added to dictionary");

		log.info("Serializing data to binary file");
		Stopwatch timer = Stopwatch.createStarted();
		// write default properties
		out.defaultWriteObject();
		// write buffers capacity and data
		out.writeInt(meanings_data.capacity());
		out.write(meanings_data.array());
		out.writeInt(glosses_data.capacity());
		out.write(glosses_data.array());

		log.info("Data written in " + timer.stop());
	}

	// Deserializes byte arrays and wraps them with ByteBuffer objects.
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		log.info("Loading data from serialized binary file");
		Stopwatch timer = Stopwatch.createStarted();
		//read default properties
		in.defaultReadObject();

		//read buffers data and wrap with ByteBuffer
		{
			int bufferSize = in.readInt();
			byte[] buffer = new byte[bufferSize];
			in.readFully(buffer, 0, bufferSize);
			meanings_data = ByteBuffer.wrap(buffer, 0, bufferSize);
		}
		{
			int bufferSize = in.readInt();
			byte[] buffer = new byte[bufferSize];
			in.readFully(buffer, 0, bufferSize);
			glosses_data = ByteBuffer.wrap(buffer, 0, bufferSize);
		}

		log.info("Data loaded in " + timer.stop());
		log.info("Dictionary contains " + words_hash_index.size() + word_index.size() + " words and " +
				meanings_hash_index.size() + meanings_index.size() + " meanings");
	}
}
