package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Immutable class, reads and writes annotated structures from/to Conll format
 */
public class ConLLAcces implements DocumentAccess
{
	/**
	 * Reads annotated graphs (DAGs) from ConLL file
	 *
	 * @param inDocumentContents  String containing conll serialization of DAGs
	 * @return a list of graphs, one per sentence
	 */
	@Override
	public List<DirectedAcyclicGraph<Annotation, LabelledEdge>> readGraphs(String inDocumentContents)
	{
		try
		{
			int numStructures = getNumberOfStructures(inDocumentContents);

			StringReader reader = new StringReader(inDocumentContents);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<DirectedAcyclicGraph<Annotation, LabelledEdge>> graphs = new ArrayList<>();
			List<Annotation> nodes = new ArrayList<>();
			Map<Integer, List<Pair<String, Integer>>> governors = new HashMap<>();
			int sentence = 0;

			String line;
			while ((line = bufferReader.readLine()) != null)
			{
				if (line.isEmpty())
				{
					continue;
				}

				String[] columns = line.split("\t");
				if (columns.length != 14 && columns.length != 15)
				{
					throw new Exception("Cannot parse line in conll file: " + line);
				}

				int id = Integer.parseInt(columns[0]);
				if ((id == 0 || id == 1) && !nodes.isEmpty())
				{
					// Create graph from previously collected nodes
					double position = ++sentence / numStructures;
					graphs.add(createGraph(nodes, governors, position));
					nodes.clear();
					governors.clear();
				}

				if (id >= 1)
				{
					String lemma = columns[1];
					String form = columns[2];
					String pos = columns[4];
					String feats = columns[6];
					Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(feats);
					String relationName = features.containsKey("fn") ? features.get("fn") : null;
					String ref = features.containsKey("bnId") ?
							features.get("bnId") : null;
					if (pos.equals("_"))
					{
						if (features.containsKey("dpos"))
						{
							pos = features.get("dpos");
						}
						else if (features.containsKey("spos"))
						{
							pos = features.get("spos");
						}
					}

					//TODO consider removing code manipulating BabelNet IRIs
					if (ref != null)
					{
						if (ref.startsWith("bn:"))
						{
							ref = ref.substring(3);
						}
						//ref = "http://babelnet.org/rdf/s" + ref;
					}
					double conf = features.containsKey("conf") ?
							Double.parseDouble(features.get("conf")) : 0.0;
					List<Integer> govns = Arrays.stream(columns[8].split(",")).map(Integer::parseInt).collect(Collectors.toList());
					List<String> roles = Arrays.stream(columns[10].split(",")).collect(Collectors.toList());
					Annotation node = new Annotation("s" + sentence + "-w" + id, form, lemma, pos, feats, ref, conf,
							relationName, null);
					// @TODO roles shouldn't be part of Annotation class for DAGs
					nodes.add(node);
					IntStream.range(0, govns.size())
							.filter(i -> govns.get(i) > 0)
							.forEach(i -> governors.computeIfAbsent(govns.get(i), v -> new ArrayList<>()).add(Pair.of(roles.get(i), id)));
				}
			}

			// Create last graph
			double position = ++sentence / numStructures;
			graphs.add(createGraph(nodes, governors, position));

			return graphs;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * Reads annotated trees from ConLL file
	 *
	 * @param inDocumentContents String containing conll serialization of trees
	 * @return a list of trees
	 */
	@Override
	public List<AnnotatedTree> readTrees(String inDocumentContents)
	{
		try
		{
			int numStructures = getNumberOfStructures(inDocumentContents);
			if (numStructures == 0)
				return new ArrayList<>();

			StringReader reader = new StringReader(inDocumentContents);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<AnnotatedTree> trees = new ArrayList<>();
			List<Annotation> nodes = new ArrayList<>();
			Map<Integer, List<Pair<String, Integer>>> governors = new HashMap<>();
			int rootId = -1;
			int sentence = 0;

			String line;
			while ((line = bufferReader.readLine()) != null)
			{
				if (line.isEmpty())
				{
					continue;
				}

				String[] columns = line.split("\t");
				if (columns.length != 14 && columns.length != 15)
				{
					throw new Exception("Cannot parse line in conll file: " + line);
				}

				int id = Integer.parseInt(columns[0]);
				if ((id == 0 || id == 1) && !nodes.isEmpty())
				{
					// Create tree from previously collected nodes
					double position = ((double)(numStructures - sentence)) / ((double)numStructures);
					trees.add(createTree(rootId, nodes, governors, position));
					++sentence;
					nodes.clear();
					governors.clear();
					rootId = -1;
				}

				if (id >= 1)
				{
					String lemma = columns[1];
					String form = columns[2];
					String pos = columns[4];
					String feats = columns[6];
					Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(feats);
					String relationName = features.containsKey("fn") ? features.get("fn") : null;
					String ref = features.containsKey("bnId") ?
							features.get("bnId") : null;
					if (pos.equals("_"))
					{
						if (features.containsKey("dpos"))
						{
							pos = features.get("dpos");
						}
						else if (features.containsKey("spos"))
						{
							pos = features.get("spos");
						}
					}

					double conf = features.containsKey("conf") ?
							Double.parseDouble(features.get("conf")) : 0.0;
					List<Integer> govns = Arrays.stream(columns[8].split(",")).map(Integer::parseInt).collect(Collectors.toList());
					List<String> roles = Arrays.stream(columns[10].split(",")).collect(Collectors.toList());

					// Perform some checks
					if (govns.isEmpty() || roles.isEmpty())
						throw new Exception("Conll file has no governor or role specified in line " + line);
					if (govns.size() > 1 || roles.size() > 1)
						throw new Exception("Conll file contains graphs which cannot be read as trees: " + line);
					if (govns.size() != roles.size())
						throw new Exception("Conll file contains different number of roles and governors in line: " + line);

					Annotation node = new Annotation("s" + sentence + "-w" + id, form, lemma, pos, feats, ref, conf,
							relationName, roles.get(0));
					nodes.add(node);
					IntStream.range(0, govns.size())
							.filter(i -> govns.get(i) > 0)
							.forEach(i -> governors.computeIfAbsent(govns.get(i), v -> new ArrayList<>()).add(Pair.of(roles.get(i), id)));
					if (govns.size() == 1 && govns.get(0) == 0)
					{
						if (rootId != -1)
							throw new Exception("Conll file contains graphs which cannot be read as trees");
						rootId = id;
					}
				}
			}

			// Create last tree
			double position = ((double)(numStructures - sentence)) / ((double)numStructures);
			trees.add(createTree(rootId, nodes, governors, position));

			return trees;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Invalid ConLL: " + e);
		}
	}

	/**
	 * Converts graphs (DAGs) to ConLL format.
	 * IMPORTANT: this conversion will only work properly if all nodes in the graph (instances of Annotation class)
	 * are unique!
	 *
	 * @param inGraphs DAGs
	 * @return ConLL-formatted representation of the DAGs
	 */
	public String writeGraphs(Collection<DirectedAcyclicGraph<Annotation, LabelledEdge>> inGraphs)
	{
		// Get a topological ordering of nodes along with their governors in the graph
		return inGraphs.stream()
				.map(g -> {
					List<Annotation> anns = g.vertexSet().stream().collect(Collectors.toList());
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, anns.size())
							.mapToObj(i -> {
								final List<Pair<String, Integer>> govs = g.incomingEdgesOf(anns.get(i)).stream()
										.map(e -> Pair.of(e.getLabel(), anns.indexOf(g.getEdgeSource(e)))) // source node must be unique!
										.collect(Collectors.toList());
								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
					return this.nodesToConll(anns, governors);
				})
				.map(s -> "0\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\tslex=Sentence\n" + s + "\n")
				.reduce(String::concat).get();
	}

	/**
	 * Converts annotated trees to ConLL format
	 *
	 * @param inTrees list of trees
	 * @return ConLL-formatted representation of the trees
	 */
	public String writeTrees(Collection<AnnotatedTree> inTrees)
	{
		// Get a preorder list of nodes in the tree along with their parents
		return inTrees.stream()
				.map(AnnotatedTree::getPreOrder)
				.map(t -> {
					List<Annotation> anns =
							t.stream().map(n -> n.getData().getAnnotation()).collect(Collectors.toList());
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, t.size())
							.mapToObj(i -> {
								String role = t.get(i).getData().getAnnotation().getRole();
								int governorIndex = -1;
								List<Pair<String, Integer>> govs = new ArrayList<>();
								if (t.get(i).getParent().isPresent())
								{
									governorIndex = t.indexOf(t.get(i).getParent().get());
									govs.add(Pair.of(role, governorIndex));
								}

								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
					return nodesToConll(anns, governors);
				})
				.map(s -> "0\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\tslex=Sentence\n" + s + "\n")
				.reduce(String::concat).get();
	}


	private int getNumberOfStructures(String inDocumentContents) throws Exception
	{
		try(StringReader reader = new StringReader(inDocumentContents); BufferedReader bufferReader = new BufferedReader(reader))
		{

			int lastId = 1;
			int numStructures = 0;
			String line;
			while ((line = bufferReader.readLine()) != null)
			{
				if (line.isEmpty())
					continue;

				String[] columns = line.split("\t");
				if (columns.length != 14 && columns.length != 15)
					throw new Exception("Cannot parse line in conll file: " + line);

				int id = Integer.parseInt(columns[0]);
				if (id <= lastId)
					++numStructures;
				lastId = id;
			}

			return numStructures;
		}
	}

	private String nodesToConll(List<Annotation> inNodes, Map<Integer, List<Pair<String, Integer>>> inGovernors)
	{
		// Iterate entities again, this time producing conll
		String conll = "";
		for (int id = 0; id < inNodes.size(); ++id)
		{
			Annotation node = inNodes.get(id);
			List<String> govIDs = inGovernors.get(id).stream()
					.filter(p -> p.getRight() != null)
					.map(p -> p.getRight() + 1)
					.map(Object::toString)
					.collect(Collectors.toList());
			String governors = govIDs.isEmpty() ? "0" : String.join(",", govIDs);
			List<String> roles = inGovernors.get(id).stream()
					.map(Pair::getLeft)
					.collect(Collectors.toList());
			String roless = roles.isEmpty() ? "ROOT" : String.join(",", roles);
			String feats = node.getFeats();
			Map<String, String> features = new HashMap<>(Splitter.on("|").withKeyValueSeparator("=").split(feats));
			if (node.getRelation() != null)
			{
				features.merge("fn", node.getRelation(), (v1, v2) -> v2);
			}
			if (node.getSense() != null)
			{
				String ref = node.getSense().startsWith("bn:") ? node.getSense() : "bn:" + node.getSense();
				features.merge("bnId", ref, (v1, v2) -> v2);
			}
			feats = features.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.reduce((e1, e2) -> e1 + "|" + e2).get();
			String entityConll = id + 1 + "\t" +
					node.getLemma() + "\t" +
					node.getForm() + "\t" +
					"_\t" +
					node.getPOS() + "\t" +
					"_\t" +
					feats + "\t" +
					"_\t" +
					governors + "\t" +
					"_\t" +
					roless + "\t" +
					"_\t" +
					"_\t" +
					"_\t" +
					"true\n";

			conll += entityConll;
		}

		return conll;
	}


	private DirectedAcyclicGraph<Annotation, LabelledEdge> createGraph(List<Annotation> inNodes,
	                                                                   Map<Integer, List<Pair<String, Integer>>> inGovernors, double inPosition)
	{
		DirectedAcyclicGraph<Annotation, LabelledEdge> graph =
				new DirectedAcyclicGraph<>(LabelledEdge.class);
		inNodes.forEach(graph::addVertex);
		inGovernors.forEach((d, l) -> l.forEach(g -> {
			LabelledEdge e = new LabelledEdge(g.getLeft()); // use role as label for edge
			graph.addEdge(inNodes.get(d - 1), inNodes.get(g.getRight() - 1), e);
		}));

		return graph;
	}

	private AnnotatedTree createTree(int inRootId, List<Annotation> inNodes,
	                                 Map<Integer, List<Pair<String, Integer>>> inGovernors, double inPosition)
	{
		Annotation root = inNodes.get(inRootId - 1);
		AnnotatedEntity rootSense = new AnnotatedEntity(root);
		AnnotatedTree tree =	new AnnotatedTree(rootSense, inPosition);
		List<Pair<Integer, OrderedTree.Node<AnnotatedEntity>>> currentGovernors = new ArrayList<>();
		currentGovernors.add(Pair.of(inRootId, tree.getRoot()));

		while (!currentGovernors.isEmpty())
		{
			List<Pair<Integer, OrderedTree.Node<AnnotatedEntity>>> newGovernors = currentGovernors.stream()
					.map(node -> {
						int governorId = node.getLeft();
						List<Pair<String, Integer>> dependents =
								inGovernors.computeIfAbsent(governorId, v -> new ArrayList<>());
						return dependents.stream()
								.map(d -> Pair.of(d.getRight(), Pair.of(inNodes.get(d.getRight() - 1), d.getLeft())))
								.map(d -> Pair.of(d.getLeft(), node.getRight().addChild(new AnnotatedEntity(d.getRight().getLeft()))))
								.collect(Collectors.toList());
					})
					.flatMap(List::stream)
					.collect(Collectors.toList());

			currentGovernors.clear();
			currentGovernors.addAll(newGovernors);
		}

		return tree;
	}
}
