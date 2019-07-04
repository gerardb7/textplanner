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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	public static final int LOGGING_STEP_SIZE = 10000;
	private final static Logger log = LogManager.getLogger();


	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures.
	 * Assumes unique vertex labels across graphs.
	 */
	public static Map<Mention, List<Candidate>> collect(MeaningDictionary dictionary, ULocale language, List<Mention> mentions)
	{
		Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong();

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
