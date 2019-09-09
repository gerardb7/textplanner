package edu.upf.taln.textplanning.core.dictionaries;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Shorts;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 *  Stores dictionary entries in a space-efficient manner. Uses ByteBuffer and Trove collections.
 *
 * Inspired by code in http://java-performance.info/use-case-optimizing-memory-footprint-of-read-only-csv-file-trove-unsafe-bytebuffer-data-compression/
 * and https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer for the serialization bits
 */
public class CompactDictionary implements Serializable
{
	private static class Meaning implements Serializable
	{
		final byte[] bytes;
		private final static long serialVersionUID = 1L;

		public Meaning(String meaning)
		{
			this.bytes = meaning.getBytes(Charsets.US_ASCII);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Meaning meaning = (Meaning) o;

			return Arrays.equals(bytes, meaning.bytes);
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode(bytes);
		}
	}

	private static class Lexicalization implements Serializable
	{
		final byte[] form;
		final int pos;
		private final static long serialVersionUID = 1L;

		public Lexicalization(String form, char pos)
		{
			this.form = form.replace(' ', '_').toLowerCase(Locale.ENGLISH).getBytes(Charsets.UTF_8);
			this.pos = Character.getNumericValue(pos);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Lexicalization lexicalization = (Lexicalization) o;

			if (pos != lexicalization.pos) return false;
			return Arrays.equals(form, lexicalization.form);
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
	private TObjectIntMap<Lexicalization> lexicalizations_index = new TObjectIntHashMap<>( 1000 );
	transient private ByteBuffer lexicalizations_info = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private TObjectIntMap<Meaning> meanings_index = new TObjectIntHashMap( 1000);
	transient private ByteBuffer meanings_info = ByteBuffer.allocate(BUFFER_SIZE_STEP);
	private final ULocale language;
	private int lexicalizations_position = 0; // used to keep buffer position when serializing
	private int meanings_position = 0;
	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger();

	public CompactDictionary(ULocale language, MeaningDictionary dictionary, Path file)
	{
		this.language = language;

		final Stopwatch timer = Stopwatch.createStarted();
		CachePopulator.populate(dictionary, language, file, this::addMeaning, this::addForm, this::getLabel,
				this::getGlosses, this::getMeanings, this::serialize);
		log.info("Cache created in "  + timer.stop());
	}

	public CompactDictionary(ULocale language, Set<Pair<String, POS.Tag>> forms,
	                         MeaningDictionary dictionary, Path file)
	{
		this.language = language;

		final Stopwatch timer = Stopwatch.createStarted();
		CachePopulator.populate(forms, dictionary, language, file, this::addMeaning, this::addForm, this::getLabel,
				this::getGlosses, this::getMeanings, this::serialize);
		log.info("Cache created in "  + timer.stop());
	}

	public void update(MeaningDictionary dictionary, Path file)
	{
		CachePopulator.populate(dictionary, language, file, this::addMeaning, this::addForm, this::getLabel,
				this::getGlosses, this::getMeanings, this::serialize);
	}

	public ULocale getLanguage()
	{
		return language;
	}

	public boolean contains(String form, char pos)
	{
		assert StringUtils.isNotBlank(form);
		final Lexicalization e = new Lexicalization(form, pos);
		return lexicalizations_index.containsKey(e);
	}

	public boolean contains(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);
		return meanings_index.containsKey(new Meaning(meaning));
	}

	public List<String> getMeanings(String form, char pos)
	{
		assert StringUtils.isNotBlank(form);

		// Get position in buffer from index
		final Lexicalization lexicalization = new Lexicalization(form, pos);
		if (!lexicalizations_index.containsKey(lexicalization))
			return new ArrayList<>();
		int position = lexicalizations_index.get(lexicalization);

		final int old_pos = lexicalizations_info.position();
		lexicalizations_info.position(position);

		final int num_meanings = Short.valueOf(lexicalizations_info.getShort()).intValue();
		final List<String> meanings = IntStream.range(0, num_meanings)
				.mapToObj(i ->
				{
					final int num_bytes = lexicalizations_info.getShort();
					byte[] bytes = new byte[num_bytes];
					lexicalizations_info.get(bytes);
					return new String(bytes, Charsets.US_ASCII);
				})
				.collect(toList());

		lexicalizations_info.position(old_pos);
		return meanings;
	}

	public Optional<String> getLabel(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);

		int position = getMeaningPosition(meaning);
		if (position == -1)
			return Optional.empty();

		final int old_pos = meanings_info.position();
		meanings_info.position(position);

		// skip number of glosses
		meanings_info.getShort();

		final int num_bytes = meanings_info.getShort();
		byte[] bytes = new byte[num_bytes];
		meanings_info.get(bytes);

		meanings_info.position(old_pos);
		return Optional.of(new String(bytes, Charsets.UTF_8).toLowerCase(Locale.ENGLISH));
	}

	public List<String> getGlosses(String meaning)
	{
		assert StringUtils.isNotBlank(meaning);

		int position = getMeaningPosition(meaning);
		if (position == -1)
			return new ArrayList<>();

		final int old_pos = meanings_info.position();
		meanings_info.position(position);

		final int num_glosses = meanings_info.getShort();
		{
			// Skip label
			final int num_bytes = meanings_info.getShort();
			byte[] bytes = new byte[num_bytes];
			meanings_info.get(bytes);
		}

		final List<String> glosses = IntStream.range(0, num_glosses)
				.mapToObj(i ->
				{
					final int num_bytes = meanings_info.getShort();
					byte[] bytes = new byte[num_bytes];
					meanings_info.get(bytes);
					return new String(bytes, Charsets.UTF_8);
				})
				.collect(toList());

		meanings_info.position(old_pos);
		return glosses;
	}

	private void serialize(Path file)
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

	@SuppressWarnings("UnusedReturnValue")
	private boolean addForm(String form, char pos, List<String> meanings)
	{
		assert StringUtils.isNotBlank(form) && meanings != null;
		meanings.removeIf(String::isBlank);

		Lexicalization lexicalization = new Lexicalization(form, pos);
		if (lexicalizations_index.containsKey(lexicalization))
			return false;

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

		if (lexicalizations_info.remaining() < num_bytes)
		{
			final ByteBuffer newBuf = ByteBuffer.allocate(lexicalizations_info.capacity() + BUFFER_SIZE_STEP);
			lexicalizations_info.flip();
			newBuf.put(lexicalizations_info);
			lexicalizations_info = newBuf;
		}

		// save offset of the first byte in this record
		final int position = lexicalizations_info.position();

		// add an integer indicating number of meanings
		lexicalizations_info.putShort((short) meanings.size()); // a short with max value 32767 should suffice

		// store the bytes for each meaning
		for (final byte[] bytes : bytes_list)
		{
			lexicalizations_info.put(bytes);
		}

		// Update index
		lexicalizations_index.put(lexicalization, position);
		return true;
	}

	@SuppressWarnings("UnusedReturnValue")
	private boolean addMeaning(final String meaning, final String label, List<String> glosses)
	{
		assert StringUtils.isNotBlank(meaning) && StringUtils.isNotBlank(label) && glosses != null;

		glosses.removeIf(String::isBlank);
		if (contains(meaning))
			return false;

		// create byte arrays for label and glosses
		final List<byte[]> bytes_list = new ArrayList<>(glosses.size() + 1);
		{
			final byte[] bytes_string = label.toLowerCase(Locale.ENGLISH).getBytes(Charsets.UTF_8);
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
		if (meanings_info.remaining() < num_bytes)
		{
			final ByteBuffer newBuf = ByteBuffer.allocate(meanings_info.capacity() + BUFFER_SIZE_STEP);
			meanings_info.flip();
			newBuf.put(meanings_info);
			meanings_info = newBuf;
		}

		// save offset of the first byte in this record
		final int position = meanings_info.position();

		// add an integer indicating number of glosses
		meanings_info.putShort((short) glosses.size()); // a short with max value 32767 should suffice

		// store the bytes for each gloss
		for (final byte[] bytes : bytes_list)
		{
			meanings_info.put(bytes);
		}

		updateMeaningsIndex(meaning, position);
		return true;
	}

	private int getMeaningPosition(String meaning)
	{
		final Meaning m = new Meaning(meaning);
		if (meanings_index.containsKey(m))
			return meanings_index.get(m);
		return -1;
	}

	private void updateMeaningsIndex(String meaning, int position)
	{
		meanings_index.put(new Meaning(meaning), position);
	}

	// Serializes byte arrays wrapped with ByteBuffer objects.
	// See https://stackoverflow.com/questions/3982704/how-to-serialize-bytebuffer
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		lexicalizations_position = lexicalizations_info.position();
		meanings_position = meanings_info.position();

		// write default properties
		out.defaultWriteObject();
		// write buffers capacity and data
		out.writeInt(lexicalizations_info.capacity());
		out.write(lexicalizations_info.array());
		out.writeInt(meanings_info.capacity());
		out.write(meanings_info.array());

		log.info("Serialized cache with " + lexicalizations_index.keySet().size() + " lexicalizations and " + meanings_index.keySet().size() + " meanings");
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
			lexicalizations_info = ByteBuffer.wrap(buffer, 0, bufferSize);
			lexicalizations_info.position(lexicalizations_position);
		}
		{
			int bufferSize = in.readInt();
			byte[] buffer = new byte[bufferSize];
			in.readFully(buffer, 0, bufferSize);
			meanings_info = ByteBuffer.wrap(buffer, 0, bufferSize);
			meanings_info.position(meanings_position);
		}

		log.info("Cache loaded with " + lexicalizations_index.keySet().size() + " forms and " + meanings_index.keySet().size() + " meanings");
	}
}
