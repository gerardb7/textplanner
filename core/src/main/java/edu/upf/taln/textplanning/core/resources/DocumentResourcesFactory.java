package edu.upf.taln.textplanning.core.resources;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.bias.ContextBias;
import edu.upf.taln.textplanning.core.bias.ContextFunction;
import edu.upf.taln.textplanning.core.bias.DomainBias;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.SenseGlossesVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

	public DocumentResourcesFactory(InitialResourcesFactory factory, Options options,
	                                List<Candidate> candidates,
	                                ContextFunction context)
	{
		this(factory, options, candidates, context, s -> factory.getDictionary().getGlosses(s, factory.getLanguage()));
	}

	public DocumentResourcesFactory(InitialResourcesFactory factory, Options options,
	                                List<Candidate> candidates,
	                                ContextFunction context,
	                                Map<String, List<String>> glosses)
	{
		this(factory, options, candidates, context, glosses::get);
	}

	private DocumentResourcesFactory(InitialResourcesFactory factory, Options options,
	                                List<Candidate> candidates,
	                                ContextFunction context,
	                                Function<String, List<String>> glosses_function)
	{
		// Glosses
		final SentenceVectors sentence_vectors = factory.getSentenceVectors();
		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());
		// include bias meanings too
		final Set<String> bias_meanings = factory.getBiasMeanings();
		if (factory.getBiasFunctionType() == BiasFunction.Type.Domain && bias_meanings != null)
			meanings.addAll(bias_meanings);
		Vectors glosses_vectors = new SenseGlossesVectors(factory.getLanguage(), meanings, glosses_function, sentence_vectors);

		// Bias function
		if (factory.getBiasFunctionType() != null)
		{
			if (factory.getBiasFunctionType() == BiasFunction.Type.Context)
			{
				bias = new ContextBias(candidates, glosses_vectors, sentence_vectors, context, factory.getSentenceSimilarityFunction());
			}
			else if (factory.getBiasFunctionType() == BiasFunction.Type.Domain && bias_meanings != null && !bias_meanings.isEmpty())
			{
				bias = new DomainBias(candidates, bias_meanings, glosses_vectors, factory.getSentenceSimilarityFunction());
			}
			else
				bias = null;
		}
		else
			bias = null;

		// Similarity function
		if (factory.getMeaningVectorsType() == Vectors.VectorType.SenseGlosses)
		{
			final Vectors meaning_vectors = new SenseGlossesVectors(factory.getLanguage(), meanings, glosses_function, sentence_vectors);
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
		final Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurfaceForm(), language);
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, weighter, options.num_first_meanings, options.min_bias_threshold);
		final Predicate<Candidate> pos_filter =	c -> options.ranking_POS_Tags.contains(c.getMention().getPOS());

		return top_filter.and(pos_filter).and(function_words_filter);
	}
}
