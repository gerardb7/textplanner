package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.SemanticGraph;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
class CandidatesCollector
{
	private final BabelNetWrapper bn;
	private final static Logger log = LoggerFactory.getLogger(CandidatesCollector.class);

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
		// Collect mentions and classify them into a map by label ("surface form + POS") to reduce lookups
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, List<Mention>> label2Mentions = graphs.stream()
				.map(SemanticGraph::getAlignments)
				.map(MentionsCollector::collectMentions)
				.flatMap(List::stream)
				// Label formed with the surface form and head POS of mention's head
				.collect(Collectors.groupingBy(m -> m.getSurfaceForm() + "_" + m.getPOS()));

		// Create a map of labels and associated candidate synsets
		AtomicInteger counter = new AtomicInteger(0);

		Map<String, List<BabelSynset>> labels2Synsets = label2Mentions.keySet().stream()
				.peek(l -> {
					if (counter.incrementAndGet() % 1000 == 0)
						log.info("Queried synsets for " + counter.get() + " labels out of " + label2Mentions.keySet().size());
				})
				.collect(toMap(l -> l, this::getSynsets));

		// Create new mapping from labels to Meaning objects
		Map<String, Set<Meaning>> labels2Meanings = labels2Synsets.keySet().stream()
				.collect(toMap(l -> l, l -> labels2Synsets.get(l).stream()
						.map(this::createMeaning)
						.collect(toSet())));

		Map<Mention, String> mentions2vertices = label2Mentions.values().stream()
				.flatMap(List::stream)
				.collect(toMap(m -> m, m -> m.getAlignedGraph().getTopSpanVertex(m.getSpan()).get()));

		// Create candidates
		List<Candidate> candidates = labels2Meanings.keySet().stream()
				.flatMap(l -> labels2Meanings.get(l).stream() // given a label l
						.flatMap(meaning -> label2Mentions.get(l).stream() // for each mention with label l
								.map(mention -> new Candidate(mentions2vertices.get(mention), meaning, mention))))
				.collect(toList());

		log.info("Candidate meanings collected in " + timer.stop());
		return candidates;
	}

	private List<BabelSynset> getSynsets(String s)
	{
		// Use surface form of mention as label
		String form = s.substring(0, s.lastIndexOf('_'));
		String pos = s.substring(s.lastIndexOf('_') + 1);
		return bn.getSynsets(form, pos);
	}

	private Meaning createMeaning(BabelSynset s)
	{
		String reference = s.getId().getID();
		boolean is_NE = s.getSynsetType() == BabelSynsetType.NAMED_ENTITY;
		return Meaning.get(reference, is_NE);
	}
}
