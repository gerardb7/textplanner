package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.babelnet.BabelNetDictionary;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.CachedDictionary;
import edu.upf.taln.textplanning.core.structures.CompactDictionary;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static edu.upf.taln.textplanning.core.utils.DebugUtils.LOGGING_STEP_SIZE;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class InitialResourcesFactory
{
	private final ULocale language;
	private final PlanningProperties properties;
	private final CompactDictionary cache;
	private final MeaningDictionary dictionary;
	private final BiasFunction.Type bias_function_type;
	private final Set<String> bias_meanings;
	private final SentenceVectors sentence_vectors;
	private final BiFunction<double[], double[], Double> sentence_similarity_function;
	private final VectorType meaning_vectors_type;
	private final Vectors meaning_vectors;
	private static final int NUM_DIMENSIONS = 300;
	private static final Logger log = LogManager.getLogger();

	public InitialResourcesFactory(ULocale language, PlanningProperties properties) throws Exception
	{
		// Load ranking params
		log.info("Loading initial params");

		this.language = language;
		this.properties = properties;

		if (properties.getDictionaryCache() != null && !properties.getDictionaryCache().toString().isEmpty())
		{
			if (Files.exists(properties.getDictionaryCache()))
				cache = (CompactDictionary) Serializer.deserialize(properties.getDictionaryCache());
			else
				cache = new CompactDictionary(language);
		}
		else
			cache = null;

		if (properties.getDictionary() != null)
		{
			MeaningDictionary babelnet = new BabelNetDictionary(properties.getDictionary());

			if (cache != null)
				dictionary =  new CachedDictionary(babelnet, cache);
			else
				dictionary = babelnet;
		}
		else
		{
			if (cache != null)
				dictionary = new CachedDictionary(cache);
			else
				dictionary = null;
		}

		// Bias params
		bias_function_type = properties.getBiasFunction();

		Vectors word_vectors = null;
		if (properties.getWordVectorsType() != null)
			word_vectors = createVectorsFromPath(properties.getWordVectorsType(), properties.getWordVectorsPath());

		if (properties.getSentenceVectorsType() != null)
		{
			switch (properties.getSentenceVectorsType() )
			{
				case SIF:
				{
					if (word_vectors != null && properties.getIdfPath() != null)
					{
						final Map<String, Double> weights = getFrequencies(properties.getIdfPath());
						final Double default_weight = Collections.min(weights.values());
						sentence_vectors = new SIFVectors(word_vectors, w -> weights.getOrDefault(w, default_weight));
						sentence_similarity_function = (SIFVectors) sentence_vectors;
					}
					else
						throw new Exception("Word vectors and idf file are required for SIF sentence vectors");
					break;
				}
				case BoW:
				{
					if (word_vectors != null)
					{
						sentence_vectors = new BoWVectors(word_vectors);
						sentence_similarity_function = new CosineSimilarity();
					}
					else
						throw new Exception("Word vectors are required for BoW sentence vectors");
					break;
				}
				case Random:
				default:
				{
					sentence_vectors = new RandomVectors();
					sentence_similarity_function = new CosineSimilarity();
				}
			}
		}
		else
		{
			sentence_vectors = null;
			sentence_similarity_function = null;
		}

		if (properties.getMeaningsPath() != null)
		{
			bias_meanings = Arrays.stream(FileUtils.readTextFile(properties.getMeaningsPath()).split("\n"))
					.filter(not(String::isEmpty))
					.map(l -> l.split("\t")[0])
					.map(String::trim)
					.collect(toSet());
		}
		else
			bias_meanings = null;

		// Similarity params
		meaning_vectors_type = properties.getSenseVectorsType();
		if (properties.getSenseVectorsType() != null && properties.getSenseVectorsPath() != null)
			meaning_vectors = createVectorsFromPath(properties.getSenseVectorsType(), properties.getSenseVectorsPath());
		else
			meaning_vectors = null;
	}

	public ULocale getLanguage() { return language; }

	public MeaningDictionary getDictionary() { return dictionary; }

	public CompactDictionary getCache() { return cache; }

	public BiasFunction.Type getBiasFunctionType()
	{
		return bias_function_type;
	}

	public Set<String> getBiasMeanings() { return bias_meanings; }

	public BiFunction<double[], double[], Double> getSentenceSimilarityFunction()
	{
		return sentence_similarity_function;
	}

	public SentenceVectors getSentenceVectors()
	{
		return sentence_vectors;
	}

	// Reads text-based IDF file
	private static Map<String, Double> getFrequencies(Path freqs_file)
	{
		log.info("Reading idf scores");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);

		final String text = FileUtils.readTextFile(freqs_file);
		if (text != null)
		{
			Map<String, Double> freqs = Arrays.stream(text.split("\n"))
					.parallel()
					.peek(l -> reporter.report()) // report number of threads
					.map(l -> l.split(" "))
					.peek(id ->
					{
						long i = counter.incrementAndGet();
						if (i % LOGGING_STEP_SIZE == 0) log.info(i + " freqs read");
					})
					.collect(toMap(a -> a[0], a -> Double.valueOf(a[1])));
			log.info(freqs.size() + " values read in " + timer.stop());
			return freqs;
		}

		return new HashMap<>();
	}

	public VectorType getMeaningVectorsType()
	{
		return meaning_vectors_type;
	}

	public Vectors getMeaningVectors()
	{
		return meaning_vectors;
	}

	private Vectors createVectorsFromPath(VectorType type, Path location) throws Exception
	{
		switch (type)
		{
			case Text_Glove:
			case Text_Word2vec:
				return new TextVectors(location, type);
			case Binary_Word2vec:
				return new Word2VecVectors(location);
			case Binary_RandomAccess:
				return new RandomAccessFileVectors(location, NUM_DIMENSIONS);
			case Random:
			default:
				return new RandomVectors();
		}
	}

	public void serializeCache() throws IOException
	{
		if (cache != null && properties.getDictionaryCache() != null)
		{
			if (cache.num_added_forms == 0 && cache.num_added_meanings == 0)
				log.info("No forms nor meanings added to the dictionary, skipping serialization");
			else
				Serializer.serialize(cache, properties.getDictionaryCache());
		}
	}
}
