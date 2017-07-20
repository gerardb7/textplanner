package edu.upf.taln.textplanning.similarity;


import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Document;
import edu.upf.taln.textplanning.structures.LinguisticStructure;

/**
 * Interface for similarity measures between pairs of a mention and a candidate entity/Sense
 */
public class CandidateSimilarity
{
	private final EntitySimilarity sem;
	private static final double c = 4.0; // width of the gaussian, standard deviation in distances in number fo sentences between pairs of mentions
	private static final double alpha = 0.2; // controls balance between semantic similarity and closeness in text

	public CandidateSimilarity(EntitySimilarity s)
	{
		sem = s;
	}

	public double computeSimilarity(Candidate c1, Candidate c2)
	{
		if (c1 == c2)
			return 1.0;

		double semantic_sim = sem.computeSimilarity(c1.getEntity(), c2.getEntity());

		double closeness = 0.0;
		LinguisticStructure s1 = c1.getMention().getStructure();
		Document d1 = s1.getDocument();
		LinguisticStructure s2 = c1.getMention().getStructure();
		Document d2 = s2.getDocument();

		if (d1 == d2)
		{
			int p1 = d1.getPosition(s1);
			int p2 = d1.getPosition(s2);
			int d = Math.abs(p1 - p2);

			// linear relation
			//closeness = 1.0 - dist/c*2 // f(0) = 1.0 (same structure) f(1)=0.875 f(2)=0.75 f(3)=0.625 f(4)=0.5 ...

			// Apply gaussian a*e^(-(d-b)^2/2*c^2) where
			closeness = Math.exp(-Math.pow(2, d) / 2*Math.pow(2, c)); // f(0) = 1.0 (same structure) f(1)=0.98 f(2)=0.93 f(3)=0.84 f(4)=0.73
		}

		// return linear combination of semantic similarity and closeness
		return (1.0 - alpha)*semantic_sim  + alpha*closeness;
	}

}
