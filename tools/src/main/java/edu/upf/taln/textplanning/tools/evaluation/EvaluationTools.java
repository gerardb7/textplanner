package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.*;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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
		Map<Mention, List<Candidate>> candidates = new HashMap<>();
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

	private static final String candidates_filename = "candidates";
	private static final String binary_extension = ".bin";
	private static final String glosses_filename = "glosses";
	private final static Logger log = LogManager.getLogger();

	public static Corpus loadResourcesFromXMI(Path xmi_folder, Path output_path, InitialResourcesFactory resource_factory,
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

		return loadResources(corpus, output_path, resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);
	}

	public static Corpus loadResourcesFromXML(Path xml_file, Path output_path, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                          Set<String> ignored_POS_Tags, Options options)
	{
		log.info("Parsing XML file");

		try
		{
			JAXBContext jc = JAXBContext.newInstance(Corpus.class);
			StreamSource xml = new StreamSource(xml_file.toString());
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
			final Corpus corpus = je1.getValue();

			return loadResources(corpus, output_path, resource_factory, language, max_span_size, rank_together, noun_pos_prefix, ignored_POS_Tags, options);

		}
		catch (JAXBException e)
		{
			log.error("Cannot parse file " + xml_file + ": " + e);
		}

		return new Corpus();
	}


	private static Corpus loadResources(Corpus corpus, Path output_path, InitialResourcesFactory resources,
	                                    ULocale language, int max_span_size, boolean rank_together, String noun_pos_prefix,
	                                    Set<String> ignored_POS_Tags, Options options)
	{
		try
		{
			final Path candidates_path = output_path.resolve(candidates_filename + "_" + language.toLanguageTag() + binary_extension);
			final Path glosses_path = output_path.resolve(glosses_filename + "_" + language.toLanguageTag() + binary_extension);
			Map<Mention, List<Candidate>> candidates;
			if (Files.exists(candidates_path))
			{
				log.info("Loading candidates from " + candidates_path);
				Stopwatch timer = Stopwatch.createStarted();
				candidates = (Map<Mention, List<Candidate>>) Serializer.deserialize(candidates_path);
				log.info("Candidates loaded in " + timer.stop());
			}
			else
			{
				log.info("Collecting mentions");
				Stopwatch timer = Stopwatch.createStarted();
				final List<Mention> mentions = collectMentions(corpus, max_span_size, noun_pos_prefix, ignored_POS_Tags, language);
				log.info("Mentions collected in " + timer.stop());

				log.info("Looking up meanings");
				timer.reset();
				timer.start();
				candidates = CandidatesCollector.collect(resources.getDictionary(), language, mentions);
				log.info("Meanings looked up in " + timer.stop());

				Serializer.serialize(candidates, candidates_path);
				log.info("Candidates saved to " + candidates_path);
			}

			// assign mentions and candidates to corpus objects
			corpus.texts.forEach(text ->
					text.sentences.forEach(sentence ->
							candidates.entrySet().stream()
									.filter(e -> e.getKey().getSourceId().equals(sentence.id))
									.forEach(e -> sentence.candidates.put(e.getKey(), e.getValue()))));

			Map<String, List<String>> glosses;
			if (Files.exists(glosses_path))
			{
				log.info("Loading glosses from " + glosses_path);
				Stopwatch timer = Stopwatch.createStarted();
				glosses = (Map<String, List<String>>) Serializer.deserialize(glosses_path);
				log.info("Glosses loaded in " + timer.stop());
			}
			else
			{
				log.info("Querying glosses");
				Stopwatch timer = Stopwatch.createStarted();
				glosses = new HashMap<>();
				for (int i = 0; i < corpus.texts.size(); ++i)
				{
					final List<Candidate> text_candidates = corpus.texts.get(i).sentences.stream()
							.flatMap(s -> s.candidates.values().stream().flatMap(List::stream))
							.collect(toList());
					final Set<String> meanings = text_candidates.stream()
							.map(Candidate::getMeaning)
							.map(Meaning::getReference)
							.collect(toSet());
					meanings.addAll(resources.getBiasMeanings());
					meanings.forEach(r -> glosses.computeIfAbsent(r, k -> resources.getDictionary().getGlosses(k, resources.getLanguage())));
				}
				log.info("Glosses queried in " + timer.stop());
				Serializer.serialize(glosses, glosses_path);
				log.info("Glosses saved to " + glosses_path);
			}

			log.info("Creating document resources");
			Stopwatch timer = Stopwatch.createStarted();

			if (rank_together)
			{
				final List<Candidate> candidates_list = candidates.values().stream()
						.flatMap(List::stream)
						.collect(toList());
				final List<String> tokens = corpus.texts.stream()
						.flatMap(text -> text.sentences.stream()
								.flatMap(s -> s.tokens.stream()))
						.map(t -> t.wf)
						.collect(toList());
				corpus.resouces = new DocumentResourcesFactory(resources, options, candidates_list, tokens, glosses);
			}
			else
			{
				for (int i = 0; i < corpus.texts.size(); ++i)
				{
					final List<String> text_tokens = corpus.texts.get(i).sentences.stream()
							.flatMap(s -> s.tokens.stream())
							.map(t -> t.wf)
							.collect(toList());
					final List<Candidate> text_candidates = corpus.texts.get(i).sentences.stream()
							.flatMap(s -> s.candidates.values().stream().flatMap(List::stream))
							.collect(toList());

					corpus.texts.get(i).resources = new DocumentResourcesFactory(resources, options, text_candidates, text_tokens, glosses);
				}
			}
			log.info("Document resources created in " + timer.stop());
		}
		catch (Exception e)
		{
			log.error("Cannot load resources: " + e);
			e.printStackTrace();
		}

		return corpus;
	}

	public static void rankMeanings(Options options, Corpus corpus)
	{
		log.info(options);

		List<DocumentResourcesFactory> texts_resources = new ArrayList<>();
		if (corpus.resouces != null)
			texts_resources.add(corpus.resouces);
		else
			corpus.texts.forEach(text -> texts_resources.add(text.resources));

		for (DocumentResourcesFactory resources : texts_resources)
		{
			final BiasFunction bias = resources.getBiasFunction();
			final SimilarityFunction similarity = resources.getSimilarityFunction();
			final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter();
			final BiPredicate<String, String> similarity_filter = resources.getMeaningPairsSimilarityFilter();

			final List<Sentence> sentences = corpus.texts.stream()
					.flatMap(text -> text.sentences.stream())
					.collect(toList());
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

	public static void rankMentions(Options options, Corpus corpus)
	{
		final int context_size = 3;
		corpus.texts.forEach(text ->
				text.sentences.forEach(sentence ->
				{
					final List<Mention> mentions = List.copyOf(sentence.candidates.keySet());
					final Map<Mention, Candidate> candidates = new HashMap<>();
					sentence.candidates.forEach((m, l) -> {
						if (l.size() == 1)
							candidates.put(m, l.get(0));
						else
							log.error("Mention " + m + " has more than one candidate!");
					});

					BiPredicate<Mention, Mention> adjacency_function =
							(m1, m2) -> Math.abs(mentions.indexOf(m1) - mentions.indexOf(m2)) <= context_size;
//					BiPredicate<Mention, Mention> adjacency_function2 =
//							(m1, m2) -> text.graph.containsEdge(m1.toString(), m2.toString());

					// rank mentions
					TextPlanner.rankMentions(mentions, candidates, adjacency_function, options);
				}));
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

	public static List<Mention> collectMentions(Corpus corpus, int max_span_size, String noun_pos_prefix,
	                                            Set<String> ignore_pos_tags, ULocale language)
	{
		// create mention objects
		return corpus.texts.stream()
				.map(d -> d.sentences.stream()
						.map(s -> IntStream.range(0, s.tokens.size())
								.mapToObj(start -> IntStream.range(start + 1, Math.min(start + max_span_size + 1, s.tokens.size() + 1))
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
						.flatMap(stream -> stream))
				.flatMap(stream -> stream)
				.collect(toList());
	}
	}
