package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final static Logger log = LoggerFactory.getLogger(ConLLAcces.class);

	/**
	 * Reads annotated trees from ConLL file
	 *
	 * @param inDocumentContents String containing conll serialization of trees
	 * @return a list of trees
	 */
	@Override
	public List<SemanticTree> readTrees(String inDocumentContents)
	{
		try
		{
			int numStructures = getNumberOfStructures(inDocumentContents);
			if (numStructures == 0)
				return new ArrayList<>();

			StringReader reader = new StringReader(inDocumentContents);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<SemanticTree> trees = new ArrayList<>();
			List<Annotation> anns = new ArrayList<>();
			Map<Integer, List<Pair<String, Integer>>> governors = new HashMap<>();
			//int rootId = -1;
			Set<Integer> roots = new HashSet<>();
			int sentence = 0;
			boolean failedSentence = false;

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
					throw new Exception("Cannot parse conll file, wrong number of columns");
				}

				try
				{
					int id = Integer.parseInt(columns[0]);
					if ((id == 0 || id == 1) && !anns.isEmpty())
					{
						if (!failedSentence)
						{
							// Create tree from previously collected nodes
							double position = ((double) (numStructures - sentence)) / ((double) numStructures);
							roots.stream()
									.map(r -> createTree(r, anns, governors, position))
									.forEach(trees::add);
						}

						// Reset variables
						failedSentence = false;
						++sentence;
						anns.clear();
						governors.clear();
						roots.clear();
						//rootId = -1;
					}

					if (id >= 1 && !failedSentence)
					{
						String lemma = columns[1];
						String form = columns[2];
						String pos = columns[4];
						String feats = columns[6];
						Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(feats);
						String relationName = features.getOrDefault("fn", null);
						String ref = features.getOrDefault("bnId", null);
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
						{
							log.error("Token in sentence " + sentence + " has no governor or role specified, skipping sentence");
							failedSentence = true;
						}
						if (govns.size() != roles.size())
						{
							log.error("Token in sentence " + sentence + " has different number of roles and governors, skipping sentence");
							failedSentence = true;
						}
						if (govns.size() > 1)
							log.warn("Token in sentence " + sentence + " has multiple governors, keeping first and ignoring the rest");

						int gov = govns.get(0);
						String role = roles.get(0);
						Annotation ann = new Annotation("s" + sentence + "-w" + id, form, lemma, pos, feats, ref, conf,
								relationName, role, line);
						anns.add(ann);

						if (gov > 0)
							governors.computeIfAbsent(gov, v -> new ArrayList<>()).add(Pair.of(role, id));
						else if (gov == 0)
						{
							if (roots.size() == 1)
							{
								log.warn("Sentence " + sentence + " has multiple roots");
							}
							roots.add(id);
						}
					}
				}
				catch (Exception e)
				{
					log.error("Parsing of sentence " + sentence + " failed, skipping sentence: " + e);
					failedSentence = true;
				}
			}

			// Create last tree
			double position = ((double)(numStructures - sentence)) / ((double)numStructures);
			roots.stream()
					.map(r -> createTree(r, anns, governors, position))
					.forEach(trees::add);

			return trees;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Invalid ConLL: " + e);
		}
	}

	/**
	 * Post-processing of trees does the following:
	 * - Quote nodes are removed
	 * - Coordinations are changed so that coordination acts as predicate governing coordinated elements
	 * @param inTrees trees to be modified
	 */
	public void postProcessTrees(List<SemanticTree> inTrees)
	{
		for (SemanticTree t : inTrees)
		{
			// Quotes: first pass
			List<Node> quotes = t.vertexSet().stream()
					.filter(v -> v.getEntity().getEntityLabel().equals("_"))
					.collect(Collectors.toList());

			List<Node> quotesToRemove = quotes.stream()
					.filter(quote -> t.inDegreeOf(quote) == 1 && t.outDegreeOf(quote) == 0)
					.collect(Collectors.toList());
			t.removeAllVertices(quotesToRemove);

			// Quotes: second pass
			quotes = t.vertexSet().stream()
					.filter(v -> v.getEntity().getEntityLabel().equals("_"))
					.collect(Collectors.toList());
			quotesToRemove.clear();
			quotes.stream()
					.filter(quote -> t.inDegreeOf(quote) == 1 && t.outDegreeOf(quote) == 1)
					.forEach(quote -> {
				quotesToRemove.add(quote);
				Edge i = t.incomingEdgesOf(quote).iterator().next();
				Edge o = t.outgoingEdgesOf(quote).iterator().next();
				Edge e = new Edge(i.getRole(), i.isArg());
				t.addEdge(t.getEdgeSource(i), t.getEdgeTarget(o), e);
			});
			t.removeAllVertices(quotesToRemove);

			// Coords
			List<Edge> coords = t.edgeSet().stream()
					.filter(e -> e.getRole().equals("COORD"))
					.filter(e -> ((AnnotatedEntity)t.getEdgeTarget(e).getEntity()).getAnnotation().getPOS().equals("CC"))
					.collect(Collectors.toList());
			List<Triple<Node, Node, Edge>> edgesToAdd = new ArrayList<>();
			List<Edge> edgesToRemove = new ArrayList<>();

			for (Edge e : coords)
			{
				// First item in coordination acts as governor of coord node, reverse relation so that coord governs both items
				Node item = t.getEdgeSource(e);
				Node coord = t.getEdgeTarget(e);
				Edge reversedEdge = new Edge("I", true);
				edgesToAdd.add(Triple.of(coord, item, reversedEdge)); // reverse of e: coord -I-> item
				edgesToRemove.add(e);

				// Now change governor of first item to be governor of coord
				if (t.inDegreeOf(item) == 1)
				{
					Edge e2 = t.incomingEdgesOf(item).iterator().next();
					Edge newE2 = new Edge(e2.getRole(), e2.isArg());
					edgesToAdd.add(Triple.of(t.getEdgeSource(e2), coord, newE2));
					edgesToRemove.add(e2);
				}
			}

			t.removeAllEdges(edgesToRemove);
			edgesToAdd.forEach(e -> t.addEdge(e.getLeft(), e.getMiddle(), e.getRight()));
		}
	}

	/**
	 * Converts semantic trees to ConLL format
	 *
	 * @param inTrees list of trees
	 * @return ConLL-formatted representation of the trees
	 */
	public String writeTrees(Collection<SemanticTree> inTrees)
	{
		// Get a preorder list of nodes in the tree along with their parents
		return inTrees.stream()
				.map(t -> {
					List<Node> nodes = new ArrayList<>();
					nodes.add(t.getRoot());
					t.getPreOrder().stream()
							.map(t::getEdgeTarget)
							.forEach(nodes::add);

					List<Annotation> anns = nodes.stream()
							.map(Node::getEntity)
							.filter(AnnotatedEntity.class::isInstance)
							.map(AnnotatedEntity.class::cast)
							.map(AnnotatedEntity::getAnnotation)
							.collect(Collectors.toList());

					// Collect governor to dependent mappings
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, nodes.size())
							.mapToObj(i -> {
								Node n = nodes.get(i);
								List<Pair<String, Integer>> govs = new ArrayList<>();
								if (t.inDegreeOf(n) > 0) // has parent
								{
									Edge e = t.incomingEdgesOf(n).iterator().next();
									Node gov = t.getEdgeSource(e);
									int govIndex = nodes.indexOf(gov);
									govs.add(Pair.of(e.getRole(), govIndex));
								}

								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));


					return nodesToConll(anns, governors);
				})
				.map(s -> "0\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\tslex=Sentence\n" + s + "\n")
				.reduce(String::concat).orElse("");
	}

	/**
	 * Converts semantic graphs to ConLL format.
	 * @return ConLL-formatted representation of the grpahs
	 */
	public String writeGraphs(Collection<SemanticGraph> graphs)
	{
		// Get a topological ordering of nodes along with their governors in the graph
		return graphs.stream()
				.map(g -> {
					List<Node> nodes = new ArrayList<>(g.vertexSet());
					List<Annotation> anns = nodes.stream()
							.map(Node::getEntity)
							.filter(AnnotatedEntity.class::isInstance)
							.map(AnnotatedEntity.class::cast)
							.map(AnnotatedEntity::getAnnotation)
							.collect(Collectors.toList());

					// Collect governor to dependent mappings
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, nodes.size())
							.mapToObj(i -> {
								Node n = nodes.get(i);
								List<Pair<String, Integer>> govs = new ArrayList<>();
								g.incomingEdgesOf(n).forEach(e -> {
									Node gov = g.getEdgeSource(e);
									int govIndex = nodes.indexOf(gov);
									govs.add(Pair.of(e.getRole(), govIndex));
								});

								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

					return this.nodesToConll(anns, governors);
				})
				.map(s -> "0\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\tslex=Sentence\n" + s + "\n")
				.reduce(String::concat).orElse("");
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
		// Iterate nodes again, this time producing conll
		StringBuilder conll = new StringBuilder();
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
					.reduce((e1, e2) -> e1 + "|" + e2).orElse("");
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

			conll.append(entityConll);
		}

		return conll.toString();
	}

	private SemanticTree createTree(int inRootId, List<Annotation> annotations,
	                                 Map<Integer, List<Pair<String, Integer>>> inGovernors, double inPosition)
	{
		// Create tree with a root
		Annotation a = annotations.get(inRootId - 1);
		Node root = createNode(a);
		SemanticTree tree =	new SemanticTree(root, inPosition);

		List<Pair<Integer, Node>> currentGovernors = new ArrayList<>();
		currentGovernors.add(Pair.of(inRootId, tree.getRoot()));

		while (!currentGovernors.isEmpty())
		{
			List<Pair<Integer, Node>> newGovernors = currentGovernors.stream()
					.map(node -> {
						int governorId = node.getLeft();
						List<Pair<String, Integer>> dependents =
								inGovernors.computeIfAbsent(governorId, v -> new ArrayList<>());
						return dependents.stream()
								.map(d -> Pair.of(d.getRight(), Pair.of(annotations.get(d.getRight() - 1), d.getLeft())))
								.map(d -> {
									// Create new nodes for governor and dependent
									Node gov = node.getRight();
									Annotation ann = d.getRight().getLeft();
									Node dep = createNode(ann);
									tree.addVertex(dep);

									// Create new edge between governor and dependent
									Edge e = new Edge(ann.getRole(), ann.isArgument());
									tree.addEdge(gov, dep, e);

									// Return dependent as new governor
									Integer depId = d.getLeft();
									return Pair.of(depId, dep);
								})
								.collect(Collectors.toList());
					})
					.flatMap(List::stream)
					.collect(Collectors.toList());

			currentGovernors.clear();
			currentGovernors.addAll(newGovernors);
		}

		return tree;
	}

	private Node createNode(Annotation a)
	{
		AnnotatedEntity e = new AnnotatedEntity(a);
		return new Node(a.getId(), e, 0.0); // no weight, no conll
	}
}
