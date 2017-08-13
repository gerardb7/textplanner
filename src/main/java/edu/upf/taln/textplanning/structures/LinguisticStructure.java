package edu.upf.taln.textplanning.structures;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static java.util.stream.Collectors.toList;

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
		PriorityQueue<AnnotatedWord> queue = new PriorityQueue<>(vertexSet().size(), Comparator.comparing(AnnotatedWord::toString));
		TopologicalOrderIterator<AnnotatedWord, Role> it = new TopologicalOrderIterator<>(this, queue);
		List<AnnotatedWord> es = new ArrayList<>();
		while (it.hasNext())
		{
			es.add(it.next());
		}
		return es;
	}

	/**
	 * @return list of vertices in the structure following their order of appearance in the text fragment it annotates
	 */
	public List<AnnotatedWord> getTextualOrder()
	{
		return vertexSet().stream()
				.sorted(Comparator.comparing(AnnotatedWord::getOffsetStart))
				.collect(toList());
	}

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(AnnotatedWord n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Role::isCore);
	}
}
