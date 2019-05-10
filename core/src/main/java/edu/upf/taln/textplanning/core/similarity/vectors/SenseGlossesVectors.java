package edu.upf.taln.textplanning.core.similarity.vectors;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SenseGlossesVectors extends Vectors
{
	private final ULocale language;
	private final Function<String, List<String>> glosses;
	private final SentenceVectors sentence_vectors;

	public SenseGlossesVectors(ULocale language, Function<String, List<String>> glosses, SentenceVectors sentence_vectors)
	{
		this.language = language;
		this.glosses = glosses;
		this.sentence_vectors = sentence_vectors;
	}

	public void setGlossesFunction( Function<String, List<String>> glosses)
	{
		this.glosses = glosses;
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
		final List<String> item_glosses = glosses.apply(item);
		if (item_glosses.isEmpty())
			return Optional.empty();

		final List<String> tokens = item_glosses.stream()
				.flatMap(g -> {
					final StringTokenizer stringTokenizer = new StringTokenizer(g, " \t\n\r\f,.:;?!{}()[]'");
					final List<Object> list = Collections.list(stringTokenizer);
					return list.stream().map(Object::toString);
				})
				.collect(toList());

		if (tokens.isEmpty())
			return Optional.empty();

		final List<String> filtered_tokens = tokens.stream()
				.filter(t -> StopWordsFilter.test(t, language)) // exclude both function and frequent words
				.collect(toList());

		// If all tokens are function words, include them
		if (filtered_tokens.isEmpty())
			filtered_tokens.addAll(tokens);

		return sentence_vectors.getVector(filtered_tokens);
	}
}
