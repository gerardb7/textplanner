package edu.upf.taln.textplanning.common;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.bias.DomainBias;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.bias.ContextBias;
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
	private final DifferentMentionsFilter meaning_pairs_similarity_filter;
	private final BiasFunction bias;
	private final SimilarityFunction similarity;
	private final Predicate<Candidate> candidates_filter;
	private final static Logger log = LogManager.getLogger();

	public DocumentResourcesFactory(InitialResourcesFactory factory, Options options,
	                                List<Candidate> candidates, List<String> tokens,
	                                Map<String, List<String>> glosses)
	{
		// Glosses
		Function<String, List<String>> glosses_function;
		if (glosses == null)
			glosses_function = s -> factory.getDictionary().getGlosses(s, factory.getLanguage());
		else
			glosses_function = s -> glosses.getOrDefault(s, List.of());
		Vectors glosses_vectors = new SenseGlossesVectors(factory.getLanguage(), glosses_function, factory.getSentenceVectors());

		// Bias function
		if (factory.getBiasFunctionType() != null)
		{
			if (factory.getBiasFunctionType() == BiasFunction.Type.Context)
			{
				// Contexts
				final Predicate<String> stop_words_filter = (str) -> StopWordsFilter.test(str, factory.getLanguage()); // meaning_pairs_similarity_filter function and frequent words
				final List<String> context_tokens = tokens.stream()
						.distinct()
						.filter(stop_words_filter)
						.collect(toList());
				context_tokens.removeIf(t -> Collections.frequency(tokens, t) < options.min_context_freq);
				log.info("Context set to: " + context_tokens);

				bias = new ContextBias(candidates, glosses_vectors, factory.getSentenceVectors(), w -> context_tokens,
						factory.getSentenceSimilarityFunction());
			}
			else if (factory.getBiasFunctionType() == BiasFunction.Type.Domain)
			{
				bias = new DomainBias(candidates, factory.getBiasMeanings(), glosses_vectors, factory.getSentenceSimilarityFunction());
			}
			else
				bias = null;
		}
		else
			bias = null;

		// Similarity function
		if (factory.getMeaningVectorsType() == Vectors.VectorType.SenseGlosses)
		{
			final Vectors meaning_vectors = new SenseGlossesVectors(factory.getLanguage(), glosses_function, factory.getSentenceVectors());
			similarity = new VectorsSimilarity(meaning_vectors, new CosineSimilarity());
		}
		else
			similarity = new VectorsSimilarity(factory.getMeaningVectors(), new CosineSimilarity());

		// Filters
		meaning_pairs_similarity_filter = new DifferentMentionsFilter(candidates);
		candidates_filter = createCandidatesFilter(candidates, bias, options, factory.getLanguage());

	}

	public BiasFunction getBiasFunction() { return bias; }

	public SimilarityFunction getSimilarityFunction() { return similarity; }

	public BiPredicate<String, String> getMeaningPairsSimilarityFilter() { return meaning_pairs_similarity_filter; }

	public Predicate<Candidate> getCandidatesFilter() { return candidates_filter; }

	private static Predicate<Candidate> createCandidatesFilter(List<Candidate> candidates, Function<String, Double> weighter, Options options, ULocale language)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));
		// exclude function words for ranking, but be careful not to remove words just because they're frequent -e.g. stop words
		final Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), language);
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, weighter, options.num_first_meanings, options.min_bias_threshold);
		final Predicate<Candidate> pos_filter =	c ->  !options.excluded_ranking_POS_Tags.contains(c.getMention().getPOS());

		return top_filter.and(pos_filter).and(function_words_filter);
	}
}
