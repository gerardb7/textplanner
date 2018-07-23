package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.Integer.min;

class MentionsCollector
{
	private static final int max_tokens = 5;
	private final static Logger log = LogManager.getLogger();

	static Multimap<String, Mention> collectMentions(Collection<SemanticGraph> graphs)
	{
		log.info("Collecting mentions");
		Stopwatch timer = Stopwatch.createStarted();

		final Multimap<String, Mention> multiwords = collectMultiwordMentions(graphs);
		final Multimap<String, Mention> singlewords = collectoSingleWordMentions(graphs);
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
	@SuppressWarnings("ConstantConditions")
	private static Multimap<String, Mention> collectMultiwordMentions(Collection<SemanticGraph> graphs)
	{
		Multimap<String, Mention> vertices2Mentions = HashMultimap.create();
		graphs.forEach(g ->
		{
			GraphAlignments a = g.getAlignments();
			List<String> tokens = a.getTokens();

			Predicate<String> is_punct = (str) -> Pattern.matches("\\p{Punct}", str);

			IntStream.range(0, tokens.size())
					.forEach(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size() + 1))
							.mapToObj(j -> Pair.of(i, j))
							.filter(p -> p.getRight() - p.getLeft() > 1) // 2 or more tokens
							.filter(p -> a.getSpanTopVertex(p).isPresent())
							.filter(span ->
							{
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
									g.getId(),
									span,
									a.getSurfaceForm(span),
									a.getLemma(span).orElse(a.getSurfaceForm(span)),
									a.getPOS(span).orElse("NN"), // in case of doubt, assume it's a noun phrase!
									a.getNEType(span).orElse(Type.Other)))
							.forEach(m -> vertices2Mentions.put(a.getSpanTopVertex(m.getSpan()).get(), m)));
		});
		return vertices2Mentions;
	}

	/**
	 * Returns a mention for every individual token
	 */
	private static Multimap<String, Mention> collectoSingleWordMentions(Collection<SemanticGraph> graphs)
	{
		Multimap<String, Mention> vertices2Mentions = HashMultimap.create();
		graphs.forEach(g ->
		{
			GraphAlignments a = g.getAlignments();

			g.vertexSet().forEach(v -> a.getAlignments(v).stream()
					.distinct()
					.map(i ->
					{
						Pair<Integer, Integer> span = Pair.of(i, i + 1);
						return new Mention(g.getId(), span, a.getSurfaceForm(span), a.getLemma(i), a.getPOS(i), a.getNEType(i));
					})
					.forEach(m -> vertices2Mentions.put(v, m)));
		});
		return vertices2Mentions;
	}
}
