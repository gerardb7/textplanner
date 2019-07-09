package edu.upf.taln.textplanning.core.similarity.vectors;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SenseGlossesVectors extends Vectors
{
	private final ULocale language;
	private final Function<String, List<String>> glosses;
	private final SentenceVectors sentence_vectors;
	private final Map<String, Optional<double[]>> vectors;

	public SenseGlossesVectors(ULocale language, List<String> meanings, Function<String, List<String>> glosses, SentenceVectors sentence_vectors)
	{
		this.language = language;
		this.glosses = glosses;
		this.sentence_vectors = sentence_vectors;

		vectors = meanings.stream()
				.collect(toMap(r -> r, r ->
				{
					final List<String> r_glosses = glosses.apply(r);
					final List<String> tokens = getTokens(r_glosses);
					return sentence_vectors.getVector(tokens);
				}));
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return !glosses.apply(item).isEmpty();
	}

	@Override
	public int getNumDimensions()
	{
		return sentence_vectors.getNumDimensions();
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		return vectors.get(item);
	}

	private List<String> getTokens(List<String> glosses)
	{
		final List<String> tokens = glosses.stream()
				.flatMap(g -> {
					final StringTokenizer stringTokenizer = new StringTokenizer(g, " \t\n\r\f,.:;?!{}()[]'");
					final List<Object> list = Collections.list(stringTokenizer);
					return list.stream().map(Object::toString);
				})
				.collect(toList());

		final List<String> filtered_tokens = tokens.stream()
				.filter(t -> StopWordsFilter.test(t, language)) // exclude both function and frequent words
				.collect(toList());

		// If all tokens are function words, include them
		if (filtered_tokens.isEmpty())
			filtered_tokens.addAll(tokens);

		return filtered_tokens;
	}
}
