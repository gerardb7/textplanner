package edu.upf.taln.textplanning.core;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.disambiguation.DisambiguationStrategies;
import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.ranking.Ranker;
import edu.upf.taln.textplanning.core.resources.DocumentResourcesFactory;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

public class Disambiguator
{
	private final static Logger log = LogManager.getLogger();

	public static void rankMeanings(Corpora.Corpus corpus, DocumentResourcesFactory resources, Options options)
	{
		log.info(options);

		final List<Corpora.Sentence> sentences = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.collect(toList());

		rankMeanings(sentences, resources, options);
	}

	public static void rankMeanings(Corpora.Text text, DocumentResourcesFactory resources, Options options)
	{
		rankMeanings(text.sentences, resources, options);
	}

	public static void rankMeanings(List<Corpora.Sentence> sentences, DocumentResourcesFactory resources, Options options)
	{
		final BiasFunction bias = resources.getBiasFunction();
		final SimilarityFunction similarity = resources.getSimilarityFunction();
		final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter();
		final BiPredicate<String, String> similarity_filter = resources.getMeaningPairsSimilarityFilter();

		final List<Candidate> candidates = sentences.stream()
				.flatMap(s -> s.candidate_meanings.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		// Log stats
		final int num_mentions = sentences.stream()
				.map(s -> s.candidate_meanings)
				.mapToInt(m -> m.keySet().size())
				.sum();
		final long num_filtered_candidates = candidates.stream()
				.filter(candidates_filter)
				.count();
		final long num_meanings = candidates.stream()
				.filter(candidates_filter)
				.map(Candidate::getMeaning)
				.distinct()
				.count();
		log.info("Ranking texts with " + num_mentions + " mentions, " +
				candidates.size() + " candidates, " +
				num_filtered_candidates + " candidates after filtering, and " +
				num_meanings + " distinct meanings");

		// Rank candidates
		rankMeanings(candidates, candidates_filter, similarity_filter, bias, similarity, options);
	}

	public static void disambiguate(Corpora.Corpus corpus, Options options)
	{
		corpus.texts.forEach(t -> disambiguate(t, options));
	}

	public static void disambiguate(Corpora.Text text, Options options)
	{
		final List<Candidate> candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.candidate_meanings.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		DisambiguationStrategies disambiguation = new DisambiguationStrategies(options.disambiguation_lambda);
		final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates);
		disambiguated.forEach((m, c) -> text.sentences.stream()
				.filter(s -> s.id.equals(m.getContextId()))
				.findFirst()
				.orElseThrow()
				.disambiguated_meanings.put(m, c));
	}

	/**
	 * Ranks set of candidate meanings
	 */
	private static void rankMeanings(List<Candidate> candidates,
	                                Predicate<Candidate> candidates_to_rank_filter,
	                                BiPredicate<String, String> meanings_similarity_filter,
	                                BiasFunction bias, SimilarityFunction similarity, Options o)
	{
		log.info("*Ranking meanings*");
		Stopwatch timer = Stopwatch.createStarted();

		// Rank candidates that pass the filter, e.g. have certain Tag
		final List<Candidate> filtered_candidates = candidates.stream()
				.filter(candidates_to_rank_filter)
				.collect(toList());

		// Exclude meanings for which similarity function is not defined
		final List<Meaning> ranked_meanings = filtered_candidates.stream()
				.map(Candidate::getMeaning)
				.distinct()
				.filter(m -> similarity.isDefined(m.getReference()))
				.collect(toList());

		final List<String> references = ranked_meanings.stream()
				.map(Meaning::getReference)
				.collect(toList());

		if (references.isEmpty())
			return;

		double[] ranking = Ranker.rank(references, bias, similarity, true, meanings_similarity_filter,
				o.sim_threshold, o.damping_meanings, o.stopping_threshold);
		final double[] rebased_ranking = MatrixFactory.rebase(ranking);

		// Assign weights to candidates: rank values for those with a ranked meaning
		candidates.forEach(candidate ->
		{
			final String reference = candidate.getMeaning().getReference();

			// If reference is ranked, assign ranking value
			if (references.contains(reference))
			{
				int i = references.indexOf(candidate.getMeaning().getReference());
				candidate.setWeight(rebased_ranking[i]);
			}
		});

		log.info("Ranking completed in " + timer.stop() + "\n");

		// for debugging purposes
		final List<String> labels = ranked_meanings.stream()
				.map(Meaning::toString)
				.collect(toList());
		final double[] rebased_bias = MatrixFactory.rebase(references.stream().mapToDouble(bias::apply).toArray());
		log.debug("Ranking of meanings:\n" + DebugUtils.printRank(rebased_ranking, references.size(), rebased_bias, labels));
	}
}
