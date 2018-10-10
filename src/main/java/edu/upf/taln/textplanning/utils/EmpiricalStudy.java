package edu.upf.taln.textplanning.utils;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreEntityMention;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.Driver;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.input.BabelNetWrapper;
import edu.upf.taln.textplanning.input.CandidatesCollector;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.ranking.GraphRanking;
import edu.upf.taln.textplanning.ranking.JamaPowerIteration;
import edu.upf.taln.textplanning.ranking.MatrixFactory;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.weighting.NumberForms;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.*;

public class EmpiricalStudy
{
	private static final int max_tokens = 5;
	private final StanfordCoreNLP pipeline;
	private final BabelNetWrapper bn;
	private final Corpus corpus;
	private final List<WeightingFunction> weighting_functions = new ArrayList<>();
	private final SimilarityFunction sim;
	private static final int lists_size = 25;
	private final static Logger log = LogManager.getLogger();

	public EmpiricalStudy(Path bn_config_folder, Path frequencies, Path vectors, VectorsTextFileUtils.Format format) throws Exception
	{
		bn = new BabelNetWrapper(bn_config_folder, false);

		corpus = (CompactFrequencies)Serializer.deserialize(frequencies);
		weighting_functions.add(new TFIDF(corpus, r -> true));
		weighting_functions.add(new NumberForms(r -> true));

		log.info("Setting up Stanford CoreNLP");
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref");
//		props.setProperty("tokenize.whitespace", "true"); // no tokenization
//		props.setProperty("ssplit.eolonly", "true"); // no sentence splitting
		props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz"); // PERSON, LOCATION, ORGANIZATION,
		props.setProperty("coref.algorithm", "neural");

		Stopwatch timer = Stopwatch.createStarted();
		RedwoodConfiguration.current().clear().apply(); // shut up, CoreNLP
		pipeline = new StanfordCoreNLP(props);
		log.info("CoreNLP pipeline created in " + timer.stop());

		sim = Driver.chooseSimilarityFunction(vectors, format);
	}

	public void run(String text)
	{
		Stopwatch timer = Stopwatch.createStarted();

		CoreDocument document = new CoreDocument(text);
		pipeline.annotate(document);
		int num_tokens = document.tokens().size();
		final Map<String, List<Integer>> tokens_by_pos = IntStream.range(0, num_tokens)
				.boxed()
				.collect(groupingBy(i -> document.tokens().get(i).tag()));
		log.info("CoreNLP processing done in " + timer.stop());

		timer.reset(); timer.start();
		final Set<Mention> mentions = getMentions(document);
		log.info(mentions.size() + " mentions collected in " + timer.stop());
//		log.debug(mentions.stream().map(Mention::toLongString).collect(joining("\n")));

		timer.reset(); timer.start();
		CandidatesCollector candidates_collector = new CandidatesCollector(bn);
		final List<Candidate> candidates = candidates_collector.getCandidateMeanings(mentions);
		final Map<String, List<Candidate>> candidates_by_pos = candidates.stream()
				.collect(groupingBy(c -> c.getMention().getPOS()));
		log.info(candidates.size() + " candidates collected in " + timer.stop());

		reportNEsCoref(document);
		log.debug("**************************");

		timer.reset(); timer.start();
		log.debug("Coverage stats for all " + candidates.size() + " candidates");
		final Set<Integer> tokens = IntStream.range(0, num_tokens).boxed().collect(toSet());
		runCandidateCoverageTest(candidates, tokens);
		candidates_by_pos.keySet().forEach(pos ->
		{
			final List<Candidate> candidates_pos = candidates_by_pos.get(pos);
			log.debug("Coverage stats for " + candidates_pos.size() + " candidates with pos " + pos);
//			log.debug(candidates_pos.stream().map(Candidate::getMention).distinct().map(Mention::toLongString).collect(joining("\n")));
			runCandidateCoverageTest( candidates_by_pos.get(pos), new HashSet<>(tokens_by_pos.get(pos)));
		});
		log.debug("**************************");

		log.debug("Similarity stats for all " + candidates.size() + " candidates");
		runSimilarityTest(candidates);
		candidates_by_pos.keySet()
				.forEach(pos ->
				{
					final List<Candidate> candidates_pos = candidates_by_pos.get(pos);
					log.debug("Similarity stats for " + candidates_pos.size() + " candidates with pos " + pos);
//					log.debug(candidates_pos.stream().map(Candidate::getMention).distinct().map(Mention::toLongString).collect(joining("\n")));
					runSimilarityTest(candidates_pos);
				});
		log.debug("**************************");

		log.debug("Frequency stats for all " + candidates.size() + " candidates");
		runFrequencyTest(candidates);
		candidates_by_pos.keySet()
				.forEach(pos ->
				{
					final List<Candidate> candidates_pos = candidates_by_pos.get(pos);
					log.debug("Frequency stats for " + candidates_pos.size() + " candidates with pos " + pos);
					runFrequencyTest(candidates_pos);
				});
		log.debug("**************************");
		log.info("Stats completed in " + timer.stop());

	}

	private void reportNEsCoref(CoreDocument document)
	{
		// Report NEs
		final Map<String, Long> nes_by_type = document.entityMentions().stream()
				.collect(Collectors.groupingBy(CoreEntityMention::entityType, Collectors.counting()));
		final List<Pair<Integer, Integer>> ne_offsets = document.entityMentions().stream()
				.map(e -> Pair.of(e.coreMap().get(CoreAnnotations.TokenBeginAnnotation.class),
						e.coreMap().get(CoreAnnotations.TokenEndAnnotation.class)))
				.collect(toList());
		log.debug(ne_offsets.size() + " NEs");
		nes_by_type.keySet().forEach(t -> log.debug("\t" + t + ": " + nes_by_type.get(t)));

		final Collection<CorefChain> chains = document.corefChains().values();
		log.debug(chains.size() + " coreference chains");
		final double[] coref_lengths = chains.stream()
				.map(CorefChain::getMentionsInTextualOrder)
				.mapToDouble(List::size)
				.toArray();
		log.debug("\tstats: " + printStats(new DescriptiveStatistics(coref_lengths)));
	}

	private void runCandidateCoverageTest(Collection<Candidate> candidates, Set<Integer> tokens)
	{
		if (candidates.isEmpty())
		{
			log.debug("\tNo candidates");
			return;
		}

		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(Collectors.groupingBy(Candidate::getMention));
		final double avg_polysemy = mentions2candidates.values().stream()
				.mapToInt(List::size)
				.average().orElse(0.0);
		log.debug("\tAverage polysemy = " + DebugUtils.printDouble(avg_polysemy));

		final Set<Integer> tokens_in_mentions = candidates.stream()
				.map(Candidate::getMention)
				.map(Mention::getSpan)
				.flatMap(span -> IntStream.range(span.getLeft(), span.getRight()).boxed())
				.collect(toSet());
		final Sets.SetView<Integer> covered_tokens = Sets.intersection(tokens, tokens_in_mentions);

		double coverage = ((double)covered_tokens.size()) / tokens.size();
		log.debug("\tAverage coverage = " + DebugUtils.printDouble(coverage));
		log.debug("\t" + covered_tokens.size() + " tokens in mentions out of " + tokens.size());

		final Set<Integer> tokens_in_multiwords = candidates.stream()
				.map(Candidate::getMention)
				.filter(Mention::isMultiWord)
				.map(Mention::getSpan)
				.flatMap(span -> IntStream.range(span.getLeft(), span.getRight()).boxed())
				.collect(toSet());
		final Sets.SetView<Integer> multiword_covered_tokens = Sets.intersection(tokens, tokens_in_multiwords);

		double avg_multiword = ((double)multiword_covered_tokens.size()) / tokens.size();
		log.debug("\tAverage coverage of multiwords = " + DebugUtils.printDouble(avg_multiword));
		log.debug("\t" + multiword_covered_tokens.size() + " tokens in multiwords out of " + tokens.size());
	}

	private void runSimilarityTest(Collection<Candidate> candidates)
	{
		if (candidates.isEmpty())
		{
			log.debug("\tNo candidates");
			return;
		}

		GraphRanking.DifferentMentions filter = new GraphRanking.DifferentMentions(candidates);

		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());

		final long num_meanings_defined = meanings.stream()
				.filter(sim::isDefinedFor)
				.count();
		double avg_coverage = ((double) num_meanings_defined) / meanings.size();
		log.debug("\tAverage coverage " + DebugUtils.printDouble(avg_coverage));
		log.debug("\tVectors are defined for " + num_meanings_defined + " out of " + meanings.size() + " meanings");

		if (meanings.size() > 1)
		{
			final long num_pairs = CombinatoricsUtils.binomialCoefficient(meanings.size(), 2);
			AtomicInteger num_valid_pairs = new AtomicInteger(0);
			final long num_pairs_meanings_defined = IntStream.range(0, meanings.size())
					.mapToLong(i -> IntStream.range(i, meanings.size())
							.filter(j -> i != j)
							.filter(j ->
							{
								final boolean test = filter.test(meanings.get(i), meanings.get(j));
								if (test)
									num_valid_pairs.incrementAndGet();
								return test;
							})
							.filter(j -> sim.isDefinedFor(meanings.get(i), meanings.get(j)))
							.count())
					.sum();

			log.debug("\tVectors are defined for " + num_pairs_meanings_defined + " out of " + num_valid_pairs +
					" valid pairs of meanings (" + num_pairs + " total pairs)");

			if (num_pairs_meanings_defined > 0)
			{
				final double[][] M = MatrixFactory.createMeaningsSimilarityMatrix(meanings, sim, filter, 0.0, false, false, false);

				final List<String> labels = candidates.stream() // for debugging purposes
						.map(Candidate::getMeaning)
						.map(Meaning::toString)
						.distinct()
						.collect(toList());

				final List<Pair<Integer, Integer>> indexes = IntStream.range(0, meanings.size())
						.mapToObj(i -> IntStream.range(i, meanings.size())
								.filter(j -> i != j)
								.filter(j -> filter.test(meanings.get(i), meanings.get(j)))
								.filter(j -> sim.isDefinedFor(meanings.get(i), meanings.get(j)))
								.mapToObj(j -> Pair.of(i, j))
								.collect(toList()))
						.flatMap(List::stream)
						.collect(toList());

				final double[] weights = indexes.stream()
						.mapToDouble(p -> M[p.getLeft()][p.getRight()])
						.toArray();

				DescriptiveStatistics stats = new DescriptiveStatistics(weights);
				log.debug("\t" + printStats(stats));

				final List<String> sorted_pairs = IntStream.range(0, indexes.size())
						.boxed()
						.sorted(Comparator.comparingDouble(i -> weights[i]))
						.map(i -> labels.get(indexes.get(i).getLeft()) + "-" + labels.get(indexes.get(i).getRight()) + " = " + DebugUtils.printDouble(weights[i]))
						.collect(toList());

				final String highest = IntStream.range(0, Math.min(sorted_pairs.size(), lists_size))
						.map(i -> sorted_pairs.size() - i - 1)
						.mapToObj(sorted_pairs::get)
						.collect(Collectors.joining("\n\t\t", "\n\t\t", ""));
				log.debug("\tHighest similarity pairs: " + highest);

				Matrix rankingMatrix = new Matrix(M);
				JamaPowerIteration alg = new JamaPowerIteration();
				// Matrix finalDistribution =
				alg.run(rankingMatrix, labels);
				//double[] ranking = finalDistribution.getColumnPackedCopy();

//				// Assign ranking values to meanings
//				log.debug("\tTop ranked meanings based on similarity:");
//				IntStream.range(0 , meanings.size())
//						.mapToObj(i -> Pair.of(meanings.get(i), ranking[i]))
//						.sorted(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed())
//						.limit(lists_size)
//						.forEach(p -> log.debug("\t\t" + p.getLeft() + " = " + DebugUtils.printDouble(p.getRight())));
			}
		}
	}

	private void runFrequencyTest(Collection<Candidate> candidates)
	{
		if (candidates.isEmpty())
		{
			log.debug("\tNo candidates");
			return;
		}

		//
		final List<Meaning> meanings = candidates.stream() // for debugging purposes
				.map(Candidate::getMeaning)
				.distinct()
				.collect(toList());
		final Map<String, Long> counts = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		DescriptiveStatistics doc_stats = new DescriptiveStatistics(counts.values().stream().mapToDouble(l -> l).toArray());
		log.debug("\tFrequency in document " + printStats(doc_stats));

		List<Pair<Meaning, Long>> meanings_and_doc_counts = meanings.stream()
				.map(m -> Pair.of(m, counts.get(m.getReference())))
				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
				.collect(toList());
		meanings_and_doc_counts.stream()
				.limit(lists_size)
				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));

		//
		final Map<String, Long> corpus_counts = meanings.stream()
				.map(Meaning::getReference)
				.map(r -> Pair.of(r, corpus.getMeaningCount(r).orElse(0)))
				.collect(Collectors.groupingBy(Pair::getLeft, Collectors.summingLong(Pair::getRight)));
		DescriptiveStatistics corpus_stats = new DescriptiveStatistics(corpus_counts.values().stream().mapToDouble(l -> l).toArray());
		log.debug("\tFrequency in corpus " + printStats(corpus_stats));

		List<Pair<Meaning, Long>> meanings_and_corpus_counts = 	meanings.stream()
				.map(m -> Pair.of(m, corpus_counts.get(m.getReference())))
				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
				.collect(toList());
		meanings_and_corpus_counts.stream()
				.limit(lists_size)
				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));
		//
		final Map<String, Long> corpus_doc_counts = meanings.stream()
				.map(Meaning::getReference)
				.map(r -> Pair.of(r, corpus.getMeaningDocumentCount(r).orElse(0)))
				.collect(Collectors.groupingBy(Pair::getLeft, Collectors.summingLong(Pair::getRight)));
		DescriptiveStatistics corpus_doc_stats = new DescriptiveStatistics(corpus_doc_counts.values().stream().mapToDouble(l -> l).toArray());
		log.debug("\tDocument frequency in corpus " + printStats(corpus_doc_stats));

		List<Pair<Meaning, Long>> meanings_and_corpus_doc_counts = 	meanings.stream()
				.map(m -> Pair.of(m, corpus_doc_counts.get(m.getReference())))
				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
				.collect(toList());
		meanings_and_corpus_doc_counts.stream()
				.limit(lists_size)
				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));

		weighting_functions.forEach(w ->
		{
			w.setContents(candidates);
			final List<Pair<Meaning, Double>> meaning_list = meanings.stream()
					.map(m -> Pair.of(m, w.weight(m.getReference())))
					.sorted(Comparator.comparingDouble(Pair<Meaning, Double>::getRight).reversed())
					.collect(toList());
			final double[] weights = meaning_list.stream()
					.mapToDouble(Pair::getRight)
					.toArray();

			DescriptiveStatistics weight_stats = new DescriptiveStatistics(weights);
			log.debug("\t" + w.getClass().getSimpleName() + " stats " + printStats(weight_stats));
			meaning_list.stream()
					.limit(lists_size)
					.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + DebugUtils.printDouble(p.getRight())));
		});
	}



	private Set<Mention> getMentions(CoreDocument document)
	{

		final List<Pair<Integer, Integer>> ne_offsets = document.entityMentions().stream()
				.map(e -> Pair.of(e.coreMap().get(CoreAnnotations.TokenBeginAnnotation.class),
						e.coreMap().get(CoreAnnotations.TokenEndAnnotation.class)))
				.collect(toList());

		final List<Candidate.Type> ne_types = document.sentences().stream()
				.flatMap(s -> s.entityMentions().stream()
						.map(CoreEntityMention::entityType)
						.map(t ->
						{
							switch (t)
							{
								case "PERSON":
									return Candidate.Type.Person;
								case "ORGANIZATION":
									return Candidate.Type.Organization;
								case "LOCATION":
									return Candidate.Type.Location;
								default:
									return Candidate.Type.Other;
							}
						}))
				.collect(toList());

		Predicate<String> is_punct = (str) -> Pattern.matches("\\p{Punct}", str);
		final List<CoreLabel> tokens = document.tokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size() - 1))
						.mapToObj(j -> Pair.of(i, j))
						.filter(span ->
						{
							final boolean is_single_word = span.getRight() - span.getLeft() == 1;
							final boolean is_nominal = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map(t -> t.get(CoreAnnotations.PartOfSpeechAnnotation.class))
									.anyMatch(pos -> pos.startsWith("N") || pos.endsWith("CC"));
							final boolean is_name = ne_offsets.contains(span);
							final boolean not_punct = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map((CoreLabel::word))
									.noneMatch(is_punct);

							return (is_single_word || is_nominal || is_name) && not_punct; // nouns, conjunctions and names
						})
						.map(span ->
						{
							final String span_text = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map(CoreLabel::word)
									.collect(Collectors.joining(" "));
							final String lemma = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map(CoreLabel::lemma)
									.collect(Collectors.joining(" ")) + tokens.get(span.getRight()).lemma();
							final String pos = tokens.get(span.getRight() - 1).tag(); // right-most tag
							final Candidate.Type ne = ne_offsets.contains(span) ?
									ne_types.get(ne_offsets.indexOf(span)) : Candidate.Type.Other;

							return new Mention(
									tokens.get(span.getRight() - 1).sentIndex() + "-" + span,
									span,
									span_text,
									lemma,
									pos, // in case of doubt, assume it's a noun phrase!
									ne);
						}))
				.flatMap(stream -> stream)
				.collect(toSet());
	}

	private static String printStats(DescriptiveStatistics stats)
	{
		return "Stats: N = " + stats.getN() + "\tmin = " + stats.getMin() + "\tmax = " + stats.getMax() +
				"\tmean = " + DebugUtils.printDouble(stats.getMean()) +
				"\tmedian = " + DebugUtils.printDouble(stats.getPercentile(50)) +
				"\tstd dev = " + DebugUtils.printDouble(stats.getStandardDeviation());
	}
}
