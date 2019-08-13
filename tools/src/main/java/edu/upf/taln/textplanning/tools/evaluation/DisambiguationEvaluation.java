package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.corpus.Corpora.Corpus;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public abstract class DisambiguationEvaluation
{
	protected static final POS.Tagset tagset = POS.Tagset.Simple;
	private final static Logger log = LogManager.getLogger();

	abstract protected void checkCandidates(Corpus corpus);
	abstract protected Set<String> getGold(Mention m);
	abstract protected Corpus getCorpus();
	abstract protected DocumentResourcesFactory getResources(Corpora.Text text);
	abstract protected Options getOptions();
	abstract protected void evaluate(List<Candidate> system, String sufix);
	abstract protected Set<POS.Tag> getEvaluatePOS();
	abstract protected boolean evaluateMultiwordsOnly();

	public void run()
	{
		final Options options = getOptions();
		final Corpus corpus = getCorpus();
		checkCandidates(corpus);
		corpus.texts.forEach(text -> EvaluationTools.rankMeanings(text, getResources(text), options));


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
			final List<Candidate> ranked_candidates = chooseTopContext(corpus, options.disambiguation_lambda);
			log.info("Context results:");
			evaluate(ranked_candidates, "context.results");
		}
		{
			final List<Candidate> ranked_candidates = chooseTopRank(corpus, options.disambiguation_lambda);
			log.info("Rank results:");
			evaluate(ranked_candidates, "rank.results");
		}
		log.info("********************************");

		corpus.texts.forEach(text -> EvaluationTools.printDisambiguationResults(text, getResources(text), this::getGold, evaluateMultiwordsOnly(), getEvaluatePOS()));
	}

	public void run_batch()
	{
		Options base_options = getOptions();
		final Corpus corpus = getCorpus();
		corpus.texts.forEach(text -> EvaluationTools.rankMeanings(text, getResources(text), getOptions()));

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
			Corpora.reset(corpus);
			Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
			corpus.texts.forEach(text -> EvaluationTools.rankMeanings(corpus, getResources(text), options));

			log.info("********************************");
			{
				final List<Candidate> ranked_candidates = chooseTopRankOrFirst(corpus, disambiguation);
				log.info("Rank results:");
				evaluate(ranked_candidates, "rank." + options.toShortString() + ".results");
			}
			{
				final List<Candidate> ranked_candidates = chooseTopRankOrFirst(corpus, disambiguation);
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

		Disambiguation disambiguation = new Disambiguation(1.0); // no multiwords
		final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates, mention_selector, candidate_selector);

		return List.copyOf(disambiguated.values());
	}

	// Dictionary first sense baseline, only single words
	protected static List<Candidate> chooseFirst(Corpus corpus)
	{
		// choose from meanings of single words only
		Predicate<Mention> mention_selector = m -> !m.isMultiWord();
		// Disambiguate using first sense
		Function<List<Candidate>, Optional<Candidate>> candidate_selector =
				l -> l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));

		Disambiguation disambiguation = new Disambiguation(1.0); // no multiwords
		return corpus.texts.stream()
				.map(text ->
				{
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());
					final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates, mention_selector, candidate_selector);

					return disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	// Uses default multiword selection strategy and bias function
	protected List<Candidate> chooseTopContext(Corpus corpus, double disambiguation_lambda)
	{
		Disambiguation disambiguation = new Disambiguation(disambiguation_lambda);
		return corpus.texts.stream()
				.map(text ->
				{
					final BiasFunction bias = getResources(text).getBiasFunction();
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					Function<List<Candidate>, Optional<Candidate>> candidate_selector =
							l -> l.stream().max(Comparator.comparingDouble(c -> bias.apply(c.getMeaning().getReference())));
					final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates, candidate_selector);
					return disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	// Default multiword selection strategy + bias function iff above threshold, otherwise first sense
	protected List<Candidate> chooseTopContextOrFirst(Corpus corpus, Disambiguation disambiguation, double threshold)
	{
		return corpus.texts.stream()
				.map(text ->
				{
					final BiasFunction bias = getResources(text).getBiasFunction();
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					Function<List<Candidate>, Optional<Candidate>> candidate_selector =
							l -> l.stream()
									.max(Comparator.comparingDouble(c -> bias.apply(c.getMeaning().getReference())))
									.map(c -> bias.apply(c.getMeaning().getReference()) >= threshold ? c : l.get(0));
					final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates, candidate_selector);
					return disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	// Default multiword and candidate selection stratgies based on ranking values
	protected static List<Candidate> chooseTopRank(Corpus corpus, double disambiguation_lambda)
	{
		Disambiguation disambiguation = new Disambiguation(disambiguation_lambda);
		return corpus.texts.stream()
				.map(text ->
				{
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates);
					return disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
	}

	protected static List<Candidate> chooseTopRankOrFirst(Corpus corpus, Disambiguation disambiguation)
	{
		Function<List<Candidate>, Optional<Candidate>> candidate_selector =
				l -> l.stream()
						.max(Comparator.comparingDouble(c -> c.getWeight().orElse(0.0)))
						.map(c -> c.getWeight().isEmpty()? l.get(0) : c);

		return corpus.texts.stream()
				.map(text ->
				{
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream()
									.flatMap(Collection::stream))
							.collect(toList());

					final Map<Mention, Candidate> disambiguated = disambiguation.disambiguate(candidates, candidate_selector);
					return disambiguated.values();
				})
				.flatMap(Collection::stream)
				.collect(toList());
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

}
