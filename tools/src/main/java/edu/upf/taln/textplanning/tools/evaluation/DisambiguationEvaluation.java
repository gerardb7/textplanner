package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingDouble;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public abstract class DisambiguationEvaluation
{

	private final static Logger log = LogManager.getLogger();

	abstract protected Set<String> getGold(Mention m);
	abstract protected Corpus getCorpus();
	abstract protected InitialResourcesFactory getFactory();
	abstract protected Options getOptions();
	abstract protected void evaluate(List<List<List<Candidate>>> system, String sufix);

	public void run()
	{
		final Corpus corpus = getCorpus();

		EvaluationTools.rankMeanings(getOptions(), corpus, getFactory().getSimilarityFunction());

		log.info("********************************");
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseRandom(corpus);
			log.info("Random results:");
			evaluate(ranked_candidates, "random.results");
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseFirst(corpus);
			log.info("First sense results:");
			evaluate(ranked_candidates, "first.results");
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContext(corpus);
			log.info("Context results:");
			evaluate(ranked_candidates, "context.results");
		}
		{
			final double context_threshold = 0.7;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
		}
		{
			final double context_threshold = 0.8;
			final List<List<List<Candidate>>> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRank(corpus);
			log.info("Rank results:");
			evaluate(ranked_candidates, "rank.results");
		}
		{
			final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
			log.info("Rank or first results:");
			evaluate(ranked_candidates, "rank_or_first.results");
		}
		log.info("********************************");

		final int max_length = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.flatMap(s -> s.candidates.values().stream())
				.flatMap(Collection::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.mapToInt(String::length)
				.max().orElse(5) + 4;



		IntStream.range(0, corpus.texts.size()).forEach(i ->
		{
			log.info("TEXT " + i);
			final Function<String, Double> weighter = corpus.texts.get(i).resources.getBiasFunction();
			corpus.texts.get(i).sentences.forEach(s ->
					s.candidates.forEach((m, l) -> print_full_ranking(l, getGold(m), weighter, max_length)));

			print_meaning_rankings(corpus.texts.get(i), weighter, max_length);
		});
	}

	public void run_batch()
	{
		Options base_options = getOptions();
		final Corpus corpus = getCorpus();

		log.info("Ranking meanings (full)");
		final int num_values = 11; final double min_value = 0.0; final double max_value = 1.0;
		final double increment = (max_value - min_value) / (double)(num_values - 1);
		final List<Options> batch_options = IntStream.range(0, num_values)
				.mapToDouble(i -> min_value + i * increment)
				.filter(v -> v >= min_value && v <= max_value)
				.mapToObj(v ->
				{
					Options o = new Options(base_options);
					o.sim_threshold = v;
					return o;
				})
				.collect(toList());

		for (Options options : batch_options)
		{
			resetRanks(corpus);
			EvaluationTools.rankMeanings(options, corpus, getFactory().getSimilarityFunction());
			log.info("********************************");
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank results:");
				evaluate(ranked_candidates, "rank." + options.toShortString() + ".results");
			}
			{
				final List<List<List<Candidate>>> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank or first results:");
				evaluate(ranked_candidates, "rank." + options.toShortString() + ".results");
			}
			log.info("********************************");
		}
	}

	protected static List<List<List<Candidate>>> chooseRandom(Corpus corpus)
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

	protected static List<List<List<Candidate>>> chooseFirst(Corpus corpus)
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

	protected static List<List<List<Candidate>>> chooseTopContext(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(c -> t.resources.getBiasFunction().apply(c.getMeaning().getReference()))))
								.flatMap(Optional::stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	protected static List<List<List<Candidate>>> chooseTopContextOrFirst(Corpus corpus, double threshold)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(c -> t.resources.getBiasFunction().apply(c.getMeaning().getReference())))
										.map(c -> t.resources.getBiasFunction().apply(c.getMeaning().getReference()) >= threshold ? c : mention_candidates.get(0)))
								.flatMap(Optional::stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	protected static List<List<List<Candidate>>> chooseTopRank(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(Candidate::getWeight)))
								.flatMap(Optional::stream)
								.filter(c -> c.getWeight() > 0.0) // If top candidates has weight 0, then there really isn't a top candidate
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	protected static List<List<List<Candidate>>> chooseTopRankOrFirst(Corpus corpus)
	{
		// Choose top candidates:
		return corpus.texts.stream()
				.map(t -> t.sentences.stream()
						.map(s -> s.candidates.values().stream()
								.filter(not(List::isEmpty))
								.map(mention_candidates -> mention_candidates.stream()
										.max(comparingDouble(Candidate::getWeight))
										.map(c -> c.getWeight() > 0.0 ? c : mention_candidates.get(0)))
								.flatMap(Optional::stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	protected static void resetRanks(Corpus corpus)
	{
		corpus.texts.forEach(t ->
				t.sentences.forEach(s ->
						s.candidates.values().forEach(m ->
								m.forEach(c -> c.setWeight(0.0)))));
	}

//	protected static void print_texts(Corpus corpus)
//	{
//		log.info("Texts:" + corpus.texts.stream()
//				.map(d -> d.sentences.stream()
//						.map(s -> s.tokens.stream()
//								.map(t -> t.wf)
//								.collect(joining(" ")))
//						.collect(joining("\n\t", "\t", "")))
//				.collect(joining("\n---\n", "\n", "\n")));
//	}
//
//	protected static void print_ranking(List<Candidate> candidates, boolean print_debug)
//	{
//		if (!print_debug || candidates.isEmpty())
//			return;
//
//		final Mention mention = candidates.get(0).getMention();
//		log.info(mention.getContextId() + " \"" + mention + "\" " + mention.getPOS() + candidates.stream()
//				.map(c -> c.toString() + " " + DebugUtils.printDouble(c.getWeight()))
//				.collect(joining("\n\t", "\n\t", "")));
//	}

	protected static void print_meaning_rankings(EvaluationTools.Text text, Function<String, Double> weighter, int max_length)
	{
		final Map<Meaning, Double> weights = text.sentences.stream()
				.flatMap(s -> s.candidates.values().stream())
				.flatMap(Collection::stream)
				.collect(groupingBy(Candidate::getMeaning, averagingDouble(Candidate::getWeight)));
		final List<Meaning> meanings = new ArrayList<>(weights.keySet());

		log.info(meanings.stream()
				.filter(m -> weights.get(m) > 0.0)
				.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
				.map(m -> String.format("%-" + max_length + "s%-15s", m.toString(), DebugUtils.printDouble(weights.get(m))))
				.collect(joining("\n\t", "Meaning ranking by ranking score:\n\t",
						"\n--------------------------------------------------------------------------------------------")));
		log.info(meanings.stream()
				.sorted(Comparator.<Meaning>comparingDouble(m -> weighter.apply(m.getReference())).reversed())
				.map(m -> String.format("%-" + max_length + "s%-15s", m.toString(), DebugUtils.printDouble(weighter.apply(m.getReference()))))
				.collect(joining("\n\t", "Meaning ranking by weighter score:\n\t",
						"\n--------------------------------------------------------------------------------------------")));
	}

	protected static void print_full_ranking(List<Candidate> candidates, Set<String> gold, Function<String, Double> weighter, int max_length)
	{
		if (candidates.isEmpty())
			return;

		Mention mention = candidates.get(0).getMention();
		final String max_m = candidates.stream()
				.max(comparingDouble(Candidate::getWeight))
				.map(Candidate::getMeaning)
				.map(Meaning::getReference).orElse("");
		final String first_m = candidates.get(0).getMeaning().getReference();

		final String result = gold.contains(max_m) ? "OK" : "FAIL";
		final String ranked = max_m.equals(first_m) ? "" : "RANKED";
		Function<String, String> marker = (r) -> (gold.contains(r) ? "GOLD " : "") + (r.equals(max_m) ? "SYSTEM" : "");

		log.info(mention.getId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
				candidates.stream()
						.map(c -> String.format("%-15s%-" + max_length + "s%-15s%-15s", marker.apply(c.getMeaning().getReference()),
								c.getMeaning().toString(), DebugUtils.printDouble(weighter.apply(c.getMeaning().getReference())),
								(c.getWeight() > 0.0 ? DebugUtils.printDouble(c.getWeight(), 6) : "")))
						.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t","Status", "Candidate","Context score","Rank score"), "")));
	}
}
