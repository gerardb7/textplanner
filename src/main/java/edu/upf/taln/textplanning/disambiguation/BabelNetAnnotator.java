package edu.upf.taln.textplanning.disambiguation;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.structures.*;
import edu.upf.taln.textplanning.structures.Candidate.Type;
import it.uniroma1.lcl.babelnet.BabelNet;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.collections4.SetUtils;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.disambiguation.POSConverter.BN_POS_EN;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.*;

/**
 * Annotates semantic graphs with BabelNet synsets.
 */
public class BabelNetAnnotator implements EntityDisambiguator
{
	private final BabelNet bn;
	private final DBPediaType dbpedia;
	private static final int maxMentionTokens = 7;
	private final static Logger log = LoggerFactory.getLogger(BabelNetAnnotator.class);

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
	public void annotateCandidates(Collection<LinguisticStructure> structures)
	{
		// Get mentions from nodes in the structures, and group by label
		log.info("Collecting mentions");
		Map<String, List<Mention>> label2Mentions = structures.stream()
				.map(s ->
				{
					// Collect tokens in the order in which they appear in the text
					List<AnnotatedWord> tokens = s.vertexSet().stream()
							.sorted(Comparator.comparing(AnnotatedWord::getOffsetStart))
							.collect(toList());

					// Collect sequences of tokens -> mentions
					List<Mention> mentions = collectNominalMentions(s, tokens);
					mentions.addAll(collectOtherMentions(tokens));
					return mentions;
				})
				.flatMap(List::stream)
				// Label formed with the surface form and head POS of mention's head
				.collect(Collectors.groupingBy(m -> m.getSurfaceForm() + "_" + m.getHead().getPOS()));

		// Collect candidate entities for each label
		log.info("Finding candidate BabelNet synsets");
		AtomicInteger counter = new AtomicInteger(0);
		Stopwatch timer = Stopwatch.createStarted();

		Map<String, List<BabelSynset>> labels2Synsets = label2Mentions.keySet().stream()
				.peek(t -> {
					if (counter.incrementAndGet() % 1000 == 0)
						log.info("Queried synsets for " + counter.get() + " labels out of " + label2Mentions.keySet().size());
				})
				.collect(toMap(l -> l, this::getSynsets));
		log.info("Synsets retrieved in " + timer.stop());

		// Get DBPedia types an instantiate Entity objects
		log.info("Querying DBPedia types");
		counter.set(0);
		timer.reset(); timer.start();

		Set<BabelSynset> all_synsets = labels2Synsets.values().stream()
				.flatMap(List::stream)
				.distinct()
				.collect(Collectors.toSet());
		Map<BabelSynset, Type> synsets2Types = all_synsets.stream()
				.peek(t -> {
					if (counter.incrementAndGet() % 1000 == 0)
						log.info("Queried types for " + counter.get() + " synsets forms out of " + all_synsets.size());
				})
				.collect(toMap(s -> s, this::getEntityType));
		log.info("Types queried in " + timer.stop());

		// Instantiate Entity objects from info gathered so far
		Map<String, Set<Entity>> labels2Entities = labels2Synsets.keySet().stream()
				.collect(toMap(l -> l, l -> labels2Synsets.get(l).stream()
						.map(s -> createEntity(s, synsets2Types.get(s)))
						.collect(toSet())));

		// Assign mentions and candidate entities to nodes in the structures
		labels2Entities.keySet().forEach(l -> // given a label l
				labels2Entities.get(l).forEach(e -> // for each candidate entity e of l
						label2Mentions.get(l).forEach(m -> // for each mention with label l
								m.getHead().addCandidate(e, m)))); // assign the pair (m,e) to the head node of m

		// For each entity and head word, keep only candidate associated with longest mention
		discardSubsumedCandidates(structures);
		// Take into account coreference chains
		propagateCandidatesCoreference(structures);
		if (checkForUnreferencedCandidates(structures))
			log.error("Unreferenced candidates found");

		reportStats(structures, true);
	}

	// Removes all candidates which are associated to an entity and head word for which a longer subsuming mention exists
	public static void discardSubsumedCandidates(Collection<LinguisticStructure> structures)
	{
		structures.forEach(s ->
				s.vertexSet().forEach(w -> {
					// Group canidadates by entity
					Map<Entity, List<Candidate>> entitites2Candidates = w.getCandidates().stream()
							.collect(groupingBy(Candidate::getEntity));

					// Find longest candidate for each entity
					Set<Candidate> subsumingCandidates = entitites2Candidates.values().stream()
							.map(l -> l.stream()
									.max(comparingInt(c -> c.getMention().getNumTokens())))
							.filter(Optional::isPresent)
							.map(Optional::get)
							.collect(toSet());

					// Remove subsumed candidates (Note: this is made easier by the fact that all mentions are made up of contiguous tokens)
					SetUtils.SetView<Candidate> toRemove = SetUtils.difference(w.getCandidates(), subsumingCandidates);
					toRemove.forEach(w::removeCandidate);
				}));
	}

	/**
	 * For each mention mark as coreferring with an antecedent mention (representative of the chain), replace its
	 * candidate entities with the candidates of the antecedent.
	 */
	public static void propagateCandidatesCoreference(Collection<LinguisticStructure> structures)
	{
		structures.forEach(s ->
				s.vertexSet().forEach(w -> {
					w.getMentions().stream()
							.filter(m -> m.getCoref().isPresent())
							.forEach(m -> {
								// Remove candidates for mention
								AnnotatedWord head = m.getHead();
								head.getCandidates(m).forEach(head::removeCandidate);

								// Replace with candidates of antecedent
								@SuppressWarnings("ConstantConditions") Mention antecedent = m.getCoref().get();
								antecedent.getHead().getCandidates(antecedent).stream()
										.map(Candidate::getEntity)
										.forEach(e -> head.addCandidate(e, m));
							});
				}));

	}

	// After annotation all candidates should point to an Entity object with a non-null non-empty reference string
	public static boolean checkForUnreferencedCandidates(Collection<LinguisticStructure> structures)
	{
		return structures.stream()
				.anyMatch(s -> s.vertexSet().stream()
						.anyMatch(w ->
								w.getCandidates().stream()
										.map(Candidate::getEntity)
										.map(Entity::getReference)
										.peek(r -> { if (r.isEmpty()) log.debug("Word " + w + " has unreferenced candidates"); })
										.anyMatch(String::isEmpty)));
	}

	/**
	 * Chooses highest ranked candidate and collapses the word-nodes spanned by the corresponding mention into a single node.
	 * @param structures input structures where nodes are assigned with mentions and sets of ranked candidate senses
	 */
	@Override
	public void disambiguate(Collection<LinguisticStructure> structures)
	{
		// todo check if collapsing node still makes sense (it probably does)
		structures.forEach(s -> {
			// Find out highest ranked candidates and collect the nodes that make up their mentions
			Map<AnnotatedWord, List<AnnotatedWord>> merges = s.vertexSet().stream().collect(toMap(n -> n, n ->
			{
				Set<Candidate> candidates = n.getCandidates();
				Optional<Candidate> best = candidates.stream().max(Comparator.comparing(Candidate::getValue));
				return best
						.map(Candidate::getMention)
						.map(Mention::getTokens)
						.orElse(Collections.emptyList());
			})); // nodes in semantic structures have unique ids shared with their WordAnnotation objects

			// discard mentions which are a spanned by another mention within the same structure (but keep overlapping mentions)
			Set<AnnotatedWord> subsumed = merges.keySet().stream()
					.filter(n1 -> merges.keySet().stream()
							.anyMatch(n2 -> merges.get(n2).containsAll(merges.get(n1))))
					.collect(toSet());
			subsumed.forEach(merges::remove);

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
	 * Valid nominal mentions are sequences of up to 7 *contiguous* tokens with a nominal head
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
							return h.map(w -> w.addMention(l)).orElse(null);
						})
						.filter(Objects::nonNull))
				.flatMap(Function.identity())
				.collect(toList());
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
				.collect(toList());
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

		// this finds out if any of the nodes in s is a nominal root to the remaining nodes
		return s.stream()
			.filter(t -> t.getPOS().startsWith("N"))
			.filter(t -> {
				Set<AnnotatedWord> descendants = g.getDescendants(g, t);
				descendants.add(t);
				return descendants.containsAll(s);
			})
			.findAny();
	}

	private List<BabelSynset> getSynsets(String s)
	{
		// Use surface form of mention as label
		String form = s.substring(0, s.lastIndexOf('_'));
		String pos = s.substring(s.lastIndexOf('_') + 1);
		BabelPOS bnPOS = BN_POS_EN.get(pos);

		// Get candidate entities using strict matching
		try
		{
			return bn.getSynsets(form, Language.EN, bnPOS);
		}
		catch (IOException e)
		{
			log.error("Cannot get synsets for form " + s + ": " + e);
			return Collections.emptyList();
		}
	}

	/**
	 * Creates an Entity object from a BabelNet synset id and its type.
	 * The latter can be either person, location, organization or other for sysnets with links to DBPedia.
	 * For synsets without link to DBPedia the type is always "other".
	 * @param s a BabelNet sysnet
	 * @return an isntance of the Entity class
	 */
	private Type getEntityType(BabelSynset s)
	{
		List<String> dbPediaURIs = s.getDBPediaURIs(Language.EN);
		Type type = Type.Other;
		if (!dbPediaURIs.isEmpty())
		{
			type = dbpedia.getType(dbPediaURIs.get(0));
		}

		return type;
	}

	private Entity createEntity(BabelSynset s, Type t)
	{
		String reference = s.getId().getID();
		return Entity.get(reference, reference, t);
	}


	private void reportStats(Collection<LinguisticStructure> structures, boolean all)
	{
		Set<AnnotatedWord> nodes = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.collect(toSet());
		Set<AnnotatedWord> nominalNodes = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.filter(n -> n.getPOS().startsWith("N"))
				.collect(toSet());

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
