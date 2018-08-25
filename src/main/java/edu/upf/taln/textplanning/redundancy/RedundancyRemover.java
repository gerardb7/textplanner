package edu.upf.taln.textplanning.redundancy;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.structures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class RedundancyRemover
{
	private final SemanticTreeSimilarity sim;
	private final static Logger log = LogManager.getLogger();

	public RedundancyRemover(SemanticTreeSimilarity sim)
	{
		this.sim = sim;
	}

	public Collection<SemanticSubgraph> filter(Collection<SemanticSubgraph> G, int num_graphs)
	{
		Stopwatch timer = Stopwatch.createStarted();

		// Convert graphs to lists
		List<SemanticTree> trees = G.stream()
				.map(SemanticTree::new)
				.collect(toList());

		// Calculate similarities between pairs of trees
		Map<Pair<Integer, Integer>, Double> sim_values = new HashMap<>();
		IntStream.range(0, G.size())
				.forEach(i -> IntStream.range(i, G.size())
						.forEach(j ->
						{
							if (i == j)
								sim_values.put(Pair.of(i,j), 1.0);
							else
							{
								double sij = sim.getSimilarity(trees.get(i), trees.get(j));
								sim_values.put(Pair.of(i,j), sij);
								sim_values.put(Pair.of(j,i), sij);
							}
						}));

		// Prune G by choosing pair of most similar graphs and keeping the one with highest average weight
		Set<Integer> pruned = new HashSet<>();
		while (G.size() > num_graphs)
		{
			sim_values.keySet().stream()
					.filter(p -> !pruned.contains(p.getLeft()) && !pruned.contains(p.getRight()))
					.max(Comparator.comparingDouble(sim_values::get))
					.ifPresent(p ->
					{
						SemanticTree t1 = trees.get(p.getLeft());
						SemanticTree t2 = trees.get(p.getRight());
						SemanticSubgraph g1 = t1.getGraph();
						SemanticSubgraph g2 = t2.getGraph();

						double avg1 = g1.vertexSet().stream()
								.mapToDouble(v -> g1.getBase().getWeight(v))
								.average().orElse(0.0);
						double avg2 = g1.vertexSet().stream()
								.mapToDouble(v -> g1.getBase().getWeight(v))
								.average().orElse(0.0);

						if (avg1 >= avg2)
							pruned.add(p.getLeft());
						else
							pruned.add(p.getRight());
					});
		}

		//noinspection SuspiciousMethodCalls
		pruned.forEach(trees::remove);
		Set<SemanticSubgraph> selected = trees.stream()
				.map(SemanticTree::getGraph)
				.collect(toSet());

		log.info("Redundancy removal took " + timer.stop());
		return selected;
	}
}
