package edu.upf.taln.textplanning.core.similarity.vectors;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class SenseGlossesVectors extends Vectors
{
	private final BiFunction<String, ULocale, List<String>> glosses;
	private final ULocale language;
	private final SentenceVectors sentence_vectors;

	public SenseGlossesVectors(BiFunction<String, ULocale, List<String>> glosses, ULocale language, SentenceVectors sentence_vectors)
	{
		this.glosses = glosses;
		this.language = language;
		this.sentence_vectors = sentence_vectors;
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return !glosses.apply(item, language).isEmpty();
	}

	@Override
	public int getNumDimensions()
	{
		return sentence_vectors.getNumDimensions();
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		final List<String> item_glosses = glosses.apply(item, language);
		if (item_glosses.isEmpty())
			return Optional.empty();

		final List<String> tokens = item_glosses.stream()
				.flatMap(g -> {
					final StringTokenizer stringTokenizer = new StringTokenizer(g, " \t\n\r\f,.:;?!{}()[]'");
					final List<Object> list = Collections.list(stringTokenizer);
					return list.stream().map(Object::toString);
				})
				.filter(t -> StopWordsFilter.test(t, language)) // exclude both function and frequent words
				.collect(Collectors.toList());
		if (tokens.isEmpty())
			return Optional.empty();

		return sentence_vectors.getVector(tokens);
	}
}
