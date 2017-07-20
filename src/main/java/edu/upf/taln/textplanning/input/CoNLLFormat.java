package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.structures.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Immutable class, reads and writes annotated structures from/to CoNLL format
 */
public class CoNLLFormat implements DocumentAccess
{
	private final static Logger log = LoggerFactory.getLogger(CoNLLFormat.class);

	/**
	 * Reads annotated trees from ConLL file
	 *
	 * @param inDocumentContents String containing conll serialization of trees
	 * @return a list of trees
	 */
	@Override
	public List<LinguisticStructure> readStructures(String inDocumentContents)
	{
		try
		{
			Document d = new Document();
			int numStructures = getNumberOfStructures(inDocumentContents);
			if (numStructures == 0)
				return new ArrayList<>();

			StringReader reader = new StringReader(inDocumentContents);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<LinguisticStructure> graphs = new ArrayList<>();
			LinguisticStructure currentStructure = new LinguisticStructure(d, Role.class);
			List<AnnotatedWord> anns = new ArrayList<>();
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
							graphs.addAll(createGraphs(d, currentStructure, anns, governors));
						}

						// Reset variables
						failedSentence = false;
						currentStructure = new LinguisticStructure(d, Role.class);
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

//						String relationName = features.getOrDefault("fn", null);
//						if (pos.equals("_"))
//						{
//							if (features.containsKey("dpos"))
//							{
//								pos = features.get("dpos");
//							}
//							else if (features.containsKey("spos"))
//							{
//								pos = features.get("spos");
//							}
//						}

						List<Integer> govns = Arrays.stream(columns[8].split(",")).map(Integer::parseInt).collect(toList());
						List<String> roles = Arrays.stream(columns[10].split(",")).collect(toList());

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
						AnnotatedWord ann = new AnnotatedWord(d, currentStructure, form, lemma, pos, feats, role, line, offsetStart, offsetEnd);
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
					.map(r -> createGraphs(d, new LinguisticStructure(d, Role.class), anns, governors))
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
	 * Converts content patterns to ConLL format
	 *
	 * @param patterns list of content patterns
	 * @return ConLL-formatted representation of the patterns
	 */
	public String writePatterns(Collection<ContentPattern> patterns)
	{
		// Get a preorder list of nodes in the tree along with their parents
		return patterns.stream()
				.map(t -> {
					ContentGraph g = (ContentGraph) t.getBase();
					List<Entity> nodes = t.getTopologicalOrder();

					// Collect governor to dependent mappings
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, nodes.size())
							.mapToObj(i -> {
								Entity n = nodes.get(i);
								List<Pair<String, Integer>> govs = new ArrayList<>();
								if (t.inDegreeOf(n) > 0) // has parent
								{
									Role r = t.incomingEdgesOf(n).iterator().next();
									Entity gov = t.getEdgeSource(r);
									int govIndex = nodes.indexOf(gov);
									govs.add(Pair.of(r.getRole(), govIndex));
								}

								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

					List<AnnotatedWord> words = nodes.stream()
							.map(g::getAnchors)
							.map(l -> l.get(0))
							.collect(toList());
					return nodesToConll(nodes, words, governors);
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

	private String nodesToConll(List<Entity> entities, List<AnnotatedWord> anchors, Map<Integer, List<Pair<String, Integer>>> governors)
	{
		// Iterate nodes again, this time producing conll
		StringBuilder conll = new StringBuilder();
		for (int id = 0; id < entities.size(); ++id)
		{
			AnnotatedWord anchor = anchors.get(id);
			List<String> govIDs = governors.get(id).stream()
					.filter(p -> p.getRight() != null)
					.map(p -> p.getRight() + 1)
					.map(Object::toString)
					.collect(toList());
			String govsString = govIDs.isEmpty() ? "0" : String.join(",", govIDs);
			List<String> roles = governors.get(id).stream()
					.map(Pair::getLeft)
					.collect(toList());
			String roless = roles.isEmpty() ? "ROOT" : String.join(",", roles);

			String feats = anchor.getFeats();
			Map<String, String> features = new HashMap<>(Splitter.on("|").withKeyValueSeparator("=").split(feats));

			Entity entity = entities.get(id);
			if (entity.getId().startsWith("bn:"))
			{
				features.merge("bnId", entity.getId(), (v1, v2) -> v2);
			}

			feats = features.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.reduce((e1, e2) -> e1 + "|" + e2).orElse("");

			String entityConll = id + 1 + "\t" +
					anchor.getLemma() + "\t" +
					anchor.getForm() + "\t" +
					"_\t" +
					anchor.getPOS() + "\t" +
					"_\t" +
					feats + "\t" +
					"_\t" +
					govsString + "\t" +
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

	private Set<LinguisticStructure> createGraphs(Document d, LinguisticStructure s, List<AnnotatedWord> anns,
	                                              Map<Integer, List<Integer>> inGovernors)
	{
		// Create graph and add vertices
		anns.forEach(s::addVertex);

		for (int i : inGovernors.keySet())
		{
			AnnotatedWord gov = anns.get(i);
			for (int j : inGovernors.get(i))
			{
				AnnotatedWord dep = anns.get(j);
				Role e = new Role(dep.getRole(), dep.isArgument());
				s.addEdge(gov, dep, e);
			}
		}

		// If resulting graph isn't connected (i.e. multiple roots), split into maximally connected subgraphs
		Set<LinguisticStructure> structures = new HashSet<>();
		ConnectivityInspector<AnnotatedWord, Role> conn = new ConnectivityInspector<>(s);
		if (!conn.isGraphConnected())
		{
			List<Set<AnnotatedWord>> sets = conn.connectedSets();
			for (Set<AnnotatedWord> set : sets)
			{
				LinguisticStructure g = new LinguisticStructure(d, Role.class);
				set.forEach(g::addVertex);
				set.forEach(a -> a.setStructure(s)); // reasign nodes to new structure!
				set.forEach(v -> s.outgoingEdgesOf(v).forEach(e -> g.addEdge(v, s.getEdgeTarget(e), e)));
				structures.add(g);
			}
		}
		else
			structures.add(s);

		return structures;
	}
}
