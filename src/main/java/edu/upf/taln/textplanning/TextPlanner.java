package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.discourse.DiscoursePlanner;
import edu.upf.taln.textplanning.input.GlobalGraphFactory;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.extraction.*;
import edu.upf.taln.textplanning.ranking.GraphRanking;
import edu.upf.taln.textplanning.redundancy.RedundancyRemover;
import edu.upf.taln.textplanning.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.RoundingMode;
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
	private final static Logger log = LogManager.getLogger();

	public static class Options
	{
		int num_subgraphs = 1000; // Number of subgraphs to extract
		double sim_threshold = 0.1; // Pairs of meanings with sim below this value have their score set to 0
		double damping_meanings = 0.2; // controls bias towards weighting function when ranking meanings
		double damping_variables = 0.2; // controls bias towards meanings rank when ranking variables
		double extraction_lambda = 1.0; // Controls balance between weight of nodes and cost of edges during subgraph extraction
		double tree_edit_lambda = 0.1; // Controls impact of roles when calculating similarity between semantic trees

		@Override
		public String toString()
		{
			NumberFormat f = NumberFormat.getInstance();
			f.setRoundingMode(RoundingMode.UP);
			//f.setMaximumFractionDigits(10);
			f.setMinimumFractionDigits(3);
			return  "num_subgraphs = " + num_subgraphs +
					" sim_threshold = " + f.format(sim_threshold) +
					" damping_meanings = " + f.format(damping_meanings) +
					" damping_variables = " + f.format(damping_variables) +
					" extraction_lambda = " + f.format(extraction_lambda) +
					" redundancy lambda = " + f.format(tree_edit_lambda);
		}
	}

	/**
	 * Generates a text plan from a global semantic graph
	 */
	public static List<SemanticSubgraph> plan(GraphList graphs, SimilarityFunction similarity, WeightingFunction weighting,
	                                   int num_graphs, Options o)
	{
		try
		{
			log.info("***Planning started***");
			Stopwatch timer = Stopwatch.createStarted();

			// 1- Rank meanings
			rankMeanings(graphs.getCandidates(), weighting, similarity, o);

			// 2- Create global graph
			final GlobalSemanticGraph global_graph = createGlobalGraph(graphs);

			// 3- Rank variables
			rankVariables(global_graph, o);

			// 4- Extract subgraphs from graph
			Collection<SemanticSubgraph> subgraphs = extractSubgraphs(global_graph, num_graphs, o);

			// 5- Remove redundant subgraphs
			subgraphs = removeRedundantSubgraphs(subgraphs, num_graphs, similarity, o);

			// 6- Sort the trees into a discourse-optimized list
			final List<SemanticSubgraph> text_plan = sortSubgraphs(subgraphs, similarity, o);

			log.info("Planning took " + timer.stop());
			return text_plan;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ranks set of candidate meanings associated with a collection of semantic graphs, and stores the resulting ranks as
	 * candidate weights.
	 */
	public static void rankMeanings(Collection<Candidate> candidates, WeightingFunction weighting, SimilarityFunction similarity,
	                                Options o)
	{
		log.info("***Ranking meanings***");
		Stopwatch timer = Stopwatch.createStarted();

		GraphRanking.rankMeanings(candidates, weighting, similarity, o.sim_threshold, o.damping_meanings);
		log.info("Ranking completed in " + timer.stop());
	}

	/**
	 * 	Creates a global planning graph from a collection of semantic graphs
 	 */
	public static GlobalSemanticGraph createGlobalGraph(GraphList graphs)
	{
		log.info("***Creating global semantic graph***");
		Stopwatch timer = Stopwatch.createStarted();
		GlobalSemanticGraph graph = GlobalGraphFactory.create(graphs);
		log.info("Global semantic graph created in " + timer.stop());

		return graph;
	}

	/**
	 * 	Ranks variables in a global planning graph
	 */
	public static void rankVariables(GlobalSemanticGraph graph, Options o)
	{
		log.info("***Ranking variables***");
		Stopwatch timer = Stopwatch.createStarted();
		GraphRanking.rankVariables(graph, o.damping_variables);
		log.info("Ranking completed in " + timer.stop());
	}

	/**
	 * 	Extract subgraphs from a global planning graph
	 */
	public static Collection<SemanticSubgraph> extractSubgraphs(GlobalSemanticGraph graph, int num_graphs, Options o)
	{
		log.info("***Extracting subgraphs***");
		Stopwatch timer = Stopwatch.createStarted();

		Explorer e = new RequirementsExplorer(true, Explorer.ExpansionPolicy.Non_core_only);
		Policy p = new SoftMaxPolicy();
		SubgraphExtraction extractor = new SubgraphExtraction(e, p, Math.min(num_graphs, o.extraction_lambda));
		Collection<SemanticSubgraph> subgraphs = extractor.multipleExtraction(graph, o.num_subgraphs);
		log.info("Extraction done in " + timer.stop());

		return subgraphs;
	}

	/**
	 * 	Remove redundant subgraphs
	 */
	public static Collection<SemanticSubgraph> removeRedundantSubgraphs(Collection<SemanticSubgraph> subgraphs, int num_graphs,
	                                                     SimilarityFunction similarity, Options o)
	{
		log.info("***Removing redundant subgraphs***");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		RedundancyRemover remover = new RedundancyRemover(tsim);
		Collection<SemanticSubgraph> out_subgraphs = remover.filter(subgraphs, num_graphs);
		log.info("Redundancy removal done in " + timer.stop());

		return out_subgraphs;
	}

	/**
	 * 	Sort subgraphs
	 */
	public static List<SemanticSubgraph> sortSubgraphs(Collection<SemanticSubgraph> subgraphs, SimilarityFunction similarity,
	                                            Options o)
	{
		log.info("***Sorting subgraphs***");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		DiscoursePlanner discourse = new DiscoursePlanner(tsim);
		List<SemanticSubgraph> text_plan = discourse.structureSubgraphs(subgraphs);
		log.info("Sorting done in " + timer.stop());

		return text_plan;
	}
}
