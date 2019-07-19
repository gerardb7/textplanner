package edu.upf.taln.textplanning.tools.evaluation.corpus;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class CorpusAdjacencyFunction implements BiPredicate<Mention, Mention>
{
	private final List<Mention> word_mentions;
	private final Map<Mention, Candidate> word_meanings;
	private final int context_size;
	private final boolean additional_links; // if false, only textual adjacency is considered.

	public CorpusAdjacencyFunction(EvaluationCorpus.Text text, int context_size, boolean additional_links)
	{
		// single word word_mentions are the item to rank
		word_mentions = text.sentences.stream()
				.flatMap(s -> s.mentions.stream()
						.filter(not(Mention::isMultiWord))
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
				.collect(toList());

		// determine their meanings...
		final Map<Mention, Candidate> all_meanings = text.sentences.stream()
				.flatMap(s -> s.disambiguated.values().stream())
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
				.filter(w -> w.getSourceId().equals(mw.getSourceId()) &&
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

		this.context_size = context_size;
		this.additional_links = additional_links;
	}

	@Override
	public boolean test(Mention m1, Mention m2)
	{
		if (m1.equals(m2))
			return false;

		// Adjacent words? <- rewards words adjacent to highly ranked words. Side effect ->  penalizes words at the start and end of a sentence!
		if (m1.getSourceId().equals(m2.getSourceId()) &&
				(Math.abs(word_mentions.indexOf(m1) - word_mentions.indexOf(m2)) <= context_size))
			return true;

		// If no additional connections are considered, stop here.
		if (!additional_links)
			return false;

//		// Same lemma=? <- this rewards frequent lemmas
//		if (m1.getLemma().equals(m2.getLemma()))
//			return true;

		// Same meaning ? <- this rewards frequent meanings
		if (!word_meanings.containsKey(m1) || !word_meanings.containsKey(m2))
			return false;

		return word_meanings.get(m1).getMeaning().equals(word_meanings.get(m2).getMeaning());
	}

	public List<Mention> getSortedWordMentions()
	{
		return word_mentions;
	}

	public Map<Mention, Candidate> getWordMeanings()
	{
		return word_meanings;
	}
}
