package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;

public class Disambiguation
{
	private final static Logger log = LogManager.getLogger();

	private final static BiPredicate<Mention, Mention> spans_over =
			(m1, m2) -> m1.getSourceId().equals(m2.getSourceId()) &&
						m1.getSpan().getLeft() <= m2.getSpan().getLeft() &&
						m1.getSpan().getRight() >= m2.getSpan().getRight();


	public static Map<Mention, Candidate> disambiguate(List<Candidate> candidates)
	{
		// Uses default weight-based strategies for mention and candidate selection
		Set<Mention> selected_mentions = selectMaxWeightMentions(candidates);
		return disambiguate(candidates, selected_mentions::contains, Disambiguation::selectMaxWeightCandidate);
	}

	public static Map<Mention, Candidate> disambiguate(List<Candidate> candidates,
	                                                   Function<List<Candidate>, Optional<Candidate>> candidate_selector)
	{
		// Uses default weight-based strategies for mention and candidate selection
		Set<Mention> selected_mentions = selectMaxWeightMentions(candidates);
		return disambiguate(candidates, selected_mentions::contains, candidate_selector);
	}

	public static Map<Mention, Candidate> disambiguate(List<Candidate> candidates,
	                                                   Predicate<Mention> mention_selector,
	                                                   Function<List<Candidate>, Optional<Candidate>> candidate_selector)
	{
		// filter candidates by mention
		final List<Candidate> filtered_candidates = candidates.stream()
				.filter(c -> mention_selector.test(c.getMention()))
				.collect(toList());

		log.info("Selected multiwords: " + filtered_candidates.stream()
				.map(Candidate::getMention)
				.distinct()
				.filter(Mention::isMultiWord)
				.map(Mention::toString)
				.collect(joining("\n\t", "\n\t", "\n")));

		// disambiguate
		return selectCandidates(filtered_candidates, candidate_selector);
	}

	// Default strategy for mention selection
	private static Set<Mention> selectMaxWeightMentions(List<Candidate> candidates)
	{
		final List<Mention> mentions = candidates.stream()
				.map(Candidate::getMention)
				.distinct()
				.collect(toList());

		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		final Map<Mention, Double> mentions2weights = mentions2candidates.entrySet().stream()
						.map(p -> Pair.of(p.getKey(), p.getValue().stream()
								.map(Candidate::getWeight)
								.mapToDouble(o -> o.orElse(0.0))
								.max().orElse(0.0)))
				.collect(toMap(Pair::getLeft, Pair::getRight));

		final Map<Mention, List<Mention>> mentions2subsumed = mentions.stream()
				.collect(toMap(m -> m, m1 -> mentions.stream()
						.filter(m2 -> m1 != m2)
						.filter(m2 -> spans_over.test(m1, m2))
						.collect(toList())));
		final Map<Mention, List<Mention>> mentions2subsumers = mentions.stream()
				.collect(toMap(m -> m, m1 -> mentions.stream()
						.filter(m2 -> m1 != m2)
						.filter(m2 -> spans_over.test(m2, m1))
						.collect(toList())));

		// Select mentions which don't have any subsumer or subsumed mention with a higher weight
		BiPredicate<Mention, Mention> weights_more = (m1, m2) -> mentions2weights.get(m1) > mentions2weights.get(m2);
		final Map<Mention, List<Mention>> top_mentions = mentions.stream()
				.filter(m1 -> mentions2subsumed.get(m1).stream().allMatch(m2 -> weights_more.test(m1, m2)))
				.filter(m1 -> mentions2subsumers.get(m1).stream().noneMatch(m2 -> weights_more.test(m2, m1)))
				.collect(toMap(m -> m, mentions2subsumed::get));

		return top_mentions.keySet();
	}

	// Default strategy for candidate selection
	private static Optional<Candidate> selectMaxWeightCandidate(List<Candidate> candidates)
	{
		return candidates.stream().max(Comparator.comparingDouble(c -> c.getWeight().orElse(0.0)));
	}

	private static Map<Mention, Candidate> selectCandidates(List<Candidate> candidates,
	                                                        Function<List<Candidate>, Optional<Candidate>> selector)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		Map<Mention, Candidate> selected = new HashMap<>();
		for (Mention m : mentions2candidates.keySet())
		{
			final List<Candidate> m_candidates = mentions2candidates.get(m);
			final Optional<Candidate> max = selector.apply(m_candidates);
			max.ifPresent(c -> selected.put(m, c));
		}

		return selected;
	}
}
