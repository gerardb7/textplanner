package edu.upf.taln.textplanning.input.amr;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

// Assumes unique vertex labels across graphs
public class GraphList implements Serializable
{
	private final List<SemanticGraph> graphs = new ArrayList<>();
	private final Set<String> vertices; // use to performs consistency checks
	private final Multimap<String, Candidate> candidate_meanings = HashMultimap.create();
	private final List<CoreferenceChain> chains = new ArrayList<>();
	private final NumberFormat format;
	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger(GraphList.class);


	public GraphList(List<SemanticGraph> graphs,
	                 List<Candidate> candidates,
	                 List<CoreferenceChain> chains)
	{
		super();
		this.graphs.addAll(graphs);
		vertices = graphs.stream().map(SemanticGraph::vertexSet).flatMap(Set::stream).collect(toSet());

		Map<String, SemanticGraph> graph_ids = graphs.stream()
				.collect(Collectors.toMap(SemanticGraph::getId, Function.identity()));
		candidates.forEach(c ->
		{
			final SemanticGraph g = graph_ids.get(c.getMention().getSentenceId());
			final Optional<String> ov = g.getAlignments().getSpanTopVertex(c.getMention().getSpan());
			ov.ifPresent(v -> 	candidate_meanings.put(v, c));
			if (!ov.isPresent())
				log.warn("Candidate " + c + " has no vertex");
		});
		this.chains.addAll(chains);

		// Sanity check

		if (chains.stream()
				.map(CoreferenceChain::getVertices)
				.flatMap(Collection::stream)
				.anyMatch(v -> !vertices.contains(v)))
			throw new RuntimeException("Invalid coreference vertex");

		format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(2);
		format.setMinimumFractionDigits(2);
	}

	public List<SemanticGraph> getGraphs() { return graphs; }

	public Collection<Candidate> getCandidates()
	{
		return candidate_meanings.values();
	}

	public Collection<Candidate> getCandidates(String v)
	{
		return candidate_meanings.get(v);
	}

	// assigns candidate c as node v only candidate
	public void chooseCandidate(String v, Candidate c)
	{
		if (!vertices.contains(v))
			throw new RuntimeException("Invalid candidate vertex");

		final Collection<Candidate> candidates = candidate_meanings.get(v);

		log.debug(c.getMention().getSurface_form() + " <- " + c.getMeaning() +
				" (candidate set = " + candidates.stream()
						.filter(c2 -> c2 != c)
						.map(Candidate::getMeaning)
						.map(Meaning::toString)
						.collect(joining(", ")) + ")");

		candidates.clear();
		candidate_meanings.put(v, c);
	}

	public void removeVertices(Collection<String> vertices)
	{
		// Remove candidates for removed vertices
		this.candidate_meanings.removeAll(vertices);

		// Now update coreference chains
		chains.forEach(v_old ->
				chains.forEach(c -> c.getVertices().removeAll(vertices)));
		chains.removeIf(c -> c.getVertices().isEmpty());

		// And now remove the vertices
		graphs.forEach(g -> g.removeAllVertices(vertices)); // this updates the alignments
	}

	public void vertexContraction(SemanticGraph g, String v, Collection<String> C)
	{
		if (!vertices.contains(v) || !vertices.containsAll(C))
			throw new RuntimeException("Invalid contraction vertices");

		// Perform contraction on graph
		g.vertexContraction(v, C); // this updates the alignments

		// Remove candidates for contracted vertices
		// And re-assign them to v
		C.stream()
			.map(candidate_meanings::removeAll)
			.flatMap(Collection::stream)
			.forEach(candidate -> candidate_meanings.put(v, candidate));

		// Now update coreference chains
		C.forEach(v_old ->
				chains.forEach(c -> c.replaceVertex(v_old, v)));
	}

	public List<CoreferenceChain> getChains()
	{
		return chains;
	}

	// Deserializes byte arrays and wraps them with ByteBuffer objects.
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		log.info("Loading graphs from serialized binary file");
		Stopwatch timer = Stopwatch.createStarted();
		//read default properties
		in.defaultReadObject();
		log.info("Loading took " + timer.stop());
	}
}
