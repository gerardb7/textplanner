package edu.upf.taln.textplanning.amr.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.ibm.icu.util.ULocale;
import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreEntityMention;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.common.BabelNetDictionary;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.min;
import static java.util.stream.Collectors.*;

public class EmpiricalStudy
{
	private static final String regex_delimiter = "((\\n\\r)|(\\r\\n)){2}|(\\r){2}|(\\n){2}";
	private static final int max_tokens = 5;


//	private static final int lists_size = 25;
	private static final String content = "CONTENT";
	private static final String function = "FUNCTION";
	private static final List<String> pos = Arrays.asList("JJ", "RB", "NN", "CD", "VB", content, function);
	//	private static final List<String> content_tags = Arrays.asList("CD", "JJ", "JJR", "JJS", "NN", "NNS", "NNP", "NNPS", "RB", "RBR", "RBS", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ");
	private static final List<String> number_tags = Collections.singletonList("CD");
	private static final List<String> noun_tags = Arrays.asList("NN", "NNS", "NNP", "NNPS");
	private static final List<String> adjective_tags = Arrays.asList("JJ", "JJR", "JJS");
	private static final List<String> adverb_tags = Arrays.asList("RB", "RBR", "RBS");
	private static final List<String> verb_tags = Arrays.asList("VB", "VBD", "VBG", "VBN", "VBP", "VBZ");
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	private static class CorpusInfo implements Serializable
	{
		private int num_docs = 0;
		private final Map<String, Long> tokens_counts_total = new HashMap<>();
		private final List<Map<String, List<Integer>>> tokens_per_doc = new ArrayList<>();
		private final List<Map<String, List<Candidate>>> candidates_pee_doc = new ArrayList<>();
		private final List<Annotation> annotation = new ArrayList<>();
		private final static long serialVersionUID = 1L;
	}

	private static class NECorefStats
	{
		private final int num_tokens;
		private final Map<String, Long> nes = new HashMap<>();
		private final Map<String, Long> nes_tokens = new HashMap<>();
		private final Map<String, Long> nes_tokens_no_meaning = new HashMap<>();
		private final List<Double> coref_lengths = new ArrayList<>();
		private final int num_coref_tokens;
		private final int num_coref_tokens_no_meaning;

		private NECorefStats()
		{
			num_tokens = 0;
			num_coref_tokens = 0;
			num_coref_tokens_no_meaning = 0;
		}

		private NECorefStats(int num_tokens, Map<String, Long> nes, Map<String, Long> nes_tokens, Map<String, Long> nes_tokens_no_meaning,
		                     List<Double> coref_lengths, int num_coref_tokens, int num_coref_tokens_no_meaning)
		{
			this.num_tokens = num_tokens;
			this.nes.putAll(nes);
			this.nes_tokens.putAll(nes_tokens);
			this.nes_tokens_no_meaning.putAll(nes_tokens_no_meaning);
			this.coref_lengths.addAll(coref_lengths);
			this.num_coref_tokens = num_coref_tokens;
			this.num_coref_tokens_no_meaning = num_coref_tokens_no_meaning;
		}

		@Override
		public String toString()
		{
			final long total_nes = nes.values().stream().mapToLong(l -> l).sum();
			final long total_ne_tokens = nes_tokens.values().stream().mapToLong(l -> l).sum();
			final double ne_coverage = ((double) total_ne_tokens) / num_tokens;
			final long total_ne_tokens_no_meaning = nes_tokens_no_meaning.values().stream().mapToLong(l -> l).sum();
			final double ne_coverage_no_meaning = ((double) total_ne_tokens_no_meaning) / num_tokens;

			String out = "\t" + total_nes + " NE mentions " + total_ne_tokens + " tokens out of " + num_tokens + " " +
					DebugUtils.printDouble(ne_coverage, 2) + " coverage " + total_ne_tokens_no_meaning + " tokens with no meaning " +
					DebugUtils.printDouble(ne_coverage_no_meaning, 2) + " coverage\n";
			out += nes.keySet().stream()
					.map(t -> "\t\t" + t + ": " + nes.get(t) + " mentions " + nes_tokens.get(t) + " tokens " +
							DebugUtils.printDouble(((double)nes_tokens.get(t)) / num_tokens, 2) + " coverage " +
							(nes_tokens_no_meaning.containsKey(t) ? nes_tokens_no_meaning.get(t) : 0) + " tokens with no meaning " +
							(nes_tokens_no_meaning.containsKey(t) ? DebugUtils.printDouble(((double)nes_tokens_no_meaning.get(t)) / num_tokens, 2) : "0.0") + " coverage ")
					.collect(Collectors.joining("\n"));

			final double coref_coverage = ((double) num_coref_tokens) / num_tokens;
			final double coref_coverage_no_meaning = ((double) num_coref_tokens_no_meaning) / num_tokens;
			final double[] doubles = coref_lengths.stream()
					.mapToDouble(d -> d)
					.toArray();
			out += "\n\t" + coref_lengths.size() + " coreference chains " + num_coref_tokens + " tokens out of " + num_tokens + " " +
					DebugUtils.printDouble(coref_coverage, 2) + " coverage " + num_coref_tokens_no_meaning + " tokens with no meaning " +
					DebugUtils.printDouble(coref_coverage_no_meaning, 2) + " coverage\n";
			out += "\t\tstats: " + printStats(new DescriptiveStatistics(doubles)) + "\n";
			return out;
		}

		private static NECorefStats combine(NECorefStats s1, NECorefStats s2)
		{
			final Map<String, Long> nes = Stream.concat(s1.nes.entrySet().stream(), s2.nes.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			final List<Double> coref_lengths = Stream.concat(s1.coref_lengths.stream(), s2.coref_lengths.stream())
					.collect(toList());
			final Map<String, Long> nes_tokens = Stream.concat(s1.nes_tokens.entrySet().stream(), s2.nes_tokens.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			final Map<String, Long> nes_tokens_no_meanings = Stream.concat(s1.nes_tokens_no_meaning.entrySet().stream(), s2.nes_tokens_no_meaning.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			return new NECorefStats(s1.num_tokens + s2.num_tokens,
					nes,
					nes_tokens,
					nes_tokens_no_meanings,
					coref_lengths,
					s1.num_coref_tokens + s2.num_coref_tokens,
					s1.num_coref_tokens_no_meaning + s2.num_coref_tokens_no_meaning);
		}
	}

	private static class CoverageStats
	{
		private final List<Double> avg_polysemy = new ArrayList<>();
		private final int num_tokens;
		private final int num_covered_tokens;
		private final int num_multiword_covered_tokens;

		private CoverageStats()
		{
			num_tokens = 0; num_covered_tokens = 0; num_multiword_covered_tokens = 0;
		}

		private CoverageStats(List<Double> avg_polysemy, int num_tokens, int num_covered_tokens, int num_multiword_covered_tokens)
		{
			this.avg_polysemy.addAll(avg_polysemy);
			this.num_tokens = num_tokens;
			this.num_covered_tokens = num_covered_tokens;
			this.num_multiword_covered_tokens = num_multiword_covered_tokens;
		}

		@Override
		public String toString()
		{
			final double avg = avg_polysemy.stream()
					.mapToDouble(Double::doubleValue)
					.average().orElse(0.0);
			return "\tAverage polysemy = " + DebugUtils.printDouble(avg, 2) +
					"\n\tAverage meaning coverage = " + DebugUtils.printDouble(((double)num_covered_tokens) / num_tokens, 2) +
					"\n\t" + num_covered_tokens + " tokens in meaning mentions " +
					"\n\tAverage coverage of multiwords = " + DebugUtils.printDouble(((double)num_multiword_covered_tokens) / num_tokens, 2) +
					"\n\t" + num_multiword_covered_tokens + " tokens in multiwords";
		}

		private static CoverageStats combine(CoverageStats s1, CoverageStats s2)
		{
			return new CoverageStats(
					Stream.concat(s1.avg_polysemy.stream(), s2.avg_polysemy.stream()).collect(toList()),
					s1.num_tokens + s2.num_tokens,
					s1.num_covered_tokens + s2.num_covered_tokens,
					s1.num_multiword_covered_tokens + s2.num_multiword_covered_tokens);
		}
	}

	private static class SimilarityStats
	{
		private final int num_meanings;
		private final long num_meanings_defined;
		private final long num_pairs;
		private final long num_valid_pairs;
		private final long num_pairs_meanings_defined;
		private final List<Double> weights = new ArrayList<>();

		private SimilarityStats(int num_meanings, long num_meanings_defined, long num_pairs, long num_valid_pairs, long num_pairs_meanings_defined, List<Double> weights)
		{
			this.num_meanings = num_meanings;
			this.num_meanings_defined = num_meanings_defined;
			this.num_pairs = num_pairs;
			this.num_valid_pairs = num_valid_pairs;
			this.num_pairs_meanings_defined = num_pairs_meanings_defined;
			this.weights.addAll(weights);
		}

		private SimilarityStats()
		{
			num_meanings = 0; num_meanings_defined = 0; num_pairs = 0; num_valid_pairs = 0; num_pairs_meanings_defined = 0;
		}

		public String toString()
		{
			final double[] doubles = weights.stream()
					.mapToDouble(d -> d)
					.toArray();

			return "\tAverage coverage = " + DebugUtils.printDouble(((double) num_meanings_defined) / num_meanings, 2) +
					"\n\tVectors are defined for " + num_meanings_defined + " out of " + num_meanings + " meanings" +
					"\n\tVectors are defined for " + num_pairs_meanings_defined + " out of " + num_valid_pairs + " valid pairs of meanings (" + num_pairs + " total pairs)" +
					"\n\t" + printStats(new DescriptiveStatistics(doubles));
		}

		private static SimilarityStats combine(SimilarityStats s1, SimilarityStats s2)
		{
			return new SimilarityStats(
					s1.num_meanings + s2.num_meanings,
					s1.num_meanings_defined + s2.num_meanings_defined,
					s1.num_pairs + s2.num_pairs,
					s1.num_valid_pairs + s2.num_valid_pairs,
					s1.num_pairs_meanings_defined + s2.num_pairs_meanings_defined,
					Stream.concat(s1.weights.stream(), s2.weights.stream()).collect(toList()));
		}
	}

	private static class FrequencyStats
	{
		private final int num_meanings;
		private final Map<String, Long> counts = new HashMap<>();
		private final Map<String, Long> doc_counts = new HashMap<>();
		private final Map<String, Long> corpus_counts = new HashMap<>();
		private final Map<String, Long> doc_corpus_counts = new HashMap<>();
		private final Map<String, List<Double>> functions_weights = new HashMap<>();

		private FrequencyStats(int num_meanings, Map<String, Long> counts, Map<String, Long> doc_counts,
		                       Map<String, Long> corpus_counts, Map<String, Long> doc_corpus_counts,
		                       Map<String, List<Double>> functions_weights)

		{
			this.num_meanings = num_meanings;
			this.counts.putAll(counts);
			this.doc_counts.putAll(doc_counts);
			this.corpus_counts.putAll(corpus_counts);
			this.doc_corpus_counts.putAll(doc_corpus_counts);
			this.functions_weights.putAll(functions_weights);
		}

		private FrequencyStats()
		{
			this.num_meanings = 0;
		}

		@Override
		public String toString()
		{
			return "\tFrequency " + printStats( new DescriptiveStatistics(counts.values().stream().mapToDouble(l -> l).toArray())) +
					"\n\tDocument frequency " + printStats( new DescriptiveStatistics(doc_counts.values().stream().mapToDouble(l -> l).toArray())) +
					"\n\tFrequency in corpus " + printStats( new DescriptiveStatistics(corpus_counts.values().stream().mapToDouble(l -> l).toArray())) +
					"\n\tDocument frequency in corpus " + printStats( new DescriptiveStatistics(doc_corpus_counts.values().stream().mapToDouble(l -> l).toArray())) +
					functions_weights.keySet().stream()
							.map(f -> "\n\t" + f + " stats " + printStats(new DescriptiveStatistics(functions_weights.get(f).stream().mapToDouble(l -> l).toArray())))
							.collect(joining());
		}

		private static FrequencyStats combine(FrequencyStats s1, FrequencyStats s2)
		{
			final Map<String, Long> counts = Stream.concat(s1.counts.entrySet().stream(), s2.counts.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			final Map<String, Long> doc_counts = Stream.concat(s1.doc_counts.entrySet().stream(), s2.doc_counts.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			final Map<String, Long> corpus_counts = Stream.concat(s1.corpus_counts.entrySet().stream(), s2.corpus_counts.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
			final Map<String, Long> doc_corpus_counts = Stream.concat(s1.doc_corpus_counts.entrySet().stream(), s2.doc_corpus_counts.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));


			final Map<String, List<Double>> weights = Stream.concat(s1.functions_weights.entrySet().stream(), s2.functions_weights.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (l1, l2) ->
					{
						List<Double> new_l = new ArrayList<>();
						new_l.addAll(l1);
						new_l.addAll(l2);
						return new_l;
					}));
			return new FrequencyStats(s1.num_meanings + s2.num_meanings,	counts,	doc_counts, corpus_counts, doc_corpus_counts, weights );
		}
	}

	public static void processText(String text, Path output, Path bn_config_folder) throws IOException
	{
		final MeaningDictionary bn = new BabelNetDictionary(bn_config_folder, false);

		log.info("Setting up Stanford CoreNLP");
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref");
//		props.setProperty("tokenize.whitespace", "true"); // no tokenization
//		props.setProperty("ssplit.eolonly", "true"); // no sentence splitting
		//props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz"); // PERSON, LOCATION, ORGANIZATION,
		props.setProperty("ner.applyFineGrained", "false");
		props.setProperty("coref.algorithm", "neural");

		Stopwatch gtimer = Stopwatch.createStarted();
		RedwoodConfiguration.current().clear().apply(); // shut up, CoreNLP
		final StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		log.info("CoreNLP pipeline created in " + gtimer.stop());

		gtimer.reset(); gtimer.start();
		final List<String> documents = Arrays.stream(text.split(regex_delimiter))
				.filter(a -> !a.isEmpty())
				.collect(toList());

		log.info("Generating stats for " + documents.size() + " documents");

		CorpusInfo corpus = new CorpusInfo();
		corpus.num_docs = documents.size();

		for (int doc_idx = 0; doc_idx <  corpus.num_docs; ++doc_idx)
		{
			Stopwatch timer = Stopwatch.createStarted();

			// Process document
			CoreDocument document = new CoreDocument(documents.get(doc_idx));
			pipeline.annotate(document);
			corpus.annotation.add(document.annotation());

			// Get number of tokens
			int num_tokens = document.tokens().size();
			final Map<String, List<Integer>> tokens_by_pos = IntStream.range(0, num_tokens)
					.boxed()
					.collect(groupingBy(i -> simplifyTag(document.tokens().get(i).tag())));

			// Add a new category for content tokens
			final List<Integer> content_tokens = tokens_by_pos.keySet().stream()
					.filter(pos -> !pos.equals(function))
					.map(tokens_by_pos::get)
					.flatMap(List::stream)
					.collect(toList());
			tokens_by_pos.put(content, content_tokens);
			corpus.tokens_per_doc.add(tokens_by_pos);

			// Update global map
			tokens_by_pos.keySet().forEach(pos -> corpus.tokens_counts_total.merge(pos, (long) tokens_by_pos.get(pos).size(),
					(l1, l2) -> l1 + l2));
			log.info("CoreNLP processing done");

			final List<Mention> mentions = getMentions(document);
			log.info(mentions.size() + " mentions collected");

			final List<Candidate> candidates = CandidatesCollector.collect(bn, language, mentions);
			final Map<String, List<Candidate>> candidates_by_pos = candidates.stream()
					.collect(groupingBy(c -> simplifyTag(c.getMention().getPOS())));
			candidates_by_pos.put(content, candidates); // Add a new category for all (content) mentions
			corpus.candidates_pee_doc.add(candidates_by_pos);

			log.info("Document " + doc_idx + " processed in " + timer.stop());
		}

		Serializer.serialize(corpus, output);
		log.info("All documents processed in " + gtimer.stop());
	}

	public static void calculateStats(Path input, Path frequencies, Path vectors_path, VectorType vectorType, boolean do_pairwise_similarity) throws Exception
	{
//		final List<WeightingFunction> weighting_functions = new ArrayList<>();
//		CompactFrequencies freqs = (CompactFrequencies)Serializer.deserialize(frequencies);
//		weighting_functions.add(new TFIDF(freqs, r -> true));
//		weighting_functions.add(new NumberForms(r -> true));
		final Vectors vectors = null; //InitialResourcesFactory.get(vectors_path, vectorType, 300);
		final CosineSimilarity sim_function = new CosineSimilarity();
		final VectorsSimilarity sim = new VectorsSimilarity(vectors, sim_function);

		Stopwatch gtimer = Stopwatch.createStarted();
		final CorpusInfo corpus = (CorpusInfo) Serializer.deserialize(input);

		List<NECorefStats> ne_coref_stats = new ArrayList<>();
		Map<String, List<CoverageStats>> coverage_stats = new HashMap<>();
		Map<String, List<SimilarityStats>> similarity_stats = new HashMap<>();
		Map<String, List<FrequencyStats>> frequency_stats = new HashMap<>();

		for (int doc_idx = 0; doc_idx < corpus.num_docs; ++doc_idx)
		{
			Stopwatch timer = Stopwatch.createStarted();
			final Map<String, List<Integer>> tokens = corpus.tokens_per_doc.get(doc_idx);
			final Map<String, List<Candidate>> candidates = corpus.candidates_pee_doc.get(doc_idx);
			final Set<Candidate> candidates_set = candidates.values().stream().flatMap(List::stream).collect(Collectors.toSet());
			final Set<Integer> tokensInMentions = getTokensInMentions(candidates_set);
			final Set<Integer> tokensInMultiwordMentions = getTokensInMultiwordMentions(candidates_set);

			log.info("Calculating NER & coreference stats");
			CoreDocument document = new CoreDocument(corpus.annotation.get(doc_idx));
			NECorefStats ncstats = tokens.containsKey(content) ?
					getNERCorefStats(document, new HashSet<>(tokens.get(content)), tokensInMentions)
					: new NECorefStats();
			ne_coref_stats.add(ncstats);

			log.info("Calculating candidate coverage stats");
			final Map<Integer, Double> tokenPolysemyAverages = getTokenPolysemyAverages(candidates_set);
			pos.forEach(pos ->
			{

				final CoverageStats stats = tokens.containsKey(pos) ?
						getCoverageStats(new HashSet<>(tokens.get(pos)), tokensInMentions, tokensInMultiwordMentions, tokenPolysemyAverages)
						: new CoverageStats();
				coverage_stats.merge(pos, Collections.singletonList(stats), (l1, l2) -> Stream.of(l1, l2).flatMap(List::stream).collect(toList()));
			});

			log.info("Calculating similarity stats");
			pos.forEach(pos ->
			{
				final SimilarityStats stats = candidates.containsKey(pos) ?
						getSimilarityStats(candidates.get(pos), sim, do_pairwise_similarity)
						: new SimilarityStats();
				similarity_stats.merge(pos, Collections.singletonList(stats), (l1, l2) -> Stream.of(l1, l2).flatMap(List::stream).collect(toList()));

			});

//			log.info("Calculating frequency stats");
//			pos.forEach(pos ->
//			{
//				final FrequencyStats stats = candidates.containsKey(pos) ?
//						getFrequencyStats(candidates.get(pos), freqs, weighting_functions)
//						: new FrequencyStats();
//				frequency_stats.merge(pos, Collections.singletonList(stats), (l1, l2) -> Stream.of(l1, l2).flatMap(List::stream).collect(toList()));
//			});
//
//			log.info("Calculated stats for doc " + doc_idx + " in " + timer.stop());
//			log.info("***");
		}

		final String num_tokens_str = corpus.tokens_counts_total.keySet().stream()
				.map(pos -> pos + "=" + corpus.tokens_counts_total.get(pos))
				.collect(joining("\n\t"));
		final long total_tokens = corpus.tokens_counts_total.values().stream().mapToLong(l -> l).sum();
		log.info("Total tokens = " + total_tokens + "\n\t" + num_tokens_str);

		final NECorefStats ne_coref_stats_total = ne_coref_stats.stream()
				.reduce(NECorefStats::combine).orElse(new NECorefStats());
		log.info("NER and Coreference stats\n" + ne_coref_stats_total.toString() + "\n**************************");

		final String coverage_stats_str = coverage_stats.keySet().stream()
				.map(pos ->
				{
					final CoverageStats stats = coverage_stats.get(pos).stream()
							.reduce(CoverageStats::combine).orElse(new CoverageStats());
					return "Stats for " + stats.num_tokens + " tokens with pos " + pos + "\n" + stats.toString();
				})
				.collect(joining("\n"));
		log.info("Candidate coverage stats\n" + coverage_stats_str + "\n**************************");

		final String similarity_stats_str = similarity_stats.keySet().stream()
				.map(pos ->
				{
					final SimilarityStats stats = similarity_stats.get(pos).stream()
							.reduce(SimilarityStats::combine).orElse(new SimilarityStats());
					return "Stats for " + stats.num_meanings + " meanings with pos " + pos + "\n" + stats.toString();
				})
				.collect(joining("\n"));
		log.info("Similarity stats\n" + similarity_stats_str + "\n**************************");

		final String frequency_stats_str = frequency_stats.keySet().stream()
				.map(pos ->
				{
					final FrequencyStats stats = frequency_stats.get(pos).stream()
							.reduce(FrequencyStats::combine).orElse(new FrequencyStats());
					return "Stats for " + stats.num_meanings + " meanings with pos " + pos + "\n" + stats.toString();
				})
				.collect(joining("\n"));
		log.info("Frequency stats\n" + frequency_stats_str + "\n**************************");

		log.info("All stats completed in " + gtimer.stop());
	}

	private static String simplifyTag(String pos)
	{
		if (adjective_tags.contains(pos))
			return "JJ";
		if (adverb_tags.contains(pos))
			return "RB";
		if (noun_tags.contains(pos))
			return "NN";
		if (number_tags.contains(pos))
			return "CD";
		if (verb_tags.contains(pos))
			return "VB";

		return function;
	}

	private static Set<Integer> getTokensInMentions(Collection<Candidate> candidates)
	{
		final List<Mention> mentions = candidates.stream()
				.map(Candidate::getMention)
				.collect(toList());
		final List<List<Integer>> mentions_tokens = mentions.stream()
				.map(m -> IntStream.range(m.getSpan().getLeft(), m.getSpan().getRight())
						.boxed()
						.collect(toList()))
				.collect(toList());

		return mentions_tokens.stream()
				.flatMap(List::stream)
				.collect(toSet());
	}

	private static Set<Integer> getTokensInMultiwordMentions(Collection<Candidate> candidates)
	{
		return candidates.stream()
				.map(Candidate::getMention)
				.filter(Mention::isMultiWord)
				.map(Mention::getSpan)
				.flatMap(span -> IntStream.range(span.getLeft(), span.getRight()).boxed())
				.collect(toSet());
	}

	private static Map<Integer, Double> getTokenPolysemyAverages(Collection<Candidate> candidates)
	{
		// Calculate average polysemy of each token (average number of all_candidates of all mentions spanning over it)
		final List<Mention> mentions = candidates.stream()
				.map(Candidate::getMention)
				.collect(toList());
		final List<List<Integer>> mentions_tokens = mentions.stream()
				.map(m -> IntStream.range(m.getSpan().getLeft(), m.getSpan().getRight())
						.boxed()
						.collect(toList()))
				.collect(toList());

		final Map<Mention, Long> mentions2num_meanings = candidates.stream()
				.collect(Collectors.groupingBy(Candidate::getMention, counting()));

		return IntStream.range(0, mentions.size())
				.mapToObj(i -> mentions_tokens.get(i).stream()
						.map(t -> Pair.of(t, mentions2num_meanings.get(mentions.get(i))))
						.collect(toList()))
				.flatMap(List::stream)
				.collect(groupingBy(Pair::getLeft, averagingLong(Pair::getRight)));
	}

	private static NECorefStats getNERCorefStats(CoreDocument document, Set<Integer> tokens, Set<Integer> tokensInMentions)
	{
		// Report NEs
		final Map<String, Long> ne_counts = document.entityMentions().stream()
				.collect(Collectors.groupingBy(CoreEntityMention::entityType, Collectors.counting()));
		final Collection<CorefChain> chains = document.corefChains().values();
		final List<Double> coref_lengths = chains.stream()
				.map(CorefChain::getMentionsInTextualOrder)
				.mapToDouble(List::size)
				.boxed()
				.collect(toList());

		final Map<Integer, edu.stanford.nlp.coref.data.Mention> corenlp_mentions = document.annotation().get(CorefCoreAnnotations.CorefMentionsAnnotation.class).stream()
				.collect(toMap(m -> m.mentionID, Function.identity()));

		final Set<Integer> tokens_in_nes = tokens.stream()
				.filter(i -> document.tokens().get(i).ner() != null)
				.filter(i -> !document.tokens().get(i).ner().equals("O"))
				.filter(tokens::contains)
				.collect(Collectors.toSet());
		final Map<String, Long> ne_token_counts = tokens_in_nes.stream()
				.collect(groupingBy(i -> document.tokens().get(i).ner(), counting()));
		final Set<Integer> tokens_in_nes_no_meaning = tokens_in_nes.stream()
				.filter(t -> !tokensInMentions.contains(t))
				.collect(Collectors.toSet());
		final Map<String, Long> ne_token_counts_no_meaning = tokens_in_nes_no_meaning.stream()
				.collect(groupingBy(i -> document.tokens().get(i).ner(), counting()));

		log.info(ne_token_counts.values().stream().mapToLong(l -> l).sum() + " NE tokens: " +
				document.tokens().stream()
						.filter(t -> t.ner() != null)
						.filter(t -> !t.ner().equals("O"))
						.map(t -> document.tokens().indexOf(t) + "-" + t.ner() + "-" + t.word())
						.collect(joining(" ")));

		final Set<Integer> tokens_in_chains = chains.stream()
				.map(CorefChain::getMentionsInTextualOrder)
				.flatMap(List::stream)
				.map(m -> {
					try
					{
						final edu.stanford.nlp.coref.data.Mention corenlp_m = corenlp_mentions.get(m.mentionID);
						final int start = document.tokens().indexOf(corenlp_m.originalSpan.get(0));
						final int end = document.tokens().indexOf(corenlp_m.originalSpan.get(corenlp_m.originalSpan.size() - 1));
						return Pair.of(start, end);
					}
					catch (Exception e)
					{
						log.error("Failed to get offsets from coreference mention: " + m.toString() +
									" (begin word=" + (m.startIndex - 1) + " end word=" + (m.endIndex - 1) + ")");
					}

					return null;
				})
				.filter(Objects::nonNull)
				.flatMap(span -> IntStream.range(span.getLeft(), span.getRight()).boxed())
				.filter(tokens::contains)
				.collect(toSet());
		final Set<Integer> tokens_in_chains_no_meanings = tokens_in_chains.stream()
				.filter(t -> !tokensInMentions.contains(t))
				.collect(toSet());

		log.info(tokens_in_chains.size() + " coref tokens:" + chains.stream()
				.map(c -> c.getMentionsInTextualOrder().stream()
						.map(m -> IntStream.range(m.startIndex-1, m.endIndex-1)
								.mapToObj(i -> document.sentences().get(m.sentNum-1).tokens().get(i).word())
								.collect(joining(" ")))
						.collect(joining(", ")))
				.collect(joining("\n\t","\n\t", "")));

		return new NECorefStats(tokens.size(), ne_counts, ne_token_counts, ne_token_counts_no_meaning, coref_lengths,
				tokens_in_chains.size(), tokens_in_chains_no_meanings.size());
	}

	private static CoverageStats getCoverageStats(Set<Integer> tokens, Set<Integer> tokens_in_mentions, Set<Integer> tokens_in_multiwords, Map<Integer, Double> tokenPolysemyAverages)
	{
		if (tokens.isEmpty())
			return new CoverageStats();

		final List<Double> average_polysemy = tokenPolysemyAverages.keySet().stream()
				.filter(tokens::contains)
				.map(tokenPolysemyAverages::get)
				.collect(toList());

		// Calculate share of all tokens spanned over in some mention
		final Sets.SetView<Integer> covered_tokens = Sets.intersection(tokens, tokens_in_mentions);
		// Calculate share of all tokens spanned over by some multiword mention
		final Sets.SetView<Integer> multiword_covered_tokens = Sets.intersection(tokens, tokens_in_multiwords);

		return new CoverageStats(average_polysemy, tokens.size(), covered_tokens.size(), multiword_covered_tokens.size());

	}

	private static  SimilarityStats getSimilarityStats(Collection<Candidate> candidates, VectorsSimilarity sim,
	                                                   boolean do_pairwise_similarity)
	{
		if (candidates.isEmpty())
		{
			log.debug("\tNo candidates");
			return new SimilarityStats();
		}

		DifferentMentionsFilter filter = new DifferentMentionsFilter(candidates);

		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());

		final int num_meanings = meanings.size();
		final long num_meanings_defined = 0;

		if (num_meanings > 1 && do_pairwise_similarity)
		{
			final long num_pairs = CombinatoricsUtils.binomialCoefficient(num_meanings, 2);
			AtomicInteger num_valid_pairs = new AtomicInteger(0);
			final long num_pairs_meanings_defined = IntStream.range(0, num_meanings)
					.mapToLong(i -> IntStream.range(i, num_meanings)
							.filter(j -> i != j)
							.filter(j ->
							{
								final boolean test = filter.test(meanings.get(i), meanings.get(j));
								if (test)
									num_valid_pairs.incrementAndGet();
								return test;
							})
							.filter(j -> sim.apply(meanings.get(i), meanings.get(j)).isPresent())
							.count())
					.sum();

			final List<Double> weights = new ArrayList<>();
			if (num_pairs_meanings_defined > 0)
			{
				final double[][] M = MatrixFactory.createSimilarityMatrix(meanings, sim, filter,
						0.0, false, false, false);
				final List<Pair<Integer, Integer>> indexes = IntStream.range(0, num_meanings)
						.mapToObj(i -> IntStream.range(i, num_meanings)
								.filter(j -> i != j)
								.filter(j -> filter.test(meanings.get(i), meanings.get(j)))
								.filter(j -> sim.apply(meanings.get(i), meanings.get(j)).isPresent())
								.mapToObj(j -> Pair.of(i, j))
								.collect(toList()))
						.flatMap(List::stream)
						.collect(toList());

				indexes.stream()
						.map(p -> M[p.getLeft()][p.getRight()])
						.forEach(weights::add);

//				final List<String> labels = candidates.stream() // for debugging purposes
//						.map(Candidate::getMeaning)
//						.map(Meaning::toString)
//						.distinct()
//						.collect(toList());
//
//				final List<String> sorted_pairs = IntStream.range(0, indexes.size())
//						.boxed()
//						.sorted(Comparator.comparingDouble(weights::get))
//						.map(i -> labels.get(indexes.get(i).getLeft()) + "-" + labels.get(indexes.get(i).getRight()) + " = " + DebugUtils.printDouble(weights.get(i), 2))
//						.collect(toList());
//
//				final String highest = IntStream.range(0, Math.min(sorted_pairs.size(), lists_size))
//						.map(i -> sorted_pairs.size() - i - 1)
//						.mapToObj(sorted_pairs::get)
//						.collect(Collectors.joining("\n\t\t", "\n\t\t", ""));
//				log.debug("\tHighest similarity pairs: " + highest);
//
//				Matrix rankingMatrix = new Matrix(M);
//				JamaPowerIteration alg = new JamaPowerIteration();
//				 Matrix finalDistribution =
//				alg.run(rankingMatrix, labels);
//				double[] ranking = finalDistribution.getColumnPackedCopy();
//
//				// Assign ranking values to meanings
//				log.debug("\tTop ranked meanings based on similarity:");
//				IntStream.range(0 , meanings.size())
//						.mapToObj(i -> Pair.of(meanings.get(i), ranking[i]))
//						.sorted(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed())
//						.limit(lists_size)
//						.peek(p -> log.debug("\t\t" + p.getLeft() + " = " + DebugUtils.printDouble(p.getRight(), 2)))
//						.collect(Collectors.toList());
			}

			return new SimilarityStats(num_meanings, num_meanings_defined, num_pairs, num_valid_pairs.get(), num_pairs_meanings_defined, weights);
		}

		return new SimilarityStats(num_meanings, num_meanings_defined, 0, 0, 0, new ArrayList<>());
	}


//				final List<String> sorted_pairs = IntStream.range(0, indexes.size())
//						.boxed()
//						.sorted(Comparator.comparingDouble(i -> weights[i]))
//						.map(i -> labels.get(indexes.get(i).getLeft()) + "-" + labels.get(indexes.get(i).getRight()) + " = " + DebugUtils.printDouble(weights[i], 2))
//						.collect(toList());
//
//				final String highest = IntStream.range(0, Math.min(sorted_pairs.size(), lists_size))
//						.map(i -> sorted_pairs.size() - i - 1)
//						.mapToObj(sorted_pairs::get)
//						.collect(Collectors.joining("\n\t\t", "\n\t\t", ""));
//				log.debug("\tHighest similarity pairs: " + highest);
//
//				Matrix rankingMatrix = new Matrix(M);
//				JamaPowerIteration alg = new JamaPowerIteration();
				// Matrix finalDistribution =
//				alg.run(rankingMatrix, labels);
				//double[] ranking = finalDistribution.getColumnPackedCopy();

//				// Assign ranking values to meanings
//				log.debug("\tTop ranked meanings based on similarity:");
//				IntStream.range(0 , meanings.size())
//						.mapToObj(i -> Pair.of(meanings.get(i), ranking[i]))
//						.sorted(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed())
//						.limit(lists_size)
//						.forEach(p -> log.debug("\t\t" + p.getLeft() + " = " + DebugUtils.printDouble(p.getRight(), 2)));


//	private static FrequencyStats getFrequencyStats(Collection<Candidate> candidates, CompactFrequencies freqs,
//	                                         Collection<WeightingFunction> weighting_functions)
//	{
//		if (candidates.isEmpty())
//		{
//			return new FrequencyStats();
//		}
//
//		final List<String> meanings = candidates.stream() // for debugging purposes
//				.map(Candidate::getMeaning)
//				.map(Meaning::getReference)
//				.collect(toList()); // must contain duplicates!
//
//		final Map<String, Long> counts = meanings.stream()
//				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
//
//
//		final Map<String, Long> doc_counts = meanings.stream()
//				.distinct()
//				.collect(toMap(Function.identity(), r -> 1L));
//
////		List<Pair<Meaning, Long>> meanings_and_doc_counts = meanings.stream()
////				.map(m -> Pair.of(m, counts.get(m.getReference())))
////				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
////				.collect(toList());
////		meanings_and_doc_counts.stream()
////				.limit(lists_size)
////				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));
//
//		final Map<String, Long> corpus_counts = meanings.stream()
//				.distinct()
//				.map(r -> Pair.of(r, freqs.getMeaningCount(r).orElse(0)))
//				.collect(Collectors.groupingBy(Pair::getLeft, Collectors.summingLong(Pair::getRight)));
//
////		List<Pair<Meaning, Long>> meanings_and_corpus_counts = 	meanings.stream()
////				.map(m -> Pair.of(m, corpus_counts.get(m.getReference())))
////				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
////				.collect(toList());
////		meanings_and_corpus_counts.stream()
////				.limit(lists_size)
////				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));
//
//		final Map<String, Long> corpus_doc_counts = meanings.stream()
//				.distinct()
//				.map(r -> Pair.of(r, freqs.getMeaningDocumentCount(r).orElse(0)))
//				.collect(Collectors.groupingBy(Pair::getLeft, Collectors.summingLong(Pair::getRight)));
//
////		List<Pair<Meaning, Long>> meanings_and_corpus_doc_counts = 	meanings.stream()
////				.map(m -> Pair.of(m, corpus_doc_counts.get(m.getReference())))
////				.sorted(Comparator.comparingLong(Pair<Meaning, Long>::getRight).reversed())
////				.collect(toList());
////		meanings_and_corpus_doc_counts.stream()
////				.limit(lists_size)
////				.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + p.getRight()));
//
////		final Map<String, List<Double>> functions_counts = new HashMap<>();
////		weighting_functions.forEach(w ->
////		{
////			w.setContents(candidates);
////			final List<Pair<String, Double>> meaning_list = meanings.stream()
////					.map(m -> Pair.of(m, w.weight(m)))
////					.sorted(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed())
////					.collect(toList());
////			final List<Double> weights = meaning_list.stream()
////					.map(Pair::getRight)
////					.collect(toList());
////
////			functions_counts.put(w.getClass().getSimpleName(), weights);
////			meaning_list.stream()
////					.limit(lists_size)
////					.forEach(p -> log.debug("\t\t" + p.getLeft().toString() + "\t" + DebugUtils.printDouble(p.getRight(), 2)));
////		});
//
////		return new FrequencyStats(meanings.size(), counts, doc_counts, corpus_counts, corpus_doc_counts, functions_counts);
////	}

	private static List<Mention> getMentions(CoreDocument document)
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

		Predicate<String> is_punct = (str) -> Pattern.matches("\\p{Punct}+", str);
		final List<CoreLabel> tokens = document.tokens();

		return IntStream.range(0, tokens.size())
				.mapToObj(i -> IntStream.range(i + 1, min(i + max_tokens + 1, tokens.size() - 1))
						.mapToObj(j -> Pair.of(i, j))
						.filter(span ->
						{
							// single words
							if (span.getRight() - span.getLeft() == 1)
							{
								// only content words!
								return !document.tokens().get(span.getLeft()).tag().equals(function);
							}

							// multiwords
							final boolean has_noun = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map(t -> t.get(CoreAnnotations.PartOfSpeechAnnotation.class))
									.anyMatch(pos -> pos.startsWith("N"));
							final boolean is_name = ne_offsets.contains(span);
							final boolean not_punct = IntStream.range(span.getLeft(), span.getRight())
									.mapToObj(tokens::get)
									.map((CoreLabel::word))
									.noneMatch(is_punct);

							return (has_noun || is_name) && not_punct; // nouns, conjunctions and names
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

							return Mention.get(
									tokens.get(span.getRight() - 1).sentIndex() + "-" + span,
									span,
									span_text,
									lemma,
									pos, // in case of doubt, assume it's a noun phrase!
									ne != Candidate.Type.Other,
									ne.toString());
						}))
				.flatMap(stream -> stream)
				.collect(toList());
	}

	private static String printStats(DescriptiveStatistics stats)
	{
		return "Stats: N = " + stats.getN() + "\tmin = " + stats.getMin() + "\tmax = " + stats.getMax() +
				"\tmean = " + DebugUtils.printDouble(stats.getMean(), 2) +
				"\tmedian = " + DebugUtils.printDouble(stats.getPercentile(50), 2) +
				"\tstd dev = " + DebugUtils.printDouble(stats.getStandardDeviation(), 2);
	}
}
