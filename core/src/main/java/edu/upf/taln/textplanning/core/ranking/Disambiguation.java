package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class Disambiguation
{
	private final static BiPredicate<Mention, Mention> spans_over = (m1, m2) -> m1.getSpan().getLeft() <= m2.getSpan().getLeft() &&
			m1.getSpan().getRight() >= m2.getSpan().getRight();

	public static void disambiguate(SemanticGraph graph, List<Candidate> candidates)
	{
		// Select which mentions should be part of the graph
		final Map<Mention, List<Mention>> selected_mentions = Disambiguation.selectMentions(candidates);
		// Add selected mentions to the graph
		selected_mentions.keySet().stream()
				.map(Mention::getId)
				.filter(not(graph::containsVertex))
				.forEach(graph::addVertex);

		// Contract vertices in graph with mentions spanned over other vertex-mentions.
		selected_mentions.forEach((subsumer, subsumed) ->
		{
			final List<String> subsumed_vertices = subsumed.stream()
					.map(Mention::getId)
					.filter(graph::containsVertex)// some subsumed mentions may not have been added to the graph
					.collect(toList());
			graph.vertexContraction(subsumer.getId(), subsumed_vertices);
		});

		// Disambiguate meanings and assign to graph vertices
		final List<Candidate> selected_candidates = selectCandidates(candidates);
		selected_candidates.forEach(c -> {
			graph.setMeaning(c.getMention().getId(), c.getMeaning());
			graph.setWeight(c.getMention().getId(), c.getWeight());
		});
	}

	private static Map<Mention, List<Mention>> selectMentions(List<Candidate> candidates)
	{
		final List<Mention> mentions = candidates.stream()
				.map(Candidate::getMention)
				.collect(toList());

		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		final Map<Mention, Double> mentions2weights = mentions2candidates.entrySet().stream()
						.map(p -> Pair.of(p.getKey(), p.getValue().stream()
								.mapToDouble(Candidate::getWeight)
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
		return mentions.stream()
				.filter(m1 -> mentions2subsumed.get(m1).stream().allMatch(m2 -> weights_more.test(m1, m2)))
				.filter(m1 -> mentions2subsumers.get(m1).stream().noneMatch(m2 -> weights_more.test(m2, m1)))
				.collect(toMap(m -> m, mentions2subsumed::get));
	}

	private static List<Candidate> selectCandidates(List<Candidate> candidates)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));
		return mentions2candidates.values().stream()
			.map(l -> l.stream().max(Comparator.comparingDouble(Candidate::getWeight)))
			.flatMap(Optional::stream)
			.collect(toList());
	}
}
