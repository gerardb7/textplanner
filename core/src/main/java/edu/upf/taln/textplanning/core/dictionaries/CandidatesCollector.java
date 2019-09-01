package edu.upf.taln.textplanning.core.dictionaries;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CandidatesCollector
{
	private static final int LOGGING_STEP_SIZE = 10000;
	private static final int CHUNK_SIZE = 1000000;
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
	public static Map<Mention, List<Candidate>> collect(MeaningDictionary dictionary,
	                                                    ULocale language, List<Mention> mentions)
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
	public static  void collect(MeaningDictionary dictionary, ULocale language, Path file,
	                    TriConsumer<String, String, List<String>> meanings_consumer,
	                    TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                    Function<String, Optional<String>> label_function,
	                    Function<String, List<String>> glosses_function,
	                    BiFunction<String, Character, List<String>> meanings_function,
	                    Consumer<Path> serializer)
	{
		collectMeanings(dictionary, language, file, meanings_consumer, label_function, glosses_function, serializer);
		collectLexicalizations(dictionary, language, file, lexicalizations_consumer, meanings_function, serializer);
	}

	// Collects all meanings for a given set of lexicalizations from a dictionary.
	// Then adds lexicalization and meanings info to cache
	public static  void collect(Set<Pair<String, POS.Tag>> forms, MeaningDictionary dictionary,
	                            ULocale language, Path file,
	                            TriConsumer<String, String, List<String>> meanings_consumer,
	                            TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                            Function<String, Optional<String>> label_function,
	                            Function<String, List<String>> glosses_function,
	                            BiFunction<String, Character, List<String>> meanings_function,
	                            Consumer<Path> serializer)
	{
		final List<Triple<String, Character, List<String>>> lexicalizations_info =
				CandidatesCollector.addLexicalizations(forms, dictionary, language, lexicalizations_consumer);
		CandidatesCollector.checkLexicalizations(lexicalizations_info, meanings_function);

		Set<String> meanings = lexicalizations_info.stream()
				.map(Triple::getRight)
				.flatMap(List::stream)
				.collect(toSet());
		final List<Triple<String, String, List<String>>> meanings_info =
				CandidatesCollector.addMeanings(meanings, dictionary, language, meanings_consumer);
		CandidatesCollector.checkMeanings(meanings_info, label_function, glosses_function);
		serializer.accept(file);
	}

	private static void collectMeanings(MeaningDictionary dictionary, ULocale language, Path file,
	                             TriConsumer<String, String, List<String>> meanings_consumer,
	                             Function<String, Optional<String>> label_function,
                                 Function<String, List<String>> glosses_function,
                                 Consumer<Path> serializer)
	{
		final Stopwatch gtimer = Stopwatch.createStarted();
		int iterate_counter = 0;
		final Iterator<String> meaning_it = dictionary.meaning_iterator();

		while (meaning_it.hasNext())
		{
			final Stopwatch timer = Stopwatch.createStarted();

			// Collect meanings
			log.info("\nCollecting meaning ids for language " + language);
			final List<String> meanings = new ArrayList<>();
			int i = 0;
			while(i < CHUNK_SIZE && meaning_it.hasNext())
			{
				meanings.add(meaning_it.next());
				if (++i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + i + " meanings iterated in " + gtimer);
			}
			iterate_counter += i;
			log.info(iterate_counter + " meanings iterated and collected from dictionary");

			// Query meanings in dictionary and add them to cache
			final List<Triple<String, String, List<String>>> meanings_info =
					addMeanings(meanings, dictionary, language, meanings_consumer);
			checkMeanings(meanings_info, label_function, glosses_function);
			serializer.accept(file);

			log.info("Chunk completed in " + timer.stop());
		}
		log.info("\n***All meanings collected in " + gtimer.stop() + "***\n");
	}

	private static List<Triple<String, String, List<String>>> addMeanings( Collection<String> meanings,
                                    MeaningDictionary dictionary, ULocale language,
	                                TriConsumer<String, String, List<String>> meanings_consumer)
	{
		final DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final Stopwatch timer = Stopwatch.createStarted();

		// query meanings info
		log.info("Querying meanings info from dictionary");
		AtomicInteger chunk_counter = new AtomicInteger();
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

					if (chunk_counter.incrementAndGet() % LOGGING_STEP_SIZE == 0)
						log.info("\t" + chunk_counter.get() + " meanings queried in " + timer);

					return Triple.of(meaning, label_str, glosses);
				})
				.filter(t -> StringUtils.isNotBlank(t.getMiddle()) || !t.getRight().isEmpty())
				.collect(toList());
		log.info(chunk_counter.get() + " meanings queried in " + timer.stop());

		// add meanings to cache
		meanings_info.forEach(info ->
				meanings_consumer.accept(info.getLeft(), info.getMiddle(), info.getRight())
		);
		log.info(meanings_info.size() + " meanings passed to cache");

		return meanings_info;
	}

	private static void checkMeanings(List<Triple<String, String, List<String>>> meanings_info,
	                                  Function<String, Optional<String>> label_function,
	                                  Function<String, List<String>> glosses_function)
	{
		log.info("Checking meanings");
		meanings_info.forEach(info ->
		{
			final String meaning = info.getLeft();
			final String dict_label = info.getMiddle();
			final List<String> dict_glosses = info.getRight();

			final String cache_label = label_function.apply(meaning).orElse("");
			if (!dict_label.equals(cache_label))
				log.error("Labels do not match for meaning " + meaning + ": dictionary label is \"" + dict_label + "\" and cache label is \"" + cache_label + "\"");

			final List<String> cache_glosses = glosses_function.apply(meaning);
			// ignore order when comparing
			if (!CollectionUtils.isEqualCollection(dict_glosses, cache_glosses))
				log.error("Glosses mismatch for meaning " + meaning + ": dictionary glosses are \n\t" + dict_glosses + "\ncache label are \n\t" + cache_glosses);
		});
	}

	private static void collectLexicalizations(MeaningDictionary dictionary, ULocale language, Path file,
	                                    TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                                    BiFunction<String, Character, List<String>> meanings_function,
	                                    Consumer<Path> serializer)
	{
		final Stopwatch gtimer = Stopwatch.createStarted();
		int iterate_counter = 0;
		final Iterator<Triple<String, POS.Tag, ULocale>> lexicon_it = dictionary.lexicon_iterator();

		while (lexicon_it.hasNext())
		{
			final Stopwatch timer = Stopwatch.createStarted();

			// Collect lexicalizations
			log.info("\nCollecting lexicalizations for language " + language);
			final List<Pair<String, POS.Tag>> lexicalizations = new ArrayList<>();
			int i = 0;
			while(i < CHUNK_SIZE && lexicon_it.hasNext())
			{
				final Triple<String, POS.Tag, ULocale> entry = lexicon_it.next();
				if (++i % LOGGING_STEP_SIZE == 0)
					log.info("\t" + i + " lexicalizations iterated in " + gtimer);

				if (entry.getRight().equals(language))
					lexicalizations.add(Pair.of(entry.getLeft(), entry.getMiddle()));
			}
			iterate_counter += i;
			log.info(iterate_counter + " lexicalizations iterated and " + lexicalizations.size() + " collected");

			// Query lexicalizations in dictionary and add to cache
			final List<Triple<String, Character, List<String>>> lexicalizations_info =
					addLexicalizations(lexicalizations, dictionary, language, lexicalizations_consumer);
			checkLexicalizations(lexicalizations_info, meanings_function);
			serializer.accept(file);

			log.info("Chunk completed in " + timer.stop());
		}
		log.info("All lexicalizations collected in " + gtimer.stop() + "\n");
	}

	private static List<Triple<String, Character, List<String>>> addLexicalizations(Collection<Pair<String, POS.Tag>> lexicalizations,
	                                      MeaningDictionary dictionary, ULocale language,
	                                      TriConsumer<String, Character, List<String>> lexicalizations_consumer)
	{
		final DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final Stopwatch timer = Stopwatch.createStarted();

		// query lexicalization meanings
		log.info("Querying lexicalization meanings from dictionary");
		reporter.reset();
		AtomicInteger chunk_counter = new AtomicInteger();
		final List<Triple<String, Character, List<String>>> lexicalizations_info = lexicalizations.parallelStream()
				.peek(meaning -> reporter.report())
				.filter(p -> StringUtils.isNotBlank(p.getLeft()))
				.map(p ->
				{
					final String word = p.getLeft();
					final Character pos_tag = POS.toTag.get(p.getRight());
					final List<String> dict_meanings = dictionary.getMeanings(word, p.getRight(), language);
					if (chunk_counter.incrementAndGet() % LOGGING_STEP_SIZE == 0)
						log.info("\t" + chunk_counter.get() + " lexicalizations queried in " + timer);

					return Triple.of(word, pos_tag, dict_meanings);
				})
				.filter(t -> !t.getRight().isEmpty())
				.collect(toList());
		log.info(chunk_counter.get() + " lexicalizations queried in " + timer.stop());

		// add lexicalizations to cache
		lexicalizations_info.forEach(info ->
		{
			final String word = info.getLeft();
			final Character pos_tag = info.getMiddle();
			final List<String> l_meanings = info.getRight();
			lexicalizations_consumer.accept(word, pos_tag, l_meanings);
		});
		log.info(lexicalizations_info.size() + " lexicalizations passed to cache");

		return lexicalizations_info;
	}

	private static void checkLexicalizations(List<Triple<String, Character, List<String>>> lexicalizations_info,
	                                         BiFunction<String, Character, List<String>> meanings_function)
	{
		log.info("Checking lexicalizations");
		lexicalizations_info.forEach(info ->
		{
			final String word = info.getLeft();
			final Character pos_tag = info.getMiddle();
			final List<String> dict_meanings = info.getRight();

			final List<String> cache_meanings = meanings_function.apply(word, pos_tag);
			// Due to non-deterministic order in list of meanings returned by the BabelNet API (affecting the last meanings of the list), compare elements in list while ignoring order
			if (!CollectionUtils.isEqualCollection(dict_meanings, cache_meanings))
				log.error("\tMeanings mismatch for lexicalization " + word + " and POS " + pos_tag + ": \n\t\tdictionary meanings are " + dict_meanings + "\n\t\tcache meanings are " + cache_meanings);
		});
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
