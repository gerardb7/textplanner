package edu.upf.taln.textplanning.core.corpus;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SameMeaningPredicate implements BiPredicate<Mention, Mention>
{
	protected final List<Mention> word_mentions;
	protected final Map<Mention, Candidate> word_meanings;

	public SameMeaningPredicate(Corpora.Text text)
	{
		// The items to rank are single content word mentions
		word_mentions = text.sentences.stream()
				.flatMap(s -> s.ranked_words.stream()
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
				.collect(toList());

		// determine their meanings...
		final Map<Mention, Candidate> all_meanings = text.sentences.stream()
				.flatMap(s -> s.disambiguated_meanings.values().stream())
				.collect(toMap(Candidate::getMention, c -> c));

		// ... single-word meanings
		word_meanings = all_meanings.entrySet().stream()
				.filter(e -> word_mentions.contains(e.getKey()))
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

		final Map<Mention, Candidate> multiword_meanings = all_meanings.entrySet().stream()
				.filter(e -> e.getKey().isMultiWord())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

		// ... or part of a multiword with a meaning
		multiword_meanings.forEach((mw, c) -> word_mentions.stream()
				.filter(w -> w.getContextId().equals(mw.getContextId()) &&
						mw.getSpan().getLeft() <= w.getSpan().getLeft() &&
						mw.getSpan().getRight() >= w.getSpan().getRight())
				.forEach(w ->
				{
					if (!word_meanings.containsKey(w))
						word_meanings.put(w, c);
					else
					{
						// in the case of overlapping multiwords, a word may be assigned two candidates
						// choose that with highest weight
						final Candidate c2 = word_meanings.get(w);
						if (c.getWeight().orElse(0.0) > c2.getWeight().orElse(0.0))
							word_meanings.put(w, c);
					}
				}));
	}

	@Override
	public boolean test(Mention m1, Mention m2)
	{
		if (!word_meanings.containsKey(m1) || !word_meanings.containsKey(m2))
			return false;

		return word_meanings.get(m1).getMeaning().equals(word_meanings.get(m2).getMeaning());
	}

	public Map<Mention, Candidate> getWordMeanings()
	{
		return word_meanings;
	}
}
