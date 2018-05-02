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
	private static Set<Mention> collectNominalMentions(GraphAlignments a)
	{
		List<String> tokens = a.getTokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size()))
						.mapToObj(j -> Pair.of(i, j))
						.filter(a::covers)
						.filter(p -> {
							String top_v = a.getTopSpanVertex(p).get();
							return a.isNominal(top_v) || a.isConjunction(top_v);
						})
						.map(p -> new Mention(a, p, a.getLemma(p).orElse(""), a.getPOS(p).orElse("NN"),
								a.getNER(p).orElse(Type.Other)))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(toSet());
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Set<Mention> collectOtherMentions(GraphAlignments a)
	{
		return  a.getGraph().vertexSet().stream()
				.filter(a::isAligned)
				.filter(v -> !a.isNominal(v))
				.mapToInt(a::getAlignment)
				.mapToObj(i -> new Mention(a, Pair.of(i, i+1), a.getLemma(i), a.getPOS(i), a.getNER(i)))
				.collect(toSet());
	}
}
