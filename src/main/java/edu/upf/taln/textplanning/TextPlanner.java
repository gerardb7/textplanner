package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.ContentGraphCreator;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.pattern.PatternExtraction;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
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
		public double dampingRelevance = 0.2; // damping factor to control bias towards prior relevance of entities
		public double rankingStopThreshold = 0.0001; // stopping threshold for the main ranking algorithm
		public double minRelevance = 0.1; // Entities with relevance below this value have their score set to 0
		public double simLowerBound = 0.0; // Pairs of entities with similarity below this value have their score set to 0
		public int patternBeamSize = 10; // Size of the beam used when searching for optimal patterns
		public double patternLambda = 0.5; // Controls balance between weight of nodes and cost of edges during pattern extraction
		public boolean generateStats = false;
		public String stats = "";

		@Override
		public String toString()
		{
			NumberFormat f = NumberFormat.getInstance();
			f.setRoundingMode(RoundingMode.UP);
			f.setMaximumFractionDigits(3);
			f.setMinimumFractionDigits(3);
			return "Params: numPatterns=" + numPatterns + " damping_rel=" + f.format(dampingRelevance) +
					" delta=" + f.format(rankingStopThreshold) + " min_rel=" + f.format(minRelevance) +
					" min_sim=" + f.format(simLowerBound) + " pattern_lambda=" + f.format(patternLambda) +
					"\n\n" + stats;
		}
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

			// 1- Create content graph
			log.info("**Creating content graph**");
			Stopwatch timer = Stopwatch.createStarted();
			SemanticGraph contentGraph = ContentGraphCreator.createContentGraph(inContents);
			log.info("Graph creation took " + timer.stop());

			// 2- Create entity ranking matrix
			log.info("**Creating ranking matrix**");
			timer.reset(); timer.start();
			weighting.setCollection(inContents);
			Matrix rankingMatrix =
					PowerIterationRanking.createRankingMatrix(contentGraph, weighting, similarity, inOptions);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 3- Rank entities using biased semantic ranking and power iteration method
			log.info("**Power iteration ranking**");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Power iteration ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();
			// Assign weights to nodes in the graph
			List<Node> nodes = new ArrayList<>(contentGraph.vertexSet());
			IntStream.range(0, ranking.length)
					.forEach(i -> nodes.get(i).setWeight(ranking[i]));

			// 4- Extract patterns from content graph
			log.info("**Extracting patterns**");
			timer.reset(); timer.start();

			List<SemanticTree> patterns = PatternExtraction.extract(contentGraph, inOptions.numPatterns,
					inOptions.patternBeamSize, inOptions.patternLambda);
			log.info("Pattern extraction took " + timer.stop());

			// 5- Sort the trees into a coherence-optimized list
			log.info("**Structuring patterns**");
			timer.reset(); timer.start();
			//patterns = DiscoursePlanner.structurePatterns(patterns, similarity);
			log.info("Pattern structuring took " + timer.stop());

			// 6- Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("**Generating stats**");
				timer.reset();
				timer.start();
				inOptions.stats = StatsReporter.reportStats(inContents, weighting, similarity, contentGraph, inOptions);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("***Planning failed***");
			throw new RuntimeException(e);
		}
	}



}
