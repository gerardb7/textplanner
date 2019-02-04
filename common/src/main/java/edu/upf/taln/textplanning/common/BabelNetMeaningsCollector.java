package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.DebugUtils.ThreadReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class BabelNetMeaningsCollector
{
	public static final ULocale language = ULocale.ENGLISH;
	public static final int LOGGING_STEP_SIZE = 100000;
	private final static Logger log = LogManager.getLogger();

	public static void collectMeanings(Path babel_path, Path output_file, boolean glosses_only, int max_meanings) throws Exception
	{
		log.info("Collecting meanings with " +  Runtime.getRuntime().availableProcessors() + " cores available");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		ThreadReporter reporter = new ThreadReporter(log);

		List<MeaningDictionary.Info> meanings;

		if (babel_path.toFile().exists() && babel_path.toFile().isDirectory())
		{
			MeaningDictionary bn = new BabelNetDictionary(babel_path);
			AtomicLong total_ids = new AtomicLong(0);

			Iterable<MeaningDictionary.Info> iterable = () -> bn.infoIterator(language);
			meanings = StreamSupport.stream(iterable.spliterator(), true)
					.limit(max_meanings)
					.parallel()
					.peek(l -> reporter.report()) // report number of threads
					.peek(id -> total_ids.incrementAndGet())
					.filter(i -> glosses_only ? !i.glosses.isEmpty() : !i.glosses.isEmpty() || !i.lemmas.isEmpty())
					.peek(id ->
					{
						long i = counter.incrementAndGet();
						if (i % LOGGING_STEP_SIZE == 0) log.info(i + " meanings collected");
					})
					.collect(toList());

			log.info(meanings.size() + " meanings collected in " + timer.stop() + "(out of " + total_ids + " synsets queried)");
			Serializer.serialize(meanings, output_file);
			log.info("Meanings serialized to " + output_file);
		}
		else
			throw new Exception("A path must be provided to the BabelNet config folder or a meanings file: " + babel_path);
	}
}
