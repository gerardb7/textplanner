package edu.upf.taln.amrplanning.io;

import edu.upf.taln.amrplanning.structures.AMRSemantics;
import edu.upf.taln.amrplanning.structures.SemanticGraph;
import edu.upf.taln.textplanning.structures.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AMRWriter
{
	public String write(List<SemanticSubgraph> subgraphs)
	{
		if (subgraphs.isEmpty())
			return "";

		final GlobalSemanticGraph base = subgraphs.get(0).getBase();

		AtomicInteger counter = new AtomicInteger();
		final List<SemanticGraph> dags = subgraphs.stream()
				.map(s ->
				{
					SemanticGraph dag = new SemanticGraph(Integer.toString(counter.incrementAndGet()), s.getRoot());
					s.vertexSet().forEach(dag::addVertex);
					s.edgeSet().forEach(e ->
					{
						final Role e2 = Role.create(e.getLabel());
						final String source = s.getEdgeSource(e);
						final String target = s.getEdgeTarget(e);
						dag.addEdge(source, target, e2);
					});
					return dag;
				})
				.collect(Collectors.toList());

		dags.forEach(this::rootGraph);

		return dags.stream()
				.map(dag -> "# ::id " + dag.getSource() + "\n" +
							printVertex(dag.getRoot(), 1, new HashSet<>(), dag, base))
				.collect(Collectors.joining("\n\n"));
	}

	String printVertex(String v, int depth, Set<String> visited, SemanticGraph g, GlobalSemanticGraph base)
	{
		if (visited.contains(v))
			return v;
		visited.add(v);

		final String tabs = IntStream.range(0, depth)
				.mapToObj(i -> "\t")
				.collect(Collectors.joining());

		String rels = g.outDegreeOf(v) == 0 ? "" :
				g.outgoingEdgesOf(v).stream()
					.sorted(Comparator.comparing(Role::getLabel))
					.map(e -> e.getLabel() + " " + printVertex(g.getEdgeTarget(e), depth + 1, visited, g, base))
					.collect(Collectors.joining("\n" + tabs,"\n" + tabs,""));

		String t = "";
		final Random r = new Random();

		if (!base.getTypes(v).isEmpty())
		{
			final List<String> types = new ArrayList<>(base.getTypes(v));
			final int i = r.nextInt(types.size());
			t = " " + types.get(i);
		}

		String names = "";
		// Vertices with a NE meaning or having a named mention
		if (base.getMeaning(v).map(Meaning::isNE).orElse(false) ||	 base.getMentions(v).stream().anyMatch(Mention::isNE))
		{
			final List<Mention> mentions = base.getMentions(v).stream()
					.distinct()
					.collect(Collectors.toList());
			Mention m = mentions.get(r.nextInt(mentions.size()));
			names = "\n" + tabs + addNames(m, depth + 1, g);
		}

		return "(" + v + " / " + t + names + rels + ")";
	}

	// Reverses out edges of source nodes until the only source is the root
	private void rootGraph(SemanticGraph g)
	{
		Set<String> sources = g.vertexSet().stream()
				.filter(v -> g.inDegreeOf(v) == 0)
				.filter(v -> !v.equals(g.getRoot()))
				.collect(Collectors.toSet());

		while(!sources.isEmpty())
		{
			final Set<Role> out_links = sources.stream()
					.map(g::outgoingEdgesOf)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());

			for (Role e : out_links)
			{
				reverseEdge(e, g);
			}

			sources = g.vertexSet().stream()
					.filter(v -> g.inDegreeOf(v) == 0)
					.filter(v -> !v.equals(g.getRoot()))
					.collect(Collectors.toSet());
		}

	}

	private void reverseEdge(Role e, SemanticGraph g)
	{
		final String source = g.getEdgeSource(e);
		final String target = g.getEdgeTarget(e);
		final String l = e.getLabel();
		final String new_l = l.endsWith(AMRSemantics.inverse_suffix) ?
				l.substring(0, l.length() - AMRSemantics.inverse_suffix.length()) :
				l + AMRSemantics.inverse_suffix;
		Role new_e = Role.create(new_l);

		g.removeEdge(e);
		g.addEdge(target, source, new_e);
	}

	private String addNames(Mention m, int depth, SemanticGraph g)
	{
		final int name_index = g.vertexSet().stream()
				.filter(n -> n.matches("n\\d"))
				.map(n -> n.substring(1))
				.mapToInt(Integer::parseInt)
				.max().orElse(1);

		final String tabs = IntStream.range(0, depth)
				.mapToObj(i -> "\t")
				.collect(Collectors.joining());

		final String[] tokens = m.getSurface_form().split(" ");
		final String names = IntStream.range(0, tokens.length)
				.mapToObj(i -> tabs + "op" + (i + 1) + " \"" + tokens[i] + "\"")
				.collect(Collectors.joining("\n"));

		return ":name (n" +  name_index + " / name\n" + names + ")";
	}
}
