package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.ProcessResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.uima.io.UIMAWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
import java.util.stream.Collectors;
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
		public List<Text> texts;
		public SemanticGraph graph = null;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences;
		public Function<String, Double> weighter;
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
			corpus.graph = d.getSemanticGraph();
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
				candidates = collectCandidates(resources.getDictionary(), mentions, language);
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

	public static Map<Mention, Double> rankMentions(Options options, List<Candidate> candidates,
	                                BiPredicate<String, String> adjacency_function,
	                                BinaryOperator<String> labelling_function)
	{
		Function<Candidate, String> createVertexId = c -> c.getMention().getId();
		final Map<String, Meaning> meanings = candidates.stream()
				.collect(toMap(createVertexId, Candidate::getMeaning));
		final Map<String, Double> weights = candidates.stream()
				.collect(groupingBy(createVertexId, averagingDouble(Candidate::getWeight)));
		final Multimap<String, Mention> mentions = HashMultimap.create();
		candidates.forEach(c -> mentions.put(createVertexId.apply(c), c.getMention()));
		final SemanticGraph g = new SemanticGraph(meanings, weights, mentions, adjacency_function, labelling_function);
		TextPlanner.rankVertices(g, options);

		final Map<Mention, Double> weighted_mentions = new HashMap<>();
		mentions.keySet().forEach(v ->
				mentions.get(v).forEach(m ->
						weighted_mentions.put(m, g.getWeight(v))));

		return weighted_mentions;
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

	public static List<Candidate> collectCandidates(MeaningDictionary bn, List<Mention> mentions, ULocale language)
	{
		log.info("Collecting mentions");
		final Map<Triple<String, String, String>, List<Mention>> forms2mentions = mentions.stream()
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		log.info("\tQuerying " + forms2mentions.keySet().size() + " forms");
		final Map<Triple<String, String, String>, List<Meaning>> forms2meanings = forms2mentions.keySet().stream()
				.collect(toMap(t -> t, t -> getSynsets(bn, t, language).stream()
						.map(meaning -> Meaning.get(meaning, bn.getLabel(meaning, language).orElse(""), false))
						.collect(toList())));

		// use traditional loops to make sure that the order of lists of candidates is preserved
		final List<Candidate> candidates = new ArrayList<>();
		for (Triple<String, String, String> t : forms2mentions.keySet())
		{
			final List<Mention> form_mentions = forms2mentions.get(t);
			final List<Meaning> form_meanings = forms2meanings.get(t);
			for (Mention mention : form_mentions)
			{
				final List<Candidate> mention_candidates = form_meanings.stream().map(meaning -> new Candidate(mention, meaning)).collect(toList());
				candidates.addAll(mention_candidates);
			}
		}

		return candidates;
	}

	/**
	 * Returns sorted list of meanings for mention using lemma and form
	 */
	private static List<String> getSynsets(MeaningDictionary bn, Triple<String, String, String> mention, ULocale language)
	{
		// Use surface form of mention as label
		String form = mention.getLeft();
		String lemma = mention.getMiddle();
		String pos = mention.getRight();
		// Lemma meanings first, sorted by dictionary criteria (BabelNet -> best sense)
		List<String> synsets = bn.getMeanings(lemma, pos, language);

		// Form meanings go after
		if (!lemma.equalsIgnoreCase(form))
		{
			List<String> lemma_synsets = bn.getMeanings(form, pos, language);
			lemma_synsets.removeAll(synsets);
			synsets.addAll(lemma_synsets);
		}

		return synsets;
	}
}
