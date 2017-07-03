package edu.upf.taln.textplanning.disambiguation;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.Mention;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import it.uniroma1.lcl.babelnet.BabelNet;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import static edu.upf.taln.textplanning.disambiguation.POSConverter.BN_POS_EN;

/**
 * Annotates semantic graphs with BabelNet synsets.
 */
public class BabelNetAnnotator implements EntityDisambiguator
{
	private final BabelNet bn = BabelNet.getInstance();
	private final static Logger log = LoggerFactory.getLogger(BabelNetAnnotator.class);

	/**
	 * Assigns candidate entities to nodes (tokens) of a given set of structures
	 */
	@Override
	public void annotateCandidates(Set<SemanticGraph> structures)
	{
		// Get mentions from nodes in the structures, and group by label
		Map<String, List<Mention>> label2Mentions = structures.stream()
				.map(s ->
				{
					// Collect tokens in the order in which they appear in the text
					List<Node> tokens = s.vertexSet().stream()
							.sorted(Comparator.comparing(n -> n.getAnnotation().getOffsetStart()))
							.collect(Collectors.toList());

					// Collect sequences of tokens -> mentions
					List<Mention> mentions = collectNominalMentions(s, tokens);
					mentions.addAll(collectOtherMentions(tokens));
					return mentions;
				})
				.flatMap(List::stream)
				// Label formed with the surface form and head POS of mention's head
				.collect(Collectors.groupingBy(m -> m.getSurfaceForm() + "_" + m.getHead().getAnnotation().getPOS()));

		// Collect candidate entities for each label
		Map<String, Set<Entity>> labels2Entities = label2Mentions.keySet().stream()
				.map(l -> Pair.of(l, getEntities(l)))
				.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

		// Assign candidate labels to nodes in the structures
		labels2Entities.keySet().forEach(l -> // given a label l
				labels2Entities.get(l).forEach(e -> // for each candidate entity e of l
						label2Mentions.get(l).forEach(m -> // for each mention with label l
								m.getHead().addCandidate(e, m)))); // assign the pair (m,e) to the head node of m

		reportStats(label2Mentions, labels2Entities);
	}

	/**
	 * Simple approach to coreference resolution for nominal expressions: replicate referred entities from mentions
	 * to others that are substrings of the former.
	 * Inspired by expansion policy of AGDISTIS (Usbeck et al. 2014)
	 */
	@Override
	public void expandCandidates(Set<SemanticGraph> structures)
	{
		// Collect 'maximal' nominal nodes: nodes with at least a candidate and not subsumed by any candidate of its
		// governing nodes.
		// Pair each node with its longest mention
		Set<Triple<SemanticGraph, Node, Mention>> maxNominalNodes = structures.stream()
				// for every structure
				.map(s -> s.vertexSet().stream()
						// for every nominal node with candidates
						.filter(n -> n.getAnnotation().getPOS().startsWith("N"))
						.filter(n -> !n.getCandidates().isEmpty())
						// map to a triple {structure, node, longest_mention}
						.map(n -> Triple.of(s, n, n.getMentions().stream()
								.max(Comparator.comparing(m -> m.getTokens().size())) // longest mention!
								.orElse(null))) // null should never happen as node has candidates & mentions
						// keep top nodes only (not subsumed by any mention of a governing node)
						.filter(t -> s.incomingEdgesOf(t.getMiddle()).stream()
								.map(s::getEdgeSource)
								.map(Node::getMentions)
								.flatMap(Set::stream)
								.noneMatch(m -> m.contains(t.getRight()))))
				// place all triples into a single stream
				.flatMap(Function.identity())
				.collect(Collectors.toSet());

		// Collect nodes the longest mention of which is a substring of some other node in maxNominalNodes
		Set<Triple<SemanticGraph, Node, Mention>> subsumedNodes = maxNominalNodes.stream()
				.map(t ->
				{
					Mention m = t.getRight();

					// Find subsumed maximal nodes: the surface form of their longest mention is a substring of this node's
					// longest mention
					final Set<Triple<SemanticGraph, Node, Mention>> nodes = maxNominalNodes.stream()
							.filter(t2 -> m.contains(t2.getRight()))
							.collect(Collectors.toSet());

					// If two or more subsumed nodes are part of the same structure and one of them contains the others, keep only
					// the top one.
					nodes.stream()
							.filter(sn1 -> nodes.stream()
									.anyMatch(sn2 ->
									{
										Node n1 = sn1.getMiddle();
										SemanticGraph s1 = sn1.getLeft();
										Node n2 = sn2.getMiddle();
										SemanticGraph s2 = sn2.getLeft();

										return (s1 == s2) && s1.getDescendants(s1, n2).contains(n1);
									}))
							.forEach(nodes::remove); // remove

					return nodes;
				})
				.flatMap(Set::stream)
				.collect(Collectors.toSet());

		// For each subsumed node, if there are multiple nodes containing it, choose the longest one and assign its
		// candidates to the subsumed node.
		subsumedNodes
				.forEach(t1 ->
				{
					Node n1 = t1.getMiddle();
					Mention m1 = t1.getRight();
					maxNominalNodes.stream()
							.filter(t2 -> t2.getRight().contains(m1))
							.max(Comparator.comparing(t2 -> t2.getRight().getTokens().size()))
							.ifPresent(t2 -> t2.getMiddle().getCandidates().forEach(e -> n1.addCandidate(e, m1)));
				});

		log.info(subsumedNodes + " coreferent nodes out of " + maxNominalNodes.size() + " maximal nominal nodes");
	}

	/**
	 * Chooses highest ranked entities and collapses into a single node the nodes that make up their mentions.
	 * @param structures input structures where nodes may have multiple candidate senses
	 * @param rankedSenses a ranking of senses
	 */
	@Override
	public void disambiguate(Set<SemanticGraph> structures, Map<String, Double> rankedSenses)
	{
		structures.forEach(s -> {
			// Find out highest ranked candidates and collect the nodes that make up their mentions
			Map<Node, List<Node>> merges = s.vertexSet().stream().collect(Collectors.toMap(n -> n, n ->
			{
				Set<Entity> candidates = n.getCandidates();
				Optional<Entity> entity = candidates.stream()
						.peek(e -> e.setWeight(rankedSenses.getOrDefault(e.getLabel(), 0.0)))
						.max(Comparator.comparing(Entity::getWeight));
				entity.ifPresent(n::setEntity);
				Optional<Mention> mention = entity.map(n::getMention).orElse(Optional.empty());
				return mention.map(Mention::getTokens).orElse(Collections.emptyList());
			})); // nodes in semantic structures have unique ids shared with their Annotation objects

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
							Node source = s.getEdgeSource(e);
							Edge newEdge = new Edge(e.getRole(), e.isArg());
							s.addEdge(source, n, newEdge);
						});
				l.stream()
						.filter(n2 -> n2 != n) // ignore head
						.map(s::outgoingEdgesOf) // get ougoing links
						.flatMap(Set::stream)
						.forEach(e -> {
							// add new edge originating from head
							Node target = s.getEdgeTarget(e);
							Edge newEdge = new Edge(e.getRole(), e.isArg());
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
	private List<Mention> collectNominalMentions(SemanticGraph s, List<Node> tokens)
	{
		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i, 7)
						.mapToObj(j -> tokens.subList(i, i + j))
						.map(l ->
						{
							Optional<Node> h = getNominalHead(l, s);
							return h.map(n -> new Mention(l, l.indexOf(n))).orElse(null);
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
	private List<Mention> collectOtherMentions(List<Node> tokens)
	{
		return tokens.stream()
				.filter(t -> !t.getAnnotation().getPOS().startsWith("N"))
				.map(t -> new Mention(Collections.singletonList(t), 0))
				.collect(Collectors.toList());
	}

	private Optional<Node> getNominalHead(List<Node> s, SemanticGraph g)
	{
		return s.stream()
			.filter(t -> t.getAnnotation().getPOS().startsWith("N"))
			.filter(t -> {
				Set<Node> descendants = g.getDescendants(g, t);
				descendants.add(t);
				return descendants.containsAll(s);
			})
			.findAny();
	}

	private Set<Entity> getEntities(String s)
	{
		// Use surface form of mention as label
		String form = s.substring(0, s.lastIndexOf('_'));
		String pos = s.substring(s.lastIndexOf('_' + 1));
		BabelPOS bnPOS = BN_POS_EN.get(pos);

		// Get candidate entities using strict matching
		try
		{
			List<BabelSynset> senses = bn.getSynsets(form, Language.EN, bnPOS);

			return senses.stream()
					.map(BabelSynset::getId)
					.map(BabelSynsetID::getID)
					.map(Entity::new)
					.collect(Collectors.toSet());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void reportStats(Map<String, List<Mention>> label2Mentions, Map<String, Set<Entity>> labels2Entities)
	{
		long numMentions = label2Mentions.values().stream()
				.mapToLong(List::size)
				.sum();
		long numNominalMentions = label2Mentions.values().stream()
				.flatMap(List::stream)
				.filter(m -> m.getHead().getAnnotation().getPOS().startsWith("N"))
				.count();
//		long numOtherMentions = numMentions - numNominalMentions;
		long numLabels = label2Mentions.keySet().size();
		long numNominalLabels = label2Mentions.keySet().stream()
				.filter(l -> l.endsWith("NN") || l.endsWith("NNS") || l.endsWith("NNP") || l.endsWith("NNPS"))
				.count();
//		long numOtherLabels = numLabels - numNominalLabels;
		long numEntities = labels2Entities.values().stream()
				.mapToLong(Set::size)
				.sum();
		long numNominalEntities = labels2Entities.values().stream()
				.flatMap(Set::stream)
				.filter(e -> e.getLabel().endsWith("n"))
				.count();
//		long numOtherEntities = numEntities - numNominalEntities;
		double avgEntitiesPerLabel = labels2Entities.values().stream()
				.mapToLong(Set::size)
				.average().orElse(0.0);
		double avgEntitiesPerNominalLabel = labels2Entities.keySet().stream()
				.filter(l -> l.endsWith("NN") || l.endsWith("NNS") || l.endsWith("NNP") || l.endsWith("NNPS"))
				.map(labels2Entities::get)
				.mapToLong(Set::size)
				.average().orElse(0.0);
//		double avgEntitiesPerOtherLabel = avgEntitiesPerLabel - avgEntitiesPerNominalLabel;
		double avgMentionsPerLabel = label2Mentions.values().stream()
				.mapToLong(List::size)
				.average().orElse(0.0);
		double avgMentionsPerNominalLabel = label2Mentions.keySet().stream()
				.filter(l -> l.endsWith("NN") || l.endsWith("NNS") || l.endsWith("NNP") || l.endsWith("NNPS"))
				.map(label2Mentions::get)
				.mapToLong(List::size)
				.average().orElse(0.0);
//		double avgMentionsPerOtherLabel = avgMentionsPerLabel - avgMentionsPerNominalLabel;

		// Set up formatting
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(6);
		f.setMinimumFractionDigits(6);

		log.info(numLabels + " labels (" + numNominalLabels+ " nominal) ");
		log.info(numMentions + " mentions (" + numNominalMentions + " nominal) ");
		log.info(numEntities + " entities (" + numNominalEntities + " nominal) ");
		log.info(f.format(avgMentionsPerLabel) + " mentions/label (" + f.format(avgMentionsPerNominalLabel) + " nominal) ");
		log.info(f.format(avgEntitiesPerLabel) + " entities/label (" + f.format(avgEntitiesPerNominalLabel) + " nominal) ");
	}
}
