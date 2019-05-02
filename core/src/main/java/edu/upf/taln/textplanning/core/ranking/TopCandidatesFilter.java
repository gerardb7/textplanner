package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;

/**
 * For each mention, reduces its set of candidates to the best items according to an evaluation function
 */
public class TopCandidatesFilter implements Predicate<Candidate>
{
	private final List<Candidate> selected_candidates;

	public TopCandidatesFilter(Map<Mention, List<Candidate>> candidate_sets, Function<String, Double> eval, int top_k, double threshold)
	{
		selected_candidates = filter(candidate_sets, eval, top_k, threshold);
	}

	@Override
	public boolean test(Candidate candidate)
	{
		return selected_candidates.contains(candidate);
	}


	private static List<Candidate> filter(Map<Mention, List<Candidate>> candidate_sets, Function<String, Double> eval, int top_k, double threshold)
	{
		return candidate_sets.keySet().stream()
				.sorted()
				.map(candidate_sets::get)
				.map(l -> limit(l, eval, top_k, threshold))
				.flatMap(List::stream)
				.collect(toList());
	}
	
	private static <T> Predicate<T> not(Predicate<T> t) {
	    return t.negate();
	}

	private static List<Candidate> limit(List<Candidate> candidates, Function<String, Double> eval, int top_k, double threshold)
	{
		if (candidates.isEmpty() || top_k < 1)
			return Collections.emptyList();

		// Top k candidates according to predefined order, e.g. dictionary's best sense ordering
		final List<Candidate> top_candidates = new ArrayList<>(candidates.subList(0, min(candidates.size(), top_k)));

		// Filtered list of candidates according to eval function
		final List<Candidate> filtered = candidates.stream()
				.filter(c -> {
					final Double avg_sim = eval.apply(c.getMeaning().getReference());
					return avg_sim >= threshold;
				})
				.collect(toList());
		top_candidates.addAll(filtered);
		return top_candidates.stream().distinct().collect(toList());
	}

}
