package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Set;

/**
 * Similarity for patterns is 1.0 if the sets of referred entities of the two patterns share at least one
 * item, and 0.0 otherwise.
 * For entities is 1 if both are the same.
 * Immutable class.
 */
public final class EntitySharingSimilarity implements PatternSimilarity
{
	@Override
	public double getSimilarity(SemanticTree inPattern1,
	                            SemanticTree inPattern2)
	{
		Set<String> entities1 = inPattern1.getEntities();
		Set<String> entities2 = inPattern2.getEntities();
		if (entities1.isEmpty() && entities2.isEmpty())
		{
			return 0.0;
		}

		Collection<String> commonEntities = CollectionUtils.intersection(entities1, entities2);
		if (commonEntities.isEmpty())
		{
			return 0.0;
		}

		return ((double) commonEntities.size()) /
				((double) CollectionUtils.union(entities1, entities2).size());
	}

	@Override
	public double getSimilarity(String inEntity1, String inEntity2)
	{
		return inEntity1.equals(inEntity2) ? 1.0 : 0.0;
	}
}
