package edu.upf.taln.textplanning.tools;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
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

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

@SuppressWarnings("ALL")
public class SemEvalEvaluation
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

	private static class Resources
	{
		Map<String, String> gold;
		Corpus corpus;
		List<List<List<List<Candidate>>>> candidates;
		List<Function<String, Double>> weighters;
	}

	private static final int max_span_size = 3;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final ULocale language = ULocale.ENGLISH;
	private static final String candidates_file = "candidates.bin";
	private static final String contexts_file = "contexts.bin";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_file, Path xml_file, Path output_path, ResourcesFactory resources_factory) throws Exception
	{
		final Resources test_resources = loadResources(gold_file, xml_file, output_path, resources_factory);
		full_rank(new Options(), test_resources.candidates, test_resources.weighters, resources_factory, output_path, true);

		log.info("********************************");
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseRandom(test_resources.candidates);
			log.info("Random results:");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "random.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseFirst(test_resources.candidates);
			log.info("First sense results:");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "first.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContext(test_resources.candidates, test_resources.weighters);
			log.info("Context results:");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "context.results", false);
		}
		{
			final double context_threshold = 0.7;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(test_resources.candidates, test_resources.weighters, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "context_or_first_" + context_threshold + ".results", false);
		}
		{
			final double context_threshold = 0.8;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(test_resources.candidates, test_resources.weighters, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "context_or_first_" + context_threshold + ".results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRank(test_resources.candidates);
			log.info("Rank results:");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "rank.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(test_resources.candidates);
			log.info("Rank or first results:");
			evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "rank_or_first.results", false);
		}
		log.info("********************************");

		final int max_length = test_resources.candidates.stream()
				.flatMap(List::stream)
				.flatMap(List::stream)
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.mapToInt(String::length)
				.max().orElse(5) + 4;

		IntStream.range(0, test_resources.candidates.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final Function<String, Double> weighter = test_resources.weighters.get(i);
			final List<List<List<Candidate>>> text_candidates = test_resources.candidates.get(i);

			text_candidates.forEach(sentence_candidates ->
					sentence_candidates.forEach(mention_candidates ->
							SemEvalEvaluation.print_full_ranking(mention_candidates, weighter, test_resources.gold, true, max_length)));

			print_meaning_rankings(text_candidates, weighter, true, max_length);
		});
	}

	public static void run_batch(Path gold_file, Path xml_file, Path output_path, ResourcesFactory resources_factory) throws JAXBException, IOException, ClassNotFoundException
	{
		final Resources test_resources = loadResources(gold_file, xml_file, output_path, resources_factory);

		log.info("Ranking meanings (full)");
		final int num_values = 11; final double min_value = 0.0; final double max_value = 1.0;
		final double increment = (max_value - min_value) / (double)(num_values - 1);
		final List<Options> batch_options = IntStream.range(0, num_values)
				.mapToDouble(i -> min_value + i * increment)
				.filter(v -> v >= min_value && v <= max_value)
				.mapToObj(v ->
				{
					Options o = new Options();
					o.sim_threshold = v;
					return o;
				})
				.collect(toList());

		for (Options options : batch_options)
		{
			resetRanks(test_resources.candidates);
			full_rank(options, test_resources.candidates, test_resources.weighters, resources_factory, output_path, true);
			log.info("********************************");
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(test_resources.candidates);
				log.info("Rank results:");
				evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "rank." + options.toShortString() + ".results", false);
			}
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(test_resources.candidates);
				log.info("Rank or first results:");
				evaluate(test_resources.corpus, ranked_candidates, gold_file, xml_file, output_path, "rank." + options.toShortString() + ".results", false);
			}
			log.info("********************************");
		}
	}

	private static Resources loadResources(Path gold_file, Path xml_file, Path output_path,
	                                       ResourcesFactory resource_factory) throws IOException, ClassNotFoundException, JAXBException
	{
		Resources test_resources = new Resources();

		log.info("Parsing gold file");
		test_resources.gold = parseGoldFile(gold_file);
		log.info(test_resources.gold.keySet().size() + " lines read from gold");
		log.info("Parsing XML file");
		test_resources.corpus = parse(xml_file);

		final Path candidates_path = output_path.resolve(candidates_file);
		final Path contexts_path = output_path.resolve(contexts_file);
		if (Files.exists(candidates_path) && Files.exists(contexts_path))
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
					.collect(toList());;
			log.info("Loading contexts from " + contexts_path);
			test_resources.weighters = (List<Function<String, Double>>) Serializer.deserialize(contexts_path);
		}
		else
		{
			log.info("Collecting mentions");
			Stopwatch timer = Stopwatch.createStarted();
			final List<List<List<Mention>>> mentions = collectMentions(test_resources.corpus);
			log.info("Mentions collected in " + timer.stop());

			log.info("Looking up meanings");
			timer.reset(); timer.start();
			test_resources.candidates =
					collectCandidates(resource_factory.getDictionary(), test_resources.corpus, mentions).stream()
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
			log.info("Canidates saved in " + candidates_path);

			log.info("Creating contexts");
			timer.reset(); timer.start();
			for (int i=0; i < test_resources.corpus.texts.size(); ++i)
			{
				final List<String> tokens = mentions.get(i).stream()
						.flatMap(sentence_mentions -> sentence_mentions.stream()
								.filter(m -> !m.isMultiWord())
								.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
						.map(Mention::getSurface_form)
						.collect(toList());

				final List<Candidate> text_candidates = test_resources.candidates.get(i).stream()
						.flatMap(l -> l.stream()
								.flatMap(Collection::stream))
						.collect(toList());

				test_resources.weighters.add(resource_factory.getMeaningsWeighter(tokens, text_candidates));
			}
			log.info("Contexts created in " + timer.stop());
			Serializer.serialize(test_resources.weighters, contexts_path);
			log.info("Contexts saved in " + contexts_path);
		}

		return test_resources;
	}

	public static Corpus parse(Path xml_file) throws JAXBException
	{
		JAXBContext jc = JAXBContext.newInstance(Corpus.class);
		StreamSource xml = new StreamSource(xml_file.toString());
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
		return je1.getValue();
	}

	private static List<List<List<Mention>>> collectMentions(Corpus corpus)
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
												return !s.tokens.get(start).pos.equalsIgnoreCase(other_pos_tag);
												// multiwords must contain at least one nominal token
											else
												return s.tokens.subList(start, end).stream()
														.anyMatch(t -> t.pos.equalsIgnoreCase(noun_pos_tag));
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
										/*.filter(m -> StopWordsFilter.filter(m, language))*/)
								.flatMap(stream -> stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<List<Candidate>>>> collectCandidates(MeaningDictionary bn, Corpus corpus, List<List<List<Mention>>> mentions)
	{
		log.info("Collecting mentions");
		final Map<Triple<String, String, String>, List<Mention>> forms2mentions = mentions.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()))
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		log.info("\tQuerying " + forms2mentions.keySet().size() + " forms");
		final Map<Triple<String, String, String>, List<Meaning>> forms2meanings = forms2mentions.keySet().stream()
				.collect(toMap(t -> t, t -> getSynsets(bn, t).stream()
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
												.map(mention -> mentions2candidates.get(mention))
												.collect(toList()))
								.collect(toList()))
				.collect(toList());
	}

	/**
	 * Returns sorted list of meanings for mention using lemma and form
	 */
	private static List<String> getSynsets(MeaningDictionary bn, Triple<String, String, String> mention)
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


	private static void full_rank(Options options, List<List<List<List<Candidate>>>> candidates, List<Function<String, Double>> weighters,
								  ResourcesFactory resources, Path output_path, boolean print_debug) throws IOException
	{
		log.info(options);

		// Rank candidates
		IntStream.range(0, candidates.size())
				.forEach(text_i ->
				{
					final List<Candidate> text_candidates = candidates.get(text_i).stream()
							.flatMap(l -> l.stream()
									.flatMap(l2 -> l2.stream()))
							.collect(toList());
					final Function<String, Double> weighter = weighters.get(text_i);
					List<String> excludePos = new ArrayList<>();
					excludePos.add(other_pos_tag);
					excludePos.add(adverb_pos_tag);
					final Predicate<Candidate> candidates_filter = resources.getCandidatesFilter(text_candidates,
							weighter, options.num_first_meanings, options.context_threshold,
							excludePos);
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
	
	private static <T> Predicate<T> not(Predicate<T> t) { 
		return t.negate(); 
	}

	private static List<List<List<Candidate>>> chooseRandom(List<List<List<List<Candidate>>>> candidates)
	{
		// Choose top candidates:
		Random random = new Random();
		return IntStream.range(0, candidates.size())
				.mapToObj(i ->candidates.get(i).stream()
						.map(sentence -> sentence.stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates ->
								{
									final int j = random.nextInt(mention_candidates.size());
									return mention_candidates.get(j);
								})
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseFirst(List<List<List<List<Candidate>>>> candidates)
	{
		// Choose top candidates:
		Random random = new Random();
		return IntStream.range(0, candidates.size())
				.mapToObj(i -> candidates.get(i).stream()
						.map(sentence -> sentence.stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.get(0))
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopContext(List<List<List<List<Candidate>>>> candidates,
	                                                            List<Function<String, Double>> weighters)
	{
		// Choose top candidates:
		Random random = new Random();
		return IntStream.range(0, candidates.size())
				.mapToObj(i ->
				{
					final Function<String, Double> weighter = weighters.get(i);
					return candidates.get(i).stream()
							.map(sentence -> sentence.stream()
									.filter(not(List::isEmpty))
									.map(mention_candidates -> mention_candidates.stream()
											.max(comparingDouble(c -> weighter.apply(c.getMeaning().getReference()))))
									.filter(Optional::isPresent)
									.map(Optional::get)
									.collect(toList()))
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopContextOrFirst(List<List<List<List<Candidate>>>> candidates,
	                                                            List<Function<String, Double>> weighters, double threshold)
	{
		// Choose top candidates:
		Random random = new Random();
		return IntStream.range(0, candidates.size())
				.mapToObj(i ->
				{
					final Function<String, Double> weighter = weighters.get(i);
					return candidates.get(i).stream()
							.map(sentence -> sentence.stream()
									.filter(not(List::isEmpty))
									.map(mention_candidates -> mention_candidates.stream()
											.max(comparingDouble(c -> weighter.apply(c.getMeaning().getReference())))
											.map(c -> weighter.apply(c.getMeaning().getReference()) >= threshold ? c : mention_candidates.get(0)))
									.filter(Optional::isPresent)
									.map(Optional::get)
									.collect(toList()))
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopRank(List<List<List<List<Candidate>>>> candidates)
	{
		// Choose top candidates:
		return IntStream.range(0, candidates.size())
				.mapToObj(i -> candidates.get(i).stream()
						.map(sentence -> sentence.stream()
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(Candidate::getWeight)))
								.filter(Optional::isPresent)
								.map(Optional::get)
								.filter(c -> c.getWeight() > 0.0) // If top candidates has weight 0, then there really isn't a top candidate
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopRankOrFirst(List<List<List<List<Candidate>>>> candidates)
	{
		// Choose top candidates:
		return IntStream.range(0, candidates.size())
				.mapToObj(i -> candidates.get(i).stream()
						.map(sentence -> sentence.stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
											.max(comparingDouble(Candidate::getWeight))
											.map(c -> c.getWeight() > 0.0 ? c : mention_candidates.get(0)))
								.filter(Optional::isPresent)
								.map(Optional::get)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static Map<String, String> parseGoldFile(Path file)
	{
		return Arrays.stream(FileUtils.readTextFile(file).split("\n"))
				.map(l -> l.split("\t"))
				.filter(a -> a.length >= 3)
				.collect(Collectors.toMap(a -> a[0].equals(a[1]) ?  a[0] : (a[0] + "-" + a[1]), a -> a[2]));
	}

	private static void print_texts(Corpus corpus)
	{
		log.info("Texts:" + corpus.texts.stream()
				.map(d -> d.sentences.stream()
						.map(s -> s.tokens.stream()
								.map(t -> t.wf)
								.collect(joining(" ")))
						.collect(joining("\n\t", "\t", "")))
				.collect(joining("\n---\n", "\n", "\n")));
	}

	private static void print_ranking(List<Candidate> candidates, boolean print_debug)
	{
		if (!print_debug || candidates.isEmpty())
			return;

		final Mention mention = candidates.get(0).getMention();
		log.info(mention.getSentenceId() + " \"" + mention + "\" " + mention.getPOS() + candidates.stream()
				.map(c -> c.toString() + " " + DebugUtils.printDouble(c.getWeight()))
				.collect(joining("\n\t", "\n\t", "")));
	}

	private static void print_full_ranking(List<Candidate> candidates, Function<String, Double> weighter, Map<String, String> gold, boolean print_debug, int max_length)
	{
		if (!print_debug || candidates.isEmpty())
			return;

		final Mention mention = candidates.get(0).getMention();
		final String gold_c = gold.get(mention.getSentenceId());
		final String max_r = candidates.stream()
				.max(comparingDouble(Candidate::getWeight))
				.map(Candidate::getMeaning)
				.map(Meaning::getReference).orElse("");
		final String first_c = candidates.get(0).getMeaning().getReference();

		final String result = gold_c != null && gold_c.equals(max_r) ? "OK" : "FAIL";
		final String ranked = max_r.equals(first_c) ? "" : "RANKED";
		Function<String, String> marker = (m) -> (m.equals(gold_c) ? "GOLD " : "") + (m.equals(max_r) ? "SYSTEM" : "");

		log.info(mention.getSentenceId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
				candidates.stream()
					.map(c -> String.format("%-15s%-" + max_length + "s%-15s%-15s", marker.apply(c.getMeaning().getReference()),
							c.getMeaning().toString(), DebugUtils.printDouble(weighter.apply(c.getMeaning().getReference())),
							(c.getWeight() > 0.0 ? DebugUtils.printDouble(c.getWeight(), 6) : "")))
					.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t","Status", "Candidate","Context score","Rank score"), "")));
	}

	private static void print_meaning_rankings(List<List<List<Candidate>>> candidates, Function<String, Double> weighter, boolean print_debug, int max_length)
	{
		if (!print_debug || candidates.isEmpty())
			return;
		final Map<Meaning, Double> weights = candidates.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()))
				.collect(groupingBy(Candidate::getMeaning, averagingDouble(Candidate::getWeight)));
		final List<Meaning> meanings = weights.keySet().stream().collect(toList());

		log.info(meanings.stream()
				.filter(m -> weights.get(m) > 0.0)
				.sorted(Comparator.comparingDouble(weights::get).reversed())
				.map(m -> String.format("%-" + max_length + "s%-15s", m.toString(), DebugUtils.printDouble(weights.get(m))))
				.collect(joining("\n\t", "Meaning ranking by ranking score:\n\t",
						"\n--------------------------------------------------------------------------------------------")));
		log.info(meanings.stream()
				.sorted(Comparator.<Meaning>comparingDouble(m -> weighter.apply(m.getReference())).reversed())
				.map(m -> String.format("%-" + max_length + "s%-15s", m.toString(), DebugUtils.printDouble(weighter.apply(m.getReference()))))
				.collect(joining("\n\t", "Meaning ranking by weighter score:\n\t",
						"\n--------------------------------------------------------------------------------------------")));
	}

	private static void evaluate(Corpus corpus, List<List<List<Candidate>>> candidates, Path gold_file, Path xml_file,
	                             Path output_path, String sufix, boolean exclude_multiwords) throws IOException
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()))
				.collect(groupingBy(Candidate::getMention));
		final List<Pair<Mention, List<Candidate>>> sorted_candidates = mentions2candidates.keySet().stream()
				.sorted(Comparator.comparing(Mention::getSentenceId).thenComparing(Mention::getSpan))
				.map(m -> Pair.of(m, mentions2candidates.get(m).stream()
						.sorted(Comparator.<Candidate>comparingDouble(Candidate::getWeight).reversed())
						.collect(toList())))
				.collect(toList());

		String results = sorted_candidates.stream()// mention list is already sorted
				.filter(p -> !(exclude_multiwords && p.getLeft().isMultiWord())) // exclude multiwords if necessary
				.map(Pair::getRight)
				.filter(l -> !l.isEmpty())
				.map(l -> l.get(0)) // top candidate from sorted candidate list
				.map(c ->
				{
					final Mention mention = c.getMention();
					final String sentenceId = mention.getSentenceId();
					final Text document = corpus.texts.stream()
							.filter(d -> sentenceId.startsWith(d.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Sentence sentence = document.sentences.stream()
							.filter(s -> sentenceId.startsWith(s.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Token first_token = sentence.tokens.get(mention.getSpan().getLeft());
					final Token last_token = sentence.tokens.get(mention.getSpan().getRight() - 1);

					return first_token.id + "\t" + last_token.id + "\t" + c.getMeaning().getReference();
				})
				.collect(joining("\n"));

		final Path results_file = FileUtils.createOutputPath(xml_file, output_path, "xml", sufix);
		FileUtils.writeTextToFile(results_file, results);
		log.info("Results file written to " + results_file);
		Scorer.main(new String[]{gold_file.toString(), results_file.toString()});
	}

	private static void resetRanks(List<List<List<List<Candidate>>>> candidates)
	{
		candidates.stream()
				.flatMap(List::stream)
				.flatMap(List::stream)
				.flatMap(List::stream)
				.forEach(c -> c.setWeight(0.0));
	}
}
