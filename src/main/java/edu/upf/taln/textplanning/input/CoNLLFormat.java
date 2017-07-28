package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.structures.*;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Immutable class, reads and writes annotated structures from/to CoNLL format
 */
public class CoNLLFormat implements DocumentAccess
{
	private final static Logger log = LoggerFactory.getLogger(CoNLLFormat.class);
	private static final String BN_ID = "bnId";
	private static final String NE_CLASS = "ne_class";
	private static final String ROOT = "ROOT";
	private static final String MENTION_FORM = "original_slex";
	private static final String MENTION_LEMMA = "word";
	private static final String START_STRING = "start_string";
	private static final String END_STRING = "end_string";
	private static final String OFFSETS = "offsets";
	private static final String SPANS = "spans";
	private static final String REFERENCES = "references";
	private static final String COREFERENCES = "coreferences";
	private static final String TYPES = "types";
	private static final String WEIGHTS = "weights";

	/**
	 * Reads annotated trees from ConLL file
	 *
	 * @param conll String containing conll serialization of trees
	 * @return a list of trees
	 */
	@Override
	public List<LinguisticStructure> readStructures(String conll)
	{
		try
		{
			Document d = new Document();
			int numStructures = getNumberOfStructures(conll);
			if (numStructures == 0)
				return new ArrayList<>();

			StringReader reader = new StringReader(conll);
			BufferedReader bufferReader = new BufferedReader(reader);

			List<LinguisticStructure> graphs = new ArrayList<>();
			LinguisticStructure currentStructure = new LinguisticStructure(d, Role.class);
			List<AnnotatedWord> anns = new ArrayList<>();
			Map<Integer, Map<Integer, String>> governors = new HashMap<>();
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
							// Add mentions, entities and candidates to words
							addMentions(currentStructure, anns);
							// Create graph(s) from previously collected nodes
							Set<LinguisticStructure> new_graphs = createGraphs(d, currentStructure, anns, governors);
							if (new_graphs.size() > 1)
								log.warn("Structure " + structure + " has " + new_graphs.size() + " components");
							graphs.addAll(new_graphs);
						}

						// Reset variables
						failedSentence = false;
						currentStructure = new LinguisticStructure(d, Role.class);
						++structure;
						anns.clear();
						governors.clear();
					}

					if (id >= 1 && !failedSentence)
					{
						String form = columns[1];
						String lemma = columns[2];
						String pos = columns[4];
						String feats = columns[6];
						Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(feats);
						if (features.containsKey(MENTION_LEMMA))
							lemma = features.get(MENTION_LEMMA);
						if (features.containsKey(MENTION_FORM))
							form = features.get(MENTION_FORM);

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
						long offsetStart = Long.valueOf(features.getOrDefault(START_STRING, "0"));
						long offsetEnd = Long.valueOf(features.getOrDefault(END_STRING, "0"));

						AnnotatedWord ann = new AnnotatedWord(d, currentStructure, form, lemma, pos, feats, line, offsetStart, offsetEnd);
						anns.add(ann);

						// Set NE_CLASS type
						Type t = Type.Other;
						if (features.containsKey(NE_CLASS))
						{
							t = Type.valueOf(features.get(NE_CLASS));
							ann.setType(t);
						}

						// Set referred entity
						if (features.containsKey(BN_ID))
						{
							String ref = features.get(BN_ID);
							Entity e = new Entity(ref + "_" + form, ref, t);
							Mention m = new Mention(currentStructure, singletonList(ann), 0);
							ann.addCandidate(e, m);
						}

						for (int i = 0; i < govns.size(); ++i)
						{
							int gov = govns.get(i);
							String role = roles.get(i);

							if (gov > 0)
								governors.merge(gov -1, Collections.singletonMap(id-1, role), (m1, m2) -> {
									// awful, awful code :-(
									Map<Integer, String> m = new HashMap<>();
									m.putAll(m1);
									m.putAll(m2); // overwrites duplicate dependents
									return m;
								});
						}
					}
				}
				catch (Exception e)
				{
					log.error("Parsing of structure " + structure + " failed, skipping structure: " + e);
					failedSentence = true;
				}
			}

			// Create last structure
			LinguisticStructure s = new LinguisticStructure(d, Role.class);
			addMentions(s, anns);
			Set<LinguisticStructure> new_graphs = createGraphs(d, s, anns, governors);
			if (new_graphs.size() > 1)
				log.warn("Structure " + structure + " has " + new_graphs.size() + " components");
			graphs.addAll(new_graphs);

			return graphs;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Invalid ConLL: " + e);
		}
	}

	public String writeStructures(Collection<LinguisticStructure> structures)
	{
		// Get a topological ordering of nodes along with their governors in the graph
		return structures.stream()
				.map(g -> {
					List<AnnotatedWord> nodes = g.getTopologicalOrder();
					Map<Integer, List<Pair<String, Integer>>> governors = IntStream.range(0, nodes.size())
							.mapToObj(i -> {
								final List<Pair<String, Integer>> govs = g.incomingEdgesOf(nodes.get(i)).stream()
										.map(e -> Pair.of(e.getRole(), nodes.indexOf(g.getEdgeSource(e)))) // source node must be unique!
										.collect(Collectors.toList());
								return Pair.of(i, govs);
							})
							.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));


					return wordsToConll(nodes, governors);
				})
				.map(s -> "0\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\t_\tslex=Sentence\n" + s + "\n")
				.reduce(String::concat).orElse("");
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

					List<Mention> anchors = nodes.stream()
							.map(g::getAnchors)
							.map(l -> l.get(0))
							.collect(toList());
					return patternNodesToConll(nodes, anchors, governors);
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

	private String wordsToConll(List<AnnotatedWord> words, Map<Integer, List<Pair<String, Integer>>> governors)
	{
		// Iterate nodes again, this time producing conll
		StringBuilder conll = new StringBuilder();
		for (int id = 0; id < words.size(); ++id)
		{
			AnnotatedWord word = words.get(id);

			List<String> govIDs = governors.get(id).stream()
					.filter(p -> p.getRight() != null)
					.map(p -> p.getRight() + 1)
					.map(Object::toString)
					.collect(toList());
			String govsString = govIDs.isEmpty() ? "0" : String.join(",", govIDs);
			List<String> roles = governors.get(id).stream()
					.map(Pair::getLeft)
					.collect(toList());
			String roless = roles.isEmpty() ? ROOT : String.join(",", roles);

			String feats = generateFeatures(word, words);

			String entityConll = id + 1 + "\t" +
					word.getLemma() + "\t" +
					word.getForm() + "\t" +
					"_\t" +
					word.getPOS() + "\t" +
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

	private String generateFeatures(AnnotatedWord word, List<AnnotatedWord> words)
	{
		// Let's generate the feats line
		String feats = word.getFeats();
		Map<String, String> features = new HashMap<>(Splitter.on("|").withKeyValueSeparator("=").split(feats));

		// Add token NE_CLASS type
		features.merge(NE_CLASS, word.getType().toString(), (v1, v2) -> v2);

		// Mention offsets
		String mentions = word.getMentions().stream()
				.map(Mention::getOffsets)
				.map(p -> p.getLeft() + "-" + p.getRight())
				.collect(joining(","));
		features.put(OFFSETS, mentions);

		// Mention spans
		String spans = word.getMentions().stream()
				.map(m ->   words.indexOf(m.getTokens().get(0)) + "-" +
						words.indexOf(m.getTokens().get(m.getNumTokens()-1)))
				.collect(joining(","));
		features.put(SPANS, spans);

		// Mention references
		String references = word.getMentions().stream()
				.map(m -> word.getCandidates(m).stream()
						.map(Candidate::getEntity)
						.map(Entity::getReference)
						.collect(joining("-")))
				.collect(joining(","));
		features.put(REFERENCES, references);

		// Mention reference types
		String types = word.getMentions().stream()
				.map(m -> word.getCandidates(m).stream()
						.map(Candidate::getEntity)
						.map(Entity::getType)
						.map(Type::toString)
						.collect(joining("-")))
				.collect(joining(","));
		features.put(TYPES, types);

		// Mention reference weights
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(2);
		f.setMinimumFractionDigits(2);
		String weights = word.getMentions().stream()
				.map(m -> word.getCandidates(m).stream()
						.map(Candidate::getEntity)
						.map(Entity::getWeight)
						.map(f::format)
						.collect(joining("-")))
				.collect(joining(","));
		features.put(WEIGHTS, weights);

		// Mention coreferences
		String coreferences = word.getMentions().stream()
				.map(m -> m.getCoref()
						.map(Mention::getOffsets)
						.map(p -> p.getLeft() + "-" + p.getRight())
						.orElse(""))
				.collect(joining(","));
		features.put(COREFERENCES, coreferences);

		// Serialize features
		return features.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.reduce((e1, e2) -> e1 + "|" + e2).orElse("");
	}

	private String patternNodesToConll(List<Entity> entities, List<Mention> anchors, Map<Integer, List<Pair<String, Integer>>> governors)
	{
		// Iterate nodes again, this time producing conll
		StringBuilder conll = new StringBuilder();
		for (int id = 0; id < anchors.size(); ++id)
		{
			Mention anchor = anchors.get(id);
			AnnotatedWord head = anchor.getHead(); // use
			String surface_form = anchor.getSurfaceForm();

			List<String> govIDs = governors.get(id).stream()
					.filter(p -> p.getRight() != null)
					.map(p -> p.getRight() + 1)
					.map(Object::toString)
					.collect(toList());
			String govsString = govIDs.isEmpty() ? "0" : String.join(",", govIDs);
			List<String> roles = governors.get(id).stream()
					.map(Pair::getLeft)
					.collect(toList());
			String roless = roles.isEmpty() ? ROOT : String.join(",", roles);

			String feats = head.getFeats();
			Map<String, String> features = new HashMap<>(Splitter.on("|").withKeyValueSeparator("=").split(feats));
			features.merge(MENTION_FORM, surface_form, (v1, v2) -> v2);

			// Add semantic info to feats
			if (!entities.isEmpty())
			{
				Entity entity = entities.get(id);
				if (entity.getId().startsWith("bn:"))
				{
					features.merge(BN_ID, entity.getId(), (v1, v2) -> v2);
				}

				features.merge(NE_CLASS, entity.getType().toString(), (v1, v2) -> v2);
			}

			feats = features.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.reduce((e1, e2) -> e1 + "|" + e2).orElse("");

			String entityConll = id + 1 + "\t" +
					head.getLemma() + "\t" +
					head.getForm() + "\t" +
					"_\t" +
					head.getPOS() + "\t" +
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

	private void addMentions(LinguisticStructure structure, List<AnnotatedWord> anns)
	{
		Map<Pair<Long, Long>, Mention> offsets2Mentions = new HashMap<>();
		Map<Mention, Optional<Pair<Long, Long>>> mentions2Coref = new HashMap<>();

		anns.forEach(word ->
		{
			Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(word.getFeats());

			if (features.containsKey(OFFSETS))
			{
				List<Mention> mentions = Arrays.stream(features.get(SPANS).split(","))
						.map(s -> s.split("-"))
						.map(as -> Pair.of(Integer.valueOf(as[0]), Integer.valueOf(as[1])))
						.map(p -> IntStream.range(p.getLeft(), p.getRight())
								.mapToObj(anns::get)
								.collect(toList()))
						.map(tokens -> new Mention(structure, tokens, tokens.indexOf(word)))
						.collect(toList());

				List<Pair<Long, Long>> offsets = Arrays.stream(features.get(OFFSETS).split(","))
						.map(s -> s.split("-"))
						.map(as -> Pair.of(Long.valueOf(as[0]), Long.valueOf(as[1])))
						.collect(toList());

				IntStream.range(0, mentions.size()).forEach(i -> offsets2Mentions.put(offsets.get(i), mentions.get(i)));

				if (features.containsKey(REFERENCES))
				{
					// Create Entity objects from references
					List<List<String>> references = Arrays.stream(features.get(REFERENCES).split(","))
							.map(v -> Arrays.asList(v.split("-")))
							.collect(toList());

					// Create Entity objects from references
					List<List<Type>> types = Arrays.stream(features.get(TYPES).split(","))
							.map(v -> Arrays.stream(v.split("-"))
									.map(Type::valueOf)
									.collect(toList()))
							.collect(toList());

					// Create Entity objects from references
					List<List<Double>> weights = Arrays.stream(features.get(WEIGHTS).split(","))
							.map(v -> Arrays.stream(v.split("-"))
									.map(Double::valueOf)
									.collect(toList()))
							.collect(toList());

					IntStream.range(0, references.size())
							.forEach(i -> IntStream.range(0, references.get(i).size())
									.forEach(j -> {
										String r = references.get(i).get(j);
										Type t = types.get(i).get(j);
										double w = weights.get(i).get(j);
										Entity e = new Entity(word.getForm() + "_" + r, r, t, w);
										word.addCandidate(e, mentions.get(i));
									}));
				}

				if (features.containsKey(COREFERENCES))
				{
					List<Optional<Pair<Long, Long>>> coreferences = Arrays.stream(features.get(COREFERENCES).split(","))
							.map(v -> v.split("-"))
							.map(a -> a.length == 2 ? Pair.of(Long.valueOf(a[0]), Long.valueOf(a[1])) : null)
							.map(Optional::ofNullable)
							.collect(toList());

					IntStream.range(0, mentions.size()).forEach(i -> mentions2Coref.put(mentions.get(i), coreferences.get(i)));
				}
			}
		});

		// Set coreference links between pairs of mentions
		mentions2Coref.keySet().forEach(m -> mentions2Coref.get(m).map(offsets2Mentions::get).ifPresent(m::setCoref));
	}

	private Set<LinguisticStructure> createGraphs(Document d, LinguisticStructure s, List<AnnotatedWord> anns,
	                                              Map<Integer, Map<Integer, String>> governors)
	{
		// Create graph and add vertices
		anns.forEach(s::addVertex);

		for (int i : governors.keySet())
		{
			AnnotatedWord gov = anns.get(i);
			for (Map.Entry<Integer, String> dep : governors.get(i).entrySet())
			{
				AnnotatedWord dep_word = anns.get(dep.getKey());
				String role = dep.getValue();
				Role e = new Role(role, isArgument(role));
				s.addEdge(gov, dep_word, e);
			}
		}

		// If resulting graph isn't connected, split into maximally connected subgraphs
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

	private static boolean isArgument(String role)
	{
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}

	// For testing purposes
	public static void main(String[] args) throws IOException
	{
		String in_conll = FileUtils.readFileToString(new File(args[0]), StandardCharsets.UTF_8);
		CoNLLFormat format = new CoNLLFormat();
		List<LinguisticStructure> structures = format.readStructures(in_conll);
		String out_conll = format.writeStructures(structures);
		System.out.println(out_conll);
	}
}
