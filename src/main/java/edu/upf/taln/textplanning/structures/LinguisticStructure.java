package edu.upf.taln.textplanning.structures;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A linguistic structure is a directed acyclic graph with annotated words as nodes. Edges indicate relations between
 * words and the roles adopted by them.
 * This class can be used to represent dependency-based linguistic structures where relations are word-to-word.
 * Relations can be syntactic, semantic or discursive.
 */
public final class LinguisticStructure extends DirectedAcyclicGraph<AnnotatedWord, Role>
{

	private final Document d;

	public LinguisticStructure(Document d, Class<? extends Role> edgeClass)
	{
		super(edgeClass);
		this.d = d;
	}

	public Document getDocument() { return d; }

	/**
	 * @return list of vertices in the structure following a fixed topological order
	 */
	public List<AnnotatedWord> getTopologicalOrder()
	{
		// Create a total order over the vertex set to guarantee that the returned iterator always iterates over the same sequence
		Queue<AnnotatedWord> sortedNodes = vertexSet().stream()
				.sorted(Comparator.comparing(AnnotatedWord::toString))
				.collect(Collectors.toCollection(ArrayDeque::new));
		TopologicalOrderIterator<AnnotatedWord, Role> it = new TopologicalOrderIterator<>(this, sortedNodes);
		List<AnnotatedWord> es = new ArrayList<>();
		while (it.hasNext())
		{
			es.add(it.next());
		}
		return es;
	}

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(AnnotatedWord n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Role::isCore);
	}
}
