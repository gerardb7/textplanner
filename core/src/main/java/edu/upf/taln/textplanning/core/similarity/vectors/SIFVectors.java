package edu.upf.taln.textplanning.core.similarity.vectors;

import com.easemob.TextualSim;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class SIFVectors implements Vectors
{
	private final TextualSim sif;
	private final Vectors vectors;

	public SIFVectors(Vectors vectors, Function<String, Double> weights)
	{
		sif = new TextualSim(s -> vectors.getVector(s).orElse(vectors.getUnknownVector()), vectors.getNumDimensions(), weights);
		this.vectors = vectors;
	}


	@Override
	public boolean isDefinedFor(String item)
	{
		return vectors.isDefinedFor(item); // sif library will still produce a similarity value, probably 0
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		return Optional.of(sif.getEmbedding(Arrays.asList(item.split("\\s"))).getRow(0));
	}

	public Optional<double[]> getVector(List<String> items)
	{
		return Optional.of(sif.getEmbedding(items).getRow(0));
	}

	@Override
	public double[] getUnknownVector()
	{
		return vectors.getUnknownVector();
	}

	@Override
	public int getNumDimensions()
	{
		return vectors.getNumDimensions();
	}

	public TextualSim getFunction() { return sif; }
}
