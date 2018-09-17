package edu.upf.taln.textplanning.extraction;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.extraction.Explorer.State;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

		// Work out average variable rank to be used as cost value
		double avg_rank = g.getWeights().values().stream()
				.collect(Collectors.averagingDouble(d -> d));

		int num_extractions = 0;

		while (subgraphs.size() < num_subgraphs && num_extractions++ < max_num_extractions)
		{
			SemanticSubgraph s = extract(g, avg_rank);
			if (isValid(s) && !isRedundant(s, subgraphs))
				subgraphs.add(s);
		}

		log.info(subgraphs.size() + " subgraphs extracted after " + num_extractions + " iterations ");
		log.info("Subgraph extraction took " + timer.stop());
		log.debug(DebugUtils.printSubgraphs(subgraphs));

		return subgraphs;
	}

	private boolean isValid(SemanticSubgraph s)
	{
		// ignore unconnected graphs
		return s != null && !s.edgeSet().isEmpty() && new ConnectivityInspector<>(s).isGraphConnected();
	}

	private boolean isRedundant(SemanticSubgraph s, List<SemanticSubgraph> subgraphs)
	{
		return subgraphs.stream()
				.map(AsSubgraph::vertexSet)
				.anyMatch(V -> V.equals(s.vertexSet()));
	}

	private SemanticSubgraph extract(GlobalSemanticGraph g, double cost)
	{
		final Set<String> V = g.vertexSet();
		if (V.isEmpty())
			return null;

		State start_state;
		State current_state;

		// Select intitial nodes
		{
			final List<State> candidates = explorer.getStartStates(g);
			if (candidates.isEmpty())
				return null;

			final double[] candidate_weights = candidates.stream()
					.mapToDouble(c -> calculateWeight(V, c.vertices, g.getWeights(), cost))
					.toArray();
			int i = policy.select(candidate_weights);
			start_state = candidates.get(i);
			current_state = new State(start_state.source, start_state.vertices);
		}

		// Declare q and q'
		double q = calculateWeight(V, current_state.vertices, g.getWeights(), cost);
		double q_old = q;

		do
		{
			// candidate sets extending (and therefore including) current_state
			List<State> candidate_states = explorer.getNextStates(current_state, g);
			if (candidate_states.isEmpty())
				break;

			final double[] candidate_weights = candidate_states.stream()
					.mapToDouble(c -> calculateWeight(V, c.vertices, g.getWeights(), cost))
					.toArray();
			int i = policy.select(candidate_weights);
			State next_state = candidate_states.get(i);

			// Update function values
			q_old = q;
			q = candidate_weights[i];

			// Update current_state if function improved by selecting candidate c
			if (q > q_old)
				current_state = next_state;
		}
		while (q > q_old);

		// return induced subgraph
		return new SemanticSubgraph(g, start_state.vertices, current_state.vertices, q_old);
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
