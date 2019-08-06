package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.tools.evaluation.corpus.CorpusAdjacencyFunction;
import edu.upf.taln.textplanning.tools.evaluation.corpus.CorpusContextFunction;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.lang.Math.min;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

public class EvaluationTools
{
	public static class AlternativeMeanings
	{
		final Set<String> alternatives = new HashSet<>();
		final String text; // covered text or label
		final String begin;
		final String end;

		AlternativeMeanings(Collection<String> meanings, String text, String begin, String end)
		{
			alternatives.addAll(meanings);
			this.text = text;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public String toString()
		{
			return text + " " + begin + "-" + end + " = " + alternatives;
		}
	}

	private static final int context_size = 1;
	private static final String corpus_filename = "corpus.xml";
	private final static Logger log = LogManager.getLogger();

	public static EvaluationCorpus.Corpus loadResourcesFromRawText(Path text_folder, Path output_path)
	{
		String corpus_contents = null;
		try
		{
			Stanford2SemEvalXML stanford = new Stanford2SemEvalXML();
			corpus_contents = stanford.convert(text_folder, "txt", output_path.resolve(corpus_filename));
		}
		catch (Exception e)
		{
			log.error("Failed to preprocess text files " + e);
			e.printStackTrace();
		}

		if (corpus_contents != null)
			return EvaluationCorpus.createFromXML(output_path);
		return null;

	}

	public static void createResources(EvaluationCorpus.Corpus corpus, POS.Tagset tagset, InitialResourcesFactory resources,
	                                                      int max_span_size, boolean rank_together,
	                                                      Set<POS.Tag> ignored_POS_Tags, Options options)
	{
		log.info("Creating resources for corpus");
		Stopwatch timer = Stopwatch.createStarted();

		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final EvaluationCorpus.Text text = corpus.texts.get(i);
			createResources(text, tagset, resources, max_span_size, ignored_POS_Tags, !rank_together, options);
		}

		if (rank_together)
		{
			final List<EvaluationCorpus.Sentence> sentences = corpus.texts.stream()
					.flatMap(t -> t.sentences.stream())
					.collect(toList());
			final List<Candidate> candidates_list = corpus.texts.stream()
					.flatMap(t -> t.sentences.stream())
					.flatMap(s -> s.candidates.values().stream())
					.flatMap(List::stream)
					.collect(toList());

			CorpusContextFunction context = new CorpusContextFunction(sentences, candidates_list, resources.getLanguage(), options.min_context_freq, options.window_size);
			corpus.resouces = new DocumentResourcesFactory(resources, options, candidates_list, context);
		}

		log.info("Corpus resources created in " + timer.stop() + "\n");
	}

	public static void createResources(EvaluationCorpus.Text text, POS.Tagset tagset, InitialResourcesFactory resources,
	                                   int max_span_size,
	                                   Set<POS.Tag> ignored_POS_Tags, boolean create_context, Options options)
	{
		log.info("Creating resources for document " + text.id  + " ( " + text.filename +  ")");
		Stopwatch timer = Stopwatch.createStarted();

		try
		{
			final ULocale language = resources.getLanguage();
			final List<Mention> mentions = collectMentions(text, tagset, max_span_size, ignored_POS_Tags, language);
			final Map<Mention, List<Candidate>> candidates = CandidatesCollector.collect(resources.getDictionary(), language, mentions);

			// assign mentions and candidates to sentences in corpus
			text.sentences.forEach(sentence ->
			{
				candidates.keySet().stream()
						.filter(m -> m.getContextId().equals(sentence.id))
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.forEach(m -> sentence.mentions.add(m));

				sentence.mentions.forEach(m -> sentence.candidates.put(m, candidates.get(m)));
			});

			final List<Candidate> text_candidates = text.sentences.stream()
					.flatMap(s -> s.candidates.values().stream().flatMap(List::stream))
					.collect(toList());
			final Set<String> meanings = text_candidates.stream()
					.map(Candidate::getMeaning)
					.map(Meaning::getReference)
					.collect(toSet());
			final Set<String> biasMeanings = resources.getBiasMeanings();
			if (biasMeanings != null)
				meanings.addAll(biasMeanings);

			if (create_context)
			{
				CorpusContextFunction context = new CorpusContextFunction(text.sentences, text_candidates, language, options.min_context_freq, options.window_size);
				text.resources = new DocumentResourcesFactory(resources, options, text_candidates, context);
			}

			log.info("Document resources created in " + timer.stop() + "\n");
		}
		catch (Exception e)
		{
			log.error("Cannot load resources for text " + text.id  + " ( " + text.filename +  "): " + e);
			e.printStackTrace();
		}
	}

	public static void rankMeanings(EvaluationCorpus.Corpus corpus, Options options)
	{
		log.info(options);

		final List<EvaluationCorpus.Sentence> sentences = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.collect(toList());

		rankMeanings(sentences, corpus.resouces, options);
	}

	public static void rankMeanings(EvaluationCorpus.Text text, Options options)
	{
		rankMeanings(text.sentences, text.resources, options);
	}

	public static void rankMeanings(List<EvaluationCorpus.Sentence> sentences, DocumentResourcesFactory resources, Options options)
	{
		final BiasFunction bias = resources.getBiasFunction();
		final SimilarityFunction similarity = resources.getSimilarityFunction();
		final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter();
		final BiPredicate<String, String> similarity_filter = resources.getMeaningPairsSimilarityFilter();

		final List<Candidate> candidates = sentences.stream()
				.flatMap(s -> s.candidates.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		// Log stats
		final int num_mentions = sentences.stream()
				.map(s -> s.candidates)
				.mapToInt(m -> m.keySet().size())
				.sum();
		final long num_filtered_candidates = candidates.stream()
				.filter(candidates_filter)
				.count();
		final long num_meanings = candidates.stream()
				.filter(candidates_filter)
				.map(Candidate::getMeaning)
				.distinct()
				.count();
		log.info("Ranking texts with " + num_mentions + " mentions, " +
				candidates.size() + " candidates, " +
				num_filtered_candidates + " candidates after filtering, and " +
				num_meanings + " distinct meanings");

		// Rank candidates
		TextPlanner.rankMeanings(candidates, candidates_filter, similarity_filter, bias, similarity, options);
	}

	public static void disambiguate(EvaluationCorpus.Corpus corpus, Options options)
	{
		corpus.texts.forEach(t -> disambiguate(t, options));
	}

	public static void disambiguate(EvaluationCorpus.Text text, Options options)
	{
		final List<Candidate> candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.candidates.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
		final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates);
		disambiguated.forEach((m, c) -> text.sentences.stream()
				.filter(s -> s.id.equals(m.getContextId()))
				.findFirst()
				.orElseThrow()
				.disambiguated.put(m, c));
	}

	public static void rankMentions(EvaluationCorpus.Corpus corpus, Options options)
	{
		corpus.texts.forEach(text -> EvaluationTools.rankMentions(text, options));
	}

	public static void rankMentions(EvaluationCorpus.Text text, Options options)
	{
		CorpusAdjacencyFunction adjacency = new CorpusAdjacencyFunction(text, context_size, true);
		final List<Mention> word_mentions = adjacency.getSortedWordMentions();
		final Map<Mention, Candidate> word_meanings = adjacency.getWordMeanings();

		final Map<Mention, Optional<Double>> words2weights = word_mentions.stream()
				.collect(toMap(m -> m, m -> word_meanings.containsKey(m) ? word_meanings.get(m).getWeight() : Optional.empty()));

		TextPlanner.rankMentions(words2weights, adjacency, options);
	}

	public static void plan(EvaluationCorpus.Corpus corpus, Options options)
	{
		corpus.texts.forEach(text -> EvaluationTools.plan(text, options));
	}

	public static void plan(EvaluationCorpus.Text text, Options options)
	{
		final SimilarityFunction sim = text.resources.getSimilarityFunction();
		final CorpusAdjacencyFunction adjacency = new CorpusAdjacencyFunction(text, context_size, false);

		EvaluationCorpus.createGraph(text, adjacency);
		final List<SemanticSubgraph> subgraphs = new ArrayList<>(TextPlanner.extractSubgraphs(text.graph, options));

		final Collection<SemanticSubgraph> selected_subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, sim, options);
		text.subgraphs.addAll(TextPlanner.sortSubgraphs(selected_subgraphs, sim, options));
	}

	public static List<Mention> collectMentions(EvaluationCorpus.Text text, POS.Tagset tagset, int max_span_size,
	                                            Set<POS.Tag> ignore_pos_tags, ULocale language)
	{
		if (text.sentences.isEmpty())
			return List.of();

		// Calculate sentence boundaries in token offsets
		final List<Integer> sentence_offsets = new ArrayList<>();
		sentence_offsets.add(0);

		for (int i=1; i < text.sentences.size(); ++i)
		{
			final Integer offset = sentence_offsets.get(i - 1);
			final int num_tokens = text.sentences.get(i - 1).tokens.size();
			sentence_offsets.add(offset + num_tokens);
		}

		// create mention objects
		return IntStream.range(0, text.sentences.size())
				.mapToObj(s_i ->
					IntStream.range(0, text.sentences.get(s_i).tokens.size())
						.mapToObj(start -> IntStream.range(start + 1, min(start + max_span_size + 1, text.sentences.get(s_i).tokens.size() + 1))
								.filter(end ->
								{
									final POS.Tag tag = POS.get(text.sentences.get(s_i).tokens.get(start).pos, tagset);
									// single words must have a pos tag other than 'X'
									if (end - start == 1)
										return !ignore_pos_tags.contains(tag);
										// multiwords must contain at least one nominal token
									else
										return text.sentences.get(s_i).tokens.subList(start, end).stream()
												.anyMatch(t -> POS.get(t.pos, tagset) == POS.Tag.NOUN);
								})
								.mapToObj(end ->
								{
									final List<EvaluationCorpus.Token> tokens = text.sentences.get(s_i).tokens.subList(start, end);
									final List<String> token_forms = tokens.stream()
											.map(t -> t.wf)
											.collect(toList());
									final String lemma = tokens.stream()
											.map(t -> t.lemma != null ? t.lemma : t.wf)
											.collect(joining(" "));
									POS.Tag pos = tokens.size() == 1 ? POS.get(tokens.get(0).pos, tagset) : POS.Tag.NOUN;
									String contextId = tokens.size() == 1 ? tokens.get(0).id : tokens.get(0).id + "-" + tokens.get(tokens.size()-1).id;
									final Pair<Integer, Integer> span = Pair.of(sentence_offsets.get(s_i) + start, sentence_offsets.get(s_i) + end);

									return new Mention(contextId, text.sentences.get(s_i).id, span, token_forms, lemma, pos, false, "");
								})
								.filter(m -> FunctionWordsFilter.test(m.getSurfaceForm(), language)))
						.flatMap(stream -> stream))
				.flatMap(stream -> stream)
				.collect(toList());
	}

	public static void printMeaningRankings(EvaluationCorpus.Corpus corpus, Map<String, Set<AlternativeMeanings>> gold, boolean multiwords_only, Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.debug("TEXT " + i);
			final EvaluationCorpus.Text text = corpus.texts.get(i);
			final Set<String> text_gold = gold.get(text.id).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());


			final int max_length = text.sentences.stream()
					.flatMap(s -> s.disambiguated.values().stream())
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			final Function<String, Double> weighter =
					corpus.resouces != null ? corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();

			final Map<Meaning, Double> weights = text.sentences.stream()
					.flatMap(s -> s.disambiguated.values().stream())
					.filter(m -> (multiwords_only && m.getMention().isMultiWord()) || (!multiwords_only && eval_POS.contains(m.getMention().getPOS())))
					.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));

			final List<Meaning> meanings = new ArrayList<>(weights.keySet());
			Function<Meaning, String> inGold = m -> text_gold.contains(m.getReference()) ? "GOLD" : "";

			log.debug(meanings.stream()
//					.filter(m -> weights.get(m) > 0.0)
					.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
					.map(m -> String.format("%-" + max_length + "s%-11s%-11s%-8s",
							m.toString(),
							DebugUtils.printDouble(weights.get(m)),
							DebugUtils.printDouble(weighter.apply(m.getReference())),
							inGold.apply(m)))
					.collect(joining("\n\t", "Meaning ranking by ranking score (and bias) :\n\t",
							"\n--------------------------------------------------------------------------------------------")));
		});
	}

	public static void printDisambiguationResults(EvaluationCorpus.Corpus corpus, Function<Mention, Set<String>> gold, boolean multiwords_only, Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final EvaluationCorpus.Text text = corpus.texts.get(i);

			final int max_length = text.sentences.stream()
					.flatMap(s -> s.candidates.values().stream())
					.flatMap(Collection::stream)
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			final Function<String, Double> bias =
					corpus.resouces != null ? corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();

			text.sentences.forEach(s ->
					s.candidates.forEach((mention, candidates) ->
					{
						if (candidates.isEmpty())
							return;

						if ((multiwords_only && !mention.isMultiWord() || (!multiwords_only && !eval_POS.contains(mention.getPOS()))))
							return;

						final String max_bias = candidates.stream()
								.map(Candidate::getMeaning)
								.map(Meaning::getReference)
								.max(comparingDouble(bias::apply))
								.orElse("");
						final String max_rank = candidates.stream()
								.max(comparingDouble(c -> c.getWeight().orElse(0.0)))
								.map(Candidate::getMeaning)
								.map(Meaning::getReference).orElse("");
						final String first_m = candidates.get(0).getMeaning().getReference();

						final Set<String> gold_set = gold.apply(mention);
						final String result = gold_set.contains(max_rank) ? "OK" : "FAIL";
						final String ranked = max_rank.equals(first_m) ? "" : "RANKED";
						Function<String, String> marker = (r) ->
								(gold_set.contains(r) ? "GOLD " : "") +
										(r.equals(max_bias) ? "BIAS " : "") +
										(r.equals(max_rank) ? "RANK" : "");


						log.info(mention.getId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
								candidates.stream()
										.map(c ->
										{
											final String mark = marker.apply(c.getMeaning().getReference());
											final String bias_value = DebugUtils.printDouble(bias.apply(c.getMeaning().getReference()));
											final String rank_value = c.getWeight().map(DebugUtils::printDouble).orElse("");

											return String.format("%-15s%-" + max_length + "s%-15s%-15s", mark,
													c.getMeaning().toString(), bias_value, rank_value);
										})
										.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t", "Status", "Candidate", "Bias", "Rank"), "")));
					}));

		});
	}

	public static void writeDisambiguatedResultsToFile(EvaluationCorpus.Corpus corpus, Path output_file)
	{
		final String str = corpus.texts.stream()
				.map(text -> text.sentences.stream()
						.map(sentence -> sentence.disambiguated.keySet().stream()
								.sorted(Comparator.comparing(Mention::getId))
								.map(mention ->
								{
									String id = mention.getId();
									if (id.contains("-"))
									{
										final String[] parts = id.split("-");
										id = parts[0] + "\t" + parts[1];
									}
									else
										id = id + "\t" + id;
									return id + "\t" +
											sentence.disambiguated.get(mention).getMeaning().getReference() + "\t\"" +
											mention.getSurfaceForm() + "\"";
								})
								.collect(joining("\n")))
						.collect(joining("\n")))
				.collect(joining("\n"));

		FileUtils.writeTextToFile(output_file, str);
	}
}
