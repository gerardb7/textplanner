package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.structures.*;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Immutable class, reads and writes annotated structures from/to CoNLL format
 */
public class CoNLLReader implements DocumentReader
{
	private final static Logger log = LoggerFactory.getLogger(CoNLLReader.class);

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

			int position = 0;
			List<LinguisticStructure> graphs = new ArrayList<>();
			LinguisticStructure currentStructure = new LinguisticStructure(d, position,  Role.class);
			List<AnnotatedWord> anns = new ArrayList<>();
			Map<Integer, Map<Integer, String>> governors = new HashMap<>();
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
							// Create graph(s) from previously collected nodes
							Set<LinguisticStructure> new_graphs = createGraphs(d, currentStructure, anns, governors);
							if (new_graphs.size() > 1)
								log.warn("Structure " + position + " has " + new_graphs.size() + " components");
							graphs.addAll(new_graphs);
						}

						// Reset variables
						failedSentence = false;
						currentStructure = new LinguisticStructure(d, ++position, Role.class);
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
						if (features.containsKey(CoNLLConstants.MENTION_LEMMA))
							lemma = features.get(CoNLLConstants.MENTION_LEMMA);
						if (features.containsKey(CoNLLConstants.MENTION_FORM))
							form = features.get(CoNLLConstants.MENTION_FORM);

						List<Integer> govns = Arrays.stream(columns[8].split(",")).map(Integer::parseInt).collect(toList());
						List<String> roles = Arrays.stream(columns[10].split(",")).collect(toList());

						// Perform some checks
						if (govns.isEmpty() || roles.isEmpty())
						{
							log.error("Token in position " + position + " has no governor or role specified, skipping position");
							failedSentence = true;
						}
						if (govns.size() != roles.size())
						{
							log.error("Token in position " + position + " has different number of roles and governors, skipping position");
							failedSentence = true;
						}
						long offsetStart = Long.valueOf(features.getOrDefault(CoNLLConstants.START_STRING, "0"));
						long offsetEnd = Long.valueOf(features.getOrDefault(CoNLLConstants.END_STRING, "0"));

						AnnotatedWord ann = new AnnotatedWord(d, currentStructure, form, lemma, pos, feats, line, offsetStart, offsetEnd);
						anns.add(ann);

						// Set NE_CLASS type
						Type t = Type.Other;
						if (features.containsKey(CoNLLConstants.NE_CLASS))
						{
							t = Type.valueOf(features.get(CoNLLConstants.NE_CLASS));
							ann.setType(t);
						}

						// Set referred entity
						if (features.containsKey(CoNLLConstants.BN_ID))
						{
							String ref = features.get(CoNLLConstants.BN_ID);
							Entity e = new Entity(ref + "_" + form, ref, t);
							Mention m = ann.addMention(singletonList(ann));
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
					log.error("Parsing of position " + position + " failed, skipping position: " + e);
					e.printStackTrace();
					failedSentence = true;
				}
			}

			// Create last position
			LinguisticStructure s = new LinguisticStructure(d, ++position, Role.class);
			Set<LinguisticStructure> new_graphs = createGraphs(d, s, anns, governors);
			if (new_graphs.size() > 1)
				log.warn("Structure " + position + " has " + new_graphs.size() + " components");
			graphs.addAll(new_graphs);

			// Add mentions, entities and candidates to annotated words acting as position nodes
			addMentions(graphs);

			return graphs;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Invalid ConLL: " + e);
		}
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



	private void addMentions(List<LinguisticStructure> structures)
	{
		Map<Pair<Long, Long>, Mention> offsets2Mentions = new HashMap<>();
		Map<Mention, Optional<Pair<Long, Long>>> mentions2Coref = new HashMap<>();

		for (LinguisticStructure structure : structures)
		{
			List<AnnotatedWord> anns = structure.getTextualOrder();
			for (AnnotatedWord word : anns)
			{
				try
				{
					Map<String, String> features = Splitter.on("|").withKeyValueSeparator("=").split(word.getFeats());

					if (features.containsKey(CoNLLConstants.OFFSETS) && !features.get(CoNLLConstants.OFFSETS).isEmpty())
					{
						List<Mention> mentions = Arrays.stream(features.get(CoNLLConstants.SPANS).split(",", -1))
								.map(s -> s.split("-"))
								.map(as -> Pair.of(Integer.valueOf(as[0]), Integer.valueOf(as[1])))
								.map(p -> IntStream.range(p.getLeft(), p.getRight())
										.mapToObj(anns::get)
										.collect(toList()))
								.map(word::addMention)
								.collect(toList());

						List<Pair<Long, Long>> offsets = Arrays.stream(features.get(CoNLLConstants.OFFSETS).split(",", -1))
								.map(s -> s.split("-"))
								.map(as -> Pair.of(Long.valueOf(as[0]), Long.valueOf(as[1])))
								.collect(toList());

						IntStream.range(0, mentions.size()).forEach(i -> offsets2Mentions.put(offsets.get(i), mentions.get(i)));

						if (features.containsKey(CoNLLConstants.REFERENCES) && !features.get(CoNLLConstants.REFERENCES).isEmpty())
						{
							// Create Entity and Candidate objects from references
							List<List<String>> references = Arrays.stream(features.get(CoNLLConstants.REFERENCES).split(",", -1))
									.map(v -> Arrays.asList(v.split("-")))
									.collect(toList());

							List<List<Type>> types = Arrays.stream(features.get(CoNLLConstants.TYPES).split(",", -1))
									.map(v -> Arrays.stream(v.split("-"))
											.map(s -> s.isEmpty() ? "Other" : s)
											.map(Type::valueOf)
											.collect(toList()))
									.collect(toList());

							List<List<Double>> weights = Arrays.stream(features.get(CoNLLConstants.WEIGHTS).split(",", -1))
									.map(v -> Arrays.stream(v.split("-"))
											.map(s -> s.isEmpty() ? "0.00" : s)
											.map(Double::valueOf)
											.collect(toList()))
									.collect(toList());

							IntStream.range(0, references.size())
									.forEach(i -> IntStream.range(0, references.get(i).size())
											.forEach(j -> {
												String r = references.get(i).get(j);
												if (!r.isEmpty())
												{
													Type t = types.get(i).get(j);
													double w = weights.get(i).get(j);
													Entity e = new Entity(word.getForm() + "_" + r, r, t, w);
													word.addCandidate(e, mentions.get(i));
												}
											}));
						}

						if (features.containsKey(CoNLLConstants.COREFERENCES) && !features.get(CoNLLConstants.COREFERENCES).isEmpty())
						{
							String[] elems = features.get(CoNLLConstants.COREFERENCES).split(",", -1);
							List<Optional<Pair<Long, Long>>> coreferences = Arrays.stream(elems)
									.map(v -> v.split("-", -1))
									.map(a -> a.length == 2 ? Pair.of(Long.valueOf(a[0]), Long.valueOf(a[1])) : null)
									.map(Optional::ofNullable)
									.collect(toList());

							IntStream.range(0, coreferences.size())
									.filter(i -> coreferences.get(i).isPresent())
									.forEach(i -> mentions2Coref.put(mentions.get(i), coreferences.get(i)));
						}
					}
				}
				catch (Exception e)
				{
					log.error("Failed to parse word " + word + ": e");
					e.printStackTrace();
					throw(e);
				}
			}
		}

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
				LinguisticStructure g = new LinguisticStructure(d, s.getPosition(), Role.class);
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
}
