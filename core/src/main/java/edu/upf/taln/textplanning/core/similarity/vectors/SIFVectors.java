package edu.upf.taln.textplanning.core.similarity.vectors;

import com.easemob.TextualSim;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SIFVectors implements SentenceVectors, BiFunction<double [], double [], Double>
{
	private final TextualSim sif;
	private final Vectors word_vectors;

	public SIFVectors(Vectors word_vectors, Function<String, Double> weights)
	{
		sif = new TextualSim(s -> word_vectors.getVector(s).orElse(word_vectors.getUnknownVector()), word_vectors.getNumDimensions(), weights);
		this.word_vectors = word_vectors;
	}


	// A meaningful SIF vector can be produced if at least on the tokens in which item is divided has a word vector
	@Override
	public boolean isDefinedFor(String item)
	{
		return Arrays.stream(item.split("\\s")).anyMatch(word_vectors::isDefinedFor);
	}

	// List of tokens
	public Optional<double[]> getVector(List<String> items)
	{
		return Optional.of(sif.getEmbedding(items).getRow(0));
	}

	@Override
	public double[] getUnknownVector()
	{
		return word_vectors.getUnknownVector();
	}

	@Override
	public int getNumDimensions()
	{
		return word_vectors.getNumDimensions();
	}

	@Override
	public Double apply(double[] v1, double[] v2)
	{
		return sif.score(v1, v2);
	}
}
