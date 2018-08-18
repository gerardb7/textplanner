package edu.upf.taln.textplanning.pattern;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
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

			// ignore subgraphs with no edges (i.e. with just one vertex)
			if (subgraph.edgeSet().isEmpty())
				continue;

			// ignore subgraph if it's part of an existing subgraph
			boolean replica = subgraphs.stream()
					.map(AsSubgraph::vertexSet)
					.anyMatch(V -> V.containsAll(subgraph.vertexSet()));
			if (replica)
				continue;

			// discard subgraphs subsumed by the new one
			subgraphs.removeIf(s -> subgraph.vertexSet().containsAll(s.vertexSet()));

			subgraphs.add(subgraph);
		}

		log.info(subgraphs.size() + " subgraphs extracted after " + num_extractions + " iterations");
		log.info("Subgraph extraction took " + timer.stop());
		log.debug(DebugUtils.printSubgraphs(g, subgraphs));

		return subgraphs;
	}

	private SemanticSubgraph extract(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours, double cost)
	{
		final Set<String> V = g.vertexSet();
		final Set<String> S = new HashSet<>();
		final Map<Set<String>, Double> candidates = new HashMap<>(); // candidates are extensions of S with new vertices
		final Map<String, Double> w = g.getWeights(); // weights

		// Sample vertex
		String v = softMax(w);
		S.add(v);
		S.addAll(Requirements.determine(g, v));

		// Declare q and q'
		double q_old;
		double q = calculateWeight(V, S, w, cost);

		// Optimization: keep track of new vertices added at each iteration
		Set<String> new_vertices = new HashSet<>(S);

		do
		{
			// Update candidate set (optimization: only use vertices added to S in the previous iteration)
			new_vertices.stream()
					// find nodes in the neighbourhood of S
					.map(neighbours::neighborsOf)
					.flatMap(Set::stream)
					.filter(n -> !S.contains(n)) // but not in S!
					.distinct()
					// map each neighbour to a set that includes its required nodes
					.map(n -> Requirements.determine(g, n))
					.map(C -> Sets.union(C, S))
					.distinct()
					.forEach(C -> candidates.put(C, calculateWeight(V, C, w, cost)));
			new_vertices.clear();

			// Argmax on expansion set
			Set<String> C_max = candidates.keySet().stream()
					.max(Comparator.comparingDouble(candidates::get))
					.orElse(Collections.emptySet());

			// Optimization: determine what vertices are added to S
			new_vertices = Sets.difference(C_max, S);

			// Update S
			S.addAll(C_max);

			// Update function values
			q_old = q;
			if (!S.isEmpty())
				q = candidates.get(S);

			// Remove C_max from candidate set
			candidates.remove(C_max);
		}
		while (q > q_old && !candidates.isEmpty());

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
