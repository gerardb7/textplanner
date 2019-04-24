package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static java.util.stream.Collectors.*;

// Accept references which are not candidates of exactly the same set of mentions
public class DifferentMentionsFilter implements BiPredicate<String, String>
{
	private final Set<Set<String>> same_mentions_sets;

	public DifferentMentionsFilter(List<Candidate> candidates)
	{
		final List<String> references = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());

		final Map<String, Set<Integer>> references2mentions = references.stream()
				.collect(toMap(r -> r, r -> candidates.stream()
						.filter(c -> c.getMeaning().getReference().equals(r))
						.map(Candidate::getMention)
						.map(Mention::getId)
						.collect(toSet())));
		same_mentions_sets = new HashSet<>(references.stream()
				.collect(groupingBy(references2mentions::get, toSet())).values());
	}

	@Override
	public boolean test(String r1, String r2)
	{
		return same_mentions_sets.stream().noneMatch(s -> s.contains(r1) && s.contains(r2));
	}
}
