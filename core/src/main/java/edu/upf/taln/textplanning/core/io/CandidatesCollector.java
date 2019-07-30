package edu.upf.taln.textplanning.core.io;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	public static final int LOGGING_STEP_SIZE = 10000;
	private final static Logger log = LogManager.getLogger();

	private final static BiPredicate<Mention, Mention> preceds =
			(m1, m2) -> m1.getSpan().getRight() < m2.getSpan().getLeft();

	private final static class SubMentionMatcher implements BiPredicate<Mention, Mention>
	{
		final Map<Mention, List<String>> mentions2tokens;

		SubMentionMatcher(List<Mention> mentions)
		{
			mentions2tokens = mentions.stream()
					.collect(toMap(m -> m, m -> m.getTokens().stream().map(String::toLowerCase).map(String::trim).collect(toList())));
		}

		@Override
		public boolean test(Mention mention1, Mention mention2)
		{
			return  mention1.numTokens() > mention2.numTokens() &&
					Collections.indexOfSubList(mentions2tokens.get(mention1), mentions2tokens.get(mention2)) != -1;
		}
	}


	/**
	 * Assigns candidate entities to mentions.
	 */
	public static Map<Mention, List<Candidate>> collect(MeaningDictionary dictionary, ULocale language, List<Mention> mentions)
	{
		Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong();

		// collect candidates for each mention
		final Map<Mention, List<Candidate>> candidates = mentions.stream()
				.peek(mention ->
				{
					if (counter.incrementAndGet() % LOGGING_STEP_SIZE == 0)
						log.info("\t\t" + counter.get());
				})
				.collect(toMap(mention -> mention, mention -> getReferences(dictionary, language, mention).stream()
						.map(id -> Meaning.get(id, dictionary.getLabel(id, language).orElse(""), false))
						.map(meaning -> new Candidate(mention, meaning))
						.collect(toList())));

		// Simple coreference rule: for each nominal single-word mention, add to its list of candidates all candidate
		// meanings of any preceding multiword mention containing it
		log.debug("Coreferent mentions:");
		SubMentionMatcher contains = new SubMentionMatcher(mentions);
		mentions.stream()
				.filter(Mention::isNominal)
				.forEach(m1 -> candidates.keySet().stream()
						.filter(Mention::isMultiWord)
						.filter(m2 -> preceds.test(m2, m1) && contains.test(m2, m1))
						.forEach(m2 -> {
							if (candidates.containsKey(m2) && !candidates.get(m2).isEmpty())
							{
								final List<Candidate> candidates1 = candidates.getOrDefault(m1, List.of());
								final List<Meaning> meanings1 = candidates1.stream()
										.map(Candidate::getMeaning)
										.distinct()
										.collect(toList());

								final List<Candidate> new_list = candidates.get(m2).stream()
										.filter(c -> !meanings1.contains(c.getMeaning()))
										.map(c -> new Candidate(m1, c.getMeaning()))
										.collect(toList());
								log.debug("\t" + new_list.size() + " candidates of mention " + m2 + " added to " + m1);
								new_list.addAll(candidates1);
								candidates.put(m1, new_list);
							}
						}));

		int num_references = candidates.values().stream()
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(toSet())
				.size();

		log.info("Created " + candidates.values().stream().mapToInt(List::size).sum() + " candidates with " + num_references +
				" distinct references for " + mentions.size() + " mentions");
		log.info("Candidate meanings collected in " + timer.stop());

		return candidates;
	}

	// Collects all meanings and forms from a dictionary. May take a very long time!
	public static void collect(MeaningDictionary dictionary, ULocale language, CompactDictionary cache, Path cache_file) throws IOException
	{
		log.info("Collecting all meanings with " + Runtime.getRuntime().availableProcessors() + " cores available");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong num_meanings = new AtomicLong(0);
		AtomicLong num_forms = new AtomicLong(0);

		final Iterator<MeaningDictionary.Info> it = dictionary.infoIterator(language);
		while (it.hasNext())
		{
			final MeaningDictionary.Info m = it.next();
			cache.addMeaning(m.id, m.label, m.glosses);
			m.forms.stream()
					.filter(l -> !cache.contains(l, POS.toTag.get(m.POS)))
					.peek(l -> num_forms.incrementAndGet())
					.forEach(l -> cache.addForm(l, POS.toTag.get(m.POS), dictionary.getMeanings(l, m.POS, language)));

			long i = num_meanings.incrementAndGet();
			if (i % LOGGING_STEP_SIZE == 0)
			{
				log.info("\t" + num_meanings.get() + " meanings and " +  num_forms.get() + " forms collected in " + timer);
				FileOutputStream fos = new FileOutputStream(cache_file.toString());
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(cache);
				fos.close();
			}
		}

		log.info(num_meanings.get() + " meanings and " +  num_forms.get() + " forms collected in " + timer.stop());
	}

	private static List<String> getReferences(MeaningDictionary dictionary, ULocale language, Mention mention)
	{
		// Use surface form of mention as label
		String form = mention.getSurfaceForm();
		String lemma = mention.getLemma();
		POS.Tag pos = mention.getPOS();
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
