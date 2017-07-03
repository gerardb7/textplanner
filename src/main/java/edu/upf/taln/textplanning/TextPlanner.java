package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.ContentGraphCreator;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.disambiguation.EntityDisambiguator;
import edu.upf.taln.textplanning.pattern.PatternExtraction;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.NumberFormat;
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
	private final ItemSimilarity similarity; // used to calculate semantic similarity
	private final EntityDisambiguator disambiguator;
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		public int numPatterns = 25; // Number of patterns to return
		public double dampingRelevance = 0.6; // damping factor to control bias towards prior relevance of entities
		public double rankingStopThreshold = 0.00000001; // stopping threshold for the main ranking algorithm
		public double minRelevance = 0.0001; // pseudocount α for additive smoothing of relevance values
		public double simLowerBound = 0.6; // Pairs of entities with similarity below this value have their score set to 0
		public double patternLambda = 1.0; // Controls balance between weight of nodes and cost of edges during pattern extraction
		public boolean generateStats = false;
		public String stats = "";

		@Override
		public String toString()
		{
			NumberFormat f = NumberFormat.getInstance();
			f.setRoundingMode(RoundingMode.UP);
			//f.setMaximumFractionDigits(10);
			f.setMinimumFractionDigits(3);
			return "numPatterns=" + numPatterns + " damping_rel=" + f.format(dampingRelevance) +
					" ranking_threshold=" + f.format(rankingStopThreshold) + " min_rel=" + f.format(minRelevance) +
					" min_sim=" + f.format(simLowerBound) + " pattern_lambda=" + f.format(patternLambda) +
					"\n\n" + stats;
		}
	}

	/**
	 * @param w functions used to weight contents
	 * @param s similarity function for pairs of entities
	 * @param d disambiguation for entities
	 */
	public TextPlanner(WeightingFunction w, ItemSimilarity s, EntityDisambiguator d)
	{
		weighting = w;
		similarity = s;
		disambiguator = d;
	}

	/**
	 * Generates a text plan from a list of structures, e.g. relations in a KB or extracted from a text
	 * @param structures initial set of structures
	 * @return list of patterns
	 */
	public List<SemanticTree> planText(Set<SemanticGraph> structures, Options inOptions)
	{
		try
		{
			log.info("Planning started");

			// 0- Collect entities referred to from the structures
			// todo all annotations should at least one reference. In the case of anns without real candidates, there should be a dummy reference. This should include unlinked NEs
			List<String> entities = structures.stream()
					.map(SemanticGraph::vertexSet)
					.flatMap(Set::stream)
					.map(Node::getCandidates)
					.flatMap(Set::stream)
					.map(Entity::getLabel)
					.distinct()
					.collect(Collectors.toList());

			// 1- Create entity ranking matrix
			log.info("Creating ranking matrix");
			Stopwatch timer = Stopwatch.createStarted();
			weighting.setContents(structures);
			Matrix rankingMatrix =
					PowerIterationRanking.createRankingMatrix(entities, structures, weighting, similarity, inOptions);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 2- Rank entities using power iteration method
			log.info("Power iteration ranking");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Power iteration ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();
			Map<String, Double> rankedEntities = IntStream.range(0, ranking.length)
					.boxed()
					.collect(Collectors.toMap(entities::get, i -> ranking[i]));

			// 3- Use ranking to disambiguate entities in structures and weight nodes
			log.info("Disambiguation: annotating candidates");
			timer.reset(); timer.start();
			disambiguator.annotateCandidates(structures);
			log.info("Annotation took " + timer.stop());
			log.info("Disambiguation: candidate expansion");
			timer.reset(); timer.start();
			disambiguator.expandCandidates(structures);
			log.info("Expansion took " + timer.stop());
			log.info("Disambiguation: selecting candidates");
			timer.reset(); timer.start();
			disambiguator.disambiguate(structures, rankedEntities);
			log.info("Selection took " + timer.stop());

			// 4- Create content graph
			log.info("Creating content graph");
			timer.reset(); timer.start();
			SemanticGraph contentGraph = ContentGraphCreator.createContentGraph(structures);
			log.info("Graph creation took " + timer.stop());

			// 5- Extract patterns from content graph
			log.info("Extracting patterns");
			timer.reset(); timer.start();
			List<SemanticTree> patterns = PatternExtraction.extract(contentGraph, inOptions.numPatterns, inOptions.patternLambda);
			log.info("Pattern extraction took " + timer.stop());

			// 6- Sort the trees into a discourse-optimized list
			log.info("Structuring patterns");
			timer.reset(); timer.start();
			//patterns = DiscoursePlanner.structurePatterns(patterns, similarity);
			log.info("Pattern structuring took " + timer.stop());

			// 7- Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("Generating stats");
				timer.reset();
				timer.start();
				inOptions.stats = StatsReporter.reportStats(structures, weighting, similarity, contentGraph, inOptions);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}



}
