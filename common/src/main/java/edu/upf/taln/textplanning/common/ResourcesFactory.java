package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.weighting.ContextWeighter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static edu.upf.taln.textplanning.core.utils.DebugUtils.LOGGING_STEP_SIZE;
import static java.util.stream.Collectors.*;

public class ResourcesFactory
{
	private final ULocale language;
	private final MeaningDictionary dictionary;
	private final BiFunction<String, ULocale, List<String>> glosses;
	private final SentenceVectors sentence_vectors;
	private final Vectors meaning_context_vectors;
	private final BiFunction<double[], double[], Double> word_vectors_similarity_function;
	private final BiFunction<String, String, OptionalDouble> meanings_similarity_function;

	private final static Logger log = LogManager.getLogger();

	public ResourcesFactory(ULocale language, Path dictionary_config) throws Exception
	{
		this(language, dictionary_config, null, null, null, null, null, null, null, null, null, null);
	}

	public ResourcesFactory(ULocale language, Path dictionary_config,
	                        BiFunction<String, ULocale, List<String>> glosses, Path idf_file,
	                        Path meaning_vectors_path, VectorType meaning_vectors_type,
	                        Path word_vectors_path, VectorType word_vectors_type,
	                        Path sentence_vectors_path, SentenceVectorType sentence_vectors_type,
	                        Path meaning_context_vectors_path, VectorType meaning_context_vectors_type) throws Exception
	{
		// Load ranking resources
		log.info("Loading resources for ranking");

		this.language = language;
		if (dictionary_config != null)
			dictionary = new BabelNetDictionary(dictionary_config);
		else
			dictionary = null;

		if (glosses != null)
			this.glosses = glosses;
		else if (dictionary != null)
			this.glosses = dictionary::getGlosses;
		else
			this.glosses = (s, l) -> List.of();

		if (meaning_vectors_type != null)
		{
			final Vectors meaning_vectors = getVectors(meaning_vectors_path, meaning_vectors_type, 300);
			meanings_similarity_function = new VectorsSimilarity(meaning_vectors, new CosineSimilarity());
		}
		else
			meanings_similarity_function = null;

		Vectors word_vectors = null;
		if (word_vectors_type != null)
			word_vectors = getVectors(word_vectors_path, word_vectors_type, 300);

		if (sentence_vectors_type != null)
		{
			switch (sentence_vectors_type)
			{
				case SIF:
				{
					if (word_vectors != null && idf_file != null)
					{
						final Map<String, Double> weights = getFrequencies(idf_file);
						final Double default_weight = Collections.min(weights.values());
						sentence_vectors = new SIFVectors(word_vectors, w -> weights.getOrDefault(w, default_weight));
						word_vectors_similarity_function = (SIFVectors) sentence_vectors;
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
						word_vectors_similarity_function = new CosineSimilarity();
					}
					else
						throw new Exception("Word vectors are required for BoW sentence vectors");
					break;
				}
				case Random:
				default:
				{
					sentence_vectors = new RandomVectors();
					word_vectors_similarity_function = new CosineSimilarity();
				}
			}
		}
		else
		{
			sentence_vectors = null;
			word_vectors_similarity_function = null;
		}

		if (meaning_context_vectors_type != null)
			meaning_context_vectors = getVectors(meaning_context_vectors_path, meaning_context_vectors_type, 300);
		else
			meaning_context_vectors = null;
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
			case SenseGlosses:
				return new SenseGlossesVectors(glosses, language, sentence_vectors);
			case Random:
			default:
				return new RandomVectors();
		}
	}

	public MeaningDictionary getDictionary() { return dictionary; }


	public SentenceVectors getSentenceVectors()
	{
		return sentence_vectors;
	}

	public BiFunction<String, String, OptionalDouble> getMeaningsSimilarity()
	{
		return meanings_similarity_function;
	}

	public Function<String, Double> getMeaningsWeighter(List<String> tokens, List<Candidate> candidates, int min_frequency)
	{
		Predicate<String> function_words_filter = (str) -> StopWordsFilter.test(str, language); // filter function and frequent words
		final List<String> context_tokens = tokens.stream()
				.distinct()
				.filter(function_words_filter)
				.collect(toList());
		context_tokens.removeIf(t -> Collections.frequency(tokens, t) < min_frequency);
		log.info("Context set to: " + context_tokens);

		return new ContextWeighter(candidates, meaning_context_vectors, sentence_vectors, w -> context_tokens, word_vectors_similarity_function);
	}

	public BiPredicate<String, String> getMeaningsFilter(List<Candidate> candidates)
	{
		return new DifferentMentionsFilter(candidates);
	}

	public Predicate<Candidate> getCandidatesFilter(List<Candidate> candidates, Function<String, Double> weighter, int num_first_meanings,
	                                                double context_threshold, Set<String> excluded_POS_Tags)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		// exclude function words for ranking, but be careful not to remove words just because they're frequent -e.g. stop words
		Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), language);
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, weighter, num_first_meanings, context_threshold);
		final Predicate<Candidate> pos_filter =	c ->  !excluded_POS_Tags.contains(c.getMention().getPOS());

		return top_filter.and(pos_filter).and(function_words_filter);
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



}
