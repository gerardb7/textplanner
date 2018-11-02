package edu.upf.taln.textplanning.core.redundancy;

import edu.upf.taln.textplanning.core.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.structures.SemanticTree;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class RedundancyRemover
{
	private static class SimilarityPair
	{
		final int t1;
		final int t2;
		final double w;
		SimilarityPair(int t1, int t2, double w) { this.t1 = t1; this.t2 = t2; this.w = w;}

		public double getW() { return w; }
	}

	private final SemanticTreeSimilarity sim;
	private final static Logger log = LogManager.getLogger();

	public RedundancyRemover(SemanticTreeSimilarity sim)
	{
		this.sim = sim;
	}

	public Collection<SemanticSubgraph> filter(Collection<SemanticSubgraph> G, int num_graphs)
	{
		// Convert graphs to lists
		List<SemanticTree> trees = G.stream()
				.map(SemanticTree::new)
				.collect(toList());
		List<SemanticTree> pruned = new ArrayList<>();

		// Calculate similarities between pairs of trees
		log.info("Calculating similarities between pairs of trees");
		List<SimilarityPair> sim_pairs = IntStream.range(0, G.size())
				.mapToObj(i -> IntStream.range(i, G.size())
						.filter(j -> i != j)
						.mapToObj(j ->
						{
							double sij = sim.getSimilarity(trees.get(i), trees.get(j));
							return new SimilarityPair(i, j, sij);
						}))
				.flatMap(s -> s)
				.sorted(Comparator.comparingDouble(SimilarityPair::getW))
				.collect(toList());

		// Prune G by choosing pair of most similar graphs and keeping the one with highest average weight
		log.info("Pruning trees");
		while (trees.size() - pruned.size() > num_graphs)
		{
			sim_pairs.stream()
					.max(Comparator.comparingDouble(SimilarityPair::getW))
					.map(p ->
					{

						SemanticTree t1 = trees.get(p.t1);
						SemanticTree t2 = trees.get(p.t2);

						double avg1 = t1.getAverageWeight();
						double avg2 = t2.getAverageWeight();
						int pruned_tree = avg1 >= avg2 ? p.t2 : p.t1;

						log.debug("Pruned tree " + pruned_tree + " from pair " + p.t1 + "-" + p.t2 + " with sim=" + DebugUtils.printDouble(p.w));
								//+ ":\n" + DebugUtils.printTree(p.t1, t1) + "\n" + DebugUtils.printTree(p.t2, t2));

						// return tree with lowest score
						return pruned_tree;
					})
					.ifPresent(t ->
					{
						pruned.add(trees.get(t));
						sim_pairs.removeIf(p -> p.t1 == t || p.t2 == t);
					});
		}

		trees.removeAll(pruned);
		Set<SemanticSubgraph> selected = trees.stream()
				.map(SemanticTree::asGraph)
				.collect(toSet());

		log.info("Selected " + num_graphs + " subgraphs out of " + G.size());
		log.debug("Selected subgraphs:\n" + DebugUtils.printSubgraphs(new ArrayList<>(selected)));
		return selected;
	}
}
