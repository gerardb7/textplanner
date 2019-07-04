package edu.upf.taln.textplanning.core.structures;

import com.google.common.base.Charsets;
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
		final char pos;
		final List<String> meanings;

		public Entry(String form, char pos)
		{
			this(form, pos, null);
		}

		public Entry(String form, char pos, List<String> meanings)
		{
			this.form = form.replace(' ', '_');
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
			return pos == that.pos;
		}

		@Override
		public int hashCode()
		{
			int result = form.hashCode();
			result = 31 * result + Character.valueOf(pos).hashCode();
			return result;
		}
	}

	private static final int BUFFER_SIZE_STEP = 10 * 1024 * 1024;
	private TObjectIntMap<Entry> forms_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap forms_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer meanings_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private TObjectIntMap<String> meanings_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap meanings_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer glosses_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private final ULocale language;
	public int num_added_forms = 0;
	public int num_added_meanings = 0;
	private int meanings_position = 0; // used to keep buffer position when serializing
	private int glosses_position = 0;
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

	public boolean contains(String form, char pos)
	{
		final Entry e = new Entry(form, pos);
		return forms_index.containsKey(e) || forms_hash_index.containsKey(e.hashCode());
	}

	public boolean contains(String meaning)
	{
		return meanings_index.containsKey(meaning) || meanings_hash_index.containsKey(meaning.hashCode());
	}

	public void addForm(String form, char pos, List<String> meanings)
	{
		if (meanings.isEmpty())
			return;

		Entry entry = new Entry(form, pos, meanings);
		if (forms_index.containsKey(entry) || forms_hash_index.containsKey(entry.hashCode()))
			return;

		// create byte arrays for meanings
		final List<byte[]> bytes_list = new ArrayList<>(entry.meanings.size());
		entry.meanings.forEach(m ->
		{
			final byte[] bytes_string = m.getBytes(Charsets.UTF_8);
			final byte[] bytes_count = Shorts.toByteArray((short) bytes_string.length);

			byte[] bytes = ArrayUtils.addAll(bytes_count, bytes_string);
			bytes_list.add(bytes);
		});

		// increase buffer size if necessary
		int num_bytes = Short.BYTES + bytes_list.stream()
				.mapToInt(a -> a.length)
				.sum();

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
		++num_added_forms;
	}

	private void updateIndexes(Entry entry, int meanings_pos)
	{
		final int hash = entry.hashCode();
		if (forms_hash_index.containsKey(hash))
			forms_index.put(entry, meanings_pos);
		else
			forms_hash_index.put(hash, meanings_pos);
	}

	public void addMeaning(String meaning, String label, List<String> glosses)
	{
		// create byte arrays for label and glosses
		final List<byte[]> bytes_list = new ArrayList<>(glosses.size() + 1);
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
		int num_bytes = Short.BYTES + bytes_list.stream()
			.mapToInt(a -> a.length)
			.sum();
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

	public List<String> getMeanings(String form, char pos)
	{
		int position = getFormsPosition(new Entry(form, pos)).orElse(-1);
		if (position == -1)
			return List.of();

		final int old_pos = meanings_data.position();
		meanings_data.position(position);

		final int num_meanings = meanings_data.getShort();
		final List<String> meanings = IntStream.range(0, num_meanings)
				.mapToObj(i ->
				{
					final int num_bytes = meanings_data.getShort();
					byte[] bytes = new byte[num_bytes];
					meanings_data.get(bytes);
					return new String(bytes);
				})
				.collect(toList());

		meanings_data.position(old_pos);
		return meanings;
	}

	public Optional<String> getLabel(String meaning)
	{
		int position = getMeaningsPosition(meaning).orElse(-1);
		if (position == -1)
			return Optional.empty();

		final int old_pos = glosses_data.position();
		glosses_data.position(position);

		// skip number of glosses
		glosses_data.getShort();

		final int num_bytes = glosses_data.getShort();
		byte[] bytes = new byte[num_bytes];
		glosses_data.get(bytes);

		glosses_data.position(old_pos);
		return Optional.of(new String(bytes));
	}

	public List<String> getGlosses(String meaning)
	{
		int position = getMeaningsPosition(meaning).orElse(-1);
		if (position == -1)
			return List.of();

		final int old_pos = glosses_data.position();
		glosses_data.position(position);

		final int num_glosses = glosses_data.getShort();
		{
			// Skip label
			final int num_bytes = glosses_data.getShort();
			byte[] bytes = new byte[num_bytes];
			glosses_data.get(bytes);
		}

		final List<String> glosses = IntStream.range(0, num_glosses)
				.mapToObj(i ->
				{
					final int num_bytes = glosses_data.getShort();
					byte[] bytes = new byte[num_bytes];
					glosses_data.get(bytes);
					return new String(bytes);
				})
				.collect(toList());

		glosses_data.position(old_pos);
		return glosses;
	}

	private OptionalInt getFormsPosition(Entry entry)
	{
		// First check if the entry 'as is' is indexed
		if (forms_index.containsKey(entry))
			return OptionalInt.of(forms_index.get(entry));

		// then check if it's indexed via its hash code
		final int hash = entry.hashCode();
		if (forms_hash_index.containsKey(hash))
			return OptionalInt.of(forms_hash_index.get(hash));

		return OptionalInt.empty();
	}

	private OptionalInt getMeaningsPosition(String meaning)
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
		log.info(num_added_forms + " forms and " + num_added_meanings + " meanings have been added to dictionary");
		meanings_position = meanings_data.position();
		glosses_position = glosses_data.position();

		// write default properties
		out.defaultWriteObject();
		// write buffers capacity and data
		out.writeInt(meanings_data.capacity());
		out.write(meanings_data.array());
		out.writeInt(glosses_data.capacity());
		out.write(glosses_data.array());

		num_added_forms = 0;
		num_added_meanings = 0;

		log.info("Serialized dictionary cache");
	}

	// Deserializes byte arrays and wraps them with ByteBuffer objects.
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		//read default properties
		in.defaultReadObject();

		//read buffers data and wrap with ByteBuffer
		{
			int bufferSize = in.readInt();
			byte[] buffer = new byte[bufferSize];
			in.readFully(buffer, 0, bufferSize);
			meanings_data = ByteBuffer.wrap(buffer, 0, bufferSize);
			meanings_data.position(meanings_position);
		}
		{
			int bufferSize = in.readInt();
			byte[] buffer = new byte[bufferSize];
			in.readFully(buffer, 0, bufferSize);
			glosses_data = ByteBuffer.wrap(buffer, 0, bufferSize);
			glosses_data.position(glosses_position);
		}

		num_added_forms = 0;
		num_added_meanings = 0;

		log.info("Dictionary cache loaded");
		log.info("Dictionary contains " + (forms_hash_index.keySet().size() + forms_index.keySet().size()) + " forms and " +
				(meanings_hash_index.keySet().size() + meanings_index.keySet().size()) + " meanings");
	}
}
