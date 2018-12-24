package edu.upf.taln.textplanning.core.similarity;

import com.easemob.TextualSim;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class VectorsSIFSimilarity implements SimilarityFunction
{
	private final TextualSim sif;
	private final Vectors vectors;

	public VectorsSIFSimilarity(Vectors vectors, Function<String, Double> weights)
	{
		sif = new TextualSim(vectors::getVector, vectors.getNumDimensions(), weights);
		this.vectors = vectors;
	}

	public double[] getVector(List<String> tokens)
	{
		return sif.getEmbedding(tokens).getRow(0);
	}

	@Override
	public boolean isDefinedFor(String e)
	{
		return vectors.isDefinedFor(e); // sif library will still produce a similarity value, probably 0
	}

	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return vectors.isDefinedFor(e1) && vectors.isDefinedFor(e2); // sif library will still produce a similarity value, probably 0
	}

	@Override
	public double computeSimilarity(String e1, String e2)
	{
		// assume text is tokenized
		return sif.score(Arrays.asList(e1.split("\\s")), Arrays.asList(e2.split("\\s")));
	}


}
