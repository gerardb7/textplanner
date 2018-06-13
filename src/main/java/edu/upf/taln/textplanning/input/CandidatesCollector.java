package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetType;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
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
		log.info("Collected " + num_nominal_mentions + " nominal mentions out of " + num_mentions +
					" mentions, with " + anchors2Mentions.keySet().size() + " different anchors");

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
		Multimap<Mention, String> mentions2vertices = HashMultimap.create();
		anchors2Mentions.values().stream()
				.flatMap(List::stream)
				.forEach(m -> {
							Pair<Integer, Integer> span = m.getSpan();
							SemanticGraph graph = graph_ids.get(m.getSentenceId());
							Optional<String> top = graph.getAlignments().getSpanTopVertex(span);
							top.ifPresent(t -> mentions2vertices.put(m, t));
						});

		// Create candidates
		List<Candidate> candidates = anchors2Meanings.keySet().stream()
				.flatMap(l -> anchors2Meanings.get(l).stream() // given a label l
						.flatMap(meaning -> anchors2Mentions.get(l).stream() // for each mention m with label l
								.flatMap(m -> mentions2vertices.get(m).stream() // for each vertex v with mention m
									.map(v -> new Candidate(v, meaning, m)))))
				.collect(toList());

		int num_references = candidates.stream().map(Candidate::getMeaning).map(Meaning::getReference).collect(toSet()).size();
		log.info("Created " + candidates.size() + " candidates, with " + num_references +
				" different references, and using " + BabelNetWrapper.num_queries.get() + " queries");
		log.info("Candidate meanings collected in " + timer.stop());

		final List<String> multiwords = candidates.stream()
				.filter(c -> c.getMention().isMultiWord())
				.map(c -> c.getMention().getSurface_form() + "-" + c.getMeaning())
				.collect(Collectors.toList());
		log.debug("List of multiword candidates: " + multiwords);

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
		String label = s.getSenses(Language.EN).iterator().next().toString();
		boolean is_NE = s.getSynsetType() == BabelSynsetType.NAMED_ENTITY;
		return Meaning.get(reference, label, is_NE);
	}
}
