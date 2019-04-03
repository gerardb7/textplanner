package edu.upf.taln.textplanning.core.io;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	private final static Logger log = LogManager.getLogger();


	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures.
	 * Assumes unique vertex labels across graphs.
	 */
	public static List<Candidate> collect(MeaningDictionary dictionary, ULocale language, List<Mention> mentions)
	{
		log.info("Collecting candidate meanings");
		Stopwatch timer = Stopwatch.createStarted();

		// Optimization: group mentions by anchor (triple of surface form, lemma and POS) to reduce lookups
		Map<Triple<String, String, String>, List<Mention>> forms2mentions = mentions.stream()
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		// Create a map of forms and associated candidate meanings
		log.info("\tQuerying " + forms2mentions.keySet().size() + " forms");
		final Map<Triple<String, String, String>, List<Meaning>> forms2meanings = forms2mentions.keySet().stream()
				.collect(toMap(t -> t, t -> getReferences(dictionary, language, t).stream()
						.map(meaning -> Meaning.get(meaning, dictionary.getLabel(meaning, language).orElse(""), false))
						.collect(toList())));

		// use traditional loops to make sure that the order of lists of candidates is preserved
		final List<Candidate> candidates = new ArrayList<>();
		for (Triple<String, String, String> t : forms2mentions.keySet())
		{
			final List<Mention> form_mentions = forms2mentions.get(t);
			final List<Meaning> form_meanings = forms2meanings.get(t);
			for (Mention mention : form_mentions)
			{
				final List<Candidate> mention_candidates = form_meanings.stream().map(meaning -> new Candidate(mention, meaning)).collect(toList());
				candidates.addAll(mention_candidates);
			}
		}

		int num_references = candidates.stream().map(Candidate::getMeaning).map(Meaning::getReference).collect(toSet()).size();
		log.info("Created " + candidates.size() + " candidates with " + num_references +
				" distinct references for " + forms2mentions.keySet().size() + " anchors (" +
				dictionary.getNumQueries() +	" queries).");
		log.info("Candidate meanings collected in " + timer.stop());

		return candidates;
	}

	private static List<String> getReferences(MeaningDictionary dictionary, ULocale language, Triple<String, String, String> mention)
	{
		// Use surface form of mention as label
		String form = mention.getLeft();
		String lemma = mention.getMiddle();
		String pos = mention.getRight();
		// Lemma meanings first, sorted by dictionary criteria (BabelNet -> best sense)
		List<String> references = dictionary.getMeanings(lemma, pos, language);

		// Form meanings go after
		if (!lemma.equalsIgnoreCase(form))
		{
			List<String> lemma_synsets = dictionary.getMeanings(form, pos, language);
			lemma_synsets.removeAll(references);
			references.addAll(lemma_synsets);
		}

		return references;
	}
}
