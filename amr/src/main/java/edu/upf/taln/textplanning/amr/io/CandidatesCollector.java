package edu.upf.taln.textplanning.amr.io;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.BabelNetDictionary;
import edu.upf.taln.textplanning.common.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	private final MeaningDictionary bn;
	private final ULocale language;
	private final static Logger log = LogManager.getLogger();

	public CandidatesCollector(MeaningDictionary babelnet, ULocale language)
	{
		bn = babelnet;
		this.language = language;
	}

	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures.
	 * Assumes unique vertex labels across graphs.
	 */
	public List<Candidate> getCandidateMeanings(Set<Mention> mentions)
	{
		log.info("Collecting candidate meanings");
		Stopwatch timer = Stopwatch.createStarted();

		// Optimization: group mentions by anchor (triple of surface form, lemma and POS) to reduce lookups
		Map<Triple<String, String, String>, List<Mention>> anchors2Mentions = mentions.stream()
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		// Create a map of anchors and associated candidate synsets
		AtomicInteger counter = new AtomicInteger(0);
		Map<Triple<String, String, String>, Set<String>> anchorsSynsets = anchors2Mentions.keySet().stream()
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

		// Create candidates
		List<Candidate> candidates = anchors2Meanings.keySet().stream()
				.flatMap(l -> anchors2Meanings.get(l).stream() // given a label l
						.flatMap(meaning -> anchors2Mentions.get(l).stream() // for each mention m with label l
								.map(mention -> new Candidate(meaning, mention))))
				.collect(toList());

		int num_references = candidates.stream().map(Candidate::getMeaning).map(Meaning::getReference).collect(toSet()).size();
		log.info("Created " + candidates.size() + " candidates with " + num_references +
				" distinct references for " + anchors2Mentions.keySet().size() + " anchors (" +
				BabelNetDictionary.num_queries.get() +	" queries).");
		log.info("Candidate meanings collected in " + timer.stop());

		return candidates;
	}

	private Set<String> getSynsets(Triple<String, String, String> mention)
	{
		// Use surface form of mention as label
		String form = mention.getLeft();
		String lemma = mention.getMiddle();
		String pos = mention.getRight();
		Set<String> synset_set = new HashSet<>(bn.getMeanings(form, pos, language));
		if (!lemma.equalsIgnoreCase(form))
		{
			List<String> lemma_synsets = bn.getMeanings(lemma, pos, language);
			synset_set.addAll(lemma_synsets);
		}

		return synset_set;
	}

	private Meaning createMeaning(String synset)
	{
		return Meaning.get(synset, bn.getLabel(synset, language).orElse(""), bn.isNE(synset).orElse(false));
	}
}
