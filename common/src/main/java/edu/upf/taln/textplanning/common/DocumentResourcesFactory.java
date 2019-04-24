package edu.upf.taln.textplanning.common;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.weighting.ContextWeighter;
import edu.upf.taln.textplanning.core.weighting.WeightFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class DocumentResourcesFactory
{
	private final DifferentMentionsFilter filter;
	private final WeightFunction weighter;
	private final Predicate<Candidate> candidates_filter;
	private final static Logger log = LogManager.getLogger();

	public DocumentResourcesFactory(InitialResourcesFactory factory, Options options,
	                                List<Candidate> candidates, List<String> tokens, Map<String, List<String>> glosses)
	{

		// Glosses
		Function<String, List<String>> glosses_function;
		if (glosses == null)
			glosses_function = s -> factory.getDictionary().getGlosses(s, factory.getLanguage());
		else
			glosses_function = s -> glosses.computeIfAbsent(s, k -> List.of());
		Vectors meaning_context_vectors = getVectors(factory, 300, glosses_function);

		// Contexts
		final Predicate<String> stop_words_filter = (str) -> StopWordsFilter.test(str, factory.getLanguage()); // filter function and frequent words
		final List<String> context_tokens = tokens.stream()
				.distinct()
				.filter(stop_words_filter)
				.collect(toList());
		context_tokens.removeIf(t -> Collections.frequency(tokens, t) < options.min_context_freq);
		log.info("Context set to: " + context_tokens);

		// Filters and weighter
		filter = new DifferentMentionsFilter(candidates);
		weighter = new ContextWeighter(candidates, meaning_context_vectors, factory.getSentenceVectors(), w -> context_tokens,
				factory.getSentenceSimilarityFunction());
		candidates_filter = createCandidatesFilter(candidates, weighter, options, factory.getLanguage());
	}

	public WeightFunction getMeaningsWeighter() { return weighter; }

	public BiPredicate<String, String> getMeaningsFilter() { return filter; }

	public Predicate<Candidate> getCandidatesFilter() { return candidates_filter; }

	private static Predicate<Candidate> createCandidatesFilter(List<Candidate> candidates, Function<String, Double> weighter, Options options, ULocale language)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));
		// exclude function words for ranking, but be careful not to remove words just because they're frequent -e.g. stop words
		final Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), language);
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, weighter, options.num_first_meanings, options.context_threshold);
		final Predicate<Candidate> pos_filter =	c ->  !options.excluded_POS_Tags.contains(c.getMention().getPOS());

		return top_filter.and(pos_filter).and(function_words_filter);
	}

	private static Vectors getVectors(InitialResourcesFactory factory, int num_dimensions, Function<String, List<String>> glosses)
	{
		try
		{
			switch (factory.getMeaningContextVectorsType())
			{
				case Text_Glove:
				case Text_Word2vec:
					return new TextVectors(factory.getMeaningContextVectorsPath(), factory.getMeaningContextVectorsType());
				case Binary_Word2vec:
					return new Word2VecVectors(factory.getMeaningContextVectorsPath());
				case Binary_RandomAccess:
					return new RandomAccessFileVectors(factory.getMeaningContextVectorsPath(), num_dimensions);
				case SenseGlosses:
					return new SenseGlossesVectors(factory.getLanguage(), glosses, factory.getSentenceVectors());
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
}
