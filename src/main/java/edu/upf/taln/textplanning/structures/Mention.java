package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A sequence of consecutive tokens spanned over by an AMR node.
 */
public final class Mention implements Serializable
{
	private final GraphAlignments aligned_graph;
	private final Pair<Integer, Integer> span;
	private final String surfaceForm;
	private final String pos;
	private final Type type;

	public Mention(GraphAlignments graph, Pair<Integer, Integer> tokens_span, String POS, Type type)
	{
		this.aligned_graph = graph;
		this.span = tokens_span;
		surfaceForm = IntStream.range(span.getLeft(), span.getRight())
				.mapToObj(graph::getToken)
				.collect(Collectors.joining(" "));
		this.pos = POS;
		this.type = type;
	}

	public GraphAlignments getAlignedGraph() { return aligned_graph; }
	public Pair<Integer, Integer> getSpan() { return span; }
	public String getSurfaceForm() { return surfaceForm; }
	public String getPOS() { return pos;}
	public Type getType() { return type; }
	public boolean isNominal() { return pos.startsWith("N"); }


	public boolean contains(Mention o)
	{
		return  aligned_graph == o.aligned_graph &&
				span.getLeft() <= o.span.getLeft() && span.getRight() <= o.span.getRight();
	}

	@Override
	public String toString()
	{
		return getSurfaceForm();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		Mention mention = (Mention) o;
		return aligned_graph == mention.aligned_graph && span.equals(mention.span);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(aligned_graph, span);
	}
}
