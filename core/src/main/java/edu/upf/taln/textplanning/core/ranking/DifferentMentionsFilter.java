package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

// Accept references which are not candidates of exactly the same set of mentions
public class DifferentMentionsFilter implements BiPredicate<String, String>
{
	private final Set<Pair<String, String>> different_mention_pairs;

	public DifferentMentionsFilter(Collection<Candidate> candidates)
	{
		final Map<String, List<Mention>> references2mentions = candidates.stream()
				.collect(Collectors.groupingBy(c -> c.getMeaning().getReference(), mapping(Candidate::getMention, toList())));

		different_mention_pairs = references2mentions.keySet().stream()
				.flatMap(r1 -> references2mentions.keySet().stream()
						.filter(r2 -> !references2mentions.get(r1).equals(references2mentions.get(r2)))
						.map(r2 -> Pair.of(r1, r2)))
				.collect(toSet());
	}

	@Override
	public boolean test(String r1, String r2)
	{
		return different_mention_pairs.contains(Pair.of(r1, r2)); // should differ in at least one mention
	}
}
