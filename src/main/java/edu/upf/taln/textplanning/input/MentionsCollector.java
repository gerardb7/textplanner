package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.Candidate.Type;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.toList;

public class MentionsCollector
{
	private static final int max_tokens = 7;

	public static List<Mention> collectMentions(GraphAlignments graph)
	{
		List<Mention> mentions = collectNominalMentions(graph);
		mentions.addAll(collectOtherMentions(graph));

		// If you think about it there is no need to check for duplicates anywhere
		return mentions;
	}

	/**
	 * Returns a mention for each sequence of 2 to max_tokens containing at least a noun, provided there is a node
	 * in the graph spanning over it.
	 */
	@SuppressWarnings("ConstantConditions")
	private static List<Mention> collectNominalMentions(GraphAlignments g)
	{
		List<String> tokens = g.getTokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size()))
						.mapToObj(j -> Pair.of(i, j))
						.filter(g::covers)
						.filter(p -> g.isNominal(g.getTopSpanVertex(p).get()))
						.map(p -> new Mention(g, p, "N", g.getNER(p).orElse(Type.Other)))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(toList());
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static List<Mention> collectOtherMentions(GraphAlignments alignments)
	{
		return  alignments.getGraph().vertexSet().stream()
				.filter(alignments::isAligned)
				.mapToInt(alignments::getAlignment)
				.mapToObj(i -> new Mention(alignments, Pair.of(i, i+1), alignments.getPOS(i), alignments.getNER(i)))
				.collect(toList());

	}
}
