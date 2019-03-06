package edu.upf.taln.textplanning.tools;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.weighting.Context;
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
import java.util.function.Function;
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

	private static final int max_span_size = 3;
	private static final String noun_pos_tag = "N";
	private static final String other_pos_tag = "X";
	private static final ULocale language = ULocale.ENGLISH;
	private static final String candidates_file = "candidates.bin";
	private static final String contexts_file = "contexts.bin";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_file, Path xml_file, Path output_path, ResourcesFactory resources, int top_k, double threshold) throws Exception
	{
		log.info("Parsing gold file");
		final Map<String, String> gold = parseGoldFile(gold_file);
		log.info(gold.keySet().size() + " lines read from gold");
		log.info("Parsing XML file");
		final Corpus corpus = parse(xml_file);

		List<List<List<List<Candidate>>>> candidates;
		List<Context> contexts;
		final Path candidates_path = output_path.resolve(candidates_file);
		final Path contexts_path = output_path.resolve(contexts_file);

		if (Files.exists(candidates_path) && Files.exists(contexts_path))
		{
			log.info("Loading candidates from " + candidates_path);
			candidates = ((List<List<List<List<Candidate>>>>) Serializer.deserialize(candidates_path)).stream().map(text ->
					text.stream().map(sentence ->
							sentence.stream().map(mention ->
									mention.stream().
											distinct().
											collect(toList()))
									.collect(toList()))
							.collect(toList()))
					.collect(toList());;
			log.info("Loading contexts from " + contexts_path);
			contexts = (List<Context>) Serializer.deserialize(contexts_path);
		}
		else
		{
			log.info("Collecting mentions");
			Stopwatch timer = Stopwatch.createStarted();
			final List<List<List<Mention>>> mentions = collectMentions(corpus);
			log.info("Mentions collected in " + timer.stop());

			log.info("Looking up meanings");
			timer.reset(); timer.start();
			candidates = collectCandidates(resources.getDictionary(), corpus, mentions).stream()
					.map(text -> text.stream().map(sentence ->
							sentence.stream().map(mention ->
									mention.stream().
											distinct().
											collect(toList()))
									.collect(toList()))
							.collect(toList()))
					.collect(toList());
			log.info("Meanings looked up in " + timer.stop());

			Serializer.serialize(candidates, candidates_path);
			log.info("Canidates saved in " + candidates_path);

			log.info("Creating contexts");
			timer.reset(); timer.start();
			contexts = createContexts(mentions, candidates, resources);
			log.info("Contexts created in " + timer.stop());
			Serializer.serialize(contexts, contexts_path);
			log.info("Contexts saved in " + contexts_path);
		}

		{
			log.info("Ranking meanings (random)");
			resetRanks(candidates);
			final List<List<List<Candidate>>> ranked_candidates = random_rank(candidates, false);
			log.info("Results (random)");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "random.results", false);
//			log.info("Results (random, no multiwords)");
//			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "random_nomw.results", true);
		}
		{
			log.info("Ranking meanings (first sense)");
			resetRanks(candidates);
			final List<List<List<Candidate>>> ranked_candidates = first_sense_rank(candidates, resources.getDictionary(), false);
			log.info("Results (first sense)");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "first_sense.results", false);
//			log.info("Results (first sense, no multiwords)");
//			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "first_sense_nomw.results", true);
		}
		{
			log.info("Ranking meanings (context only)");
			resetRanks(candidates);
			final List<List<List<Candidate>>> ranked_candidates = context_rank(candidates, contexts, resources, false);
			log.info("Results (context only)");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "context.results", false);
//			log.info("Results (context only, no multiwords)");
//			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "context_nomw.results", true);
		}
		{
			for (double context_threshold = 0.7; context_threshold <= 0.7; context_threshold += 0.01)
			{
				log.info("Ranking meanings (first sense + context, threshold set to " + DebugUtils.printDouble(context_threshold) + ")");
				resetRanks(candidates);
				final List<List<List<Candidate>>> ranked_candidates = first_sense_context_rank(candidates, contexts, resources, context_threshold, false);
				log.info("Results (first sense + context, threshold set to " + DebugUtils.printDouble(context_threshold) + ")");
				evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "first_sense_context + " + context_threshold +".results", false);
			}

//			for (double context_threshold = 0.7; context_threshold <= 0.7; context_threshold += 0.01)
//			{
//				log.info("Ranking meanings (first sense + context, no multiwords threshold set to " + DebugUtils.printDouble(context_threshold) + ")");
//				resetRanks(candidates);
//				final List<List<List<Candidate>>> ranked_candidates = first_sense_context_rank(candidates, contexts, resources, context_threshold, false);
//				log.info("Results (first sense + context, no multiwords, threshold set to " + DebugUtils.printDouble(context_threshold) + ")");
//				evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "first_sense_context_nomw.results", true);
//			}
		}
		{
			log.info("Ranking meanings (full)");
			resetRanks(candidates);
			final List<List<List<Candidate>>> ranked_candidates = full_rank(candidates, contexts, resources, top_k, threshold, output_path, true);
			log.info("Results (full)");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "full.results", false);
//			log.info("Results (full, no multiwords)");
//			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "full_nomw.results", true);
			log.info("DONE");

			final int max_length = candidates.stream()
					.flatMap(List::stream)
					.flatMap(List::stream)
					.flatMap(List::stream)
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			IntStream.range(0, candidates.size()).forEach(i ->
			{
				log.info("TEXT " + i);
				final Context text_context = contexts.get(i);
				final List<List<List<Candidate>>> text_candidates = candidates.get(i);

				text_candidates.forEach(sentence_candidates ->
						sentence_candidates.forEach(mention_candidates ->
								SemEvalEvaluation.print_full_ranking(mention_candidates, contexts.get(i), gold, true, max_length)));

				print_meaning_rankings(text_candidates, text_context, true, max_length);
			});
		}
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
										.filter(m -> StopWordsFilter.filter(m, language)))
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

	private static List<Context> createContexts(List<List<List<Mention>>> mentions, List<List<List<List<Candidate>>>> candidates,
	                                            ResourcesFactory resources)
	{
		final List<Context> contexts = new ArrayList<>();
		for (int i = 0; i < mentions.size(); ++i)
		{
			final List<String> tokens = mentions.get(i).stream()
					.flatMap(sentence_mentions -> sentence_mentions.stream()
							.filter(m -> !m.isMultiWord())
							.filter(m -> StopWordsFilter.filter(m, language))
							.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft())))
					.map(Mention::getSurface_form)
					.collect(toList());

			tokens.removeIf(t -> Collections.frequency(tokens, t) < 3);
			final List<String> context = tokens.stream().distinct().collect(toList());
			log.info("Context for text " + i + " is: " + context);

			final List<Candidate> text_candidates = candidates.get(i).stream()
					.flatMap(l -> l.stream()
							.flatMap(s -> s.stream()))
					.collect(toList());

			final Context context_weighter = new Context(text_candidates, resources.getSenseContextVectors(),
					resources.getSentenceVectors(), w -> context, resources.getSimilarityFunction());
			contexts.add(context_weighter);
		}

		return contexts;
	}

	private static List<List<List<Candidate>>> random_rank(List<List<List<List<Candidate>>>> candidates, boolean print_debug)
	{
		Random rand = new Random();
		return candidates.stream()
				.map(text -> text.stream()
						.map(sentence -> sentence.stream()
								.filter(mention_candidates -> !mention_candidates.isEmpty())
								.map(mention_candidates ->
								{
									mention_candidates.forEach(c -> c.setWeight(0.0));
									final Candidate candidate = mention_candidates.get(rand.nextInt(mention_candidates.size()));
									candidate.setWeight(1.0);
									print_ranking(mention_candidates, print_debug);
									return candidate;
								})
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> first_sense_rank(List<List<List<List<Candidate>>>> candidates,
	                                                            MeaningDictionary bn, boolean print_debug)
	{
		return candidates.stream()
				.map(text -> text.stream()
						.map(sentence -> sentence.stream()
								.filter(mention_candidates -> !mention_candidates.isEmpty())
								.map(mention_candidates ->
								{
									final Iterator<Candidate> it = mention_candidates.iterator();
									it.next().setWeight(1.0);
									it.forEachRemaining(c -> c.setWeight(0.0));
									print_ranking(mention_candidates, print_debug);
									return mention_candidates.get(0);
								})
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> context_rank(List<List<List<List<Candidate>>>> candidates, List<Context> contexts,
	                                                        ResourcesFactory resources, boolean print_debug)
	{
		return IntStream.range(0, candidates.size())
				.mapToObj(text_i ->
				{
					final Context document_context = contexts.get(text_i);
					return candidates.get(text_i).stream()
							.map(sentence -> sentence.stream()
									.map(mention_candidates -> mention_candidates.stream()
											.map(c ->
											{
												c.setWeight(document_context.weight(c.getMeaning().getReference()));
												return c;
											})
											.collect(toList()))
									.map(mention_candidates ->
									{
										final Optional<Candidate> max = mention_candidates.stream()
												.max(comparingDouble(c -> c.getWeight()));
										SemEvalEvaluation.print_ranking(mention_candidates, print_debug);
										return max;
									})
									.filter(Optional::isPresent)
									.map(Optional::get)
									.collect(toList()))
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<List<Candidate>>> first_sense_context_rank(List<List<List<List<Candidate>>>> candidates, List<Context> contexts,
	                                                                    ResourcesFactory resources, double threshold, boolean print_debug)
	{
		return IntStream.range(0, candidates.size())
				.mapToObj(text_i ->
				{
					final Context document_context = contexts.get(text_i);
					return candidates.get(text_i).stream()
							.map(sentence -> sentence.stream()
									.map(mention_candidates -> mention_candidates.stream()
											.map(c ->
											{
												c.setWeight(document_context.weight(c.getMeaning().getReference()));
												return c;
											})
											.collect(toList()))
									.map(mention_candidates ->
									{
										final Optional<Candidate> max = mention_candidates.stream()
												.max(comparingDouble(c -> c.getWeight()));

										SemEvalEvaluation.print_ranking(mention_candidates, print_debug);

										// Return top scored candidate if scored above the threshold, otherwise return first sense
										if (max.map(Candidate::getWeight).orElse(0.0) >= threshold)
											return max;
										else if (!mention_candidates.isEmpty())
											return Optional.<Candidate>of(mention_candidates.get(0));
										else
											return Optional.<Candidate>empty();

									})
									.filter(Optional::isPresent)
									.map(Optional::get)
									.collect(toList()))
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<List<Candidate>>> full_rank(List<List<List<List<Candidate>>>> candidates, List<Context> contexts,
	                                                     ResourcesFactory resources, int top_k, double threshold, Path output_path,
	                                                     boolean print_debug) throws IOException
	{
		// Rank candidates
		IntStream.range(0, candidates.size())
				.forEach(text_i ->
				{
					final List<Candidate> text_candidates = candidates.get(text_i).stream()
							.flatMap(l -> l.stream()
									.flatMap(l2 -> l2.stream()))
							.collect(toList());
					final Map<Mention, List<Candidate>> mentions2candidates = candidates.get(text_i).stream()
							.flatMap(l -> l.stream()
									.filter(l2 -> !l2.isEmpty()))
							.collect(toMap(l -> l.get(0).getMention(), l -> l));

					final Context context_weighter = contexts.get(text_i);
					final VectorsSimilarity sim = new VectorsSimilarity(resources.getSenseVectors(), resources.getSimilarityFunction());
					final TopCandidatesFilter candidates_filter =
							new TopCandidatesFilter(mentions2candidates, context_weighter::weight, top_k, threshold);
					final DifferentMentionsFilter meanings_filter = new DifferentMentionsFilter(text_candidates);

					final long num_filtered_candidates = text_candidates.stream()
							.filter(candidates_filter)
							.count();
					final long num_meanings = text_candidates.stream()
							.filter(candidates_filter)
							.map(Candidate::getMeaning)
							.distinct()
							.count();

					log.info("\tRanking document " + (text_i + 1) + " with " + text_candidates.size() + " candidates, " +
							num_filtered_candidates + " candidates after filtering, and " +
							num_meanings + " distinct meanings");
					TextPlanner.rankMeanings(text_candidates, candidates_filter, meanings_filter, context_weighter::weight,
							sim::of, new TextPlanner.Options());
				});



		// Choose top candidates
		return IntStream.range(0, candidates.size())
				.mapToObj(i ->
				{
					final Context context = contexts.get(i);
					return candidates.get(i).stream()
							.map(sentence -> sentence.stream()
									.map(mention_candidates -> mention_candidates.stream()
											.max(comparingDouble(c -> c.getWeight())))
									.filter(Optional::isPresent)
									.map(Optional::get)
									.collect(toList()))
							.collect(toList());
				})
				.collect(toList());

//			final List<List<Set<Candidate>>> grouped_candidates = candidates_i.stream()
//					.collect(groupingBy(c -> c.getMention().getSentenceId(), groupingBy(c -> c.getMention().getSpan(), toSet())))
//					.entrySet().stream()
//					.sorted(Comparator.comparing(Map.Entry::getKey))
//					.map(Map.Entry::getValue)
//					.map(e -> e.entrySet().stream()
//							.sorted(Comparator.comparingInt(e2 -> e2.getKey().getLeft()))
//							.map(Map.Entry::getValue)
//							.collect(toList()))
//					.collect(toList());
//
//			if (!Files.exists(output_path))
//				Files.createDirectories(output_path);
//
//			final String out_filename = document.id + ".candidates";
//			FileUtils.serializeMeanings(grouped_candidates, output_path.resolve(out_filename));
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

	private static void print_full_ranking(List<Candidate> candidates, Context context, Map<String, String> gold, boolean print_debug, int max_length)
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
							c.getMeaning().toString(), DebugUtils.printDouble(context.weight(c.getMeaning().getReference())),
							(c.getWeight() > 0.0 ? DebugUtils.printDouble(c.getWeight(), 6) : "")))
					.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t","Status", "Candidate","Context score","Rank score"), "")));
	}

	private static void print_meaning_rankings(List<List<List<Candidate>>> candidates, Context context, boolean print_debug, int max_length)
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
				.sorted(Comparator.<Meaning>comparingDouble(m -> context.weight(m.getReference())).reversed())
				.map(m -> String.format("%-" + max_length + "s%-15s", m.toString(), DebugUtils.printDouble(context.weight(m.getReference()))))
				.collect(joining("\n\t", "Meaning ranking by context score:\n\t",
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
