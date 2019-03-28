package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

public class EvaluationTools
{
	@XmlRootElement(name = "corpus")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Corpus
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
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens;
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

	public static class Resources
	{
		EvaluationTools.Corpus corpus = null;
		List<List<List<List<Candidate>>>> candidates = new ArrayList<>();
		List<Function<String, Double>> weighters = new ArrayList<>();
	}

	public static final String noun_pos_tag = "N";
	public static final String adj_pos_tag = "J";
	public static final String verb_pos_tag = "V";
	public static final String adverb_pos_tag = "R";
	public static final String other_pos_tag = "X";
	private static final String candidates_file = "candidates.bin";
	private static final String contexts_file = "contexts.bin";
	private final static Logger log = LogManager.getLogger();


	public static Resources loadResources(Path xml_file, Path output_path, ResourcesFactory resource_factory,
	                                      ULocale language, int max_span_size, Set<String> exclude_pos_tags,
	                                      int context_size)
			throws IOException, ClassNotFoundException, JAXBException
	{
		Resources test_resources = new Resources();

		log.info("Parsing XML file");
		test_resources.corpus = EvaluationTools.parse(xml_file);

		final Path candidates_path = output_path.resolve(candidates_file);
		final Path contexts_path = output_path.resolve(contexts_file);
		if (Files.exists(candidates_path))
		{
			log.info("Loading candidates from " + candidates_path);
			test_resources.candidates =
					((List<List<List<List<Candidate>>>>) Serializer.deserialize(candidates_path)).stream().map(text ->
							text.stream().map(sentence ->
									sentence.stream().map(mention ->
											mention.stream().
													distinct().
													collect(toList()))
											.collect(toList()))
									.collect(toList()))
							.collect(toList());
		}
		else
		{
			log.info("Collecting mentions");
			Stopwatch timer = Stopwatch.createStarted();
			final List<List<List<Mention>>> mentions =
					collectMentions(test_resources.corpus, max_span_size, exclude_pos_tags);
			log.info("Mentions collected in " + timer.stop());

			log.info("Looking up meanings");
			timer.reset(); timer.start();
			test_resources.candidates =
					collectCandidates(resource_factory.getDictionary(), mentions, language).stream()
							.map(text -> text.stream().map(sentence ->
									sentence.stream().map(mention ->
											mention.stream().
													distinct().
													collect(toList()))
											.collect(toList()))
									.collect(toList()))
							.collect(toList());
			log.info("Meanings looked up in " + timer.stop());

			Serializer.serialize(test_resources.candidates, candidates_path);
			log.info("Candidates saved in " + candidates_path);
		}

		if (Files.exists(contexts_path))
		{
			log.info("Loading contexts from " + contexts_path);
			test_resources.weighters = (List<Function<String, Double>>) Serializer.deserialize(contexts_path);
		}
		else
		{
			log.info("Creating context weighters");
			Stopwatch timer = Stopwatch.createStarted();
			for (int i=0; i < test_resources.corpus.texts.size(); ++i)
			{
				final List<String> text_tokens = test_resources.corpus.texts.get(i).sentences.stream()
						.flatMap(s -> s.tokens.stream())
						.map(t -> t.wf)
						.collect(toList());
				final List<Candidate> text_candidates = test_resources.candidates.get(i).stream()
						.flatMap(l -> l.stream()
								.flatMap(Collection::stream))
						.collect(toList());

				final Function<String, Double> weigther = resource_factory.getMeaningsWeighter(text_tokens, text_candidates, context_size);
				test_resources.weighters.add(weigther);
			}
			log.info("Contexts created in " + timer.stop());
			Serializer.serialize(test_resources.weighters, contexts_path);
			log.info("Contexts saved in " + contexts_path);
		}

		return test_resources;
	}

	public static void full_rank(Options options, List<List<List<List<Candidate>>>> candidates, List<Function<String, Double>> weighters,
	                             ResourcesFactory resources, Set<String> exclude_pos_tags)
	{
		log.info(options);

		// Rank candidates
		IntStream.range(0, candidates.size())
				.forEach(text_i ->
				{
					final List<Candidate> text_candidates = candidates.get(text_i).stream()
							.flatMap(l -> l.stream()
									.flatMap(Collection::stream))
							.collect(toList());
					final Function<String, Double> weighter = weighters.get(text_i);
					final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter(text_candidates,
							weighter, options.num_first_meanings, options.context_threshold, exclude_pos_tags);
					final BiFunction<String, String, OptionalDouble> sim = resources.getMeaningsSimilarity();
					final BiPredicate<String, String> meanings_filter = resources.getMeaningsFilter(text_candidates);

					// Log stats
					final int num_mentions = candidates.get(text_i).stream()
							.flatMap(l -> l.stream()
									.filter(l2 -> !l2.isEmpty()))
							.collect(toMap(l -> l.get(0).getMention(), l -> l)).size();
					final long num_filtered_candidates = text_candidates.stream()
							.filter(candidates_filter)
							.count();
					final long num_meanings = text_candidates.stream()
							.filter(candidates_filter)
							.map(Candidate::getMeaning)
							.distinct()
							.count();
					log.info("Ranking document " + (text_i + 1) + " with " + num_mentions + " mentions, " +
							text_candidates.size() + " candidates, " +
							num_filtered_candidates + " candidates after filtering, and " +
							num_meanings + " distinct meanings");

					// Rank
					TextPlanner.rankMeanings(text_candidates, candidates_filter, meanings_filter, weighter, sim, options);
				});
	}

	private static Corpus parse(Path xml_file) throws JAXBException
	{
		JAXBContext jc = JAXBContext.newInstance(Corpus.class);
		StreamSource xml = new StreamSource(xml_file.toString());
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
		return je1.getValue();
	}

	public static List<List<List<Mention>>> collectMentions(Corpus corpus, int max_span_size, Set<String> exclude_pos_tags)
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
								.flatMap(stream -> stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<List<Candidate>>>> collectCandidates(MeaningDictionary bn, List<List<List<Mention>>> mentions, ULocale language)
	{
		log.info("Collecting mentions");
		final Map<Triple<String, String, String>, List<Mention>> forms2mentions = mentions.stream()
				.flatMap(l -> l.stream()
						.flatMap(Collection::stream))
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		log.info("\tQuerying " + forms2mentions.keySet().size() + " forms");
		final Map<Triple<String, String, String>, List<Meaning>> forms2meanings = forms2mentions.keySet().stream()
				.collect(toMap(t -> t, t -> getSynsets(bn, t, language).stream()
						.map(meaning -> Meaning.get(meaning, bn.getLabel(meaning, language).orElse(""), false))
						.collect(toList())));

		// use traditional loops to make sure that the order of lists of candidates is preserved
		Map<Mention, List<Candidate>> mentions2candidates = new HashMap<>();
		for (Triple<String, String, String> t : forms2mentions.keySet())
		{
			final List<Mention> form_mentions = forms2mentions.get(t);
			final List<Meaning> form_meanings = forms2meanings.get(t);
			for (Mention mention : form_mentions)
			{
				final List<Candidate> mention_candidates = form_meanings.stream().map(meaning -> new Candidate(mention, meaning)).collect(toList());
				mentions2candidates.put(mention, mention_candidates);
			}
		}

		return mentions.stream()
				.map(text_mentions ->
						text_mentions.stream()
								.map(sentence_mentions ->
										sentence_mentions.stream()
												.map(mentions2candidates::get)
												.collect(toList()))
								.collect(toList()))
				.collect(toList());
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
