package edu.upf.taln.textplanning.core.bias;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;

// Maps meanings to lists of tokens taken from its mentions in a text, i.e. tokens surrounding the mention of the meaning
public abstract class ContextFunction
{
	private final List<String> context_tokens; // context used for nominal expressions
	private final Map<String, List<Mention>> meanings2mentions;
	protected final ULocale language;
	private final static Logger log = LogManager.getLogger();

	public ContextFunction(ULocale language, List<String> tokens, int min_frequency, List<Candidate> candidates)
	{
		this.language = language;
		context_tokens = filterTokens(tokens.stream()
				.distinct()
				.collect(toList()));
		context_tokens.removeIf(t -> Collections.frequency(tokens, t) < min_frequency);
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
