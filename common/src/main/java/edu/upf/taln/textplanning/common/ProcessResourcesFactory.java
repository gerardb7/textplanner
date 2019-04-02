package edu.upf.taln.textplanning.common;

import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.weighting.ContextWeighter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class ProcessResourcesFactory
{
	private final InitialResourcesFactory factory;
	private final Options options;
	private final Vectors meaning_context_vectors;
	private final List<Candidate> candidates;
	private final List<String> tokens;
	private final Function<String, List<String>> glosses;
	private final static Logger log = LogManager.getLogger();

	public ProcessResourcesFactory(InitialResourcesFactory factory, Options options,
	                               List<Candidate> candidates, List<String> tokens, Map<String, List<String>> glosses)
	{
		// Load ranking resources
		log.info("Loading process resources");
		this.factory = factory;
		this.options = options;
		this.candidates = candidates;
		this.tokens = tokens;
		if (glosses == null)
			this.glosses = s -> factory.getDictionary().getGlosses(s, factory.getLanguage());
		else
			this.glosses = s -> glosses.computeIfAbsent(s, k -> new ArrayList<>());
		this.meaning_context_vectors = getVectors(factory.getMeaningContextVectorsPath(), factory.getMeaningContextVectorsType(), 300);
	}

	private Vectors getVectors(Path location, Vectors.VectorType type, int num_dimensions)
	{
		try
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
					return new SenseGlossesVectors(factory.getLanguage(), this.glosses, factory.getSentenceVectors());
				case Random:
				default:
					return new RandomVectors();
			}
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public Function<String, Double> getMeaningsWeighter()
	{
		Predicate<String> function_words_filter = (str) -> StopWordsFilter.test(str, factory.getLanguage()); // filter function and frequent words
		final List<String> context_tokens = tokens.stream()
				.distinct()
				.filter(function_words_filter)
				.collect(toList());
		context_tokens.removeIf(t -> Collections.frequency(tokens, t) < options.min_context_freq);
		log.info("Context set to: " + context_tokens);

		return new ContextWeighter(candidates, meaning_context_vectors, factory.getSentenceVectors(), w -> context_tokens,
				factory.getSentenceSimilarityFunction());
	}

	public BiPredicate<String, String> getMeaningsFilter()
	{
		return new DifferentMentionsFilter(candidates);
	}

	public Predicate<Candidate> getCandidatesFilter(Function<String, Double> weighter)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		// exclude function words for ranking, but be careful not to remove words just because they're frequent -e.g. stop words
		Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), factory.getLanguage());
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, weighter, options.num_first_meanings, options.context_threshold);
		final Predicate<Candidate> pos_filter =	c ->  !options.excluded_POS_Tags.contains(c.getMention().getPOS());

		return top_filter.and(pos_filter).and(function_words_filter);
	}
}
