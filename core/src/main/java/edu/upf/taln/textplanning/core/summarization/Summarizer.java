package edu.upf.taln.textplanning.core.summarization;

import edu.upf.taln.textplanning.core.corpus.Corpora.Sentence;
import edu.upf.taln.textplanning.core.corpus.Corpora.Text;
import edu.upf.taln.textplanning.core.corpus.Corpora.Token;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class Summarizer
{
	public enum ItemType {Sentence, Word}
	public enum WeightCriterion { Leading, Maximum, Average }
	private final static Logger log = LogManager.getLogger();

	/**
	 * BoM -> Bag of Meanings
	 */
	public static String getBoMSummary(Text text, long num_words)
	{
		final List<Candidate> disambiguated_candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.disambiguated_meanings.values().stream())
				.sorted(Comparator.<Candidate>comparingDouble(c -> c.getWeight().orElse(0.0)).reversed())
				.collect(toList());

		final Map<Meaning, List<Candidate>> meanings2candidates = disambiguated_candidates.stream()
				.collect(groupingBy(Candidate::getMeaning));
		final Map<Meaning, Double> meanings2weights = meanings2candidates.keySet().stream()
				.collect(toMap(m -> m, m -> meanings2candidates.get(m).stream()
						.map(Candidate::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(d -> d)
						.average().orElse(0.0)));
		final List<Meaning> sorted_meanings = meanings2weights.keySet().stream()
				.sorted(Comparator.<Meaning>comparingDouble(meanings2weights::get).reversed())
				.collect(toList());

		// BoM summary
		AtomicInteger accum_words = new AtomicInteger();
		return sorted_meanings.stream()
				.map(Meaning::getLabel)
				.takeWhile(l -> accum_words.addAndGet(l.split(" ").length) < num_words)
				.collect(joining(" "));
	}

	/**
	 * BoW -> Bag of Words
	 */
	public static String getBoWSummary(Text text, long num_words)
	{
		// BoW summary
		final List<Mention> sorted_single_words= text.sentences.stream()
				.flatMap(sentence -> sentence.ranked_words.stream())
				.filter(c -> c.getWeight().isPresent())
				.sorted(Comparator.<Mention>comparingDouble(m -> m.getWeight().get()).reversed())
				.collect(toList());

		AtomicInteger accum_words = new AtomicInteger();
		return sorted_single_words.stream()
				.map(Mention::getSurfaceForm)
				.distinct() // no two identical lists of tokens
				.takeWhile(l -> accum_words.addAndGet(l.split(" ").length) < num_words)
				.collect(joining(" "));
	}

	public static String getExtractiveSummary(Text text, ItemType item_type, int num_items, WeightCriterion criterion,
	                                          Function<Token, Optional<Double>> weight_function, double default_weight)
	{
		// 1- Sort sentences
		final List<Sentence> sorted_sentences;
		switch (criterion)
		{
			case Maximum:
				sorted_sentences = sortSentencesByMaxWeight(text, weight_function);
				break;
			case Average:
				sorted_sentences = sortSentencesByAverageWeight(text, weight_function, default_weight);
				break;
			case Leading:
			default:
				sorted_sentences = text.sentences; // textual order
				break;
		}

		// 2- Compose summary
		return composeSummary(sorted_sentences, item_type, num_items);
	}

	public static String getExtractivePlanSummary(Text text, long num_words)
	{
		final Map<SemanticSubgraph, List<Mention>> subgraphs2mentions = text.subgraphs.stream()
				.collect(toMap(subgraph -> subgraph, subgraph -> subgraph.vertexSet().stream()
						.map(v -> subgraph.getBase().getMentions(v))
						.flatMap(Collection::stream)
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.collect(toList())));

		final Map<SemanticSubgraph, String> subgraphs2weightlists = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(m -> m.getWeight().isPresent())
						.map(m -> m.getSurfaceForm() + " " + DebugUtils.printDouble(m.getWeight().get()))
						.collect(joining(", "))));

		final Map<SemanticSubgraph, Double> subgraphs2weights = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.map(Mention::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(w -> w)
						.average().orElse(0.0)));

		final List<SemanticSubgraph> sorted_subgraphs = text.subgraphs.stream()
				.sorted(Comparator.<SemanticSubgraph>comparingDouble(subgraphs2weights::get).reversed())
				.collect(toList());

		final Map<SemanticSubgraph, Pair<Integer, Integer>> subgraphs2spans = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e ->
				{
					final IntSummaryStatistics stats = e.getValue().stream()
							.map(Mention::getSpan)
							.flatMap(span -> Stream.of(span.getLeft(), span.getRight()))
							.mapToInt(i -> i)
							.summaryStatistics();
					return Pair.of(stats.getMin(), stats.getMax());
				}));

		final List<Token> tokens = text.sentences.stream()
				.flatMap(s -> s.tokens.stream())
				.collect(toList());

		final AtomicInteger counter = new AtomicInteger();
		final List<String> summary = sorted_subgraphs.stream()
				.map(subgraphs2spans::get)
				.map(span -> IntStream.range(span.getLeft(), span.getRight())
						.mapToObj(tokens::get)
						.map(t -> t.wf)
						.collect(toList()))
				.takeWhile(l -> counter.getAndAdd(l.size()) < num_words)
				.map(l -> String.join(" ", l))
				.collect(toList());

		final String debug = IntStream.range(0, summary.size())
				.mapToObj(i -> summary.get(i) + ".\t" + DebugUtils.printDouble(subgraphs2weights.get(sorted_subgraphs.get(i))) + "\t-\t" + subgraphs2weightlists.get((sorted_subgraphs.get(i))))
				.collect(joining("\n\t", "\n\t", "\n"));
		log.debug("Extractive plan summary:" + debug);

		return "\t" + String.join("\n\t" , summary);
	}

	public static String getPlanSummary(Text text, long num_words)
	{
		final Map<SemanticSubgraph, List<Mention>> subgraphs2mentions = text.subgraphs.stream()
				.collect(toMap(subgraph -> subgraph, subgraph -> subgraph.vertexSet().stream()
						.map(v -> subgraph.getBase().getMentions(v))
						.flatMap(Collection::stream)
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.collect(toList())));

		final Map<SemanticSubgraph, String> subgraphs2weightlists = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(m -> m.getWeight().isPresent())
						.map(m -> m.getSurfaceForm() + " " + DebugUtils.printDouble(m.getWeight().get()))
						.collect(joining(", "))));

		final Map<SemanticSubgraph, Double> subgraphs2weights = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.map(Mention::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(w -> w)
						.average().orElse(0.0)));

		final List<SemanticSubgraph> sorted_subgraphs = text.subgraphs.stream()
				.sorted(Comparator.<SemanticSubgraph>comparingDouble(subgraphs2weights::get).reversed())
				.collect(toList());

		final List<Token> tokens = text.sentences.stream()
				.flatMap(s -> s.tokens.stream())
				.collect(toList());

		final AtomicInteger counter = new AtomicInteger();
		final List<String> summary = sorted_subgraphs.stream()
				.map(subgraphs2mentions::get)
				.map(l -> l.stream()
						.flatMap(mention -> IntStream.range(mention.getSpan().getLeft(), mention.getSpan().getRight())
								.mapToObj(tokens::get)
								.map(t -> t.wf))
						.collect(toList()))
				.takeWhile(l -> counter.getAndAdd(l.size()) < num_words)
				.map(l -> String.join(" ", l))
				.collect(toList());

		final String debug = IntStream.range(0, summary.size())
				.mapToObj(i -> summary.get(i) + ".\t" + DebugUtils.printDouble(subgraphs2weights.get(sorted_subgraphs.get(i))) + "\t-\t" + subgraphs2weightlists.get((sorted_subgraphs.get(i))))
				.collect(joining("\n\t", "\n\t", "\n"));
		log.debug("Plan summary:" + debug);

		return "\t" + String.join("\n\t" , summary);
	}

	private static String composeSummary(List<Sentence> sentences, ItemType item_type, int num_items)
	{
		final String summary;
		if (item_type == ItemType.Sentence)
		{
			summary = sentences.stream()
					.limit(num_items)
					.map(Summarizer::formatSentence)
					.flatMap(List::stream)
					.collect(joining(" "));
		}
		else if (item_type == ItemType.Word)
			summary = sentences.stream()
					.map(Summarizer::formatSentence)
					.flatMap(List::stream)
					.limit(num_items)
					.collect(joining(" "));
		else
			summary = "";

		return summary;

	}

	private static List<String> formatSentence(Sentence s)
	{
		final Deque<String> sentence_tokens = s.tokens.stream().map(t -> t.wf).collect(toCollection(ArrayDeque::new));

		// modify first and last elements of the collection
		String first_token = sentence_tokens.removeFirst();
		first_token = "\t" + first_token;
		sentence_tokens.addFirst(first_token);
		String last_token = sentence_tokens.removeLast();
		last_token += "\n";
		sentence_tokens.addLast(last_token);

		return List.copyOf(sentence_tokens);
	}

	private static List<Sentence> sortSentencesByMaxWeight(Text text, Function<Token, Optional<Double>> weight_function)
	{
		return text.sentences.stream()
				.sorted(Comparator.<Sentence>comparingDouble(s -> s.tokens.stream()
						.map(weight_function)
						.flatMap(Optional::stream)
						.mapToDouble(d -> d)
						.max().orElse(0.0)).reversed())
				.collect(toList());
	}

	private static List<Sentence> sortSentencesByAverageWeight(Text text, Function<Token, Optional<Double>> weight_function, double default_weight)
	{
		// 2- Calculate weights for each sentence and sort accordingly
		final Map<Sentence, Double> sentences2weights = text.sentences.stream()
				.collect(toMap(s -> s, s -> s.tokens.stream()
							.map(weight_function)
							.mapToDouble(o -> o.orElse(default_weight))
							.sum() / s.tokens.size()));

		return sentences2weights.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Sentence, Double>>comparingDouble(Map.Entry::getValue).reversed())
				.map(Map.Entry::getKey)
				.collect(toList());
	}

	public static void printDebugInfo(Text text, int num_items, Function<Token, Optional<Double>> meaning_weight_function,
	                                  Function<Token, Optional<Double>> mention_weight_function, double default_weight)
	{
		printBagDebugInfo(text, num_items);
		log.debug("Sentences sorted by average meaning weight");
		printExtractiveDebugInfo(text, meaning_weight_function, default_weight);
		log.debug("\nSentences sorted by average mention weight");
		printExtractiveDebugInfo(text, mention_weight_function, default_weight);
	}

	private static void printBagDebugInfo(Text text, int num_items)
	{
		// BoM
		final List<Candidate> disambiguated_candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.disambiguated_meanings.values().stream())
				.sorted(Comparator.<Candidate>comparingDouble(c -> c.getWeight().orElse(0.0)).reversed())
				.collect(toList());

		final Map<Meaning, List<Candidate>> meanings2candidates = disambiguated_candidates.stream()
				.collect(groupingBy(Candidate::getMeaning));
		final Map<Meaning, Double> meanings2weights = meanings2candidates.keySet().stream()
				.collect(toMap(m -> m, m -> meanings2candidates.get(m).stream()
						.map(Candidate::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(d -> d)
						.average().orElse(0.0)));
		final List<Meaning> sorted_meanings = meanings2weights.keySet().stream()
				.sorted(Comparator.<Meaning>comparingDouble(meanings2weights::get).reversed())
				.collect(toList());

		AtomicInteger accum_words = new AtomicInteger();
		final String meanings_str = sorted_meanings.stream()
				.takeWhile(m -> accum_words.addAndGet(m.getLabel().split(" ").length) < num_items)
				.map(m -> m.toString() + "\t" + DebugUtils.printDouble(meanings2weights.get(m)))
				.collect(joining("\n\t"));
		log.debug("BoM meanings sorted by weight:\n\t" + meanings_str + "\n");

		// BoW
		final List<Mention> sorted_single_words= text.sentences.stream()
				.flatMap(sentence -> sentence.ranked_words.stream())
				.filter(m -> m.getWeight().isPresent())
				.sorted(Comparator.<Mention>comparingDouble(m -> m.getWeight().get()).reversed())
				.collect(toList());

		accum_words.set(0);
		final String candidates_str = sorted_single_words.stream()
				.takeWhile(m -> accum_words.addAndGet(m.getSurfaceForm().split(" ").length) < num_items)
				.map(m -> m.toString() + "\t" + m.getWeight().map(DebugUtils::printDouble).orElse("-"))
				.collect(joining("\n\t"));
		log.debug("BoW words sorted by weight:\n\t" + candidates_str + "\n");
	}

	private static void printExtractiveDebugInfo(Text text, Function<Token, Optional<Double>> weight_function, double default_weight)
	{
		final Map<Sentence, String> sentences2weightLists = new HashMap<>();
		final Map<Sentence, Double> sentences2weights = text.sentences.stream()
				.collect(toMap(s -> s, s -> {
					final StringBuffer b = new StringBuffer();
					final double sentence_weight = s.tokens.stream()
							.mapToDouble(t -> {
								final double w = weight_function.apply(t).orElse(default_weight);
								b.append(t.wf).append(" ");
								if (w > default_weight)
									b.append(DebugUtils.printDouble(w)).append(" ");
								return w;
							})
							.sum() / s.tokens.size();

					sentences2weightLists.put(s, b.toString());

					return sentence_weight;
				}));

		final double average_weight = text.sentences.stream()
			.flatMap(s -> s.tokens.stream())
			.map(weight_function)
			.flatMap(Optional::stream)
			.mapToDouble(d -> d)
			.average().orElse(0.0);
		log.debug("Average weight is " + DebugUtils.printDouble(average_weight));

		final List<Sentence> sorted_sentences = sentences2weights.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Sentence, Double>>comparingDouble(Map.Entry::getValue).reversed())
				.map(Map.Entry::getKey)
				.collect(toList());

		final StringBuilder debug = new StringBuilder("\n\t");
		sorted_sentences.forEach(sentence -> debug.append(sentence.tokens.stream()
				.map(t -> t.wf)
				.collect(joining(" ")))
				.append("\t")
				.append(DebugUtils.printDouble(sentences2weights.get(sentence)))
				.append("\n\t\t")
				.append(sentences2weightLists.get(sentence))
				.append("\n\t"));
		log.debug(debug.toString() + "\n");
	}
}
