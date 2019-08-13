package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.AlternativeMeanings;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.corpus.Corpora.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class GoldDisambiguationEvaluation extends DisambiguationEvaluation
{
	private final Corpus corpus;
	private final Map<Corpora.Text, DocumentResourcesFactory> texts2resources;
	private final Options options = new Options();
	final Map<String, AlternativeMeanings> gold;
	private final Set<POS.Tag> eval_POS;
	private static final int max_span_size = 3;
	private static final boolean multiwords_only = false;
	private final static Logger log = LogManager.getLogger();

	public GoldDisambiguationEvaluation(Path gold_file, Path xml_file, InitialResourcesFactory resources_factory)
	{
		this.options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		this.options.min_bias_threshold = 0.0; // minimum bias value below which candidate meanings are ignored
		this.options.num_first_meanings = 1;
		this.options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		this.options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);
		// Include these Tag in the ranking of meanings
		options.ranking_POS_Tags = Set.of(POS.Tag.NOUN, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV);
		// Evaluate these Tag tags only
		this.eval_POS = Set.of(POS.Tag.NOUN, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV);

		this.corpus = Corpora.createFromXML(xml_file);
		texts2resources = EvaluationTools.createResources(corpus, tagset, resources_factory, max_span_size, excluded_mention_POS, options);
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
					final AlternativeMeanings meanings = gold.get(m.getId());
					if (meanings != null && !(m.getSurfaceForm().equalsIgnoreCase(meanings.text) || m.getLemma().equalsIgnoreCase(meanings.text)))
						log.info("\tMention from XML: " + m.getSurfaceForm() + "/" + m.getLemma() + " doesn't match mention from gold: " + meanings.text);
				});
	}

	@Override
	protected Set<String> getGold(Mention m)
	{
		final AlternativeMeanings meanings = gold.get(m.getId());
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
	protected DocumentResourcesFactory getResources(Corpora.Text text)
	{
		return texts2resources.get(text);
	}

	@Override
	protected Options getOptions()
	{
		return options;
	}

	@Override
	protected Set<POS.Tag> getEvaluatePOS() { return eval_POS; }

	@Override
	protected boolean evaluateMultiwordsOnly()
	{
		return multiwords_only;
	}

	@Override
	protected void evaluate(List<Candidate> candidates, String sufix)
	{
		Predicate<AlternativeMeanings> filter_by_POS = a ->
				(a.alternatives.stream().anyMatch(m -> m.endsWith("n")) && eval_POS.contains(POS.Tag.NOUN)) ||
				(a.alternatives.stream().anyMatch(m -> m.endsWith("v")) && eval_POS.contains(POS.Tag.VERB)) ||
				(a.alternatives.stream().anyMatch(m -> m.endsWith("a")) && eval_POS.contains(POS.Tag.ADJ)) ||
				(a.alternatives.stream().anyMatch(m -> m.endsWith("r")) && eval_POS.contains(POS.Tag.ADV));

		// Determine the actual subset of the gold to be used in the evaluation
		final Map<String, Set<String>> evaluated_gold = new HashMap<>();
		if (multiwords_only)
		{
			gold.entrySet().stream()
					.filter(e -> e.getKey().contains("-"))
					.forEach(e -> evaluated_gold.put(e.getKey(), e.getValue().alternatives));
		}
		else
		{
			gold.entrySet().stream()
					.filter(e -> filter_by_POS.test(e.getValue()))
					.forEach(e -> evaluated_gold.put(e.getKey(), e.getValue().alternatives));
		}

		// Now determine the subset of the system candidates to be evaluated
		final Map<String, String> system = new HashMap<>();
		for (Candidate candidate : candidates)
		{
			final Mention mention = candidate.getMention();
			if (    (multiwords_only && mention.isMultiWord()) ||
					(!multiwords_only && eval_POS.contains(mention.getPOS())))
			{
				system.put(mention.getId(), candidate.getMeaning().getReference());
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
		log.info("\tEvaluated Tag : " + eval_POS);
		log.info("\tPrecision = " + DebugUtils.printDouble(precision, 2));
		log.info("\tRecall = " + DebugUtils.printDouble(recall, 2));
		log.info("\tF1 score = " + DebugUtils.printDouble(fscore, 2));
	}
}
