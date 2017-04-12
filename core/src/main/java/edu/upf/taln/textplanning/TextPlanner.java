package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.coherence.DiscoursePlanner;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.pattern.PatternExtraction;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text plan
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final WeightingFunction weighting;
	private final EntitySimilarity similarity; // used to calculate semantic similarity
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		public int numPatterns = 10; // Number of patterns to return
		public double relevanceLowerBound = 0.1; // Entities with relevance below this value have their score set to 0
		public double simLowerBound = 0.1; // Pairs of entities with similarity below this value have their score set to 0
		public double dampingFactor = 0.1; // damping factor to control balance between relevance bias and similarity
		public double rankingStopThreshold = 0.0001; // stopping threshold for the main ranking algorithm
		public boolean generateStats = false;
		public String stats = "";
	}

	/**
	 * @param inWeightingFunction functions used to weight contents
	 * @param inSimilarityFunction similarity function for pairs of entities
	 */
	public TextPlanner(WeightingFunction inWeightingFunction, EntitySimilarity inSimilarityFunction)
	{
		weighting = inWeightingFunction;
		similarity = inSimilarityFunction;
	}

	/**
	 * Generates a text plan from some contents encoded as annotated trees
	 * @param inContents initial set of contents
	 * @return list of patterns
	 */
	public List<SemanticTree> planText(List<SemanticTree> inContents, Options inOptions)
	{
		try
		{
			log.info("***Planning started***");

			// 1- Collect entities in trees
			List<Entity> entities = inContents.stream()
					.map(SemanticTree::vertexSet)
					.flatMap(Set::stream)
					.map(Node::getEntity)
					.distinct() // equality tested through labels of objects
					.collect(Collectors.toList());

			// 2- Create ranking matrix
			log.info("**Creating ranking matrix**");
			Stopwatch timer = Stopwatch.createStarted();
			weighting.setCollection(inContents);
			Matrix rankingMatrix = PowerIterationRanking.createRankingMatrix(entities, weighting, similarity, inOptions);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 3- Rank entities using biased semantic ranking and power iteration method
			log.info("**Power iteration ranking**");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Power iteration ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();

			// 4- Create semantic graph and extract patterns
			log.info("**Extracting patterns**");
			timer.reset(); timer.start();
			Map<Entity, Double> rankedEntities = IntStream.range(0, ranking.length)
					.boxed()
					.collect(Collectors.toMap(entities::get, i -> ranking[i]));
			Set<SemanticTree> patterns = PatternExtraction.extract(inContents, rankedEntities, inOptions.numPatterns);
			log.info("Pattern extraction took " + timer.stop());

			// 5- Sort the trees into a coherence-optimized list
			log.info("**Structuring patterns**");
			timer.reset(); timer.start();
			List<SemanticTree> patternList = DiscoursePlanner.structurePatterns(patterns, similarity, rankedEntities);
			log.info("Pattern structuring took " + timer.stop());

			// 6- Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("**Generating stats**");
				timer.reset();
				timer.start();
				String stats = StatsReporter.reportStats(inContents, weighting, similarity);
				log.info(stats);
				log.info("Stats generation took " + timer.stop());
			}

			return patternList;
		}
		catch (Exception e)
		{
			log.error("***Planning failed***");
			throw new RuntimeException(e);
		}
	}



}
