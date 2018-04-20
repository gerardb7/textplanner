package edu.upf.taln.textplanning.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

// Assumes unique vertex labels across graphs
public class GraphList extends ArrayList<SemanticGraph>
{
	private final Multimap<String, Candidate> candidate_meanings = HashMultimap.create();
	private final List<CoreferenceChain> chains = new ArrayList<>();
	private final static long serialVersionUID = 1L;


	public GraphList(java.util.Collection<SemanticGraph> graphs,
	                 List<Candidate> candidates,
	                 List<CoreferenceChain> chains)
	{
		super();
		addAll(graphs);
		candidates.forEach(c -> candidate_meanings.put(c.getVertex(), c));
		this.chains.addAll(chains);
	}

	public Collection<Candidate> getCandidates()
	{
		return candidate_meanings.values();
	}

	public Collection<Candidate> getCandidates(String v)
	{
		return candidate_meanings.get(v);
	}

	public void chooseCandidate(String v, Candidate c)
	{
		candidate_meanings.get(v).clear();
		candidate_meanings.put(v, c);
	}

	public void vertexContraction(SemanticGraph g, String v, Collection<String> C)
	{
		// Perform contraction on graph
		g.vertexContraction(v, C);

		// Remove candidates for contracted vertices
		// And add new ones to v
		List<Candidate> new_candidates = candidate_meanings.values().stream()
				.filter(c -> C.contains(c.getVertex()))
				.map(c -> new Candidate(v, c.getMeaning(), c.getMention()))
				.collect(toList());

		C.forEach(candidate_meanings::removeAll);
		new_candidates.forEach(c -> candidate_meanings.put(v, c));

		// Now update coreference chains
		C.forEach(v_old ->
				chains.forEach(c -> c.replaceVertex(v_old, v)));
	}

	public List<CoreferenceChain> getChains()
	{
		return chains;
	}

}
