package edu.upf.taln.textplanning.core.similarity.vectors;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SenseGlossesVectors implements Vectors
{
	private final MeaningDictionary dictionary;
	private final ULocale language;
	private final SentenceVectors sentence_vectors;

	public SenseGlossesVectors(MeaningDictionary dictionary, ULocale language, SentenceVectors sentence_vectors)
	{
		this.dictionary = dictionary;
		this.language = language;
		this.sentence_vectors = sentence_vectors;
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return dictionary.contains(item);
	}

	@Override
	public int getNumDimensions()
	{
		return sentence_vectors.getNumDimensions();
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		final List<String> glosses = dictionary.getGlosses(item, language);
		if (glosses.isEmpty())
			return Optional.empty();

		final List<String> tokens = glosses.stream()
				.flatMap(g -> Arrays.stream(g.split("\\s")))
				.filter(t -> StopWordsFilter.filter(t, language))
				.collect(Collectors.toList());
		if (tokens.isEmpty())
			return Optional.empty();

		return sentence_vectors.getVector(tokens);
	}
}
