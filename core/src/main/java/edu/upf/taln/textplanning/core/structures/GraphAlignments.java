package edu.upf.taln.textplanning.core.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.core.structures.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;

// Maps vertices in semantic graphs to tokens and their linguistic annotations
public class GraphAlignments implements Serializable
{
	private final SemanticGraph graph;
	private final List<String> tokens = new ArrayList<>();
	private final List<String> lemma = new ArrayList<>();
	private final List<String> pos = new ArrayList<>();
	private final List<Type> ner = new ArrayList<>();
	private final Multimap<String, Integer> alignments = HashMultimap.create(); // vertices to token offsets
	private final static long serialVersionUID = 1L;

	/**
	 * Vertices in a graph are associated by one or 0 alignments to a token:
	 * -> Given a vertex v and an alignment to token i, this class associates v with the span (i, i+1).
	 * Vertices may also be assigned with an additional span over all the tokens aligned with its descendants:
	 *  -> Given a set of descendants D of v and the sequence of tokens T aligned with vertices in D, v is
	 *  associated with the span  (T_min, T_max).
	 *
	 *  Wrapping up: vertices are associated with one or two spans, one for the optional alignment, and one for the
	 *  descendants alignments.
	 */
	public GraphAlignments(SemanticGraph graph, Multimap<String, Integer> align, List<String> tokens)
	{
		this.graph = graph;
		this.tokens.addAll(tokens);
		this.tokens.forEach(t ->
		{
			this.lemma.add(t); // lemma is initially set to token
			this.pos.add(""); // ugly_
			this.ner.add(Type.Other);
		});
		
		// Set alignments
		this.graph.vertexSet().forEach(v -> {
					String current = v;
					boolean end = false;
					while (!end)
					{
						if (align.containsKey(current))
						{
							this.alignments.putAll(v, align.get(current));
							end = true;
						}
						else
						{

							if (graph.outDegreeOf(current) != 1)
								end = true;
							else
								current = graph.getEdgeTarget(graph.outgoingEdgesOf(v).iterator().next());
						}
					}
				});
	}

	public List<String> getTokens() { return new ArrayList<>(tokens); }
	private String getToken(int token_index)	{ return tokens.get(token_index); }
	public String getLemma(int token_index)	{ return lemma.get(token_index); }
	public void setLemma(int token_index, String lemma) { this.lemma.set(token_index, lemma); }
	public String getPOS(int token_index)	{ return pos.get(token_index); }
	public void setPOS(int token_index, String pos) { this.pos.set(token_index, pos); }
	public Type getNEType(int token_index)	{ return ner.get(token_index); }
	public void setNER(int token_index, Type ner) { this.ner.set(token_index, ner); }

	public Collection<Integer> getAlignments(String vertex) { return alignments.get(vertex); }

	public Set<String> getAlignedVertices(int token_index)
	{
		return alignments.keySet().stream()
				.filter(v -> alignments.get(v).contains(token_index))
				.collect(toSet());
	}

	public Set<String> getSpanVertices(Pair<Integer, Integer> span)
	{
		return IntStream.range(span.getLeft(), span.getRight())
				.mapToObj(this::getAlignedVertices)
				.flatMap(Set::stream)
				.collect(toSet());
	}

	public Optional<String> getSpanTopVertex(Pair<Integer, Integer> span)
	{
		// Get vertices aligned with tokens in span
		final Set<String> vertices = getSpanVertices(span);

		if (vertices.isEmpty())
			return Optional.empty();
		if (vertices.size() == 1)
			return Optional.of(vertices.iterator().next());

		// Does the set of vertices form a connected induced subgraph?
		AsSubgraph<String, Role> induced = new AsSubgraph<>(graph, vertices);
		ConnectivityInspector<String, Role> inspector = new ConnectivityInspector<>(induced);
		if (!inspector.isGraphConnected())
			return Optional.empty();

		// Get top vertex (minimum depth -> closer to root)
		final Optional<String> top_vertex = vertices.stream()
				.min(Comparator.comparingInt(graph::getDepth));
		if (!top_vertex.isPresent())
			return Optional.empty();

		// More than one root?
		vertices.remove(top_vertex.get());
		if (!graph.getDescendants(top_vertex.get()).containsAll(vertices))
			return Optional.empty();

		return top_vertex;
	}

	public String getSurfaceForm(Pair<Integer, Integer> span)
	{
		return IntStream.range(span.getLeft(), span.getRight())
				.mapToObj(this::getToken)
				.collect(Collectors.joining(" "));
	}


	// The lemma of a multi-word span corresponds to a whitespace-separated sequence of tokens where the head token
	// has been replaced with its lemma, e.g. [cyber,attacks]" -> "cyber attack"
	public Optional<String> getLemma(Pair<Integer, Integer> span)
	{

		if (span.getRight() - span.getLeft() == 1)
			return Optional.of(getLemma(span.getLeft()));
		else
		{
			final Optional<String> head = getSpanTopVertex(span);
			if (!head.isPresent())
				return Optional.empty();
			Collection<Integer> head_tokens = getAlignments(head.get());
			String lemma = IntStream.range(span.getLeft(), span.getRight())
					.mapToObj(i ->
					{
						if (head_tokens.contains(i))
							return getToken(i);
						else
							return getLemma(i);
					})
					.collect(Collectors.joining(" "));
			return Optional.of(lemma);
		}
	}

	// The POS of a span of tokens is the POS of the tokens aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no POS
	public Optional<String> getPOS(Pair<Integer, Integer> span)
	{
		final Optional<String> vertex = getSpanTopVertex(span);
		if (!vertex.isPresent())
			return Optional.empty();

		final Collection<Integer> tokens = getAlignments(vertex.get());
		if (tokens.isEmpty())
			return Optional.empty();
		else // (tokens.size() >= 1)
			return Optional.of(getPOS(tokens.iterator().next())); // assume all tokens share the same POS
	}

	// The NE type of a span of tokens is the NE type of the tokens aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no NE type
	public Optional<Type> getNEType(Pair<Integer, Integer> span)
	{
		final Optional<String> vertex = getSpanTopVertex(span);
		if (!vertex.isPresent())
			return Optional.empty();

		final Collection<Integer> tokens = getAlignments(vertex.get());
		if (tokens.isEmpty())
			return Optional.empty();
		else // (tokens.size() >= 1)
			return Optional.of(getNEType(tokens.iterator().next())); // assume all tokens share the same NE type
	}

	public void renameVertex(String old_label, String new_label)
	{
		// Update all fields containing vertices
		final Collection<Integer> a = alignments.get(old_label);
		alignments.putAll(new_label, a);
		alignments.removeAll(old_label);
	}

	public void removeVertex(String v)
	{
		// Update all fields containing vertices
		alignments.removeAll(v);
	}
}
