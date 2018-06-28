package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class MentionsCollector
{
	private static final int max_tokens = 5;

	static Set<Mention> collectMentions(SemanticGraph graph)
	{
		Set<Mention> multiwords = collectMultiwordMentions(graph);
		Set<Mention> singlewords = collectoSingleWordMentions(graph);
		multiwords.addAll(singlewords);

		return multiwords;
	}

	/**
	 * Returns a mention for each sequence of up to 2 to max_tokens, provided there is a node
	 * in the graph spanning over it.
	 */
	@SuppressWarnings("ConstantConditions")
	private static Set<Mention> collectMultiwordMentions(SemanticGraph graph)
	{
		GraphAlignments a = graph.getAlignments();
		List<String> tokens = a.getTokens();

		Predicate<String> is_punct = (str) -> Pattern.matches("\\p{Punct}", str);

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size() + 1))
						.mapToObj(j -> Pair.of(i, j))
						.filter(p -> p.getRight() - p.getLeft() > 1) // 2 or more tokens
						.filter(p -> a.getSpanTopVertex(p).isPresent())
						.filter(span -> {
							final Optional<String> opos = a.getPOS(span);
							if (!opos.isPresent())
								return true; // in case of doubt, assume it's a nominal group
							String pos = opos.get();
							return pos.startsWith("N") || pos.endsWith("CC"); // nouns and conjunctions
						})
						.filter(span -> IntStream.range(span.getLeft(), span.getRight())
								.mapToObj(index -> a.getTokens().get(index))
								.noneMatch(is_punct)) // no punctuation marks please
						.map(span -> new Mention(
								graph.getId(),
								span,
								a.getSurfaceForm(span),
								a.getLemma(span).orElse(a.getSurfaceForm(span)),
								a.getPOS(span).orElse("NN"), // in case of doubt, assume it's a noun phrase!
								a.getNER(span).orElse(Type.Other)))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(toSet());
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Set<Mention> collectoSingleWordMentions(SemanticGraph g)
	{
		GraphAlignments a = g.getAlignments();

		return  g.vertexSet().stream()
				.map(a::getAlignment)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.distinct() // multiple vertices can be aligned to same token...
				.filter(i -> a.getPOS(i).startsWith("N"))
				.map(i ->
				{
					Pair<Integer, Integer> span = Pair.of(i, i + 1);
					return new Mention(g.getId(), span, a.getSurfaceForm(span), a.getLemma(i), a.getPOS(i), a.getNER(i));
				})
				.collect(toSet());
	}
}
