package edu.upf.taln.textplanning.disambiguation;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.*;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import it.uniroma1.lcl.babelnet.BabelNet;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.jlt.util.Language;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.Subgraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.disambiguation.POSConverter.BN_POS_EN;

/**
 * Annotates semantic graphs with BabelNet synsets.
 */
public class BabelNetAnnotator implements EntityDisambiguator
{
	private final BabelNet bn;
	private final DBPediaType dbpedia;
	private final static Logger log = LoggerFactory.getLogger(BabelNetAnnotator.class);
	private static final int maxMentionTokens = 7;

	public BabelNetAnnotator()
	{
		log.info("BabelNetAnnotator: Loading BabelNet instance");
		Stopwatch timer = Stopwatch.createStarted();
		PrintStream oldOut = System.err;
		System.setErr(new PrintStream(new OutputStream() { public void write(int b) {} })); // shut up, BabelNet
		bn = BabelNet.getInstance();
		System.setErr(oldOut);
		log.info("Loading completed in " + timer.stop());

		log.info("BabelNetAnnotator : Setting up DBPedia SPARQL endpoint access");
		timer.reset(); timer.start();
		dbpedia = new DBPediaType();
		log.info("Set up completed in " + timer.stop());
	}

	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures
	 */
	@Override
	public void annotateCandidates(Set<LinguisticStructure> structures)
	{
		// Get mentions from nodes in the structures, and group by label
		Map<String, List<Mention>> label2Mentions = structures.stream()
				.map(s ->
				{
					// Collect tokens in the order in which they appear in the text
					List<AnnotatedWord> tokens = s.vertexSet().stream()
							.sorted(Comparator.comparing(AnnotatedWord::getOffsetStart))
							.collect(Collectors.toList());

					// Collect sequences of tokens -> mentions
					List<Mention> mentions = collectNominalMentions(s, tokens);
					mentions.addAll(collectOtherMentions(tokens));
					return mentions;
				})
				.flatMap(List::stream)
				// Label formed with the surface form and head POS of mention's head
				.collect(Collectors.groupingBy(m -> m.getSurfaceForm() + "_" + m.getHead().getPOS()));

		// Collect candidate entities for each label
		Map<String, Set<Entity>> labels2Entities = new HashMap<>();
		int counter = 0;
		for (String l : label2Mentions.keySet())
		{
			if (++counter % 100 == 0)
				log.info("Retrieved candidates for " + counter + " mention forms out of " + label2Mentions.keySet().size());
			Set<Entity> entities = getEntities(l);
			labels2Entities.put(l, entities);
		}

		// Assign mentions and candidate entities to nodes in the structures
		labels2Entities.keySet().forEach(l -> // given a label l
				labels2Entities.get(l).forEach(e -> // for each candidate entity e of l
						label2Mentions.get(l).forEach(m -> // for each mention with label l
								m.getHead().addCandidate(e, m)))); // assign the pair (m,e) to the head node of m

		reportStats(structures, true);
	}

//	/**
//	 * Simple approach to coreference resolution for nominal expressions: replicate referred entities from mentions
//	 * to others that are substrings of the former.
//	 * Inspired by expansion policy of AGDISTIS (Usbeck et al. 2014)
//	 */
//	@Override
//	public void expandCandidates(Set<LinguisticStructure> structures)
//	{
//		// Collect 'maximal' nominal nodes: nodes with at least a candidate and not subsumed by any candidate of its
//		// governing nodes.
//		// Pair each node with its longest mention
//		Set<Triple<LinguisticStructure, AnnotatedWord, Mention>> maxNominalNodes = structures.stream()
//				// for every structure
//				.map(s -> s.vertexSet().stream()
//						// for every nominal node with candidates
//						.filter(n -> n.getPOS().startsWith("N"))
//						.filter(n -> !n.getCandidates().isEmpty())
//						// map to a triple {structure, node, longest_mention}
//						.map(n -> Triple.of(s, n, n.getMentions().stream()
//								.max(Comparator.comparing(m -> m.getTokens().size())) // longest mention!
//								.orElse(null))) // null should never happen as node has candidates & mentions
//						// keep top nodes only (not subsumed by any mention of a governing node)
//						.filter(t -> s.incomingEdgesOf(t.getMiddle()).stream()
//								.map(s::getEdgeSource)
//								.map(AnnotatedWord::getMentions)
//								.flatMap(Set::stream)
//								.noneMatch(m -> m.contains(t.getRight()))))
//				// place all triples into a single stream
//				.flatMap(Function.identity())
//				.collect(Collectors.toSet());
//
//		// Collect nodes the longest mention of which is a substring of some other node in maxNominalNodes
//		Set<Triple<LinguisticStructure, AnnotatedWord, Mention>> subsumedNodes = maxNominalNodes.stream()
//				.filter(t1 -> t1.getRight().getNumTokens() > 1) // no subsuming is possible if node has just one token
//				.map(t1 ->
//				{
//					Mention m = t1.getRight();
//
//					// Find subsumed maximal nodes: the surface form of their longest mention is a substring of this node's
//					// longest mention
//					final Set<Triple<LinguisticStructure, AnnotatedWord, Mention>> nodes = maxNominalNodes.stream()
//							.filter(t2 -> t1 != t2)
//							.filter(t2 -> m.contains(t2.getRight()))
//							.collect(Collectors.toSet());
//
//					// If a structure contains 2 or more subsumed nodes, and one of them is an ancestor of the others
//					// and contains them, keep only the top containing node.
//					Set<Triple<LinguisticStructure, AnnotatedWord, Mention>> nodesToRemove = nodes.stream()
//							.filter(sn1 -> nodes.stream()
//									.filter(sn2 -> sn1 != sn2)
//									.anyMatch(sn2 ->
//									{
//										AnnotatedWord n1 = sn1.getMiddle();
//										LinguisticStructure s1 = sn1.getLeft();
//										Mention m1 = sn1.getRight();
//										AnnotatedWord n2 = sn2.getMiddle();
//										LinguisticStructure s2 = sn2.getLeft();
//										Mention m2 = sn2.getRight();
//
//										return (s1 == s2) && s1.getDescendants(s1, n2).contains(n1) && m1.contains(m2);
//									}))
//							.collect(Collectors.toSet());
//
//					nodes.removeAll(nodesToRemove);
//
//					return nodes;
//				})
//				.flatMap(Set::stream)
//				.collect(Collectors.toSet());
//
//		// For each subsumed node, if there are multiple nodes containing it, choose the longest one and assign its
//		// candidates to the subsumed node.
//		subsumedNodes
//				.forEach(t1 ->
//				{
//					AnnotatedWord n1 = t1.getMiddle();
//					Mention m1 = t1.getRight();
//					List<Triple<LinguisticStructure, AnnotatedWord, Mention>> subsuming = maxNominalNodes.stream()
//							.filter(t2 -> t2.getRight().contains(m1)).collect(Collectors.toList());
//
//					subsuming.stream()
//							.max(Comparator.comparing(t2 -> t2.getRight().getTokens().size()))
//							.ifPresent(t2 -> t2.getMiddle().getCandidates().forEach(c -> n1.addCandidate(c.getEntity(), c.getMention())));
//				});
//
//		log.info("Assigned senses to " + subsumedNodes.size() + " coreferent nodes ( " + maxNominalNodes.size() + " maximal nominal nodes)");
//		reportStats(structures, false);
//	}

	/**
	 * Chooses highest ranked candidate and collapses the word-nodes spanned by the corresponding mention into a single node.
	 * @param structures input structures where nodes are assigned with mentions and sets of ranked candidate senses
	 */
	@Override
	public void disambiguate(Set<LinguisticStructure> structures)
	{
		// todo check if collapsing node still makes sense (it probably does)
		structures.forEach(s -> {
			// Find out highest ranked candidates and collect the nodes that make up their mentions
			Map<AnnotatedWord, List<AnnotatedWord>> merges = s.vertexSet().stream().collect(Collectors.toMap(n -> n, n ->
			{
				Set<Candidate> candidates = n.getCandidates();
				Optional<Candidate> best = candidates.stream().max(Comparator.comparing(Candidate::getValue));
				return best
						.map(Candidate::getMention)
						.map(Mention::getTokens)
						.orElse(Collections.emptyList());
			})); // nodes in semantic structures have unique ids shared with their WordAnnotation objects

			// discard mentions which are a spanned by another mention within the same structure (but keep overlapping mentions)
			merges.keySet().stream()
					.filter(n1 -> merges.keySet().stream()
							.anyMatch(n2 -> merges.get(n2).containsAll(merges.get(n1))))
					.forEach(merges::remove);

			// collapse all nodes in each mention to its head
			merges.forEach((n, l) -> {
				// Replicate edges from/to nodes in the mention, so that they all go from/to the head
				l.stream()
						.filter(n2 -> n2 != n) // ignore head
						.map(s::incomingEdgesOf) // get incoming links
						.flatMap(Set::stream)
						.forEach(e -> {
							// add new edge pointing to head
							AnnotatedWord source = s.getEdgeSource(e);
							Role newEdge = new Role(e.getRole(), e.isCore());
							s.addEdge(source, n, newEdge);
						});
				l.stream()
						.filter(n2 -> n2 != n) // ignore head
						.map(s::outgoingEdgesOf) // get ougoing links
						.flatMap(Set::stream)
						.forEach(e -> {
							// add new edge originating from head
							AnnotatedWord target = s.getEdgeTarget(e);
							Role newEdge = new Role(e.getRole(), e.isCore());
							s.addEdge(n, target, newEdge);
						});

				// Now remove nodes making up mention, except for head
				l.remove(n);
				s.removeAllVertices(l);
			});
		});
	}

	/**
	 * Valid nominal mentions are sequences of up to 7 tokens with a nominal head
	 * @param s a structure extracted from text
	 * @param tokens list of nodes in the structure sorted by textual order
	 * @return a list of potential mentions to entities together with their heads
	 */
	private List<Mention> collectNominalMentions(LinguisticStructure s, List<AnnotatedWord> tokens)
	{
		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i, Math.min(i + maxMentionTokens + 1, tokens.size()))
						.mapToObj(j -> tokens.subList(i, j + 1))
						.map(l ->
						{
							// May create a new Mention or return an existing one
							Optional<AnnotatedWord> h = getNominalRoot(l, s);
							return h.map(annotatedWord -> annotatedWord.addMention(l)).orElse(null);
						})
						.filter(Objects::nonNull))
				.flatMap(Function.identity())
				.collect(Collectors.toList());
	}

	/**
	 * Valid nominal mentions are sequences of up to 7 tokens with a nominal head
	 * @param tokens list of nodes in a structure sorted by textual order
	 * @return a list of potential mentions to entities together with their heads
	 */
	private List<Mention> collectOtherMentions(List<AnnotatedWord> tokens)
	{
		return tokens.stream()
				.filter(t -> !t.getPOS().startsWith("N"))
				.map(t -> t.addMention(Collections.singletonList(t)))
				.collect(Collectors.toList());
	}

	private Optional<AnnotatedWord> getNominalRoot(List<AnnotatedWord> s, LinguisticStructure g)
	{
		// This code checks if the list of nodes form a connected subgraph of g
		Subgraph<AnnotatedWord, Role, LinguisticStructure> sub = new Subgraph<>(g, new HashSet<>(s));
		DirectedAcyclicGraph<AnnotatedWord, Role> sub2 = new DirectedAcyclicGraph<>(Role.class);
		sub.vertexSet().forEach(sub2::addVertex);
		sub.edgeSet().forEach(e -> sub2.addEdge(sub.getEdgeSource(e), sub.getEdgeTarget(e), e));
		ConnectivityInspector<AnnotatedWord, Role> conn = new ConnectivityInspector<>(sub2);
		if (!conn.isGraphConnected())
			return Optional.empty(); // if not connected, no head

		// this finds out if any of the nodes in s is a nominal root to the raemaining nodes
		return s.stream()
			.filter(t -> t.getPOS().startsWith("N"))
			.filter(t -> {
				Set<AnnotatedWord> descendants = g.getDescendants(g, t);
				descendants.add(t);
				return descendants.containsAll(s);
			})
			.findAny();
	}

	private Set<Entity> getEntities(String s)
	{
		// Use surface form of mention as label
		String form = s.substring(0, s.lastIndexOf('_'));
		String pos = s.substring(s.lastIndexOf('_') + 1);
		BabelPOS bnPOS = BN_POS_EN.get(pos);

		// Get candidate entities using strict matching
		try
		{
			List<BabelSynset> synsets = bn.getSynsets(form, Language.EN, bnPOS);

			return synsets.stream()
					.map(syn -> createEntity(syn, s))
					.collect(Collectors.toSet());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates an Entity object from a BabelNet synset id and its type.
	 * The latter can be either person, location, organization or other for sysnets with links to DBPedia.
	 * For synsets without link to DBPedia the type is always "other".
	 * @param s a BabelNet sysnet
	 * @param surface  the surface string denoting s
	 * @return an isntance of the Entity class
	 */
	private Entity createEntity(BabelSynset s, String surface)
	{
		List<String> dbPediaURIs = s.getDBPediaURIs(Language.EN);
		Type type = Type.Other;
		if (!dbPediaURIs.isEmpty())
		{
			type = dbpedia.getType(dbPediaURIs.get(0));
		}
		String reference = s.getId().getID();
		return new Entity(reference + "_" + surface, reference, type);
	}

	private void reportStats(Set<LinguisticStructure> structures, boolean all)
	{
		Set<AnnotatedWord> nodes = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
		Set<AnnotatedWord> nominalNodes = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.filter(n -> n.getPOS().startsWith("N"))
				.collect(Collectors.toSet());

		long numForms = nodes.stream()
				.map(AnnotatedWord::getMentions)
				.flatMap(Set::stream)
				.map(Mention::getSurfaceForm)
				.distinct()
				.count();
		long numNominalForms = nominalNodes.stream()
				.map(AnnotatedWord::getMentions)
				.flatMap(Set::stream)
				.map(Mention::getSurfaceForm)
				.distinct()
				.count();
		long numMentions = nodes.stream()
				.map(AnnotatedWord::getMentions)
				.mapToLong(Set::size)
				.sum();
		long numNominalMentions = nominalNodes.stream()
				.map(AnnotatedWord::getMentions)
				.mapToLong(Set::size)
				.sum();
		long numCandidates = nodes.stream()
				.map(AnnotatedWord::getCandidates)
				.flatMap(Set::stream)
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.distinct()
				.count();
		long numNominalCandidates = nominalNodes.stream()
				.map(AnnotatedWord::getCandidates)
				.flatMap(Set::stream)
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.distinct()
				.count();
		double candidatesPerNode = nodes.stream()
				.map(AnnotatedWord::getCandidates)
				.mapToLong(Set::size)
				.average()
				.orElse(0.0);
		double candidatesPerNominalNode = nominalNodes.stream()
				.map(AnnotatedWord::getCandidates)
				.mapToLong(Set::size)
				.average()
				.orElse(0.0);
		double candidatesPerMention = nodes.stream()
				.mapToDouble(n -> n.getMentions().stream()
							.map(n::getCandidates)
							.mapToLong(Set::size)
							.average()
							.orElse(0.0))
				.average()
				.orElse(0.0);
		double candidatesPerNominalMention = nominalNodes.stream()
				.mapToDouble(n -> n.getMentions().stream()
						.map(n::getCandidates)
						.mapToLong(Set::size)
						.average()
						.orElse(0.0))
				.average()
				.orElse(0.0);

//		double avgMentionsPerOtherLabel = avgMentionsPerLabel - avgMentionsPerNominalLabel;

		// Set up formatting
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(2);
		f.setMinimumFractionDigits(2);

		if (all)
		{
			log.info(numForms + " forms (" + numNominalForms + " nominal) ");
			log.info(numMentions + " mentions (" + numNominalMentions + " nominal) ");
			log.info(numCandidates + " entities (" + numNominalCandidates + " nominal) ");
		}
		log.info(f.format(candidatesPerNode) + " candidates/node (" + f.format(candidatesPerNominalNode) + " nominal) ");
		log.info(f.format(candidatesPerMention) + " candidates/mention (" + f.format(candidatesPerNominalMention) + " nominal) ");
	}
}
