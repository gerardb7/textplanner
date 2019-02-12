package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * For each mention, reduces its set of candidates to the best items according to an evaluation function
 */
public class TopCandidatesFilter implements Predicate<Candidate>
{
	private final List<Candidate> selected_candidates;

	public TopCandidatesFilter(List<Candidate> candidates, Function<String, Double> eval, int max_candidates)
	{
		selected_candidates = filter(candidates, eval, max_candidates);
	}

	@Override
	public boolean test(Candidate candidate)
	{
		return selected_candidates.contains(candidate);
	}


	private static List<Candidate> filter(List<Candidate> candidates, Function<String, Double> eval, int max_candidates)
	{
		final Map<Mention, List<Candidate>> collect = candidates.stream()
				.collect(Collectors.groupingBy(Candidate::getMention));
		return collect.values().stream()
				.map(l -> limit(l, eval, max_candidates))
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}

	private static List<Candidate> limit(List<Candidate> candidates, Function<String, Double> eval, int max_candidates)
	{
		if (candidates.size() < max_candidates)
			return candidates;

		candidates.sort(Comparator.comparingDouble(c -> eval.apply(c.getMeaning().getReference())));
		return candidates.subList(0, max_candidates);
	}

}
