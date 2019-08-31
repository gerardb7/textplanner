package edu.upf.taln.textplanning.core.dictionaries;

import com.google.common.base.Charsets;
import com.google.common.primitives.Shorts;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 *  Stores dictionary entries in a space-efficient manner. Used a ByteBuffer and Trove collections.
 *
 * Inspired by code in http://java-performance.info/use-case-optimizing-memory-footprint-of-read-only-csv-file-trove-unsafe-bytebuffer-data-compression/
 * and https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer for the serialization bits
 */
public class CompactDictionary implements Serializable
{
	private static class Entry implements Serializable
	{
		final byte[] form;
		final int pos;
		private final static long serialVersionUID = 1L;

		public Entry(String form, char pos)
		{
			this.form = form.replace(' ', '_').toLowerCase().getBytes(Charsets.UTF_8);
			this.pos = Character.getNumericValue(pos);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Entry entry = (Entry) o;

			if (pos != entry.pos) return false;
			return Arrays.equals(form, entry.form);
		}

		@Override
		public int hashCode()
		{
			int result = Arrays.hashCode(form);
			result = 31 * result + pos;
			return result;
		}
	}

	private static final int BUFFER_SIZE_STEP = 10 * 1024 * 1024;
	private TObjectIntMap<Entry> forms_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap forms_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer meanings_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private TObjectIntMap<byte[]> meanings_index = new TObjectIntHashMap<>( 1000 );
	private TIntIntMap meanings_hash_index = new TIntIntHashMap( 1000, 0.75f, -1, -1);
	transient private ByteBuffer glosses_data = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private final ULocale language;
	private int meanings_position = 0; // used to keep buffer position when serializing
	private int glosses_position = 0;
	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger();

	public CompactDictionary(ULocale language, MeaningDictionary dictionary, Path file)
	{
		this.language = language;

		CandidatesCollector.collect(dictionary, language, file, this::addMeaning, this::addForm, this::getLabel,
				this::getGlosses, this::getMeanings, this::serialize);
	}

	public CompactDictionary(ULocale language, Set<Pair<String, POS.Tag>> forms,
	                         MeaningDictionary dictionary)
	{
		this.language = language;

		final List<Triple<String, Character, List<String>>> lexicalizations_info =
				CandidatesCollector.addLexicalizations(forms, dictionary, language, this::addForm);

		Set<String> meanings = lexicalizations_info.stream()
				.map(Triple::getRight)
				.flatMap(List::stream)
				.collect(toSet());
		CandidatesCollector.addMeanings(meanings, dictionary, language, this::addMeaning);
	}

	public ULocale getLanguage()
	{
		return language;
	}

	public boolean contains(String form, char pos)
	{
		assert StringUtils.isNotBlank(form);
		final Entry e = new Entry(form, pos);
		return forms_index.containsKey(e) || forms_hash_index.containsKey(e.hashCode()); // order of terms is important
	}

	public boolean contains(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);
		return meanings_index.containsKey(meaning) || meanings_hash_index.containsKey(meaning.hashCode()); // order of terms is important
	}

	public List<String> getMeanings(String form, char pos)
	{
		assert StringUtils.isNotBlank(form);
		int position = getFormsPosition(new Entry(form, pos)).orElse(-1);
		if (position == -1)
			return List.of();

		final int old_pos = meanings_data.position();
		meanings_data.position(position);

		final int num_meanings = Short.valueOf(meanings_data.getShort()).intValue();
		final List<String> meanings = IntStream.range(0, num_meanings)
				.mapToObj(i ->
				{
					final int num_bytes = meanings_data.getShort();
					byte[] bytes = new byte[num_bytes];
					meanings_data.get(bytes);
					return new String(bytes, Charsets.US_ASCII);
				})
				.collect(toList());

		meanings_data.position(old_pos);
		return meanings;
	}

	public Optional<String> getLabel(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);
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
		return Optional.of(new String(bytes, Charsets.UTF_8));
	}

	public List<String> getGlosses(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);

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
					return new String(bytes, Charsets.UTF_8);
				})
				.collect(toList());

		glosses_data.position(old_pos);
		return glosses;
	}

	public synchronized void serialize(Path file)
	{
		try
		{
			FileOutputStream fos = new FileOutputStream(file.toString());
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			fos.close();
		}
		catch (Exception e)
		{
			log.error("failed to serialize cache: " + e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void addForm(String form, char pos, List<String> meanings)
	{
		assert StringUtils.isNotBlank(form) && meanings != null;
		meanings.removeIf(String::isBlank);
		if (meanings.isEmpty())
		{
			log.info("Ignoring form " + form + " (" + pos + ") with no meanings");
			return;
		}

		final Entry e = new Entry(form, pos);
		if (forms_hash_index.containsKey(e.hashCode()) && forms_index.containsKey(e)) // do not replace with call to CompactDictionary::contains!
		{
			log.info("Ignoring form " + form + " (" + pos + ") already in cache");
			return;
		}

		// create byte arrays for meanings
		final List<byte[]> bytes_list = new ArrayList<>(meanings.size());
		meanings.forEach(m ->
		{
			final byte[] bytes_string = m.getBytes(Charsets.US_ASCII);
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
		meanings_data.putShort((short) meanings.size()); // a short with max value 32767 should suffice

		// store the bytes for each meaning
		for (final byte[] bytes : bytes_list)
		{
			meanings_data.put(bytes);
		}

		Entry entry = new Entry(form, pos);
		updateIndexes(entry, position);
	}

	private void updateIndexes(Entry entry, int meanings_pos)
	{
		final int hash = entry.hashCode();
		if (forms_hash_index.containsKey(hash))
			forms_index.put(entry, meanings_pos);
		else
			forms_hash_index.put(hash, meanings_pos);
	}

	private void addMeaning(final String meaning, final String label, List<String> glosses)
	{
		assert StringUtils.isNotBlank(meaning) && StringUtils.isNotBlank(label) && glosses != null;

		if (glosses.removeIf(String::isBlank))
		if (StringUtils.isBlank(label) && glosses.isEmpty())
		{
			log.warn("Ignoring meaning " + meaning + " with no label nor glosses");
			return;
		}
		if (meanings_hash_index.containsKey(meaning.hashCode()) && meanings_index.containsKey(meaning)) // do not replace with call to CompactDictionary::contains!
		{
			log.info("Ignoring meaning " + meaning + " already in cache");
			return;
		}

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
	}

	private void updateIndexes(String meaning, int glosses_pos)
	{
		final int hash = meaning.hashCode();
		if (meanings_hash_index.containsKey(hash))
			meanings_index.put(meaning.getBytes(Charsets.US_ASCII), glosses_pos);
		else
			meanings_hash_index.put(hash, glosses_pos);
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
		if (meanings_index.containsKey(meaning.getBytes(Charsets.US_ASCII)))
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
		meanings_position = meanings_data.position();
		glosses_position = glosses_data.position();

		// write default properties
		out.defaultWriteObject();
		// write buffers capacity and data
		out.writeInt(meanings_data.capacity());
		out.write(meanings_data.array());
		out.writeInt(glosses_data.capacity());
		out.write(glosses_data.array());

		log.info("Serialized cache with " + (forms_hash_index.keySet().size() + forms_index.keySet().size()) + " lexicalizations and " +
				(meanings_hash_index.keySet().size() + meanings_index.keySet().size()) + " meanings");
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


		log.info("Cache loaded with " + (forms_hash_index.keySet().size() + forms_index.keySet().size()) + " forms and " +
				(meanings_hash_index.keySet().size() + meanings_index.keySet().size()) + " meanings");
	}
}
