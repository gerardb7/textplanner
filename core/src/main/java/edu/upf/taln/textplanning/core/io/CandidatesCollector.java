package edu.upf.taln.textplanning.core.io;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	public static final int LOGGING_STEP_SIZE = 100000;
	public static final int CHUNK_SIZE = 1000000;
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

		// Does mention1 contain mention2?
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
								if (!new_list.isEmpty())
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
	public static void collect(MeaningDictionary dictionary, ULocale language, CompactDictionary cache, Path cache_file)
	{
		final Stopwatch timer = Stopwatch.createStarted();
		collectMeanings(dictionary, language, cache, cache_file);
		collectLexicalizations(dictionary, language, cache, cache_file);
		log.info("ALL DONE in " + timer.stop());
	}

	private static void collectMeanings(MeaningDictionary dictionary, ULocale language, CompactDictionary cache, Path cache_file)
	{
		final Stopwatch timer = Stopwatch.createStarted();
		int iterate_counter = 0;
		final AtomicInteger query_counter = new AtomicInteger(0);
		final AtomicInteger check_counter = new AtomicInteger(0);
		final DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final Iterator<String> meaning_it = dictionary.meaning_iterator();

		while (meaning_it.hasNext())
		{
			// 1- collect meanings
			log.info("\nCollecting meaning ids for language " + language);
			final List<String> meanings = new ArrayList<>();
			for (int i = 0; i < CHUNK_SIZE && meaning_it.hasNext(); ++i)
			{
				if (i > 0 && i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + (iterate_counter + i) + " meanings iterated in " + timer);
				meanings.add(meaning_it.next());
			}
			iterate_counter += meanings.size();
			log.info(iterate_counter + " meanings collected from dictionary");

			// 2- query meanings info
			log.info("Querying meanings info from dictionary");
			final List<Triple<String, String, List<String>>> meanings_info = meanings.parallelStream()
					.peek(meaning -> reporter.report())
					.map(meaning ->
					{
						final Optional<String> label = dictionary.getLabel(meaning, language);
						final List<String> glosses = dictionary.getGlosses(meaning, language);
						final List<Pair<String, POS.Tag>> lexicalizations = dictionary.getLexicalizations(meaning, language);

						// determine label value
						final String label_str;
						if (label.isPresent() && StringUtils.isNotBlank(label.get()))
							label_str = label.get();
						else if (!lexicalizations.isEmpty())
							label_str = lexicalizations.get(0).getLeft();
						else
						{
							// last ditch attempt
							final List<Pair<String, POS.Tag>> all_lexicalizations = dictionary.getLexicalizations(meaning);
							if (!all_lexicalizations.isEmpty())
								label_str = all_lexicalizations.get(0).getLeft();
							else
								label_str = meaning; // if there are absolutely no labels, then use the meaning ref...
						}

						long i = query_counter.getAndIncrement();
						if (i > 0 && i % LOGGING_STEP_SIZE == 0)
							log.info("\t" + i + " meanings queried in " + timer);

						return Triple.of(meaning, label_str, glosses);
					})
					.collect(toList());
			log.info(query_counter.get() + " meanings queried in " + timer);

			// 3 - add meanings to cache
			log.info("Adding meanings info to cache");
			meanings_info.forEach(info ->
					cache.addMeaning(info.getLeft(), info.getMiddle(), info.getRight()) // does nothing if meaning already in cache
			);

			// 4 - serialize cache to file
			serialize(cache, cache_file);

			// 5- run integrity tests
			log.info("Checking meanings");
			meanings_info.forEach(info ->
			{
				final String meaning = info.getLeft();
				final String dict_label = info.getMiddle();
				final List<String> dict_glosses = info.getRight();
				long i = check_counter.getAndIncrement();

				final String cache_label = cache.getLabel(meaning).orElse("");
				if (!dict_label.equals(cache_label))
					log.error("Labels do not match for meaning " + i + " " + meaning + ": dictionary label is \"" + dict_label + "\" and cache label is \"" + cache_label + "\"");

				final List<String> cache_glosses = cache.getGlosses(meaning);
				if (!dict_glosses.equals(cache_glosses))
					log.error("Glosses do not match for meaning " + i + " " + meaning + ": dictionary glosses are \n\t" + dict_glosses + "\ncache label are \n\t" + cache_glosses);

				if (i > 0 && i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + i + " meanings checked in " + timer);
			});
			log.info(check_counter.get() + " meanings checked in " + timer);
		}
		log.info("\n***All meanings collected in " + timer.stop() + "***\n");
	}

	private static void collectLexicalizations(MeaningDictionary dictionary, ULocale language, CompactDictionary cache, Path cache_file)
	{
		final Stopwatch timer = Stopwatch.createStarted();
		int iterate_counter = 0;
		final AtomicInteger query_counter = new AtomicInteger(0);
		final AtomicInteger check_counter = new AtomicInteger(0);
		final DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final Iterator<Pair<String, POS.Tag>> lexicon_it = dictionary.lexicon_iterator();

		while (lexicon_it.hasNext())
		{
			// 1- collect lexicalizations
			log.info("\nCollecting lexicalizations for language " + language);
			final List<Pair<String, POS.Tag>> lexicalizations = new ArrayList<>();
			for (int i = 0; i < CHUNK_SIZE && lexicon_it.hasNext(); ++i)
			{
				if (i > 0 && i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + (iterate_counter + i) + " lexicalizations iterated in " + timer);
				lexicalizations.add(lexicon_it.next());
			}
			iterate_counter += lexicalizations.size();
			log.info(iterate_counter + " lexicalizations collected from dictionary");

			// 2 - query lexicalization meanings
			log.info("Querying lexicalization meanings from dictionary");
			reporter.reset();
			final List<Triple<String, Character, List<String>>> lexicalizations_info = lexicalizations.parallelStream()
					.peek(meaning -> reporter.report())
					.filter(p -> StringUtils.isNotBlank(p.getLeft()))
					.map(p ->
					{
						final String word = p.getLeft();
						final Character pos_tag = POS.toTag.get(p.getRight());
						final List<String> dict_meanings = dictionary.getMeanings(word, p.getRight(), language);

						long i = query_counter.getAndIncrement();
						if ( i > 0 && i % LOGGING_STEP_SIZE == 0)
							log.info("\t" + i + " lexicalizations queried in " + timer);

						return Triple.of(word, pos_tag, dict_meanings);
					})
					.collect(toList());
			log.info(query_counter.get() + " lexicalizations queried in " + timer);

			// 3 - add lexicalizations to cache
			log.info("Adding meanings info to cache");
			lexicalizations_info.forEach(info ->
			{
				final String word = info.getLeft();
				final Character pos_tag = info.getMiddle();
				final List<String> l_meanings = info.getRight();
				cache.addForm(word, pos_tag, l_meanings); // does nothing if word-tag pair already in cache
			});

			// 4 - serialize cache to file
			serialize(cache, cache_file);

			// 5- run integrity tests
			log.info("Checking lexicalizations");
			lexicalizations_info.forEach(info ->
			{
				final String word = info.getLeft();
				final Character pos_tag = info.getMiddle();
				final List<String> dict_meanings = info.getRight();
				long i = check_counter.getAndIncrement();

				final List<String> cache_meanings = cache.getMeanings(word, pos_tag);
				if (!dict_meanings.equals(cache_meanings))
					log.error("Meanings do not match for lexicalization " + i + " word " + word + " and POS " + pos_tag + ": \n\tdictionary meanings are " + dict_meanings + "\n\tcache meanings are " + cache_meanings);

				if (i > 0 && i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + i + " lexicalizations checked in " + timer);
			});
			log.info(check_counter.get() + " lexicalizations checked in " + timer);
		}
		log.info("All lexicalizations collected in " + timer.stop() + "\n");
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

	private synchronized static void serialize(CompactDictionary cache, Path cache_file)
	{
		try
		{
			FileOutputStream fos = new FileOutputStream(cache_file.toString());
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(cache);
			fos.close();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
