package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.corpus.*;
import edu.upf.taln.textplanning.core.dictionaries.CandidatesCollector;
import edu.upf.taln.textplanning.core.dictionaries.CompactDictionary;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
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
import static java.util.function.Predicate.not;
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

	private static final String corpus_filename = "corpus.xml";
	private final static Logger log = LogManager.getLogger();

	public static Corpora.Corpus loadResourcesFromRawText(Path text_folder, Path output_path)
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
			return Corpora.createFromXML(output_path);
		return null;

	}

	public static Map<Corpora.Text, DocumentResourcesFactory> createResources(Corpora.Corpus corpus, POS.Tagset tagset, InitialResourcesFactory initial_resources,
	                                                                          int max_span_size, Set<POS.Tag> ignored_POS_Tags, Options options)
	{
		log.info("Creating resources for corpus");
		Stopwatch timer = Stopwatch.createStarted();

		// single content words and multiwords
		final List<List<Mention>> mentions = corpus.texts.stream()
				.map(text -> collectMentions(text, tagset, max_span_size, ignored_POS_Tags, initial_resources.getLanguage()))
				.collect(toList());
		createCache(mentions, initial_resources);

		Map<Corpora.Text, DocumentResourcesFactory> texts2resources = new HashMap<>();
		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Corpora.Text text = corpus.texts.get(i);
			texts2resources.put(text, createResources(text, mentions.get(i), initial_resources, true, options));
		}

		log.info("Corpus resources created in " + timer.stop() + "\n");
		return texts2resources;
	}


	public static DocumentResourcesFactory createJointResources(Corpora.Corpus corpus, POS.Tagset tagset,
	                                                            InitialResourcesFactory initial_resources, int max_span_size,
	                                                            Set<POS.Tag> ignored_POS_Tags, Options options)
	{
		log.info("Creating resources for corpus");
		Stopwatch timer = Stopwatch.createStarted();

		// single content words and multiwords
		final List<List<Mention>> mentions = corpus.texts.stream()
				.map(text -> collectMentions(text, tagset, max_span_size, ignored_POS_Tags, initial_resources.getLanguage()))
				.collect(toList());
		createCache(mentions, initial_resources);

		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Corpora.Text text = corpus.texts.get(i);
			createResources(text, mentions.get(i), initial_resources, false, options);
		}

		final List<Corpora.Sentence> sentences = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.collect(toList());
		final List<Candidate> candidates_list = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.flatMap(s -> s.candidate_meanings.values().stream())
				.flatMap(List::stream)
				.collect(toList());

		CorpusContextFunction context = new CorpusContextFunction(sentences, candidates_list, initial_resources.getLanguage(), options.min_context_freq, options.window_size);
		DocumentResourcesFactory resources = new DocumentResourcesFactory(initial_resources, options, candidates_list, context);

		log.info("Corpus resources created in " + timer.stop() + "\n");

		return resources;
	}

	private static DocumentResourcesFactory createResources(Corpora.Text text, List<Mention> mentions,
	                                                       InitialResourcesFactory initial_resources, boolean create_context, Options options)
	{
		log.info("Creating resources for document " + text.id  + " ( " + text.filename +  ")");
		Stopwatch timer = Stopwatch.createStarted();

		try
		{
			final ULocale language = initial_resources.getLanguage();
			final Map<Mention, List<Candidate>> candidates = CandidatesCollector.collect(initial_resources.getDictionary(), language, mentions);

			// assign mentions and candidates to sentences in corpus
			text.sentences.forEach(sentence ->
			{
				sentence.ranked_words = mentions.stream()
						.filter(m -> m.getContextId().equals(sentence.id))
						.filter(not(Mention::isMultiWord))
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.collect(toList());

				candidates.keySet().stream()
						.filter(m -> m.getContextId().equals(sentence.id))
						.forEach(m -> sentence.candidate_meanings.put(m, candidates.get(m)));

			});

			final List<Candidate> text_candidates = text.sentences.stream()
					.flatMap(s -> s.candidate_meanings.values().stream().flatMap(List::stream))
					.collect(toList());

			DocumentResourcesFactory resources = null;
			if (create_context)
			{
				CorpusContextFunction context = new CorpusContextFunction(text.sentences, text_candidates, language, options.min_context_freq, options.window_size);
				resources = new DocumentResourcesFactory(initial_resources, options, text_candidates, context);
			}

			log.info("Document resources created in " + timer.stop() + "\n");
			return resources;
		}
		catch (Exception e)
		{
			log.error("Cannot load resources for text " + text.id  + " ( " + text.filename +  "): " + e);
			e.printStackTrace();
			return null;
		}
	}

	private static void createCache(List<List<Mention>> mentions, InitialResourcesFactory initial_resources)
	{
		if (initial_resources.getCache() == null && initial_resources.getProperties().getCachePath() != null)
		{
			log.info("Creating cache from corpus candidates");
			final Set<Pair<String, POS.Tag>> forms = mentions.stream()
					.flatMap(List::stream)
					.map(m -> Pair.of(m.getSurfaceForm(), m.getPOS()))
					.collect(toSet());
			CompactDictionary cache = new CompactDictionary(initial_resources.getLanguage(), forms, initial_resources.getBase());
			initial_resources.setCache(cache);
			cache.serialize(initial_resources.getProperties().getCachePath());
		}
	}

	public static void rankMeanings(Corpora.Corpus corpus, DocumentResourcesFactory resources, Options options)
	{
		log.info(options);

		final List<Corpora.Sentence> sentences = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.collect(toList());

		rankMeanings(sentences, resources, options);
	}

	public static void rankMeanings(Corpora.Text text, DocumentResourcesFactory resources, Options options)
	{
		rankMeanings(text.sentences, resources, options);
	}

	public static void rankMeanings(List<Corpora.Sentence> sentences, DocumentResourcesFactory resources, Options options)
	{
		final BiasFunction bias = resources.getBiasFunction();
		final SimilarityFunction similarity = resources.getSimilarityFunction();
		final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter();
		final BiPredicate<String, String> similarity_filter = resources.getMeaningPairsSimilarityFilter();

		final List<Candidate> candidates = sentences.stream()
				.flatMap(s -> s.candidate_meanings.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		// Log stats
		final int num_mentions = sentences.stream()
				.map(s -> s.candidate_meanings)
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

	public static void disambiguate(Corpora.Corpus corpus, Options options)
	{
		corpus.texts.forEach(t -> disambiguate(t, options));
	}

	public static void disambiguate(Corpora.Text text, Options options)
	{
		final List<Candidate> candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.candidate_meanings.values().stream()
						.flatMap(Collection::stream))
				.collect(toList());

		Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
		final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates);
		disambiguated.forEach((m, c) -> text.sentences.stream()
				.filter(s -> s.id.equals(m.getContextId()))
				.findFirst()
				.orElseThrow()
				.disambiguated_meanings.put(m, c));
	}

	public static void rankMentions(Corpora.Corpus corpus, boolean use_dependencies, int context_size, Options options)
	{
		corpus.texts.forEach(text -> EvaluationTools.rankMentions(text, use_dependencies, context_size, options));
	}

	public static void rankMentions(Corpora.Text text, boolean use_dependencies, int context_size, Options options)
	{
		final AdjacencyFunction adjacency = use_dependencies ? new DependencyBasedAdjacencyFunction(text)
				: new TextualOrderAdjacencyFunction(text, context_size);
		final List<Mention> word_mentions = adjacency.getSortedWordMentions();
		final SameMeaningPredicate same_meaning = new SameMeaningPredicate(text);
		final Map<Mention, Candidate> word_meanings = same_meaning.getWordMeanings();

		final Map<Mention, Optional<Double>> words2weights = word_mentions.stream()
				.collect(toMap(m -> m, m -> word_meanings.containsKey(m) ? word_meanings.get(m).getWeight() : Optional.empty()));

		TextPlanner.rankMentions(words2weights, (m1, m2) -> adjacency.test(m1, m2) || adjacency.test(m2, m1), same_meaning, options);
	}

	public static void plan(Corpora.Corpus corpus, DocumentResourcesFactory resources, boolean use_dependencies, int context_size, Options options)
	{
		corpus.texts.forEach(text -> EvaluationTools.plan(text, resources, use_dependencies, context_size, options));
	}

	public static void plan(Corpora.Text text, DocumentResourcesFactory resources, boolean use_dependencies, int context_size, Options options)
	{
		final AdjacencyFunction adjacency = use_dependencies ? new DependencyBasedAdjacencyFunction(text)
				: new TextualOrderAdjacencyFunction(text, context_size);
		Corpora.createGraph(text, adjacency);
		final List<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(text.graph, options);

		final SimilarityFunction sim = resources.getSimilarityFunction();
		final List<SemanticSubgraph> selected_subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, sim, options);
		text.subgraphs.addAll(TextPlanner.sortSubgraphs(selected_subgraphs, sim, options));
	}

	public static List<Mention> collectMentions(Corpora.Text text, POS.Tagset tagset, int max_span_size,
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
									final List<Corpora.Token> tokens = text.sentences.get(s_i).tokens.subList(start, end);
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

	public static void printMeaningRankings(Corpora.Corpus corpus, DocumentResourcesFactory resources,
	                                        Map<String, Set<AlternativeMeanings>> gold, boolean multiwords_only,
	                                        Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
				printMeaningRankings(corpus.texts.get(i), resources, gold, multiwords_only, eval_POS));
	}

	public static void printMeaningRankings(Corpora.Text text, DocumentResourcesFactory resources,
	                                        Map<String, Set<AlternativeMeanings>> gold, boolean multiwords_only,
	                                        Set<POS.Tag> eval_POS)
	{
			log.debug("TEXT " + text.id);
			final Set<String> text_gold = gold.get(text.id).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());


			final int max_length = text.sentences.stream()
					.flatMap(s -> s.disambiguated_meanings.values().stream())
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			final Function<String, Double> weighter = resources.getBiasFunction();

			final Map<Meaning, Double> weights = text.sentences.stream()
					.flatMap(s -> s.disambiguated_meanings.values().stream())
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
	}

	public static void printDisambiguationResults(Corpora.Corpus corpus, DocumentResourcesFactory resources,
	                                              Function<Mention, Set<String>> gold,
	                                              boolean multiwords_only, Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
				printDisambiguationResults(corpus.texts.get(i), resources, gold, multiwords_only, eval_POS));
	}

	public static void printDisambiguationResults(Corpora.Text text, DocumentResourcesFactory resources,
	                                              Function<Mention, Set<String>> gold, boolean multiwords_only,
	                                              Set<POS.Tag> eval_POS)
	{
		log.info("TEXT " + text.id);

		final int max_length = text.sentences.stream()
				.flatMap(s -> s.candidate_meanings.values().stream())
				.flatMap(Collection::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.mapToInt(String::length)
				.max().orElse(5) + 4;

		final Function<String, Double> bias = resources.getBiasFunction();

		text.sentences.forEach(s ->
				s.candidate_meanings.forEach((mention, candidates) ->
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

	}

	public static void writeDisambiguatedResultsToFile(Corpora.Corpus corpus, Path output_file)
	{
		final String str = corpus.texts.stream()
				.map(text -> text.sentences.stream()
						.map(sentence -> sentence.disambiguated_meanings.keySet().stream()
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
											sentence.disambiguated_meanings.get(mention).getMeaning().getReference() + "\t\"" +
											mention.getSurfaceForm() + "\"";
								})
								.collect(joining("\n")))
						.collect(joining("\n")))
				.collect(joining("\n"));

		FileUtils.writeTextToFile(output_file, str);
	}
}
