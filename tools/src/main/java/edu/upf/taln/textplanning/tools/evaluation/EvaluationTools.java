package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.bias.ContextFunction;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.uima.io.DSyntSemantics;
import edu.upf.taln.textplanning.uima.io.UIMAWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Comparator.comparingDouble;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class EvaluationTools
{
	@XmlRootElement(name = "corpus")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class
	Corpus
	{
		@XmlAttribute
		public String lang;
		@XmlElement(name = "text")
		public List<Text> texts = new ArrayList<>();
		@XmlTransient
		public DocumentResourcesFactory resouces = null;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String filename;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences = new ArrayList<>();
		@XmlTransient
		public DocumentResourcesFactory resources;
		@XmlTransient
		public SemanticGraph graph = null;
		@XmlTransient
		public List<SemanticSubgraph> subgraphs = new ArrayList<>();
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens = new ArrayList<>();
		@XmlTransient
		List<Mention> mentions = new ArrayList<>();
		@XmlTransient
		Map<Mention, List<Candidate>> candidates = new HashMap<>();
		@XmlTransient
		Map<Mention, Candidate> disambiguated = new HashMap<>();
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Token
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String lemma;
		@XmlAttribute
		public String pos;
		@XmlValue
		public String wf;
	}

	// Implements local context based on corpus class
	private static class Context extends ContextFunction
	{
		private final List<Sentence> sentences;
		private final int window_size;
		private final Map<Mention, List<String>> mentions2contexts;

		public Context(List<Sentence> sentences, List<Candidate> candidates, ULocale language, int min_frequency, int window_size)
		{
			super(language, sentences.stream().flatMap(s -> s.tokens.stream().map(tok -> tok.wf))
					.collect(toList()), min_frequency, candidates);
			this.sentences = sentences;
			this.window_size = window_size;

			this.mentions2contexts = candidates.stream()
					.map(Candidate::getMention)
					.distinct()
					.collect(toMap(mention -> mention, this::calculateWindow));
		}

		@Override
		protected List<String> getWindow(Mention mention)
		{
			return mentions2contexts.get(mention);
		}

		private List<String> calculateWindow(Mention mention)
		{
			final String contextId = mention.getSourceId();

			final List<String> sentence_tokens = sentences.stream()
					.filter(s -> contextId.equals(s.id))
					.findFirst()
					.map(s -> s.tokens.stream()
							.map(t -> t.wf)
							.collect(toList()))
					.orElse(List.of());


			final Pair<Integer, Integer> span = mention.getSpan();
			final Integer start = span.getLeft();
			final Integer end = span.getRight();
			final int size = sentence_tokens.size();

			final List<String> tokens_left = start == 0 ?  List.of() : sentence_tokens.subList(max(0, start - window_size), start);
			final List<String> tokens_right = end == size ? List.of() : sentence_tokens.subList(end, min(size, end + window_size));
			List<String> window = new ArrayList<>(tokens_left);
			window.addAll(tokens_right);

			return filterTokens(window);
		}
	}

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

		AlternativeMeanings(Collection<String> meanings, String text)
		{
			alternatives.addAll(meanings);
			this.text = text;
			this.begin = "";
			this.end = "";
		}

		@Override
		public String toString()
		{
			return text + " " + begin + "-" + end + " = " + alternatives;
		}
	}

	private static final String corpus_filename = "corpus.xml";
	private final static Logger log = LogManager.getLogger();


	public static Corpus loadResourcesFromRawText(Path text_folder, Path output_path, InitialResourcesFactory resource_factory,
	                                              ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                              Set<String> ignored_POS_Tags, Options options)
	{
		String corpus_contents = null;
		try
		{
			Stanford2SemEvalXML stanford = new Stanford2SemEvalXML();
			corpus_contents = stanford.convert(text_folder, output_path.resolve(corpus_filename));
		}
		catch (Exception e)
		{
			log.error("Failed to preprocess text files " + e);
			e.printStackTrace();
		}

		if (corpus_contents != null)
			return loadResourcesFromXML(corpus_contents, resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);
		return null;

	}

	public static Corpus loadResourcesFromXMI(Path xmi_folder, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                          Set<String> ignored_POS_Tags, Options options)
	{

		Corpus corpus = new Corpus();
		AtomicInteger doc_counter = new AtomicInteger(0);
		final List<UIMAWrapper> docs = UIMAWrapper.readFromXMI(xmi_folder);

		docs.forEach(d -> {
			final Text text = new Text();
			text.id = "t" + doc_counter.incrementAndGet();
			text.graph = d.getSemanticGraph();
			corpus.texts.add(text);
			AtomicInteger sentence_counter = new AtomicInteger(0);

			d.getTokensInfo().forEach(s -> {
				final Sentence sentence = new Sentence();
				sentence.id = text.id + ".s" + sentence_counter.incrementAndGet();
				text.sentences.add(sentence);
				AtomicInteger token_counter = new AtomicInteger(0);

				s.forEach(t -> {
					final Token token = new Token();
					token.id = sentence.id + ".t" + token_counter.incrementAndGet();
					token.wf = t.getWordForm();
					token.lemma = t.getLemma();
					token.pos = t.getPos();
					sentence.tokens.add(token);
				});
			});

		});

		return loadResources(corpus, resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);
	}

	public static Corpus loadResourcesFromXML(Path xml_file, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                          Set<String> ignored_POS_Tags, Options options)
	{
		return loadResourcesFromXML(FileUtils.readTextFile(xml_file), resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);
	}

	public static Corpus loadResourcesFromXML(String xml_contents, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                          Set<String> ignored_POS_Tags, Options options)
	{
		log.info("Parsing XML");

		try
		{
			JAXBContext jc = JAXBContext.newInstance(Corpus.class);
			StreamSource xml = new StreamSource(new StringReader(xml_contents));
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
			final Corpus corpus = je1.getValue();

			return loadResources(corpus, resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);

		}
		catch (JAXBException e)
		{
			log.error("Failed to parse xml : " + e);
		}

		return new Corpus();
	}


	private static Corpus loadResources(Corpus corpus, InitialResourcesFactory resources,
	                                    ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                    Set<String> ignored_POS_Tags, Options options)
	{
		log.info("Collecting mentions, candidate meanings and glosses");
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, List<String>> glosses = new HashMap<>();

		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Text text = corpus.texts.get(i);
			try
			{
				log.info("\tCollecting for " + text.id  + " ( " + text.filename +  ")");
				Stopwatch text_timer = Stopwatch.createStarted();

				final List<Mention> mentions = collectMentions(text, max_span_size, noun_pos_prefix, ignored_POS_Tags, language);
				final Map<Mention, List<Candidate>> candidates = CandidatesCollector.collect(resources.getDictionary(), language, mentions);
				resources.serializeCache();

				// assign mentions and candidates to sentences in corpus
				text.sentences.forEach(sentence ->
				{
					candidates.keySet().stream()
							.filter(m -> m.getSourceId().equals(sentence.id))
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

				meanings.forEach(r -> glosses.computeIfAbsent(r, k -> resources.getDictionary().getGlosses(k, resources.getLanguage())));
				log.info("\t\tText info collected in " + text_timer.stop());
			}
			catch (Exception e)
			{
				log.error("Cannot load resources for text " + text.id  + " ( " + text.filename +  "): " + e);
				e.printStackTrace();
			}
		}
		log.info("Mentions, candidate meanings and glosses queried in " + timer.stop());

		log.info("Creating document resources");
		timer = Stopwatch.createStarted();

		if (rank_together)
		{
			final List<Sentence> sentences = corpus.texts.stream()
					.flatMap(t -> t.sentences.stream())
					.collect(toList());
			final List<Candidate> candidates_list = corpus.texts.stream()
					.flatMap(t -> t.sentences.stream())
					.flatMap(s -> s.candidates.values().stream())
					.flatMap(List::stream)
					.collect(toList());

			Context context = new Context(sentences, candidates_list, language, options.min_context_freq, options.window_size);
			corpus.resouces = new DocumentResourcesFactory(resources, options, candidates_list, context, glosses);
		}
		else
		{
			for (int i = 0; i < corpus.texts.size(); ++i)
			{
				final Text text = corpus.texts.get(i);
				log.info("\tResources for " + text.id  + " ( " + text.filename +  ")");
				Stopwatch text_timer = Stopwatch.createStarted();

				final List<Candidate> text_candidates = text.sentences.stream()
						.flatMap(s -> s.candidates.values().stream().flatMap(List::stream))
						.collect(toList());

				Context context = new Context(text.sentences, text_candidates, language, options.min_context_freq, options.window_size);
				text.resources = new DocumentResourcesFactory(resources, options, text_candidates, context, glosses);
				log.info("\tResources collected in " + text_timer.stop());

			}
		}
		log.info("Document resources created in " + timer.stop());

		return corpus;
	}

	public static void reset(Corpus corpus)
	{
		corpus.texts.forEach(t ->
			t.sentences.forEach(s ->
			{
				s.disambiguated.clear();
				s.candidates.values().forEach(m ->
						m.forEach(c -> c.setWeight(0.0)));
			}));
	}

	public static void rankMeanings(Options options, Corpus corpus)
	{
		log.info(options);

		List<DocumentResourcesFactory> texts_resources = new ArrayList<>();
		List<List<Sentence>> sentence_sets = new ArrayList<>();
		if (corpus.resouces != null)
		{
			texts_resources.add(corpus.resouces);
			final List<Sentence> sentences = corpus.texts.stream()
					.flatMap(text -> text.sentences.stream())
					.collect(toList());
			sentence_sets.add(sentences);
		}
		else
		{
			corpus.texts.forEach(text -> texts_resources.add(text.resources));
			corpus.texts.stream()
					.map(t -> t.sentences)
					.forEach(sentence_sets::add);
		}

		for (int i=0; i < sentence_sets.size(); ++i)
		{
			DocumentResourcesFactory resources = texts_resources.get(i);
			final BiasFunction bias = resources.getBiasFunction();
			final SimilarityFunction similarity = resources.getSimilarityFunction();
			final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter();
			final BiPredicate<String, String> similarity_filter = resources.getMeaningPairsSimilarityFilter();

			final List<Sentence> sentences = sentence_sets.get(i);
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
	}

	public static void disambiguate(Corpus corpus)
	{
		corpus.texts.forEach(text -> {
			final List<Candidate> candidates = text.sentences.stream()
					.flatMap(sentence -> sentence.candidates.values().stream()
							.flatMap(Collection::stream))
					.collect(toList());

			final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates);
			disambiguated.forEach((m, c) -> text.sentences.stream()
					.filter(s -> s.id.equals(m.getSourceId()))
					.findFirst()
					.orElseThrow()
					.disambiguated.put(m, c));
		});
	}

	// Ranking mentions based on their position in the text
	public static void rankMentions(Options options, Corpus corpus)
	{
		corpus.texts.forEach(text ->
		{
			// single word word_mentions are the item to rank
			final List<Mention> word_mentions = text.sentences.stream()
							.flatMap(s -> s.mentions.stream()
									.filter(not(Mention::isMultiWord))
									.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
					.collect(toList());

			// determine their meanings...
			final Map<Mention, Candidate> all_meanings = text.sentences.stream()
							.flatMap(s -> s.disambiguated.values().stream())
					.collect(toMap(Candidate::getMention, c -> c));

			// ... single-word meanings
			final Map<Mention, Candidate> word_meanings = all_meanings.entrySet().stream()
					.filter(e -> word_mentions.contains(e.getKey()))
					.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

			final Map<Mention, Candidate> multiword_meanings = all_meanings.entrySet().stream()
					.filter(e -> e.getKey().isMultiWord())
					.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

			// ... or part of a multiword with a meaning
			multiword_meanings.forEach((mw, c) -> word_mentions.stream()
					.filter(w -> w.getSourceId().equals(mw.getSourceId()) &&
							mw.getSpan().getLeft() <= w.getSpan().getLeft() &&
							mw.getSpan().getRight() >= w.getSpan().getRight())
					.forEach(w -> {
						if (!word_meanings.containsKey(w))
							word_meanings.put(w, c);
						else
						{
							// in the case of overlapping multiwords, a word may be assigned two candidates
							// choose that with highest weight
							final Candidate c2 = word_meanings.get(w);
							if (c.getWeight().orElse(0.0) > c2.getWeight().orElse(0.0))
								word_meanings.put(w, c);
						}
					}));

			final int context_size = 1;
			BiPredicate<Mention, Mention> adjacency_function = (m1, m2) ->
			{
				if (m1.equals(m2))
					return false;

				// Adjacent words?
				if (m1.getSourceId().equals(m2.getSourceId()) &&
						(Math.abs(word_mentions.indexOf(m1) - word_mentions.indexOf(m2)) <= context_size))
					return true;

				// Same lemma=?
				if (m1.getLemma().equals(m2.getLemma()))
					return true;

				// Same meaning ?
				if (!word_meanings.containsKey(m1) || !word_meanings.containsKey(m2))
					return false;

				return word_meanings.get(m1).getMeaning().equals(word_meanings.get(m2).getMeaning());
			};

			final Map<Mention, Optional<Double>> words2weights = word_mentions.stream()
					.collect(toMap(m -> m, m -> word_meanings.containsKey(m) ? word_meanings.get(m).getWeight() : Optional.empty()));

			TextPlanner.rankMentions(words2weights, adjacency_function, options);
		});
	}

	public static void plan(Options options, Corpus corpus)
	{
		corpus.texts.forEach(text ->
		{
			final SimilarityFunction sim = corpus.resouces != null ? corpus.resouces.getSimilarityFunction() : text.resources.getSimilarityFunction();
			final Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(text.graph, new DSyntSemantics(), options);
			final Collection<SemanticSubgraph> selected_subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, sim, options);
			text.subgraphs = TextPlanner.sortSubgraphs(subgraphs, sim, options);
		});
	}

	public static List<Mention> collectMentions(Text text, int max_span_size, String noun_pos_prefix,
	                                            Set<String> ignore_pos_tags, ULocale language)
	{
		// create mention objects
		return text.sentences.stream()
				.map(s -> IntStream.range(0, s.tokens.size())
						.mapToObj(start -> IntStream.range(start + 1, min(start + max_span_size + 1, s.tokens.size() + 1))
								.filter(end ->
								{
									// single words must have a pos tag other than 'X'
									if (end - start == 1)
										return !ignore_pos_tags.contains(s.tokens.get(start).pos);
										// multiwords must contain at least one nominal token
									else
										return s.tokens.subList(start, end).stream()
												.anyMatch(t -> t.pos.startsWith(noun_pos_prefix));
								})
								.mapToObj(end ->
								{
									final List<Token> tokens = s.tokens.subList(start, end);
									final String form = tokens.stream()
											.map(t -> t.wf)
											.collect(joining(" "));
									final String lemma = tokens.stream()
											.map(t -> t.lemma != null ? t.lemma : t.wf)
											.collect(joining(" "));
									String pos = tokens.size() == 1 ? tokens.get(0).pos : noun_pos_prefix;
									String contextId = tokens.size() == 1 ? tokens.get(0).id : tokens.get(0).id + "-" + tokens.get(tokens.size()-1).id;

									return new Mention(contextId, s.id, Pair.of(start, end), form, lemma, pos, false, "");
								})
								.filter(m -> FunctionWordsFilter.test(m.getSurface_form(), language)))
						.flatMap(stream -> stream))
				.flatMap(stream -> stream)
				.collect(toList());
	}

	public static void printMeaningRankings(Corpus corpus, Map<String, Set<String>> gold, boolean multiwords_only, Set<String> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final Text text = corpus.texts.get(i);
			final Set<String> text_gold = gold.get(text.id);

			final int max_length = text.sentences.stream()
					.flatMap(s -> s.candidates.values().stream())
					.flatMap(Collection::stream)
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			final Function<String, Double> weighter =
					corpus.resouces != null ? corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();

			final Map<Meaning, Double> weights = text.sentences.stream()
					.flatMap(s -> s.candidates.values().stream())
					.flatMap(Collection::stream)
					.filter(m -> (multiwords_only && m.getMention().isMultiWord()) || (!multiwords_only && eval_POS.contains(m.getMention().getPOS())))
					.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));

			final List<Meaning> meanings = new ArrayList<>(weights.keySet());
			Function<Meaning, String> inGold = m -> text_gold.contains(m.getReference()) ? "GOLD" : "";

			log.info(meanings.stream()
					.filter(m -> weights.get(m) > 0.0)
					.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
					.map(m -> String.format("%-" + max_length + "s%-11s%-11s%-8s",
							m.toString(),
							DebugUtils.printDouble(weights.get(m)),
							DebugUtils.printDouble(weighter.apply(m.getReference())),
							inGold.apply(m)))
					.collect(joining("\n\t", "Meaning ranking by ranking score:\n\t",
							"\n--------------------------------------------------------------------------------------------")));
		});
	}

	public static void printDisambiguationResults(Corpus corpus, Function<Mention, Set<String>> gold, boolean multiwords_only, Set<String> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final Text text = corpus.texts.get(i);

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

	public static void writeDisambiguatedResultsToFile(Corpus corpus, Path output_file)
	{
		final String str = corpus.texts.stream()
				.map(text -> text.sentences.stream()
						.map(sentence -> sentence.disambiguated.keySet().stream()
								.sorted(Comparator.comparing(Mention::getContextId))
								.map(mention ->
								{
									String id = mention.getContextId();
									if (id.contains("-"))
									{
										final String[] parts = id.split("-");
										id = parts[0] + "\t" + parts[1];
									}
									else
										id = id + "\t" + id;
									return id + "\t" +
											sentence.disambiguated.get(mention).getMeaning().getReference() + "\t\"" +
											mention.getSurface_form() + "\"";
								})
								.collect(joining("\n")))
						.collect(joining("\n")))
				.collect(joining("\n"));

		FileUtils.writeTextToFile(output_file, str);
	}
}
