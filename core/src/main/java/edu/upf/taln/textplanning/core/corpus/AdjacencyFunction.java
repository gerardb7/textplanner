package edu.upf.taln.textplanning.core.corpus;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public abstract class AdjacencyFunction implements BiPredicate<Mention, Mention>
{
	protected final List<Mention> word_mentions;

	public AdjacencyFunction(Corpora.Text text)
	{
		// The items to rank are single content word mentions
		word_mentions = text.sentences.stream()
				.flatMap(s -> s.ranked_words.stream()
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
				.collect(toList());
	}

	@Override
	public abstract boolean test(Mention m1, Mention m2);
	public abstract String getLabel(Mention m1, Mention m2);
	public List<Mention> getSortedWordMentions()
	{
		return word_mentions;
	}
}
