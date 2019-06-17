package edu.upf.taln.textplanning.amr.io;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.amr.structures.AMRAlignments;
import edu.upf.taln.textplanning.amr.structures.AMRGraph;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.Integer.min;

public class AMRMentionsCollector //implements MentionsCollector<Collection<AMRGraph>>
{
	private static final int max_tokens = 5;
	private final static Logger log = LogManager.getLogger();

	public static Multimap<String, Mention> collectMentions(Collection<AMRGraph> graphs, ULocale language, POS.Tagset tagset)
	{
		log.info("Collecting mentions");
		Stopwatch timer = Stopwatch.createStarted();

		final Multimap<String, Mention> multiwords = collectMultiwordMentions(graphs, tagset);
		final Multimap<String, Mention> singlewords = collectoSingleWordMentions(graphs, language, tagset);
		Multimap<String, Mention> mentions = HashMultimap.create(multiwords);
		mentions.putAll(singlewords);

		log.info("Collected " + mentions.size() +	" mentions where " + singlewords.size() + " are single words and " +
				singlewords.values().stream().filter(Mention::isNominal).count() + " are noun words");
		log.info("Mentions collected in " + timer.stop());

		return mentions;
	}

	/**
	 * Returns a mention for each sequence of up to 2 to max_tokens, provided there is a node
	 * in the graph spanning over it.
	 */
	private static Multimap<String, Mention> collectMultiwordMentions(Collection<AMRGraph> graphs, POS.Tagset tagset)
	{
		Multimap<String, Mention> vertices2Mentions = HashMultimap.create();
		graphs.forEach(g ->
		{
			AMRAlignments a = g.getAlignments();
			List<String> tokens = a.getTokens();

			Predicate<String> is_punct = (str) -> Pattern.matches("\\p{Punct}+", str);
			IntStream.range(0, tokens.size())
					.forEach(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size() + 1))
							.mapToObj(j -> Pair.of(i, j))
							.filter(span -> span.getRight() - span.getLeft() > 1) // 2 or more tokens
							.filter(span -> a.getSpanTopVertex(span).isPresent()) // restricts multiwords to those that can be aligned with a single vertex!
							.filter(span -> isNominal(span,g) || isName(span, g)) // nouns, conjunctions and names
							.filter(span -> IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(index -> a.getTokens().get(index))
									.noneMatch(is_punct)) // no punctuation marks please
							.map(span -> new Mention(
									a.getSurfaceForm(span),
									g.getContextId(),
									span,
									a.getSurfaceForm(span),
									a.getLemma(span).orElse(a.getSurfaceForm(span)),
									a.getPOS(span).map(p -> POS.get(p, tagset)).orElse(POS.Tag.NOUN), // in case of doubt, assume it's a noun phrase!
									isName(span, g),
									getType(span, g)))
							.forEach(m -> vertices2Mentions.put(a.getSpanTopVertex(m.getSpan()).get(), m)));
		});
		return vertices2Mentions;
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Multimap<String, Mention> collectoSingleWordMentions(Collection<AMRGraph> graphs, ULocale language, POS.Tagset tagset)
	{
		Multimap<String, Mention> vertices2Mentions = HashMultimap.create();
		graphs.forEach(g ->
		{
			AMRAlignments a = g.getAlignments();

			g.vertexSet().forEach(v -> a.getAlignments(v).stream()
					.distinct()
					.map(i ->
					{
						Pair<Integer, Integer> span = Pair.of(i, i + 1);
						final POS.Tag tag = POS.get(a.getPOS(i), tagset);
						return new Mention(a.getSurfaceForm(span), g.getContextId(), span, a.getSurfaceForm(span), a.getLemma(i), tag,
								isName(span, g), getType(span, g));
					})
					.filter(m -> FunctionWordsFilter.test(m, language)) // use list of non-ambiguous function words
					.forEach(m -> vertices2Mentions.put(v, m)));
		});
		return vertices2Mentions;
	}

	// in case of doubt, assume it's a nominal group
	private static boolean isNominal(Pair<Integer, Integer> span, AMRGraph g)
	{
		return g.getAlignments().getPOS(span).map(pos -> pos.startsWith("N") || pos.endsWith("CC")).orElse(true);
	}

	public static boolean isName(Pair<Integer, Integer> span, AMRGraph g)
	{
		return g.getAlignments().getSpanTopVertex(span).map(v -> g.outgoingEdgesOf(v).stream()
					.anyMatch(e -> e.getLabel().equals(AMRSemantics.name)) || g.outgoingEdgesOf(v).stream()
							.filter(e -> e.getLabel().equals(AMRSemantics.instance))
							.map(g::getEdgeTarget)
							.anyMatch(c -> c.equals(AMRSemantics.name_concept)))
					.orElse(false);
	}

	public static String getType(Pair<Integer, Integer> span, AMRGraph g)
	{
		return g.getAlignments().getSpanTopVertex(span).flatMap(v -> g.outgoingEdgesOf(v).stream()
				.filter(e -> e.getLabel().equals(AMRSemantics.instance))
				.map(g::getEdgeTarget)
				.findAny()).orElse("");
	}
}
