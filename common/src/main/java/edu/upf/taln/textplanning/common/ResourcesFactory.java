package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static edu.upf.taln.textplanning.core.utils.DebugUtils.LOGGING_STEP_SIZE;
import static java.util.stream.Collectors.toMap;

public class ResourcesFactory
{
	private MeaningDictionary dictionary = null;
	private final Predicate<Mention> stop_words_filter;
	private Vectors word_vectors = null;
	private Vectors sense_vectors = null;
	private Vectors sense_context_vectors = null;
	private SentenceVectors sentence_vectors;
	private BiFunction<double[], double[], Double> similarity_function = null;

	private final static Logger log = LogManager.getLogger();

	public ResourcesFactory(Path dictionary_config) throws Exception
	{
		this(dictionary_config, null, null, null, null, null, null, null, null, null, null);
	}

	public ResourcesFactory(Path dictionary_config, Path idf_file, Path stop_words_file,
	                        Path sentence_vectors_path, SentenceVectors.VectorType sentence_vectors_type,
	                        Path word_vectors_path, Vectors.VectorType word_vectors_type,
	                        Path sense_context_vectors_path, Vectors.VectorType sense_context_vectors_type,
	                        Path sense_vectors_path, Vectors.VectorType sense_vectors_type) throws Exception
	{
		// Load ranking resources
		log.info("Loading resources for ranking");

		if (dictionary_config != null)
			dictionary = new BabelNetDictionary(dictionary_config);
		if (stop_words_file != null)
			stop_words_filter = new StopWordsFilter(stop_words_file);
		else
			stop_words_filter = (m) -> true;
		if (word_vectors_type != null)
			word_vectors = Vectors.get(word_vectors_path, word_vectors_type, 300);
		if (sense_context_vectors_type != null)
			sense_context_vectors = Vectors.get(sense_context_vectors_path, sense_context_vectors_type, 300);
		if (sense_vectors_type != null)
			sense_vectors = Vectors.get(sense_vectors_path, sense_vectors_type, 300);

		if (sentence_vectors_type != null)
		{
			switch (sentence_vectors_type)
			{
				case SIF:
				{
					if (word_vectors != null && idf_file != null)
					{
						final Map<String, Double> weights = getWeights(idf_file);
						final Double default_weight = Collections.min(weights.values());
						sentence_vectors = new SIFVectors(word_vectors, w -> weights.getOrDefault(w, default_weight));
						similarity_function = (SIFVectors) sentence_vectors;
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
						similarity_function = new CosineSimilarity();
					}
					else
						throw new Exception("Word vectors are required for BoW sentence vectors");
					break;
				}
				case Random:
				default:
				{
					sentence_vectors = new RandomVectors();
					similarity_function = new CosineSimilarity();
				}
			}
		}
	}

	public MeaningDictionary getDictionary() { return dictionary; }

	public Predicate<Mention> getStopWordsFilter() { return stop_words_filter; }

	public Vectors getWordVectors()
	{
		return word_vectors;
	}

	public Vectors getSenseVectors()
	{
		return sense_vectors;
	}

	public Vectors getSenseContextVectors()
	{
		return sense_context_vectors;
	}

	public SentenceVectors getSentenceVectors()
	{
		return sentence_vectors;
	}


	public BiFunction<double[], double[], Double> getSimilarityFunction()
	{
		return similarity_function;
	}

	// Reads text-based IDF file
	private static Map<String, Double> getWeights(Path weights_file)
	{
		log.info("Reading idf scores");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);

		Map<String, Double> weights = Arrays.stream(FileUtils.readTextFile(weights_file).split("\n"))
				.parallel()
				.peek(l -> reporter.report()) // report number of threads
				.map(l -> l.split(" "))
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % LOGGING_STEP_SIZE == 0) log.info(i + " weights read");
				})
				.collect(toMap(a -> a[0], a -> Double.valueOf(a[1])));
		log.info(weights.size() + " weights read in " + timer.stop());

		return weights;
	}
}
