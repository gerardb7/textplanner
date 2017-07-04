package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.alg.ConnectivityInspector;
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
	public List<SemanticGraph> readStructures(String inDocumentContents)
	{
		try
		{
			int numStructures = getNumberOfStructures(inDocumentContents);
			if (numStructures == 0)
				return new ArrayList<>();

			StringReader reader = new StringReader(inDocumentContents);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<SemanticGraph> graphs = new ArrayList<>();
			List<Annotation> anns = new ArrayList<>();
			Map<Integer, List<Integer>> governors = new HashMap<>();
			Set<Integer> roots = new HashSet<>();
			int structure = 0;
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
							// Create graph from previously collected nodes
							graphs.addAll(createGraphs(anns, governors));
						}

						// Reset variables
						failedSentence = false;
						++structure;
						anns.clear();
						governors.clear();
						roots.clear();
					}

					if (id >= 1 && !failedSentence)
					{
						String form = columns[1];
						String lemma = columns[2];
						String pos = columns[4];
						String feats = columns[6];
						Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(feats);
						if (features.containsKey("original_slex"))
							form = features.get("original_slex");

						String relationName = features.getOrDefault("fn", null);
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

						List<Integer> govns = Arrays.stream(columns[8].split(",")).map(Integer::parseInt).collect(Collectors.toList());
						List<String> roles = Arrays.stream(columns[10].split(",")).collect(Collectors.toList());

						// Perform some checks
						if (govns.isEmpty() || roles.isEmpty())
						{
							log.error("Token in structure " + structure + " has no governor or role specified, skipping structure");
							failedSentence = true;
						}
						if (govns.size() != roles.size())
						{
							log.error("Token in structure " + structure + " has different number of roles and governors, skipping structure");
							failedSentence = true;
						}
						if (govns.size() > 1)
							log.warn("Token in structure " + structure + " has multiple governors, keeping first and ignoring the rest");

						// todo temporary fix to be reverted once offsets are back into conll
						String id0 = features.getOrDefault("id0", "0");
						if (id0.endsWith("_elid"))
							id0 = id0.substring(0, id0.indexOf('_'));
						long offsetStart = Long.valueOf(id0);
						long offsetEnd = Long.valueOf(id0);
//						long offsetStart = Long.valueOf(features.getOrDefault("start_string", "0"));
//						long offsetEnd = Long.valueOf(features.getOrDefault("end_string", "0"));

						int gov = govns.get(0);
						String role = roles.get(0);
						Annotation ann = new Annotation("s" + structure + "-w" + id, form, lemma, pos, feats, relationName, role, line, offsetStart, offsetEnd);
						anns.add(ann);

						if (gov > 0)
							governors.computeIfAbsent(gov-1, v -> new ArrayList<>()).add(id-1);
						else if (gov == 0)
						{
							if (roots.size() == 1)
							{
								log.warn("Structure " + structure + " has multiple roots");
							}
							roots.add(id);
						}
					}
				}
				catch (Exception e)
				{
					log.error("Parsing of structure " + structure + " failed, skipping structure: " + e);
					failedSentence = true;
				}
			}

			// Create last tree
			roots.stream()
					.map(r -> createGraphs(anns, governors))
					.forEach(graphs::addAll);

			return graphs;
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
	 *
	 * todo remove this after moving from dsynt to semantic structures
	 */
	public void postProcessTrees(List<SemanticTree> inTrees)
	{
		for (SemanticTree t : inTrees)
		{
			// Quotes: first pass
			List<Node> quotes = t.vertexSet().stream()
					.filter(v -> v.getAnnotation().getForm().equals("_"))
					.collect(Collectors.toList());

			List<Node> quotesToRemove = quotes.stream()
					.filter(quote -> t.inDegreeOf(quote) == 1 && t.outDegreeOf(quote) == 0)
					.collect(Collectors.toList());
			t.removeAllVertices(quotesToRemove);

			// Quotes: second pass
			quotes = t.vertexSet().stream()
					.filter(v -> v.getAnnotation().getForm().equals("_"))
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
					.filter(e -> t.getEdgeTarget(e).getAnnotation().getPOS().equals("CC"))
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

					// Collect coreferring nodes
					List<Optional<Node>> coref = nodes.stream()
							.map(Node::getCoref)
							.map(o -> o.orElse(""))
							.map(id -> nodes.stream()
									.filter(n -> n.getId().equals(id))
									.findFirst())
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


					return nodesToConll(nodes, governors, coref);
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

					return this.nodesToConll(nodes, governors, new ArrayList<>());
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

	private String nodesToConll(List<Node> inNodes, Map<Integer, List<Pair<String, Integer>>> inGovernors,
	                            List<Optional<Node>> coref)
	{
		// Iterate nodes again, this time producing conll
		StringBuilder conll = new StringBuilder();
		for (int id = 0; id < inNodes.size(); ++id)
		{
			Node node = inNodes.get(id);
			Annotation ann = node.getAnnotation();
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
			String feats = ann.getFeats();
			Map<String, String> features = new HashMap<>(Splitter.on("|").withKeyValueSeparator("=").split(feats));
			if (ann.getRelation() != null)
			{
				features.merge("fn", ann.getRelation(), (v1, v2) -> v2);
			}
			if (node.getEntity().getLabel().startsWith("bn:"))
			{
				features.merge("bnId", node.getEntity().getLabel(), (v1, v2) -> v2);
			}
			if (!coref.isEmpty() && coref.get(id).isPresent())
			{
				Node corefAnn = coref.get(id).get();
				features.merge("coref_id", Integer.toString(inNodes.indexOf(corefAnn) + 1), (v1, v2) -> v2);
			}
			feats = features.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.reduce((e1, e2) -> e1 + "|" + e2).orElse("");
			String entityConll = id + 1 + "\t" +
					ann.getLemma() + "\t" +
					ann.getForm() + "\t" +
					"_\t" +
					ann.getPOS() + "\t" +
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

	private Set<SemanticGraph> createGraphs(List<Annotation> annotations,
	                                 Map<Integer, List<Integer>> inGovernors)
	{
		// Create graph and add vertices
		SemanticGraph graph = new SemanticGraph(Edge.class);
		List<Node> nodes = new ArrayList<>();
		annotations.stream()
				.map(this::createNode)
				.peek(nodes::add)
				.forEach(graph::addVertex);

		for (int i : inGovernors.keySet())
		{
			Node gov = nodes.get(i);
			for (int j : inGovernors.get(i))
			{
				Node dep = nodes.get(j);
				Annotation ann = annotations.get(j);
				Edge e = new Edge(ann.getRole(), ann.isArgument());
				graph.addEdge(gov, dep, e);
			}
		}

		// If resulting graph isn't connected (i.e. multiple roots), split into maximally connected subgraphs
		Set<SemanticGraph> graphs = new HashSet<>();
		ConnectivityInspector<Node, Edge> conn = new ConnectivityInspector<>(graph);
		if (!conn.isGraphConnected())
		{
			List<Set<Node>> sets = conn.connectedSets();
			for (Set<Node> set : sets)
			{
				SemanticGraph g = new SemanticGraph(Edge.class);
				set.forEach(g::addVertex);
				set.forEach(v -> graph.outgoingEdgesOf(v).forEach(e -> g.addEdge(v, graph.getEdgeTarget(e), e)));
				graphs.add(g);
			}
		}
		else
			graphs.add(graph);

		return graphs;
	}

	/**
	 *
	 */
	private Node createNode(Annotation a)
	{
		// Create dummy entity for each token using wordform
		Entity e = new Entity(a.getForm());
		return new Node(a.getId(), e, a);
	}
}
