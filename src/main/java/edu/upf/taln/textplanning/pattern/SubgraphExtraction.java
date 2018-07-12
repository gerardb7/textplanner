package edu.upf.taln.textplanning.pattern;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.NeighborCache;
import org.jgrapht.graph.AsSubgraph;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * Subgraph extraction strategy based on finding dense subgraphs in a global semantic graph.
 */
public class SubgraphExtraction
{
	private final double lambda;
	private final static double temperature = 0.001;
	private final static int max_num_extractions = 1000;
	private final static Logger log = LogManager.getLogger();

	public SubgraphExtraction(double lambda)
	{
		this.lambda = lambda;
	}

	public List<SemanticSubgraph> multipleExtraction(GlobalSemanticGraph g, int num_subgraphs)
	{
		Stopwatch timer = Stopwatch.createStarted();
		List<SemanticSubgraph> subgraphs = new ArrayList<>();
		NeighborCache<String, Role> neighbours = new NeighborCache<>(g);

		// Work out average variable rank to be used as cost value
		double avg_rank = g.getWeights().values().stream()
				.collect(Collectors.averagingDouble(d -> d));

		int num_extractions = 0;
		while (subgraphs.size() < num_subgraphs && num_extractions++ < max_num_extractions)
		{
			SemanticSubgraph subgraph = extract(g, neighbours, avg_rank);

			boolean replica = subgraphs.stream()
					.map(AsSubgraph::vertexSet)
					.anyMatch(V -> V.containsAll(subgraph.vertexSet()));
			if (!replica)
				subgraphs.add(subgraph);
		}

		log.info(subgraphs.size() + " subgraphs extracted after " + num_extractions + " iterations");
		log.info("Subgraph extraction took " + timer.stop());
		log.debug(DebugUtils.printSubgraphs(g, subgraphs));

		return subgraphs;
	}

	private SemanticSubgraph extract(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours, double cost)
	{
		// Sample vertex
		Map<String, Double> w = g.getWeights();
		String v = softMax(w);
		Set<String> S = new HashSet<>(Requirements.determine(g, v));
		Set<String> new_nodes = new HashSet<>(S); // new vertices added at each iteration

		while (calculateWeight(g.vertexSet(), S, w, cost) > 0.0 && !new_nodes.isEmpty())
		{
			Map<String, Double> expansions = new_nodes.stream()
					.map(neighbours::neighborsOf)
					.flatMap(Set::stream)
					.filter(n -> !S.contains(n))
					.distinct()
					.collect(toMap(e -> e, w::get)); // candidate expansions
			new_nodes.clear();

			if (!expansions.isEmpty())
			{
				// Choose expansion
				String e = softMax(expansions);
				S.add(e); // add to S

				// Keep new nodes
				new_nodes = new HashSet<>(Requirements.determine(g, e));
				new_nodes.add(e);
			}
		}

		// return induced subgraph
		return new SemanticSubgraph(g, S);
	}

	/**
	 * Calculates the cost of a subgraph as a combination of node weights and edge distances
	 */
	private double calculateWeight(Set<String> V, Set<String> S, Map<String, Double> W, double C)
	{
		double WS = S.stream()
				.mapToDouble(W::get)
				.sum(); // weight of S
		double CS = S.size() * C; // cost of the graph induced by S
		double CV = V.size() * C; // cost of V, used to keep weighting function non-negative
		return lambda*WS - CS + CV;
	}

	/**
	 * Softmax with low temperatures boosts probabilities of nodes with high weights and produces low probabilities
	 * for nodes with low weights. Temperature set experimentally.
	 */
	private static String softMax(Map<String, Double> w)
	{
		// Convert map to arrays
		String[] keys = w.keySet().toArray(new String[0]);
		double[] values = Arrays.stream(keys)
				.mapToDouble(w::get)
				.toArray();

		// Create distribution
		double[] exps = Arrays.stream(values)
				.map(v -> v / temperature)
				.map(Math::exp)
				.toArray();
		double sum = Arrays.stream(exps).sum();
		double[] softmax = Arrays.stream(exps)
				.map(e -> e / sum)
				.toArray();

		// Choose key
		double p = Math.random();
		double cumulativeProbability = 0.0;
		for (int i=0; i < keys.length; ++i)
		{
			cumulativeProbability += softmax[i];
			if (p <= cumulativeProbability)
				return keys[i];
		}

		return null; // only if w is empty
	}
}
