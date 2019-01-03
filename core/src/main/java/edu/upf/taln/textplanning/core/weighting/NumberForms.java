package edu.upf.taln.textplanning.core.weighting;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.toList;

public class NumberForms
{
	private final Map<String, Double> weights = new HashMap<>();

	public NumberForms(Collection<Candidate> candidates)
	{
		final List<Pair<Meaning, Long>> counts = candidates.stream()
				.map(c -> Pair.of(c.getMeaning(), candidates.stream()
						.filter(c2 -> c2.getMeaning().equals(c.getMeaning()))
						.map(Candidate::getMention)
						.map(Mention::getSurface_form)
						.distinct()
						.count()))
				.sorted(comparingLong(Pair<Meaning, Long>::getRight).reversed())
				.collect(toList());

		final long max_count = counts.stream()
				.mapToLong(Pair::getRight)
				.max().orElse(1);
		counts.forEach(p -> weights.put(p.getLeft().getReference(), p.getRight().doubleValue() / max_count));
	}

	public double weight(String meaning)
	{
		assert weights.containsKey(meaning);

		return weights.get(meaning);
	}
}
