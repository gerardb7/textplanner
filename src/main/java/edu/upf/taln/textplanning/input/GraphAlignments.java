package edu.upf.taln.textplanning.input;

import com.google.common.collect.*;
import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

// Maps vertices in semantic graphs to tokens and their linguistic annotations
public class GraphAlignments implements Serializable
{
	private final List<String> topo_order = new ArrayList<>(); // vertices
	private final List<String> tokens = new ArrayList<>();
	private final List<String> lemma = new ArrayList<>();
	private final List<String> pos = new ArrayList<>();
	private final List<Type> ner = new ArrayList<>();
	private final Map<String, Integer> alignments = new HashMap<>(); // vertices to token offsets
	private final Multimap<Pair<Integer, Integer>, String> spans2nodes = HashMultimap.create(); // spans to vertices
	private final static long serialVersionUID = 1L;

	GraphAlignments(SemanticGraph graph, Map<String, Integer> alignments, List<String> tokens)
	{
		graph.iterator().forEachRemaining(topo_order::add);
		this.tokens.addAll(tokens);
		this.tokens.forEach(t ->
		{
			this.lemma.add("");
			this.pos.add("");
			this.ner.add(Type.Other);
		});
		this.alignments.putAll(alignments);

		// Calculate spans
		graph.vertexSet()
				.forEach(v -> {
					if (alignments.containsKey(v))
					{
						int i = alignments.get(v);
						Pair<Integer, Integer> span = Pair.of(i, i + 1);
						spans2nodes.put(span, v);
					}
				});

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
						spans2nodes.put(span, v);
					}
				});
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
				.collect(Collectors.toSet());
	}

	boolean covers(Pair<Integer, Integer> span) { return spans2nodes.containsKey(span); }

	String getSurfaceForm(Pair<Integer, Integer> span)
	{
		return IntStream.range(span.getLeft(), span.getRight())
				.mapToObj(this::getToken)
				.collect(Collectors.joining(" "));
	}


	// Given a span of tokens and the set of vertices with exactly this span, return the first vertex appearing in a
	// topological sort (closest to the root of the graph)
	Optional<String>  getTopSpanVertex(Pair<Integer, Integer> span)
	{
		return spans2nodes.get(span).stream().max(Comparator.comparingInt(topo_order::indexOf));
	}

	// The lemma of a multi-word span correpsonds to a whitespace-separated sequence of tokens where the head token
	// has been replaced with its lemma, e.g. [cyber,attacks]" -> "cyber attack"
	Optional<String> getLemma(Pair<Integer, Integer> span)
	{
		if (span.getRight() - span.getLeft() == 1)
			return Optional.of(getLemma(span.getLeft()));
		else
		{
			Optional<Integer> head = getTopSpanVertex(span).flatMap(this::getAlignment);
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
		return getTopSpanVertex(span).flatMap(v -> getAlignment(v).map(this::getPOS));
	}

	// The NER of a span of tokens is the NER of the token aligned with its head vertex.
	// If there is no top vertex or it is unaligned, then there is no NER
	Optional<Type> getNER(Pair<Integer, Integer> span)
	{
		return getTopSpanVertex(span).flatMap(v -> getAlignment(v).map(this::getNER));
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
		topo_order.replaceAll(v -> v.equals(old_label) ? new_label : v);
		if (getAlignment(old_label).isPresent())
		{
			int a = alignments.remove(old_label);
			alignments.put(new_label, a);
		}
		Set<Pair<Integer, Integer>> spans = spans2nodes.keySet().stream()
				.filter(span -> spans2nodes.get(span).contains(old_label))
				.collect(Collectors.toSet());

		spans.forEach(span ->
		{
			spans2nodes.get(span).remove(old_label);
			spans2nodes.get(span).add(new_label);
		});
	}

	public void removeVertex(String v)
	{
		// Update all fields containing vertices
		topo_order.remove(v);
		alignments.remove(v);
		List<Pair<Integer, Integer>> spans = spans2nodes.keySet().stream()
				.filter(span -> spans2nodes.containsEntry(span, v))
				.collect(toList());
		for (Pair<Integer, Integer> span : spans)
		{
			spans2nodes.remove(span, v);
		}
	}
}
