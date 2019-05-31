package edu.upf.taln.textplanning.core.bias;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;

// Maps meanings to lists of tokens taken from its mentions in a text, i.e. tokens surrounding the mention of the meaning
public abstract class ContextFunction
{
	private final List<String> context_tokens = new ArrayList<>(); // context used for nominal expressions
	private final Map<String, List<Mention>> meanings2mentions;
	protected final ULocale language;
	private static final int min_context_size = 5;
	private final static Logger log = LogManager.getLogger();

	public ContextFunction(ULocale language, List<String> tokens, int min_frequency, List<Candidate> candidates)
	{
		this.language = language;
		final AtomicInteger actual_min_frequency = new AtomicInteger(min_frequency);
		boolean stop = min_frequency < 2;

		while (!stop)
		{
			context_tokens.addAll(filterTokens(tokens.stream()
					.distinct()
					.collect(toList())));
			context_tokens.removeIf(t -> Collections.frequency(tokens, t) < actual_min_frequency.get());

			if (context_tokens.size() < min_context_size)
			{
				if (actual_min_frequency.decrementAndGet() < 2)
					stop = true;
				else
					context_tokens.clear();
			}
			else
				stop = true;
		}

		log.info("Nominal context set to: " + context_tokens);


		this.meanings2mentions = candidates.stream()
				.collect(groupingBy(c -> c.getMeaning().getReference(), mapping(Candidate::getMention, toList())));
	}

	public List<String> getContext(String meaning)
	{
		return meanings2mentions.get(meaning).stream()
				.map(mention -> mention.isNominal() ? context_tokens : getWindow(mention))
				.flatMap(List::stream)
				.distinct()
				.collect(toList());
	}

	protected List<String> filterTokens(List<String> tokens)
	{
		final Predicate<String> stop_words_filter = (str) -> StopWordsFilter.test(str, language);

		return tokens.stream()
				.filter(stop_words_filter)
				.collect(toList());

	}

	protected abstract List<String> getWindow(Mention mention);
}
