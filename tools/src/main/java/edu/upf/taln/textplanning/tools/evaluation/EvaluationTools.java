package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.ProcessResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.IntStream;

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
		public List<Text> texts;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences;
		public Function<String, Double> weighter;
		public SemanticGraph graph = null;
		public List<SemanticSubgraph> subgraphs = null;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens;
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

	public static final String noun_pos_tag = "N";
	public static final String adj_pos_tag = "J";
	public static final String verb_pos_tag = "V";
	public static final String adverb_pos_tag = "R";
	public static final String other_pos_tag = "X";
	private static final String text_suffix = "txt";
	private static final String candidates_file = "candidates.bin";
	private static final String contexts_file = "contexts.bin";
	private final static Logger log = LogManager.getLogger();

	public static Corpus loadResourcesFromText(Path text_files, Path output_path, InitialResourcesFactory resource_factory,
	                                      ULocale language, int max_span_size, Options options)
	{
		final File[] filesInFolder = FileUtils.getFilesInFolder(text_files, text_suffix);
		if (filesInFolder == null)
			return new Corpus();

		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createParsingPipeline(language);
		final List<UIMAWrapper> docs = Arrays.stream(filesInFolder)
				.map(File::toPath)
				.map(FileUtils::readTextFile)
				.map(text -> new UIMAWrapper(text, pipeline))
				.collect(toList());

		Corpus corpus = new Corpus();
		AtomicInteger doc_counter = new AtomicInteger(0);

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

		return loadResources(corpus, output_path, resource_factory, language, max_span_size, options);
	}

	public static Corpus loadResourcesFromXML(Path xml_file, Path output_path, InitialResourcesFactory resource_factory,
	                                      ULocale language, int max_span_size, Options options)
	{
		log.info("Parsing XML file");

		try
		{
			JAXBContext jc = JAXBContext.newInstance(Corpus.class);
			StreamSource xml = new StreamSource(xml_file.toString());
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
			final Corpus corpus = je1.getValue();

			return loadResources(corpus, output_path, resource_factory, language, max_span_size, options);

		}
		catch (JAXBException e)
		{
			log.error("Cannot parse file " + xml_file + ": " + e);
		}

		return new Corpus();
	}


	private static Corpus loadResources(Corpus corpus, Path output_path, InitialResourcesFactory resources,
	                                      ULocale language, int max_span_size, Options options)
	{
		try
		{
			final Path candidates_path = output_path.resolve(candidates_file);
			final Path contexts_path = output_path.resolve(contexts_file);
			List<Candidate> candidates;
			if (Files.exists(candidates_path))
			{
				log.info("Loading candidates from " + candidates_path);
				candidates = (List<Candidate>) Serializer.deserialize(candidates_path);
			}
			else
			{
				log.info("Collecting mentions");
				Stopwatch timer = Stopwatch.createStarted();
				final List<Mention> mentions = collectMentions(corpus, max_span_size, options.excluded_POS_Tags);
				log.info("Mentions collected in " + timer.stop());

				log.info("Looking up meanings");
				timer.reset();
				timer.start();
				candidates = CandidatesCollector.collect(resources.getDictionary(), language, mentions);
				log.info("Meanings looked up in " + timer.stop());

				Serializer.serialize(candidates, candidates_path);
				log.info("Candidates saved in " + candidates_path);
			}

			// assign candidates to corpus objects
			final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream().collect(groupingBy(Candidate::getMention));
			final Map<String, List<Mention>> contexts2mentions = mentions2candidates.keySet().stream().collect(groupingBy(Mention::getContextId));
			corpus.texts.forEach(t ->
					t.sentences.forEach(s ->
							contexts2mentions.keySet().stream()
									.filter(c -> c.equals(s.id))
									.map(contexts2mentions::get)
									.flatMap(List::stream)
									.forEach(m -> s.candidates.put(m, mentions2candidates.get(m)))));

			if (Files.exists(contexts_path))
			{
				log.info("Loading contexts from " + contexts_path);
				final List<Function<String, Double>> weigthers = (List<Function<String, Double>>) Serializer.deserialize(contexts_path);
				IntStream.range(0, corpus.texts.size())
						.forEach(i -> corpus.texts.get(i).weighter = weigthers.get(i));
			}
			else
			{
				log.info("Creating context weighters");
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
					ProcessResourcesFactory process = new ProcessResourcesFactory(resources, options, text_candidates, text_tokens, null);
					corpus.texts.get(i).weighter = process.getMeaningsWeighter();
				}
				log.info("Contexts created in " + timer.stop());
				Serializer.serialize(corpus.texts.stream().map(t -> t.weighter).collect(toList()), contexts_path);
				log.info("Contexts saved in " + contexts_path);
			}
		}
		catch (Exception e)
		{
			log.error("Cannot load resources: " + e);
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
					final List<String> tokens = sentences.stream()
							.flatMap(sentence -> sentence.tokens.stream()
									.map(t -> t.wf))
							.collect(toList());

					final Function<String, Double> weighter = corpus.texts.get(i).weighter;
					ProcessResourcesFactory process = new ProcessResourcesFactory(resources, options, text_candidates, tokens, null);
					final Predicate<Candidate> candidates_filter = process.getCandidatesFilter(weighter);
					final BiFunction<String, String, OptionalDouble> sim = resources.getMeaningsSimilarity();
					final BiPredicate<String, String> meanings_filter = process.getMeaningsFilter();

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
			text.sentences.forEach(sentence ->
					sentence.candidates.replaceAll((mention, old_candidates) -> List.of(selected_candidates.get(mention))));
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
							(m1, m2) -> text.graph.containsEdge(m1.getId(), m2.getId());

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

	public static List<Mention> collectMentions(Corpus corpus, int max_span_size, Set<String> exclude_pos_tags)
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
																.anyMatch(t -> t.pos.equals(noun_pos_tag));
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
													String pos = tokens.size() == 1 ? tokens.get(0).pos : noun_pos_tag;

													final String start_id = tokens.get(0).id;
													final String end_id = tokens.get(tokens.size() - 1).id;
													final String id = tokens.size() == 1 ? start_id : start_id + "-" + end_id;
													return Mention.get(id, Pair.of(start, end), form, lemma, pos, false, "");
												})
										/*.filter(m -> FunctionWordsFilter.filter(m, language))*/)
								.flatMap(stream -> stream))
						.flatMap(stream -> stream))
				.flatMap(stream -> stream)
				.collect(toList());
	}
}
