package edu.upf.taln.textplanning.input;

import com.google.common.base.Splitter;
import edu.upf.taln.textplanning.structures.*;
import org.apache.commons.lang3.tuple.Pair;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.input.CoNLLConstants.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class CoNLLWriter implements DocumentWriter
{
	@Override
	public String writeStructures(Collection<LinguisticStructure> structures)
	{
		return structures.stream()
				.map(g -> {
					List<AnnotatedWord> nodes = g.getTextualOrder(); // // Get a sorted list of nodes
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
				.map(m ->   {
					int span_start = words.indexOf(m.getTokens().get(0));
					int span_end = words.indexOf(m.getTokens().get(m.getNumTokens() - 1)) + 1;
					return span_start + "-" + span_end;
				})
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
						.map(Candidate.Type::toString)
						.collect(joining("-")))
				.collect(joining(","));
		features.put(TYPES, types);

		// Mention reference weights
		NumberFormat f = NumberFormat.getNumberInstance(Locale.GERMAN); // force European locale to use dot as decimal separator
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
}
