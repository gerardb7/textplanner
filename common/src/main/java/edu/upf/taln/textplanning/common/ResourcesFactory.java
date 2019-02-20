package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static edu.upf.taln.textplanning.core.utils.DebugUtils.LOGGING_STEP_SIZE;
import static java.util.stream.Collectors.toMap;

public class ResourcesFactory
{
	private final ULocale language;
	private final MeaningDictionary dictionary;
	private final Vectors sense_vectors;
	private final Vectors word_vectors;
	private final SentenceVectors sentence_vectors;
	private final Vectors sense_context_vectors;
	private final BiFunction<double[], double[], Double> similarity_function;

	private final static Logger log = LogManager.getLogger();

	public ResourcesFactory(ULocale language, Path dictionary_config) throws Exception
	{
		this(language, dictionary_config, null, null, null, null, null, null, null, null, null);
	}

	public ResourcesFactory(ULocale language, Path dictionary_config, Path idf_file,
	                        Path sense_vectors_path, VectorType sense_vectors_type,
	                        Path word_vectors_path, VectorType word_vectors_type,
	                        Path sentence_vectors_path, SentenceVectorType sentence_vectors_type,
	                        Path sense_context_vectors_path, VectorType sense_context_vectors_type) throws Exception
	{
		// Load ranking resources
		log.info("Loading resources for ranking");

		this.language = language;
		if (dictionary_config != null)
			dictionary = new BabelNetDictionary(dictionary_config);
		else
			dictionary = null;

		if (sense_vectors_type != null)
			sense_vectors = get(sense_vectors_path, sense_vectors_type, 300);
		else
			sense_vectors = null;

		if (word_vectors_type != null)
			word_vectors = get(word_vectors_path, word_vectors_type, 300);
		else word_vectors = null;

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
		else
		{
			sentence_vectors = null;
			similarity_function = null;
		}

		if (sense_context_vectors_type != null)
			sense_context_vectors = get(sense_context_vectors_path, sense_context_vectors_type, 300);
		else
			sense_context_vectors = null;

	}

	public Vectors get(Path location, VectorType type, int num_dimensions) throws Exception
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
			case SenseGlosses:
				return new SenseGlossesVectors(dictionary, language, sentence_vectors);
			case Random:
			default:
				return new RandomVectors();
		}
	}

	public MeaningDictionary getDictionary() { return dictionary; }

	public Vectors getSenseVectors()
	{
		return sense_vectors;
	}

	public Vectors getWordVectors()
	{
		return word_vectors;
	}

	public SentenceVectors getSentenceVectors()
	{
		return sentence_vectors;
	}

	public Vectors getSenseContextVectors()
	{
		return sense_context_vectors;
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
