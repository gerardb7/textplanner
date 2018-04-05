package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.discourse.DiscoursePlanner;
import edu.upf.taln.textplanning.input.DocumentReader;
import edu.upf.taln.textplanning.input.GlobalGraphFactory;
import edu.upf.taln.textplanning.pattern.SubgraphExtraction;
import edu.upf.taln.textplanning.ranking.GraphRanking;
import edu.upf.taln.textplanning.redundancy.RedundancyRemover;
import edu.upf.taln.textplanning.similarity.MeaningSimilarity;
import edu.upf.taln.textplanning.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text plan
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final GraphListFactory graphs_factory;
	private final WeightingFunction weighting;
	private final MeaningSimilarity similarity;
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		int numSubgraphs = 1000; // Number of subgraphs to extract
		double min_weight = 0.0001; // pseudocount α for additive smoothing of meaning weight values
		double sim_threshold = 0.1; // Pairs of meanings with sim below this value have their score set to 0
		double damping_meanings = 0.2; // controls bias towards weighting function when ranking meanings
		double min_meaning_rank = 0.1; // pseudocount α for additive smoothing of meaning rank values @todo this may be unnecessary
		double damping_variables = 0.2; // controls bias towards meanings rank when ranking variables
		double patternLambda = 1.0; // Controls balance between weight of nodes and cost of edges during pattern extraction
		double treeEditLambda = 0.1; // Controls impact of roles when calculating similarity between semantic trees
		boolean generateStats = false;
		String stats = "";

		@Override
		public String toString()
		{
			NumberFormat f = NumberFormat.getInstance();
			f.setRoundingMode(RoundingMode.UP);
			//f.setMaximumFractionDigits(10);
			f.setMinimumFractionDigits(3);
			return "numSubgraphs=" + numSubgraphs +
					" min_weight=" + f.format(min_weight) +
					" sim_threshold=" + f.format(sim_threshold) +
					" damping_meanings=" + f.format(damping_meanings) +
					" min_rank=" + f.format(min_meaning_rank) +
					" damping_variables=" + f.format(damping_variables) +
					" pattern_lambda=" + f.format(patternLambda) +
					" redundancy lambda=" + f.format(treeEditLambda) +
					"\n\n" + stats;
		}
	}

	/**
	 * @param w functions used to weight contents
	 * @param s similarity function for entities
	 */
	TextPlanner(DocumentReader d, Path types_file, WeightingFunction w, MeaningSimilarity s) throws IOException
	{
		graphs_factory = new GraphListFactory(d, types_file);
		weighting = w;
		similarity = s;
		log.info("Set up completed");
	}

	/**
	 * Generates a text plan from a list of structures, e.g. relations in a KB or extracted from a text
	 */
	public List<SemanticSubgraph> plan(String contents, int num_graphs, Options o)
	{
		try
		{
			log.info("Planning started");
			Stopwatch timer = Stopwatch.createStarted();

			// 1- Create global graph (involves disambiguation of meanings via ranking)
			GraphList graphs = graphs_factory.getGraphs(contents);
			GraphRanking ranker = new GraphRanking(weighting, similarity, o.min_weight, o.sim_threshold, o.damping_meanings,
					o.min_meaning_rank, o.damping_variables);
			GlobalSemanticGraph graph = GlobalGraphFactory.create(graphs, ranker);
			log.info("Graph representation created in " + timer.stop());

			// 2- Extract subgraphs from graph (involves ranking variables)
			SubgraphExtraction extractor = new SubgraphExtraction(ranker, Math.min(num_graphs, o.patternLambda));
			Collection<SemanticSubgraph> subgraphs = extractor.multipleExtraction(graph, o.numSubgraphs);

			// 3- Remove redundant subgraphs
			SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.treeEditLambda);
			RedundancyRemover remover = new RedundancyRemover(tsim);
			subgraphs = remover.filter(subgraphs, num_graphs);

			// 4- Sort the trees into a discourse-optimized list
			DiscoursePlanner discourse = new DiscoursePlanner(tsim);
			List<SemanticSubgraph> text_plan = discourse.structurePatterns(subgraphs);
			log.info("Planning took " + timer.stop());

//			if (o.generateStats)
				//o.stats = StatsReporter.reportStats(structures, weighting, sim, contentGraph, o);

			return text_plan;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}
}
