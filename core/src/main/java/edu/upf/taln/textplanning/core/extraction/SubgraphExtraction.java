package edu.upf.taln.textplanning.core.extraction;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.CycleDetector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subgraph extraction strategy based on finding dense subgraphs in a semantic graph.
 */
public class SubgraphExtraction
{
	private final Explorer explorer;
	private final Policy start_policy;
	private final Policy expand_policy;
	private final double lambda;
	private final static int max_num_extractions = 1000;
	private final List<SemanticSubgraph> visited_starts = new ArrayList<>();
	private final static Logger log = LogManager.getLogger();

	public SubgraphExtraction(Explorer explorer, Policy start_policy, Policy expand_policy, double lambda)
	{
		this.explorer = explorer;
		this.start_policy = start_policy;
		this.expand_policy = expand_policy;
		this.lambda = lambda;
	}

	public List<SemanticSubgraph> multipleExtraction(SemanticGraph g, int num_subgraphs)
	{
		Stopwatch timer = Stopwatch.createStarted();
		List<SemanticSubgraph> subgraphs = new ArrayList<>();

		// Work out average variable rank to be used as cost value
		double avg_rank = g.getWeights().values().stream()
				.collect(Collectors.averagingDouble(d -> d));

		int num_extractions = 0;

		while (subgraphs.size() < num_subgraphs && num_extractions++ < max_num_extractions)
		{
			SemanticSubgraph s = extract(g, avg_rank);
			if (isValid(s))
				subgraphs.add(s);
			removeRedundant(subgraphs);
		}

		final List<SemanticSubgraph> sorted_subgraphs = subgraphs.stream()
				.sorted(Comparator.comparingDouble(SemanticSubgraph::getAverageWeight).reversed())
				.collect(Collectors.toList());
		log.info(sorted_subgraphs.size() + " subgraphs extracted after " + num_extractions + " iterations ");
		log.info("Subgraph extraction took " + timer.stop());
		log.debug(DebugUtils.printSubgraphs(sorted_subgraphs));

		return sorted_subgraphs;
	}

	// valid subgraphs must be connected and simple
	private boolean isValid(SemanticSubgraph s)
	{
		if (s == null)
			return false;

		final boolean is_empty = s.vertexSet().isEmpty();
		final boolean is_connected = new ConnectivityInspector<>(s).isGraphConnected();
		final boolean has_cycles = new CycleDetector<>(s).detectCycles();

		return !is_empty && is_connected && !has_cycles;
	}

	// a subgraph is redundant if it's vertex set is a subset of any other extracted graph
	private void removeRedundant(List<SemanticSubgraph> subgraphs)
	{
		subgraphs.removeIf(s1 -> subgraphs.stream()
					.filter(s2 -> s1 != s2)
					.anyMatch(s2 -> s2.contains(s1)));
	}

	private SemanticSubgraph extract(SemanticGraph g, double cost)
	{
		final Set<String> V = g.vertexSet();
		if (V.isEmpty())
			return null;

		// Select intitial state and q value
		SemanticSubgraph current_state;
		{
			final List<SemanticSubgraph> candidates = explorer.getStartStates(g);
			if (start_policy instanceof ArgMaxPolicy)
				candidates.removeIf(visited_starts::contains); // remove seen starts
			if (candidates.isEmpty())
				return null;

			final double[] candidate_weights = candidates.stream()
					.mapToDouble(c -> calculateValue(c, cost))
					.toArray();
			int i = start_policy.select(candidate_weights);
			current_state = candidates.get(i);
			visited_starts.add(current_state);
		}
		double q = 0.0, q_old;

		do
		{
			// candidate sets extending (and therefore including) current_state
			List<SemanticSubgraph> candidate_states = explorer.getNextStates(current_state);
			if (candidate_states.isEmpty())
				break;

			final double[] candidate_weights = candidate_states.stream()
					.mapToDouble(c -> calculateValue(c, cost))
					.toArray();
			int i = expand_policy.select(candidate_weights);
			SemanticSubgraph next_state = candidate_states.get(i);

			// Update function values
			q_old = q;
			q = candidate_weights[i];

			// Update current_state if function improved by selecting candidate c
			if (q > q_old)
				current_state = next_state;
		}
		while (q > q_old);

		// return induced subgraph
		return current_state;
	}

	/**
	 * Calculates the value of a subgraph as a combination of node weights and edge distances
	 */
	private double calculateValue(SemanticSubgraph s, double C)
	{
		final Set<String> V = s.getBase().vertexSet();
		final Set<String> S = s.vertexSet();
		final Map<String, Double> W = s.getBase().getWeights();

		final double WS = S.stream()
				.filter(W::containsKey) // ignore vertices with no weight -but they compute towards higher cost CS anyway
				.mapToDouble(W::get)
				.sum(); // weight of S
		final double CS = S.size() * C; // cost of the graph induced by S
		final double CV = V.size() * C; // cost of V, used to keep weighting function non-negative
		final double value = lambda*WS - CS + CV;

		s.setValue(value);
		return value;
	}


}
