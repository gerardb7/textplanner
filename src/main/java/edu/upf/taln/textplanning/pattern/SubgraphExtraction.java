package edu.upf.taln.textplanning.pattern;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.util.NeighborCache;
import org.jgrapht.graph.AsSubgraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subgraph extraction strategy based on finding dense subgraphs in a global semantic graph.
 */
public class SubgraphExtraction
{
	private final Explorer explorer;
	private final Policy policy;
	private final double lambda;
	private final static int max_num_extractions = 1000;
	private final static Logger log = LogManager.getLogger();

	public SubgraphExtraction(Explorer explorer, Policy policy, double lambda)
	{
		this.explorer = explorer;
		this.policy = policy;
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
		int num_redundant_extraction = 0;

		while (subgraphs.size() < num_subgraphs && num_extractions++ < max_num_extractions)
		{
			SemanticSubgraph s = extract(g, neighbours, avg_rank);

			if (isValidSubgraph(s))
			{
				num_redundant_extraction += updateSubgraphList(s, subgraphs);
			}
		}

		log.info(subgraphs.size() + " subgraphs extracted after " + num_extractions + " iterations (" +
				num_redundant_extraction + " redundant extractions)");
		log.info("Subgraph extraction took " + timer.stop());
		log.debug(DebugUtils.printSubgraphs(g, subgraphs));

		return subgraphs;
	}

	private boolean isValidSubgraph(SemanticSubgraph s)
	{
		// ignore subgraphs with no edges (i.e. with just one vertex)
		boolean is_valid =  s != null && new ConnectivityInspector<>(s).isGraphConnected();
		if (!is_valid)
			log.error("Ignoring malformed graph");

		return is_valid;
	}

	private int updateSubgraphList(SemanticSubgraph s, List<SemanticSubgraph> subgraphs)
	{
		// ignore subgraph if it's part of an existing subgraph
		boolean replica = subgraphs.stream()
				.map(AsSubgraph::vertexSet)
				.anyMatch(V -> V.containsAll(s.vertexSet()));
		if (replica)
			return 1;

		final int size_1 = subgraphs.size();
		subgraphs.removeIf(s2 -> s.vertexSet().containsAll(s2.vertexSet()));
		final int num_redundant = subgraphs.size() - size_1;
		subgraphs.add(s);

		return num_redundant;
	}

	private SemanticSubgraph extract(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours, double cost)
	{
		final Set<String> V = g.vertexSet();
		if (V.isEmpty())
			return null;

		Set<String> start; // starting nodes
		Set<String> V_selected; // keeps track of selected nodes

		// Select intitial ndoes
		{
			final List<Set<String>> candidates = explorer.getInitialCandidates(g, neighbours);
			if (candidates.isEmpty())
				return null;

			final double[] candidate_weights = candidates.stream()
					.mapToDouble(c -> calculateWeight(V, c, g.getWeights(), cost))
					.toArray();
			int i = policy.select(candidate_weights);
			start = new HashSet<>(candidates.get(i));
			V_selected = new HashSet<>(start);
		}

		// Declare q and q'
		double q = calculateWeight(V, V_selected, g.getWeights(), cost);
		double q_old;

		do
		{
			// candidate sets extending (and therefore including) V_selected
			List<Set<String>> candidates = explorer.getNextCandidates(V_selected, g, neighbours);
			if (candidates.isEmpty())
				break;

			final double[] candidate_weights = candidates.stream()
					.mapToDouble(c -> calculateWeight(V, c, g.getWeights(), cost))
					.toArray();
			int i = policy.select(candidate_weights);
			Set<String> c = new HashSet<>(candidates.get(i));

			// Update V_selected
			V_selected.addAll(c);

			// Update function values
			q_old = q;
			q = candidate_weights[i];
		}
		while (q > q_old);

		// return induced subgraph
		return new SemanticSubgraph(g, start, V_selected);
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


}
