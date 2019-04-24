package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.weighting.WeightFunction;
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
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
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
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences = new ArrayList<>();
		public DocumentResourcesFactory resources;
		public SemanticGraph graph = null;
		public List<SemanticSubgraph> subgraphs = new ArrayList<>();
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens = new ArrayList<>();
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

	private static final String candidates_file = "candidates.bin";
	private static final String glosses_file = "glosses.bin";
	private final static Logger log = LogManager.getLogger();

	public static Corpus loadResourcesFromXMI(Path xmi_folder, Path output_path, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, String noun_pos_prefix, Options options)
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

		return loadResources(corpus, output_path, resource_factory, language, max_span_size, noun_pos_prefix, options);
	}

	public static Corpus loadResourcesFromXML(Path xml_file, Path output_path, InitialResourcesFactory resource_factory,
	                                          ULocale language, int max_span_size, String noun_pos_prefix, Options options)
	{
		log.info("Parsing XML file");

		try
		{
			JAXBContext jc = JAXBContext.newInstance(Corpus.class);
			StreamSource xml = new StreamSource(xml_file.toString());
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
			final Corpus corpus = je1.getValue();

			return loadResources(corpus, output_path, resource_factory, language, max_span_size, noun_pos_prefix, options);

		}
		catch (JAXBException e)
		{
			log.error("Cannot parse file " + xml_file + ": " + e);
		}

		return new Corpus();
	}


	private static Corpus loadResources(Corpus corpus, Path output_path, InitialResourcesFactory resources,
	                                    ULocale language, int max_span_size, String noun_pos_prefix, Options options)
	{
		try
		{
			final Path candidates_path = output_path.resolve(candidates_file);
			final Path glosses_path = output_path.resolve(glosses_file);
			List<Candidate> candidates;
			if (Files.exists(candidates_path))
			{
				log.info("Loading candidates from " + candidates_path);
				Stopwatch timer = Stopwatch.createStarted();
				candidates = (List<Candidate>) Serializer.deserialize(candidates_path);
				log.info("Candidates loaded in " + timer.stop());
			}
			else
			{
				log.info("Collecting mentions");
				Stopwatch timer = Stopwatch.createStarted();
				final List<Mention> mentions = collectMentions(corpus, max_span_size, noun_pos_prefix, options.excluded_POS_Tags, language);
				log.info("Mentions collected in " + timer.stop());

				log.info("Looking up meanings");
				timer.reset();
				timer.start();
				candidates = CandidatesCollector.collect(resources.getDictionary(), language, mentions);
				log.info("Meanings looked up in " + timer.stop());

				Serializer.serialize(candidates, candidates_path);
				log.info("Candidates saved to " + candidates_path);
			}

			{
				log.info("Assigning candidates to mentions");
				Stopwatch timer = Stopwatch.createStarted();
				corpus.texts.forEach(text ->
				{
					final Map<Mention, List<Candidate>> text_candidates = candidates.stream()
							.filter(c -> c.getMention().getContextId().startsWith(text.id + "."))
							.collect(groupingBy(Candidate::getMention));

					text_candidates.forEach((mention, mention_candidates) ->
					{
						final String mention_context_id = mention.getContextId();
						text.sentences.forEach(sentence ->
						{
							if (mention_context_id.equals(sentence.id))
								sentence.candidates.put(mention, mention_candidates);
						});
					});
				});
				log.info("Assignment done in " + timer.stop());
			}

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
					text_candidates.stream()
							.map(Candidate::getMeaning)
							.map(Meaning::getReference)
							.distinct()
							.forEach(r -> glosses.computeIfAbsent(r, k -> resources.getDictionary().getGlosses(k, resources.getLanguage())));
				}
				log.info("Glosses queried in " + timer.stop());
				Serializer.serialize(glosses, glosses_path);
				log.info("Glosses saved to " + glosses_path);
			}

			log.info("Creating document resources");
			Stopwatch timer = Stopwatch.createStarted();
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
			log.info("Document resources created in " + timer.stop());
		}
		catch (Exception e)
		{
			log.error("Cannot load resources: " + e);
			e.printStackTrace();
		}

		return corpus;
	}

	public static void rankMeanings(Options options, Corpus corpus, InitialResourcesFactory resources)
	{
		log.info(options);

		// Rank candidates
		IntStream.range(0, corpus.texts.size())
				.forEach(i ->
				{
					final List<Sentence> sentences = corpus.texts.get(i).sentences;
					final List<Candidate> text_candidates = sentences.stream()
							.flatMap(s -> s.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					DocumentResourcesFactory doc_resources = corpus.texts.get(i).resources;
					final WeightFunction weighter = doc_resources.getMeaningsWeighter();
					final Predicate<Candidate> candidates_filter = doc_resources.getCandidatesFilter();
					final SimilarityFunction sim = resources.getMeaningsSimilarity();
					final BiPredicate<String, String> meanings_filter = doc_resources.getMeaningsFilter();

					// Log stats
					final int num_mentions = sentences.stream()
							.map(s -> s.candidates)
							.mapToInt(m -> m.keySet().size())
							.sum();
					final long num_filtered_candidates = text_candidates.stream()
							.filter(candidates_filter)
							.count();
					final long num_meanings = text_candidates.stream()
							.filter(candidates_filter)
							.map(Candidate::getMeaning)
							.distinct()
							.count();
					log.info("Ranking document " + (i + 1) + " with " + num_mentions + " mentions, " +
							text_candidates.size() + " candidates, " +
							num_filtered_candidates + " candidates after filtering, and " +
							num_meanings + " distinct meanings");

					// Rank
					TextPlanner.rankMeanings(text_candidates, candidates_filter, meanings_filter, weighter, sim, options);
				});
	}

	public static void disambiguate(Corpus corpus)
	{
		corpus.texts.forEach(text ->
		{
			final List<Candidate> text_candidates = text.sentences.stream()
					.flatMap(sentence -> sentence.candidates.values().stream())
					.flatMap(List::stream)
					.collect(toList());
			final Map<Mention, Candidate> selected_candidates = Disambiguation.disambiguate(text_candidates);
			text.sentences.forEach(sentence -> {
				sentence.candidates.clear();
				selected_candidates.forEach((m, c) -> sentence.candidates.put(m, List.of(c)));
			});
		});
	}

		public static void rankMentions(Options options, Corpus corpus)
		{
			final int context_size = 3;
			corpus.texts.forEach(text ->
					text.sentences.forEach(sentence ->
					{
						final List<Candidate> candidates = sentence.candidates.values().stream()
								.flatMap(List::stream)
								.collect(toList());
						final List<Mention> mentions = candidates.stream().map(Candidate::getMention).collect(toList());
						BiPredicate<Mention, Mention> adjacency_function =
								(m1, m2) -> Math.abs(mentions.indexOf(m1) - mentions.indexOf(m2)) <= context_size;
						BiPredicate<Mention, Mention> adjacency_function2 =
								(m1, m2) -> text.graph.containsEdge(m1.toString(), m2.toString());

						// rank mentions
						TextPlanner.rankMentions(candidates, adjacency_function, options);
					}));
		}

		public static void plan(Options options, Corpus corpus, InitialResourcesFactory resources)
		{
			corpus.texts.forEach(text ->
			{
				final Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(text.graph, new DSyntSemantics(), options);
				final Collection<SemanticSubgraph> selected_subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, resources.getMeaningsSimilarity(), options);
				text.subgraphs = TextPlanner.sortSubgraphs(subgraphs, resources.getMeaningsSimilarity(), options);
			});
		}

		public static List<Mention> collectMentions(Corpus corpus, int max_span_size, String noun_pos_prefix,
		                                            Set<String> exclude_pos_tags, ULocale language)
		{
			// create mention objects
			return corpus.texts.stream()
					.map(d -> d.sentences.stream()
							.map(s -> IntStream.range(0, s.tokens.size())
									.mapToObj(start -> IntStream.range(start + 1, Math.min(start + max_span_size + 1, s.tokens.size()))
											.filter(end ->
											{
												// single words must have a pos tag other than 'X'
												if (end - start == 1)
													return !exclude_pos_tags.contains(s.tokens.get(start).pos);
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

												return new Mention(s.id, Pair.of(start, end), form, lemma, pos, false, "");
											})
											.filter(m -> FunctionWordsFilter.test(m.getSurface_form(), language)))
									.flatMap(stream -> stream))
							.flatMap(stream -> stream))
					.flatMap(stream -> stream)
					.collect(toList());
		}
	}
