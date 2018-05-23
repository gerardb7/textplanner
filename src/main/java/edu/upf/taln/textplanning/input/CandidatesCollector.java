package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.amr.SemanticGraph;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
class CandidatesCollector
{
	private final BabelNetWrapper bn;
	private final static Logger log = LogManager.getLogger(CandidatesCollector.class);

	CandidatesCollector(BabelNetWrapper babelnet)
	{
		bn = babelnet;
	}

	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures.
	 * Assumes unique vertex labels across graphs.
	 */
	@SuppressWarnings("ConstantConditions")
	List<Candidate> getCandidateMeanings(List<SemanticGraph> graphs)
	{
		Map<String, SemanticGraph> graph_ids =
				graphs.stream().collect(Collectors.toMap(SemanticGraph::getId, Function.identity()));

		// Collect mentions and group them by anchor (triple of surface form, lemma and POS) -> to reduce lookups
		Stopwatch timer = Stopwatch.createStarted();
		Map<Triple<String, String, String>, List<Mention>> anchors2Mentions = graphs.stream()
				.map(MentionsCollector::collectMentions)
				.flatMap(Set::stream)
				// Label formed with the surface form and head POS of mention's head
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		long num_mentions = anchors2Mentions.values().stream()
				.mapToLong(List::size)
				.sum();
		long num_nominal_mentions = anchors2Mentions.values().stream()
				.flatMap(List::stream)
				.filter(Mention::isNominal)
				.count();
		log.info("Collected " + num_nominal_mentions + " nominal mentions out of " + num_mentions + " mentions, with " + anchors2Mentions.keySet().size() + " different anchors");

		// Create a map of anchors and associated candidate synsets
		AtomicInteger counter = new AtomicInteger(0);

		Map<Triple<String, String, String>, Set<BabelSynset>> anchorsSynsets = anchors2Mentions.keySet().stream()
				.peek(l -> {
					if (counter.incrementAndGet() % 1000 == 0)
						log.info("Queried synsets for " + counter.get() + " labels out of " + anchors2Mentions.keySet().size());
				})
				.collect(toMap(a -> a, this::getSynsets));

		// Create new mapping from anchors to Meaning objects
		Map<Triple<String, String, String>, Set<Meaning>> anchors2Meanings = anchorsSynsets.keySet().stream()
				.collect(toMap(l -> l, l -> anchorsSynsets.get(l).stream()
						.map(this::createMeaning)
						.collect(toSet())));

		// Get vertex governing each mention
		Function<Mention, String> getVertex =
				m ->
				{
					Pair<Integer, Integer> span = m.getSpan();
					SemanticGraph graph = graph_ids.get(m.getSentenceId());
					return graph.getAlignments().getTopSpanVertex(span).get();
				};
		Map<Mention, String> mentions2vertices = anchors2Mentions.values().stream()
				.flatMap(List::stream)
				.collect(toMap(m -> m, getVertex));

		// Create candidates
		List<Candidate> candidates = anchors2Meanings.keySet().stream()
				.flatMap(l -> anchors2Meanings.get(l).stream() // given a label l
						.flatMap(meaning -> anchors2Mentions.get(l).stream() // for each mention with label l
								.map(mention -> new Candidate(mentions2vertices.get(mention), meaning, mention))))
				.collect(toList());

		log.info("Created " + candidates.size() + " candidates using " + BabelNetWrapper.num_queries.get() + " queries");
		log.info("Candidate meanings collected in " + timer.stop());
		return candidates;
	}

	private Set<BabelSynset> getSynsets(Triple<String, String, String> s)
	{
		// Use surface form of mention as label
		String form = s.getLeft();
		String lemma = s.getMiddle();
		String pos = s.getRight();
		Set<BabelSynset> synsets = new HashSet<>(bn.getSynsets(form, pos));
		if (!lemma.equalsIgnoreCase(form))
		{
			List<BabelSynset> lemma_synsets = bn.getSynsets(lemma, pos);
			synsets.addAll(lemma_synsets);
		}

		return synsets;
	}

	private Meaning createMeaning(BabelSynset s)
	{
		String reference = s.getId().getID();
		boolean is_NE = s.getSynsetType() == BabelSynsetType.NAMED_ENTITY;
		return Meaning.get(reference, is_NE);
	}
}
