package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.Candidate.Type;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class MentionsCollector
{
	private static final int max_tokens = 7;

	static Set<Mention> collectMentions(GraphAlignments graph)
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
	private static Set<Mention> collectNominalMentions(GraphAlignments g)
	{
		List<String> tokens = g.getTokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size()))
						.mapToObj(j -> Pair.of(i, j))
						.filter(g::covers)
						.filter(p -> {
							String top_v = g.getTopSpanVertex(p).get();
							return g.isNominal(top_v) || g.isConjunction(top_v);
						})
						.map(p -> new Mention(g, p, g.getPOS(p).orElse("NN"), g.getNER(p).orElse(Type.Other)))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(toSet());
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Set<Mention> collectOtherMentions(GraphAlignments alignments)
	{
		return  alignments.getGraph().vertexSet().stream()
				.filter(alignments::isAligned)
				.filter(v -> !alignments.isNominal(v))
				.mapToInt(alignments::getAlignment)
				.mapToObj(i -> new Mention(alignments, Pair.of(i, i+1), alignments.getPOS(i), alignments.getNER(i)))
				.collect(toSet());
	}
}
