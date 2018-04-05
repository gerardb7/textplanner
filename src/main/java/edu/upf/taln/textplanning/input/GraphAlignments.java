package edu.upf.taln.textplanning.input;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import edu.upf.taln.textplanning.structures.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class GraphAlignments implements Serializable
{
	public static final int unaligned = -1;
	private final SemanticGraph graph;
	private final int sentence_number;
	private final List<String> topo_order = new ArrayList<>();
	private final List<String> tokens = new ArrayList<>();
	private final List<String> pos = new ArrayList<>();
	private final List<Type> ner = new ArrayList<>();
	private final BiMap<String, Integer> alignments = HashBiMap.create();
	private final Multimap<Pair<Integer, Integer>, String> spans2nodes = HashMultimap.create();

	GraphAlignments(SemanticGraph graph, int sentence_number, BiMap<String, Integer> alignments, List<String> tokens)
	{
		this.graph = graph;
		this.sentence_number = sentence_number;
		graph.iterator().forEachRemaining(topo_order::add);
		this.tokens.addAll(tokens);
		this.tokens.forEach(t -> this.pos.add(""));
		this.alignments.putAll(alignments);

		// Calculate spans
		graph.vertexSet().stream()
				.map(v -> {
					List<Integer> token_idx = graph.getDescendants(v).stream()
							.map(this::getAlignment)
							.collect(toList());
					int min = token_idx.stream().mapToInt(i -> i).min().orElse(0);
					int max = token_idx.stream().mapToInt(i -> i).max().orElse(0);
					return Pair.of(v, Pair.of(min, max + 1));
				})
				.forEach(p -> spans2nodes.put(p.getRight(), p.getLeft()));
	}

	public SemanticGraph getGraph() { return graph; }
	public int getSentenceNumber() { return sentence_number; }
	List<String> getTokens() { return new ArrayList<>(tokens); }
	public String getToken(int index)	{ return tokens.get(index); }
	public String getPOS(int index)	{ return pos.get(index); }
	void setPOS(int index, String pos) { this.pos.set(index, pos); }
	Type getNER(int index)	{ return ner.get(index); }
	void setNER(int index, Type ner) { this.ner.set(index, ner); }
	boolean isAligned(String vertex) { return alignments.containsKey(vertex); }
	public int getAlignment(String vertex) { return alignments.getOrDefault(vertex, unaligned); }
	boolean covers(Pair<Integer, Integer> span) { return spans2nodes.containsKey(span); }

	// Given a span of tokens and the set of vertices with exactly this span, return the first vertex appearing in a
	// topological sort (closest to the root of the graph)
	Optional<String>  getTopSpanVertex(Pair<Integer, Integer> span)
	{
		return spans2nodes.get(span).stream().max(Comparator.comparingInt(topo_order::indexOf));
	}

	// The POS of a span of tokens is the POS of the token aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no POS
	Optional<String> getPOS(Pair<Integer, Integer> span)
	{
		return getTopSpanVertex(span).filter(this::isAligned).map(this::getAlignment).map(this::getPOS);
	}

	// The NER of a span of tokens is the NER of the token aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no NER
	Optional<Type> getNER(Pair<Integer, Integer> span)
	{
		return getTopSpanVertex(span).filter(this::isAligned).map(this::getAlignment).map(this::getNER);
	}

	boolean isNominal(String vertex)
	{
		return getAlignment(vertex) != unaligned && getPOS(getAlignment(vertex)).startsWith("N");
	}
}
