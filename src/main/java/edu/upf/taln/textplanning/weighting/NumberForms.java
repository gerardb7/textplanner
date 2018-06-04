package edu.upf.taln.textplanning.weighting;

import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.toList;


public class NumberForms implements WeightingFunction
{
	private final Predicate<String> filter;
	private final Map<String, Double> weights = new HashMap<>();

	public NumberForms(Predicate<String> filter)
	{
		this.filter = filter;
	}

	@Override
	public void setContents(Multimap<Meaning, Mention> contents)
	{
		final List<Pair<Meaning, Long>> counts = contents.keySet().stream()
				.filter(m -> filter.test(m.getReference()))
				.map(meaning -> Pair.of(meaning, contents.get(meaning).stream()
						.map(Mention::getSurface_form)
						.distinct()
						.count()))
				.sorted(comparingLong(Pair<Meaning, Long>::getRight).reversed())
				.collect(toList());

		final long max_count = counts.stream()
				.mapToLong(Pair::getRight)
				.max().orElse(1);
		counts.forEach(p -> {
			weights.put(p.getLeft().getReference(), p.getRight().doubleValue() / max_count);
		});
	}

	@Override
	public double weight(String item)
	{
		return weights.getOrDefault(item, 0.0);
	}
}
