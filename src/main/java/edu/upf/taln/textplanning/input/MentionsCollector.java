package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class MentionsCollector
{
	private static final int max_tokens = 7;

	static Set<Mention> collectMentions(SemanticGraph graph)
	{
		Set<Mention> nominal = collectNominalMentions(graph);
		Set<Mention> non_nominal = collectOtherMentions(graph);
		nominal.addAll(non_nominal);

		return nominal;
	}

	/**
	 * Returns a mention for each sequence of 2 to max_tokens containing at least a noun, provided there is a node
	 * in the graph spanning over it.
	 */
	@SuppressWarnings("ConstantConditions")
	private static Set<Mention> collectNominalMentions(SemanticGraph graph)
	{
		GraphAlignments a = graph.getAlignments();
		List<String> tokens = a.getTokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size()))
						.mapToObj(j -> Pair.of(i, j))
						.filter(a::covers)
						.filter(span -> {
							Optional<String> top_v = a.getTopSpanVertex(span);
							return top_v.filter(s -> a.isNominal(s) || a.isConjunction(s)).isPresent();
						})
						.map(span -> new Mention(
								graph.getId(),
								span,
								a.getSurfaceForm(span),
								a.getLemma(span).orElse(""),
								a.getPOS(span).orElse("NN"),
								a.getNER(span).orElse(Type.Other)))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(toSet());
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Set<Mention> collectOtherMentions(SemanticGraph g)
	{
		GraphAlignments a = g.getAlignments();

		return  g.vertexSet().stream()
				.filter(v -> !a.isNominal(v))
				.map(a::getAlignment)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(i ->
				{
					Pair<Integer, Integer> span = Pair.of(i, i + 1);
					return new Mention(g.getId(), span, a.getSurfaceForm(span), a.getLemma(i), a.getPOS(i), a.getNER(i));
				})
				.collect(toSet());
	}
}
