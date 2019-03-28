package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;
import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.other_pos_tag;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

@SuppressWarnings("ALL")
public class SemEvalEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_file, Path xml_file, Path output_path, ResourcesFactory resources_factory) throws Exception
	{
		final Options options = new Options();
		final Map<String, String> gold = parseGoldFile(gold_file);
		Set<String> excludedPOSTags = new HashSet<>(Arrays.asList(other_pos_tag, adverb_pos_tag));
		excludedPOSTags = Collections.unmodifiableSet(excludedPOSTags);
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path, resources_factory,
				language, max_span_size, excludedPOSTags, options.num_first_meanings);
		EvaluationTools.rankMeanings(options, corpus, resources_factory, excludedPOSTags);

		log.info("********************************");
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseRandom(corpus);
			log.info("Random results:");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "random.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseFirst(corpus);
			log.info("First sense results:");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "first.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContext(corpus);
			log.info("Context results:");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "context.results", false);
		}
		{
			final double context_threshold = 0.7;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "context_or_first_" + context_threshold + ".results", false);
		}
		{
			final double context_threshold = 0.8;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "context_or_first_" + context_threshold + ".results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRank(corpus);
			log.info("Rank results:");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "rank.results", false);
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
			log.info("Rank or first results:");
			evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "rank_or_first.results", false);
		}
		log.info("********************************");

		final int max_length = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.flatMap(s -> s.candidates.values().stream())
				.flatMap(m -> m.stream())
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.mapToInt(String::length)
				.max().orElse(5) + 4;

		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final Function<String, Double> weighter = corpus.texts.get(i).weighter;
			corpus.texts.get(i).sentences.forEach(s ->
					s.candidates.values().forEach(m -> print_full_ranking(m, weighter, gold, true, max_length)));

			print_meaning_rankings(corpus.texts.get(i), weighter, max_length);
		});
	}

	public static void run_batch(Path gold_file, Path xml_file, Path output_path, ResourcesFactory resources_factory) throws JAXBException, IOException, ClassNotFoundException
	{
		Options base_options = new Options();
		final Map<String, String> gold = parseGoldFile(gold_file);
		Set<String> excludedPOSTags = new HashSet<>(Arrays.asList(other_pos_tag, adverb_pos_tag));
		excludedPOSTags = Collections.unmodifiableSet(excludedPOSTags);
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path,
				resources_factory, language, max_span_size, excludedPOSTags, base_options.min_context_freq);

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
			resetRanks(corpus);
			EvaluationTools.rankMeanings(options, corpus, resources_factory,
					excludedPOSTags);
			log.info("********************************");
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank results:");
				evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "rank." + options.toShortString() + ".results", false);
			}
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank or first results:");
				evaluate(corpus, ranked_candidates, gold_file, xml_file, output_path, "rank." + options.toShortString() + ".results", false);
			}
			log.info("********************************");
		}
	}

	private static Map<String, String> parseGoldFile(Path file)
	{
		log.info("Parsing gold file");
		final Map<String, String> gold = Arrays.stream(FileUtils.readTextFile(file).split("\n"))
				.map(l -> l.split("\t"))
				.filter(a -> a.length >= 3)
				.collect(toMap(a -> a[0].equals(a[1]) ? a[0] : (a[0] + "-" + a[1]), a -> a[2]));
		log.info(gold.keySet().size() + " lines read from gold");

		return gold;
	}
	
	private static <T> Predicate<T> not(Predicate<T> t) {  
		return t.negate();  
	} 

	private static List<List<List<Candidate>>> chooseRandom(Corpus corpus)
	{
		// Choose top candidates:
		Random random = new Random();
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
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

	private static List<List<List<Candidate>>> chooseFirst(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.get(0))
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopContext(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(c -> t.weighter.apply(c.getMeaning().getReference()))))
									.filter(Optional::isPresent)
									.map(Optional::get) 
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopContextOrFirst(Corpus corpus, double threshold)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(c -> t.weighter.apply(c.getMeaning().getReference())))
										.map(c -> t.weighter.apply(c.getMeaning().getReference()) >= threshold ? c : mention_candidates.get(0)))
									.filter(Optional::isPresent)
									.map(Optional::get) 
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopRank(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(Candidate::getWeight)))
								.filter(Optional::isPresent)
								.map(Optional::get) 
								.filter(c -> c.getWeight() > 0.0) // If top candidates has weight 0, then there really isn't a top candidate
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<Candidate>>> chooseTopRankOrFirst(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
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
		log.info(mention.getContextId() + " \"" + mention + "\" " + mention.getPOS() + candidates.stream()
				.map(c -> c.toString() + " " + DebugUtils.printDouble(c.getWeight()))
				.collect(joining("\n\t", "\n\t", "")));
	}

	private static void print_full_ranking(List<Candidate> candidates, Function<String, Double> weighter, Map<String, String> gold, boolean print_debug, int max_length)
	{
		if (!print_debug || candidates.isEmpty())
			return;

		final Mention mention = candidates.get(0).getMention();
		final String gold_c = gold.get(mention.getContextId());
		final String max_r = candidates.stream()
				.max(comparingDouble(Candidate::getWeight))
				.map(Candidate::getMeaning)
				.map(Meaning::getReference).orElse("");
		final String first_c = candidates.get(0).getMeaning().getReference();

		final String result = gold_c != null && gold_c.equals(max_r) ? "OK" : "FAIL";
		final String ranked = max_r.equals(first_c) ? "" : "RANKED";
		Function<String, String> marker = (m) -> (m.equals(gold_c) ? "GOLD " : "") + (m.equals(max_r) ? "SYSTEM" : "");

		log.info(mention.getId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
				candidates.stream()
					.map(c -> String.format("%-15s%-" + max_length + "s%-15s%-15s", marker.apply(c.getMeaning().getReference()),
							c.getMeaning().toString(), DebugUtils.printDouble(weighter.apply(c.getMeaning().getReference())),
							(c.getWeight() > 0.0 ? DebugUtils.printDouble(c.getWeight(), 6) : "")))
					.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t","Status", "Candidate","Context score","Rank score"), "")));
	}

	private static void print_meaning_rankings(Text text, Function<String, Double> weighter, int max_length)
	{
		final Map<Meaning, Double> weights = text.sentences.stream()
				.flatMap(s -> s.candidates.values().stream())
				.flatMap(m -> m.stream())
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
				.sorted(Comparator.comparing(Mention::getContextId).thenComparing(Mention::getSpan))
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
					final String contextId = mention.getContextId();
					final Text document = corpus.texts.stream()
							.filter(d -> contextId.startsWith(d.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final EvaluationTools.Sentence sentence = document.sentences.stream()
							.filter(s -> contextId.startsWith(s.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final EvaluationTools.Token first_token = sentence.tokens.get(mention.getSpan().getLeft());
					final EvaluationTools.Token last_token = sentence.tokens.get(mention.getSpan().getRight() - 1);

					return first_token.id + "\t" + last_token.id + "\t" + c.getMeaning().getReference();
				})
				.collect(joining("\n"));

		final Path results_file = FileUtils.createOutputPath(xml_file, output_path, "xml", sufix);
		FileUtils.writeTextToFile(results_file, results);
		log.info("Results file written to " + results_file);
		SemEvalScorer.main(new String[]{gold_file.toString(), results_file.toString()});
	}

	private static void resetRanks(Corpus corpus)
	{
		corpus.texts.forEach(t ->
				t.sentences.forEach(s ->
						s.candidates.values().forEach(m ->
								m.forEach(c -> c.setWeight(0.0)))));
	}
}
