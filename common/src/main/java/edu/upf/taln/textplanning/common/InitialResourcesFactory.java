package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private final MeaningDictionary dictionary;
	private final Set<String> bias_meanings;
	private final SentenceVectors sentence_vectors;
	private final BiFunction<double[], double[], Double> sentence_similarity_function;
	private final SimilarityFunction meanings_similarity_function;

	private final static Logger log = LogManager.getLogger();

	public static class BiasResources
	{
		// Resources for domain-based bias
		public Path bias_meanings_path = null;

		// Resources for context-based bias
		public Path word_vectors_path = null;
		public VectorType word_vectors_type = null;
		public Path sentence_vectors_path = null;
		public SentenceVectorType sentence_vectors_type = null;
		public Path idf_file = null; // for SIF sentence vectors
	}

	public static class SimilarityResources
	{
		public Path meaning_vectors_path = null;
		public VectorType meaning_vectors_type = null;
	}


	public InitialResourcesFactory(ULocale language, Path dictionary_config) throws Exception
	{
		this(language, dictionary_config, new BiasResources(), new SimilarityResources());
	}


	public InitialResourcesFactory(ULocale language, Path dictionary_config,
	                               BiasResources bias_resources, SimilarityResources sim_resources) throws Exception
	{
		// Load ranking resources
		log.info("Loading initial resources");

		this.language = language;
		if (dictionary_config != null)
			dictionary = new BabelNetDictionary(dictionary_config);
		else
			dictionary = null;

		// Bias resources
		Vectors word_vectors = null;
		if (bias_resources.word_vectors_type != null)
			word_vectors = getVectors(bias_resources.word_vectors_path, bias_resources.word_vectors_type, 300);

		if (bias_resources.sentence_vectors_type != null)
		{
			switch (bias_resources.sentence_vectors_type)
			{
				case SIF:
				{
					if (word_vectors != null && bias_resources.idf_file != null)
					{
						final Map<String, Double> weights = getFrequencies(bias_resources.idf_file);
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

		if (bias_resources.bias_meanings_path != null)
		{
			bias_meanings = Arrays.stream(FileUtils.readTextFile(bias_resources.bias_meanings_path).split("\n"))
					.filter(not(String::isEmpty))
					.collect(toSet());
		}
		else
			bias_meanings = null;

		// Similarity resources
		if (sim_resources.meaning_vectors_type != null)
		{
			final Vectors meaning_vectors = getVectors(sim_resources.meaning_vectors_path, sim_resources.meaning_vectors_type, 300);
			meanings_similarity_function = new VectorsSimilarity(meaning_vectors, new CosineSimilarity());
		}
		else
			meanings_similarity_function = null;
	}

	public ULocale getLanguage() { return language; }

	public BiFunction<double[], double[], Double> getSentenceSimilarityFunction()
	{
		return sentence_similarity_function;
	}

	public MeaningDictionary getDictionary() { return dictionary; }

	public Set<String> getBiaseanings() { return bias_meanings; }

	public SentenceVectors getSentenceVectors()
	{
		return sentence_vectors;
	}

	public SimilarityFunction getSimilarityFunction()
	{
		return meanings_similarity_function;
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

	private Vectors getVectors(Path location, VectorType type, int num_dimensions) throws Exception
	{
		switch (type)
		{
			case Text_Glove:
			case Text_Word2vec:
				return new TextVectors(location, type);
			case Binary_Word2vec:
				return new Word2VecVectors(location);
			case Binary_RandomAccess:
				return new RandomAccessFileVectors(location, num_dimensions);
			case Random:
			default:
				return new RandomVectors();
		}
	}

}
