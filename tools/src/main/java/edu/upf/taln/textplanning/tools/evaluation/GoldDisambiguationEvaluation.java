package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.AlternativeMeanings;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class GoldDisambiguationEvaluation extends DisambiguationEvaluation
{
	private final Corpus corpus;
	final Map<String, AlternativeMeanings> gold;
	private final Options options;
	private final Set<String> evaluate_POS;
	private final static Logger log = LogManager.getLogger();

	private static final int max_span_size = 3;
	private static final boolean multiwords_only = false;
	private static final boolean rank_together = true;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final ULocale language = ULocale.ITALIAN;

	public GoldDisambiguationEvaluation(Path gold_file, Path xml_file, Path output_path,
	                                    InitialResourcesFactory resources_factory, Options options)
	{
		this.options = options;
		this.options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		this.options.min_bias_threshold = 0.7; // minimum bias value below which candidate meanings are ignored
		this.options.num_first_meanings = 1;
		this.options.sim_threshold = 0.0; // Pairs of meanings with sim below this value have their score set to 0
		this.options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias

		this.options.damping_variables = 0.2; // controls bias towards meanings rank when ranking variables
		this.options.num_subgraphs_extract = 1000; // Number of subgraphs to extract
		this.options.extraction_lambda = 1.0; // Controls balance between weight of nodes and cost of edges during subgraph extraction
		this.options.num_subgraphs = 10; // Number of subgraphs to include in the plan
		this.options.tree_edit_lambda = 0.1;

		// Exclude POS from mention collection
		final Set<String> excluded_mention_POS = Set.of(other_pos_tag);
		// Exclude POS from ranking of meanings (but included in WSD, ranking of mentions, etc)
		this.options.excluded_ranking_POS_Tags = Set.of(other_pos_tag, adj_pos_tag, verb_pos_tag, adverb_pos_tag);
		// Evaluate these POS tags only
		this.evaluate_POS = Set.of(adverb_pos_tag); //noun_pos_tag, adj_pos_tag, verb_pos_tag, adverb_pos_tag);

		this.corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path, resources_factory, language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);
		this.gold = parseGoldFile(gold_file);

		// Check gold anns
		final List<String> tokens = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.flatMap(s -> s.tokens.stream())
				.map(t -> t.id)
				.collect(toList());
		gold.values().forEach(a -> {
			if (!tokens.contains(a.begin) || !tokens.contains(a.end))
				log.error("Incorrect gold annotation " + a);
		});
	}

	private static Map<String, AlternativeMeanings> parseGoldFile(Path gold_file)
	{
		log.info("Parsing gold file");
		return Arrays.stream(FileUtils.readTextFile(gold_file).split("\n"))
				.filter(not(String::isEmpty))
				.filter(l -> !l.startsWith("@text"))
				.map(l -> l.split("\t"))
				.filter(a -> a.length == 4)
				.map(a -> Arrays.stream(a).map(String::trim).collect(toList()))
				.collect(toMap(
						a -> a.get(0).equals(a.get(1)) ? a.get(0) : a.get(0) + "-" + a.get(1),
						a ->  new AlternativeMeanings(Arrays.asList(a.get(2).split("\\|")),
								a.get(3).substring(1, a.get(3).length() -1), a.get(0), a.get(1))));

	}

	@Override
	protected void checkCandidates(Corpus corpus)
	{
		log.info("Checking candidate mentions against gold");
		corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidates.values().stream()
								.flatMap(List::stream)))
				.map(Candidate::getMention)
				.distinct()
				.forEach(m -> {
					final AlternativeMeanings meanings = gold.get(m.getContextId());
					if (meanings == null)
						log.info("\tMention " + m + " not in gold");
					else if (!m.getSurface_form().equalsIgnoreCase(meanings.text))
						log.info("\tMention from XML: " + m.getSurface_form() + " doesn't match mention from gold: " + meanings.text);
				});
	}

	@Override
	protected Set<String> getGold(Mention m)
	{
		final AlternativeMeanings meanings = gold.get(m.getContextId());
		if (meanings == null)
			return Set.of();

		return meanings.alternatives;
	}

	@Override
	protected Corpus getCorpus()
	{
		return corpus;
	}

	@Override
	protected Options getOptions()
	{
		return options;
	}

	@Override
	protected Set<String> getEvaluatePOS() { return evaluate_POS; }

	@Override
	protected boolean evaluateMultiwordsOnly()
	{
		return multiwords_only;
	}

	@Override
	protected void evaluate(List<Candidate> candidates, String sufix)
	{
		// Split gold by POS
		final Map<String, Set<String>> gold_nouns = gold.entrySet().stream()
				.filter(e -> e.getValue().alternatives.stream().anyMatch(m -> m.endsWith("n")))
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().alternatives));
		final Map<String, Set<String>> gold_verbs = gold.entrySet().stream()
				.filter(e -> e.getValue().alternatives.stream().anyMatch(m -> m.endsWith("v")))
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().alternatives));
		final Map<String, Set<String>> gold_adjs = gold.entrySet().stream()
			.filter(e -> e.getValue().alternatives.stream().anyMatch(m -> m.endsWith("a")))
			.collect(toMap(Map.Entry::getKey, e -> e.getValue().alternatives));
		final Map<String, Set<String>> gold_advs = gold.entrySet().stream()
			.filter(e -> e.getValue().alternatives.stream().anyMatch(m -> m.endsWith("r")))
			.collect(toMap(Map.Entry::getKey, e -> e.getValue().alternatives));

		// Determine the actual subset of the gold to be used in the evaluation
		final Map<String, Set<String>> evaluated_gold = new HashMap<>();
		if (multiwords_only)
			gold.entrySet().stream()
				.filter(e -> e.getKey().contains("-"))
				.forEach(e -> evaluated_gold.put(e.getKey(), e.getValue().alternatives));
		else
		{
			if (evaluate_POS.contains(noun_pos_tag))
				evaluated_gold.putAll(gold_nouns);
			if (evaluate_POS.contains(verb_pos_tag))
				evaluated_gold.putAll(gold_verbs);
			if (evaluate_POS.contains(adj_pos_tag))
				evaluated_gold.putAll(gold_adjs);
			if (evaluate_POS.contains(adverb_pos_tag))
				evaluated_gold.putAll(gold_advs);
		}

		// Now determine the subset of the system candidates to be evaluated
		final Map<String, String> system = new HashMap<>();
		for (Candidate candidate : candidates)
		{
			final Mention mention = candidate.getMention();
			if (    (multiwords_only && mention.isMultiWord()) ||
					(!multiwords_only && evaluate_POS.contains(mention.getPOS())))
			{
				system.put(mention.getContextId(), candidate.getMeaning().getReference());
			}
		}

		// True positives
		final Map<String, String> true_positives = system.keySet().stream()
				.filter(k -> evaluated_gold.containsKey(k) && evaluated_gold.get(k).contains(system.get(k)))
				.collect(toMap(k -> k, system::get));

		// False positives
		final Map<String, String> false_positives = system.keySet().stream()
				.filter(k -> !evaluated_gold.containsKey(k) || !evaluated_gold.get(k).contains(system.get(k)))
				.collect(toMap(k -> k, system::get));

		// False negatives
		final Map<String, Set<String>> false_negatives = evaluated_gold.keySet().stream()
				.filter(k -> !system.containsKey(k) || !evaluated_gold.get(k).contains(system.get(k)))
				.collect(toMap(k -> k, evaluated_gold::get));

		double precision = (double)true_positives.size() / (double)(true_positives.size() + false_positives.size());
		double recall = (double)true_positives.size() / (double)(true_positives.size() + false_negatives.size());
		double fscore = 2.0*(precision * recall) / (precision + recall);
		log.info("\tEvaluated POS : " + evaluate_POS);
		log.info("\tPrecision = " + DebugUtils.printDouble(precision, 2));
		log.info("\tRecall = " + DebugUtils.printDouble(recall, 2));
		log.info("\tF1 score = " + DebugUtils.printDouble(fscore, 2));
	}
}
