package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.corpora.CorpusCounts;
import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.metrics.CorpusMetric;
import edu.upf.taln.textplanning.metrics.PatternMetric;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Tester using synthetic data
 */
public class TextPlannerSyntheticDataTest
{
	private String[] scoredSenses = {"e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9"};
	private String[] unscoredSenses = {"e11", "e12", "e21", "e22", "e31", "e32", "e41", "e42", "e51", "e52",
			"e61", "e62", "e71", "e72", "e81", "e82", "e91", "e92"};

	private class SyntheticCounts implements CorpusCounts
	{
		@Override
		public Counts getCounts(String inEntry1, String inEntry2, String inDomain)
		{
			if (inEntry1.length() == 3 || inEntry2.length() == 3)
			{
				return new Counts(inEntry1, inEntry2, 8, 0); // only score entities from first list;
			}

			int diff = Math.abs(Integer.parseInt(inEntry1.substring(1, inEntry1.length())) -
					Integer.parseInt(inEntry2.substring(1, inEntry2.length())));
			return new Counts(inEntry1, inEntry2, 8, 8 - diff);
		}

		@Override
		public Counts getCounts(String inEntry1, String inEntry2, String inDomain, int inDistance)
		{
			return getCounts(inEntry1, inEntry2, inDomain);
		}

		@Override
		public Counts getOrderedCounts(String inEntry1, String inEntry2, String inDomain, int inDistance)
		{
			return getCounts(inEntry1, inEntry2, inDomain);
		}
	}

	private class SyntheticVectors implements ItemSimilarity
	{
		@Override
		public boolean isDefinedFor(String inEntry1, String inEntry2)
		{
			return true;
		}

		@Override
		public double computeSimilarity(String inEntry1, String inEntry2)
		{
			if (inEntry1.equals(inEntry2))
				return 1.0;

			if ((inEntry1.length() == 2 && inEntry2.length() == 2) ||
					(inEntry1.length() == 3 && inEntry2.length() == 3))
			{
				return 0; // compute similarity only between an unscored and a scored entity
			}

			String scored = inEntry1.length() == 2 ? inEntry1 : inEntry2;
			String unscored = inEntry1.length() == 3 ? inEntry1 : inEntry2;

			int si = Integer.parseInt(scored.substring(1, 2));
			int ui1 = Integer.parseInt(unscored.substring(1, 2));
			int ui2 = Integer.parseInt(unscored.substring(2, 3)) + 1;

			if (si != ui1)
			{
				return 0.0;
			}
			return 1.0 / (double) ui2;
		}
	}

	@Test
	public void testPlanText() throws Exception
	{

		CorpusCounts corpusCounts = new SyntheticCounts();
		PatternMetric corpusMetric = new CorpusMetric(corpusCounts, CorpusMetric.Metric.Cooccurrence, "");
		List<Pair<PatternMetric, Double>> metrics = new ArrayList<>();
		metrics.add(Pair.of(corpusMetric, 0.8));
		ItemSimilarity vectors = new SyntheticVectors();
		SalientEntitiesMiner miner = new SalientEntitiesMiner(vectors);
		TextPlanner planner = new TextPlanner(null, metrics, null, vectors, null, miner);

		String e = scoredSenses[new Random().nextInt(scoredSenses.length)];
		int numTrees = 10;
		Set<SemanticTree> orderedTrees = generateSyntheticTrees(numTrees);
		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.0001;
		System.out.println("Creating a plan for entity " + e);
		planner.planText(Collections.singleton(e), orderedTrees, options);
	}

	private Set<SemanticTree> generateSyntheticTrees(int inNumTrees)
	{
		List<AnnotationInfo> anns = Stream.concat(Arrays.stream(scoredSenses), Arrays.stream(unscoredSenses))
				.map(e -> new AnnotationInfo(e, null, null, "NN", "f1=v1|f2=v2", e, 0.0))
				.collect(Collectors.toList());

		Random rand = new Random();
		int minSize = 3;
		int maxSize = 10;

		return IntStream.range(0, inNumTrees)
				.mapToObj(i -> {
					SemanticTree tree = new SemanticTree(chooseAnnotation(anns, rand), 0.0);
					IntStream.range(0, minSize + rand.nextInt(maxSize - minSize)) // choose tree size
							.forEach(j -> {
								List<OrderedTree.Node<Pair<AnnotationInfo, String>>> preOrder = tree.getPreOrder();
								OrderedTree.Node<Pair<AnnotationInfo, String>> parent =
										preOrder.get(rand.nextInt(preOrder.size())); // choose parent
								parent.addChild(Pair.of(chooseAnnotation(anns, rand), "")); // choose child
							});
					return tree;
				})
				.collect(Collectors.toSet());
	}

	private AnnotationInfo chooseAnnotation(List<AnnotationInfo> inAnns, Random inRand)
	{
		if (inRand.nextBoolean())
		{
			return inAnns.get(inRand.nextInt(inAnns.size()));
		}
		else
		{
			String id = "noRef" + inRand.nextInt(100);
			return new AnnotationInfo(id, id, id, "", "f1=v1|f2=v2", null, 0.0);
		}
	}
}