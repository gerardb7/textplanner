package edu.upf.taln.textplanning.input;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

// Maps vertices in semantic graphs to tokens and their linguistic annotations
public class GraphAlignments implements Serializable
{
	private final SemanticGraph graph;
	private final List<String> tokens = new ArrayList<>();
	private final List<String> lemma = new ArrayList<>();
	private final List<String> pos = new ArrayList<>();
	private final List<Type> ner = new ArrayList<>();
	private final Map<String, Integer> alignments = new HashMap<>(); // vertices to token offsets
	private final Multimap<Pair<Integer, Integer>, String> spans2vertices = HashMultimap.create(); // spans to vertices
//	private final Multimap<String, Pair<Integer, Integer>> vertices2spans = HashMultimap.create(); // vertices to spans
	private final static long serialVersionUID = 1L;

	/**
	 * Vertices in a graph are associated by one or 0 alignments to a token:
	 * -> Given a vertex v and an alignment to token i, this class associates v with the span (i, i+1).
	 * Vertices may also be assigned with an additional span over all the tokens aligned with its descendants:
	 *  -> Given a set of descendants D of v and the sequence of tokens T aligned with vertices in D, v is
	 *  associated with the span  (T_min, T_max).
	 *
	 *  Wrapping up: vertices are assiociated with oen or two spans, one for the optional alignment, and one for the
	 *  descendants alignements.
	 */
	GraphAlignments(SemanticGraph graph, Map<String, Integer> alignments, List<String> tokens)
	{
		this.graph = graph;
		this.tokens.addAll(tokens);
		this.tokens.forEach(t ->
		{
			this.lemma.add("");
			this.pos.add("");
			this.ner.add(Type.Other);
		});
		this.alignments.putAll(alignments);

		// Calculate spans: first alignment-spans
		graph.vertexSet()
				.forEach(v -> {
					if (alignments.containsKey(v))
					{
						int i = alignments.get(v);
						Pair<Integer, Integer> span = Pair.of(i, i + 1);
						spans2vertices.put(span, v);
//						vertices2spans.put(v, span);
					}
				});

		// now spans over descendants
		graph.vertexSet()
				.forEach(v -> {
					List<Integer> indexes = alignments.containsKey(v) ? Lists.newArrayList(alignments.get(v)) : Lists.newArrayList();
					graph.getDescendants(v).stream()
							.filter(alignments::containsKey)
							.map(alignments::get)
							.forEach(indexes::add);

					if (!indexes.isEmpty())
					{
						int min = indexes.stream().mapToInt(i -> i).min().orElse(0);
						int max = indexes.stream().mapToInt(i -> i).max().orElse(0);
						Pair<Integer, Integer> span = Pair.of(min, max + 1);
						spans2vertices.put(span, v);
//						vertices2spans.put(v, span);
					}
				});

//		// For vertices without a span, try assigning the span of the closest descendant with a span
// 		graph.vertexSet().stream()
//			.filter(v -> !vertices2spans.containsKey(v))
//			.forEach(v -> graph.getDescendants(v).stream()
//					.filter(vertices2spans::containsKey)
//					.min(Comparator.comparingInt(v2 -> graph.getDistance(v, v2)))
//					.map(vertices2spans::get)
//					.ifPresent(span -> vertices2spans.put(v, span)));
//
// 		// Some vertices may not have a span yet, i.e. concepts. Assign the span of their closes ancestor.
//		graph.vertexSet().stream()
//				.filter(v -> !vertices2spans.containsKey(v))
//				.forEach(v -> graph.getAncestors(v).stream()
//						.filter(vertices2spans::containsKey)
//						.min(Comparator.comparingInt(v2 -> graph.getDistance(v, v2)))
//						.map(vertices2spans::get)
//						.ifPresent(span -> vertices2spans.put(v, span)));
	}

	public List<String> getTokens() { return new ArrayList<>(tokens); }
	private String getToken(int token_index)	{ return tokens.get(token_index); }
	public String getLemma(int token_index)	{ return lemma.get(token_index); }
	public void setLemma(int token_index, String lemma) { this.lemma.set(token_index, lemma); }
	public String getPOS(int token_index)	{ return pos.get(token_index); }
	public void setPOS(int token_index, String pos) { this.pos.set(token_index, pos); }
	public Type getNER(int token_index)	{ return ner.get(token_index); }
	public void setNER(int token_index, Type ner) { this.ner.set(token_index, ner); }

	public Optional<Integer> getAlignment(String vertex)
	{
		return Optional.ofNullable(alignments.get(vertex));
	}

	public Set<String> getVertices(int token_index)
	{
		return alignments.keySet().stream()
				.filter(v -> alignments.get(v) == token_index)
				.collect(toSet());
	}

	boolean covers(Pair<Integer, Integer> span) { return spans2vertices.containsKey(span); }

	String getSurfaceForm(Pair<Integer, Integer> span)
	{
		return IntStream.range(span.getLeft(), span.getRight())
				.mapToObj(this::getToken)
				.collect(Collectors.joining(" "));
	}


	// Given a span of tokens and the set of vertices with exactly this span, return the vertex closest to the root
	// In some cases such as coordinations, multiple top vertices may exists for a single span.
	Set<String>  getTopSpanVertex(Pair<Integer, Integer> span)
	{
		final Map<String, Integer> depths = spans2vertices.get(span).stream()
				.collect(toMap(v -> v, graph::getDepth));
		final int min = depths.values().stream()
				.mapToInt(d -> d)
				.min().orElse(0);
		return depths.keySet().stream()
				.filter(v -> depths.get(v) == min)
				.collect(Collectors.toSet());
	}

	// The lemma of a multi-word span corresponds to a whitespace-separated sequence of tokens where the head token
	// has been replaced with its lemma, e.g. [cyber,attacks]" -> "cyber attack"
	Optional<String> getLemma(Pair<Integer, Integer> span)
	{

		if (span.getRight() - span.getLeft() == 1)
			return Optional.of(getLemma(span.getLeft()));
		else
		{
			final Set<String> vertices = getTopSpanVertex(span);
			if (vertices.isEmpty())
				return Optional.empty();
			Optional<Integer> head = getAlignment(vertices.iterator().next());
			String lemma = IntStream.range(span.getLeft(), span.getRight())
					.mapToObj(i ->
					{
						if (head.isPresent() && i == head.get())
							return getLemma(i);
						else
							return getToken(i);
					})
					.collect(Collectors.joining(" "));
			return Optional.of(lemma);
		}
	}

	// The POS of a span of tokens is the POS of the token aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no POS
	Optional<String> getPOS(Pair<Integer, Integer> span)
	{
		final Set<String> vertices = getTopSpanVertex(span);
		if (vertices.isEmpty())
			return Optional.empty();
		return getAlignment(vertices.iterator().next()).map(this::getPOS);
	}

	// The NER of a span of tokens is the NER of the token aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no NER
	Optional<Type> getNER(Pair<Integer, Integer> span)
	{
		final Set<String> vertices = getTopSpanVertex(span);
		if (vertices.isEmpty())
			return Optional.empty();
		return getAlignment(vertices.iterator().next()).map(this::getNER);
	}

	boolean isNominal(String vertex)
	{
		return getAlignment(vertex).isPresent() && getPOS(getAlignment(vertex).get()).startsWith("N");
	}

	boolean isConjunction(String vertex)
	{
		return getAlignment(vertex).isPresent() && getPOS(getAlignment(vertex).get()).equals("CC");
	}

	public void renameVertex(String old_label, String new_label)
	{
		// Update all fields containing vertices
		if (getAlignment(old_label).isPresent())
		{
			int a = alignments.remove(old_label);
			alignments.put(new_label, a);
		}
		Set<Pair<Integer, Integer>> spans = spans2vertices.keySet().stream()
				.filter(span -> spans2vertices.get(span).contains(old_label))
				.collect(toSet());

		spans.forEach(span ->
		{
			spans2vertices.get(span).remove(old_label);
			spans2vertices.get(span).add(new_label);
		});
	}

	public void removeVertex(String v)
	{
		// Update all fields containing vertices
		alignments.remove(v);
		List<Pair<Integer, Integer>> spans = spans2vertices.keySet().stream()
				.filter(span -> spans2vertices.containsEntry(span, v))
				.collect(toList());
		for (Pair<Integer, Integer> span : spans)
		{
			spans2vertices.remove(span, v);
		}
	}
}
