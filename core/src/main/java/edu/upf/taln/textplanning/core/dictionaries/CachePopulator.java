package edu.upf.taln.textplanning.core.dictionaries;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.ibm.icu.util.ULocale;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Assigns candidate meanings to graph vertices.
 */
public class CachePopulator
{
	private static final int LOGGING_STEP_SIZE = 100000;
	private static final int CHUNK_SIZE = 1000000;
	private final static Logger log = LogManager.getLogger();

	// Collects all meanings and forms from a dictionary. May take a very long time!
	public static  void populate(MeaningDictionary dictionary, ULocale language, Path file,
	                             TriConsumer<String, String, List<String>> meanings_consumer,
	                             TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                             Function<String, Optional<String>> label_function,
	                             Function<String, List<String>> glosses_function,
	                             BiFunction<String, Character, List<String>> meanings_function,
	                             Consumer<Path> serializer)
	{
		populateMeanings(dictionary, language, file, meanings_consumer, label_function, glosses_function, serializer);
		populateLexicalizations(dictionary, language, file, lexicalizations_consumer, meanings_function, serializer);
	}

	// Collects all meanings for a given set of lexicalizations from a dictionary.
	// Then adds lexicalization and meanings info to cache
	public static  void populate(Set<Pair<String, POS.Tag>> forms, MeaningDictionary dictionary,
	                             ULocale language, Path file,
	                             TriConsumer<String, String, List<String>> meanings_consumer,
	                             TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                             Function<String, Optional<String>> label_function,
	                             Function<String, List<String>> glosses_function,
	                             BiFunction<String, Character, List<String>> meanings_function,
	                             Consumer<Path> serializer)
	{
		final List<Triple<String, Character, List<String>>> lexicalizations_info =
				CachePopulator.addLexicalizations(forms, dictionary, language, lexicalizations_consumer);
		CachePopulator.checkLexicalizations(lexicalizations_info, meanings_function);

		Set<String> meanings = lexicalizations_info.stream()
				.map(Triple::getRight)
				.flatMap(List::stream)
				.collect(toSet());
		final List<Triple<String, String, List<String>>> meanings_info =
				CachePopulator.addMeanings(meanings, dictionary, language, meanings_consumer);
		CachePopulator.checkMeanings(meanings_info, label_function, glosses_function);
		serializer.accept(file);
	}

	private static void populateMeanings(MeaningDictionary dictionary, ULocale language, Path file,
	                                     TriConsumer<String, String, List<String>> meanings_consumer,
	                                     Function<String, Optional<String>> label_function,
	                                     Function<String, List<String>> glosses_function,
	                                     Consumer<Path> serializer)
	{
		log.info("\nCollecting meaning ids for language " + language);
		final Stopwatch gtimer = Stopwatch.createStarted();
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicInteger iterate_counter = new AtomicInteger();

		// Collect meanings
		final Stream<String> stream = dictionary.getMeaningsStream(language);
		Iterators.partition(stream.iterator(), CHUNK_SIZE).forEachRemaining(chunk ->
		{
			log.info(DebugUtils.printInteger(iterate_counter.get()) + " meanings iterated from dictionary in " + timer);

			// Query meanings in dictionary and add them to cache
			final List<Triple<String, String, List<String>>> meanings_info =
					addMeanings(chunk, dictionary, language, meanings_consumer);
			checkMeanings(meanings_info, label_function, glosses_function);
			serializer.accept(file);

			log.info("Chunk completed in " + gtimer);
			timer.reset();
		});
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

					return Triple.of(meaning, label_str, glosses);
				})
				.filter(t -> StringUtils.isNotBlank(t.getMiddle()) || !t.getRight().isEmpty())
				.collect(toList());
		log.info(DebugUtils.printInteger(chunk_counter.get()) + " meanings queried in " + timer.stop());
		timer.reset();timer.start();

		// add meanings to cache
		meanings_info.forEach(info ->
				meanings_consumer.accept(info.getLeft(), info.getMiddle(), info.getRight())
		);
		log.info(DebugUtils.printInteger(meanings_info.size()) + " meanings passed to cache in " + timer.stop());

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

	private static void populateLexicalizations(MeaningDictionary dictionary, ULocale language, Path file,
	                                            TriConsumer<String, Character, List<String>> lexicalizations_consumer,
	                                            BiFunction<String, Character, List<String>> meanings_function,
	                                            Consumer<Path> serializer)
	{
		log.info("\nCollecting lexicalizations for language " + language);
		final Stopwatch gtimer = Stopwatch.createStarted();
		final AtomicInteger iterate_counter = new AtomicInteger();
		final Stream<Pair<String, POS.Tag>> stream = dictionary.getLexicalizationsStream(language);
		final Stopwatch timer = Stopwatch.createStarted();

		Iterators.partition(stream.iterator(), CHUNK_SIZE).forEachRemaining(chunk ->
		{
			// Collect lexicalizations
			log.info(DebugUtils.printInteger(iterate_counter.get()) + " lexicalizations iterated from dictionary in " + timer);

			// Query lexicalizations in dictionary and add to cache
			final List<Triple<String, Character, List<String>>> lexicalizations_info =
					addLexicalizations(chunk, dictionary, language, lexicalizations_consumer);
			checkLexicalizations(lexicalizations_info, meanings_function);
			serializer.accept(file);

			log.info("Chunk completed in " + gtimer);
			timer.reset();
		});
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
					return Triple.of(word, pos_tag, dict_meanings);
				})
				.filter(t -> !t.getRight().isEmpty())
				.collect(toList());
		log.info(DebugUtils.printInteger(chunk_counter.get()) + " lexicalizations queried in " + timer.stop());
		timer.reset();timer.start();

		// add lexicalizations to cache
		lexicalizations_info.forEach(info ->
		{
			final String word = info.getLeft();
			final Character pos_tag = info.getMiddle();
			final List<String> l_meanings = info.getRight();
			lexicalizations_consumer.accept(word, pos_tag, l_meanings);
		});
		log.info(DebugUtils.printInteger(lexicalizations_info.size()) + " lexicalizations passed to cache in " + timer.stop());

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
}
