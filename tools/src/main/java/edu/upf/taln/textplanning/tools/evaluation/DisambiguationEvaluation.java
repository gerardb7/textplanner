package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

public abstract class DisambiguationEvaluation
{

	private final static Logger log = LogManager.getLogger();

	abstract protected void checkCandidates(Corpus corpus);
	abstract protected Set<String> getGold(Mention m);
	abstract protected Corpus getCorpus();
	abstract protected Options getOptions();
	abstract protected void evaluate(List<Candidate> system, String sufix);
	abstract protected Set<String> getEvaluatePOS();
	abstract protected boolean evaluateMultiwordsOnly();

	public void run()
	{
		final Corpus corpus = getCorpus();
		checkCandidates(corpus);

		EvaluationTools.rankMeanings(getOptions(), corpus);

		log.info("********************************");
		{
			final List<Candidate> ranked_candidates = chooseRandom(corpus);
			log.info("Random results:");
			evaluate(ranked_candidates, "random.results");
		}
		{
			final List<Candidate> ranked_candidates = chooseFirst(corpus);
			log.info("First sense results:");
			evaluate(ranked_candidates, "first.results");
		}
		{
			final List<Candidate> ranked_candidates = chooseTopContext(corpus);
			log.info("Context results:");
			evaluate(ranked_candidates, "context.results");
		}
//		{
//			final double context_threshold = 0.2;
//			final List<Candidate> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
//			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
//			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
//		}
//		{
//			final double context_threshold = 0.4;
//			final List<Candidate> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
//			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
//			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
//		}
//		{
//			final double context_threshold = 0.6;
//			final List<Candidate> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
//			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
//			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
//		}
//		{
//			final double context_threshold = 0.8;
//			final List<Candidate> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
//			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
//			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
//		}
//		{
//			final double context_threshold = 0.9;
//			final List<Candidate> ranked_candidates = chooseTopContextOrFirst(corpus, context_threshold);
//			log.info("Context or first results (threshold = " + DebugUtils.printDouble(context_threshold) + "):");
//			evaluate(ranked_candidates, "context_or_first_" + context_threshold + ".results");
//		}
		{
			final List<Candidate> ranked_candidates = chooseTopRank(corpus);
			log.info("Rank results:");
			evaluate(ranked_candidates, "rank.results");
		}
		{
			final List<Candidate> ranked_candidates = chooseTopRankOrFirst(corpus);
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
			final Function<String, Double> weighter =
					corpus.resouces != null ? corpus.resouces.getBiasFunction() : corpus.texts.get(i).resources.getBiasFunction();
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
			EvaluationTools.rankMeanings(options, corpus);
			log.info("********************************");
			{
				final List<Candidate> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank results:");
				evaluate(ranked_candidates, "rank." + options.toShortString() + ".results");
			}
			{
				final List<Candidate> ranked_candidates = chooseTopRankOrFirst(corpus);
				log.info("Rank or first results:");
				evaluate(ranked_candidates, "rank." + options.toShortString() + ".results");
			}
			log.info("********************************");
		}
	}

	// Random baseline, only single words
	protected static List<Candidate> chooseRandom(Corpus corpus)
	{
		// Choose top candidates:
		Random random = new Random();
		Predicate<Mention> mention_selector = (m) -> !m.isMultiWord(); // only single words
		Function<List<Candidate>, Optional<Candidate>> candidate_selector =
				l -> l.isEmpty() ? Optional.empty() : Optional.of(l.get(random.nextInt(l.size())));

		final List<Candidate> candidates = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidates.values().stream()
								.flatMap(Collection::stream)))
				.collect(toList());
		final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates, mention_selector, candidate_selector);

		return List.copyOf(disambiguated.values());
	}

	// Dictionary first sense baseline, only single words
	protected static List<Candidate> chooseFirst(Corpus corpus)
	{
		// Choose top candidates:
		Predicate<Mention> mention_selector = (m) -> !m.isMultiWord(); // only single words
		Function<List<Candidate>, Optional<Candidate>> candidate_selector =
				l -> l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));

		final List<Candidate> candidates = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidates.values().stream()
								.flatMap(Collection::stream)))
				.collect(toList());
		final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates, mention_selector, candidate_selector);

		return List.copyOf(disambiguated.values());
	}

	// Uses default multiword selection strategy and bias function
	protected static List<Candidate> chooseTopContext(Corpus corpus)
	{
		return corpus.texts.stream()
				.map(text ->
				{
					final BiasFunction bias = corpus.resouces != null ? corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					Function<List<Candidate>, Optional<Candidate>> candidate_selector =
							l -> l.stream().max(Comparator.comparingDouble(c -> bias.apply(c.getMeaning().getReference())));
					final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates, candidate_selector);
					return  disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	// Default multiword selection strategy + bias function iff above threshold, otherwise first sense
	protected static List<Candidate> chooseTopContextOrFirst(Corpus corpus, double threshold)
	{
		return corpus.texts.stream()
				.map(text ->
				{
					final BiasFunction bias = corpus.resouces != null ? corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					Function<List<Candidate>, Optional<Candidate>> candidate_selector =
							l -> l.stream()
									.max(Comparator.comparingDouble(c -> bias.apply(c.getMeaning().getReference())))
									.map(c -> bias.apply(c.getMeaning().getReference()) >= threshold ? c : l.get(0));
					final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates, candidate_selector);
					return  disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	// Default multiword and candidate selection stratgies based on ranking values
	protected static List<Candidate> chooseTopRank(Corpus corpus)
	{
		final List<Candidate> candidates = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidates.values().stream()
								.flatMap(Collection::stream)))
				.collect(toList());
		final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates);

		return List.copyOf(disambiguated.values());
	}

	protected static List<Candidate> chooseTopRankOrFirst(Corpus corpus)
	{
		Function<List<Candidate>, Optional<Candidate>> candidate_selector =
				l -> l.stream()
						.max(Comparator.comparingDouble(Candidate::getWeight))
						.map(c -> c.getWeight() > 0.0 ? c : l.get(0));
		final List<Candidate> candidates = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidates.values().stream()
								.flatMap(Collection::stream)))
				.collect(toList());
		final Map<Mention, Candidate> disambiguated = Disambiguation.disambiguate(candidates, candidate_selector);

		return List.copyOf(disambiguated.values());
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
//		log.info(mention.getSourceId() + " \"" + mention + "\" " + mention.getPOS() + candidates.stream()
//				.map(c -> c.toString() + " " + DebugUtils.printDouble(c.getWeight()))
//				.collect(joining("\n\t", "\n\t", "")));
//	}

	protected void print_meaning_rankings(EvaluationTools.Text text, Function<String, Double> weighter, int max_length)
	{
		final boolean multiwords = evaluateMultiwordsOnly();
		final Map<Meaning, Double> weights = text.sentences.stream()
				.flatMap(s -> s.candidates.values().stream())
				.flatMap(Collection::stream)
				.filter(m -> (multiwords && m.getMention().isMultiWord()) || (!multiwords && getEvaluatePOS().contains(m.getMention().getPOS())))
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

	protected void print_full_ranking(List<Candidate> candidates, Set<String> gold, Function<String, Double> bias, int max_length)
	{
		if (candidates.isEmpty())
			return;

		Mention mention = candidates.get(0).getMention();
		if ((evaluateMultiwordsOnly() && !mention.isMultiWord() || (!evaluateMultiwordsOnly() && !getEvaluatePOS().contains(mention.getPOS()))))
			return;

		final String max_bias = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.max(comparingDouble(bias::apply))
				.orElse("");
		final String max_rank = candidates.stream()
				.max(comparingDouble(Candidate::getWeight))
				.map(Candidate::getMeaning)
				.map(Meaning::getReference).orElse("");
		final String first_m = candidates.get(0).getMeaning().getReference();

		final String result = gold.contains(max_rank) ? "OK" : "FAIL";
		final String ranked = max_rank.equals(first_m) ? "" : "RANKED";
		Function<String, String> marker = (r) ->
				(gold.contains(r) ? "GOLD " : "") +
				(r.equals(max_bias) ? "BIAS " : "") +
				(r.equals(max_rank) ? "RANK" : "");


		log.info(mention.getId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
				candidates.stream()
						.map(c ->
						{
							final String mark = marker.apply(c.getMeaning().getReference());
							final String bias_value = DebugUtils.printDouble(bias.apply(c.getMeaning().getReference()));
							final String rank_value = c.getWeight() > 0.0 ? DebugUtils.printDouble(c.getWeight()) : "";

							return String.format("%-15s%-" + max_length + "s%-15s%-15s", mark,
									c.getMeaning().toString(), bias_value, rank_value);
						})
						.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t","Status", "Candidate","Bias","Rank"), "")));
	}
}
