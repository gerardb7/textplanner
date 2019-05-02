package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.AlternativeMeanings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class GoldDisambiguationEvaluation extends DisambiguationEvaluation
{
	private final EvaluationTools.Corpus corpus;
	final Map<String, AlternativeMeanings> gold;
	private final InitialResourcesFactory resources_factory;
	private final Options options = new Options();
	private final static Logger log = LogManager.getLogger();

	private static final int max_span_size = 3;
	private static boolean exclude_multiwords = false;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final ULocale language = ULocale.ENGLISH;

	public GoldDisambiguationEvaluation(Path gold_file, Path xml_file, Path output_path,
	                                    InitialResourcesFactory resources_factory, Options options)
	{
		this.corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path, resources_factory, language, max_span_size, noun_pos_tag, options);
		this.gold = parseGoldFile(gold_file);
		this.resources_factory = resources_factory;
		options.excluded_POS_Tags = Set.of(other_pos_tag, adverb_pos_tag);

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

	private Map<String, AlternativeMeanings> parseGoldFile(Path gold_file)
	{
		log.info("Parsing gold file");
		return Arrays.stream(FileUtils.readTextFile(gold_file).split("\n"))
				.filter(not(String::isEmpty))
				.filter(l -> !l.startsWith("@text"))
				.map(l -> l.split("\t"))
				.filter(a -> a.length == 4)
				.collect(toMap(
						a -> a[0].equals(a[1]) ? a[0] : a[0] + "-" + a[1],
						a ->  new AlternativeMeanings(Arrays.asList(a[2].split("\\|")),
								a[3].substring(1, a[3].length() -1), a[0], a[1])));

	}

	@Override
	protected Set<String> getGold(Mention m)
	{
		final AlternativeMeanings meanings = gold.get(m.getContextId());
		if (!m.getSurface_form().equals(meanings.text))
			log.warn("Mention from XML: " + m.getSurface_form() + " doesn't match mention from gold: " + meanings.text);
		return meanings.alternatives;
	}

	@Override
	protected EvaluationTools.Corpus getCorpus()
	{
		return corpus;
	}

	@Override
	protected InitialResourcesFactory getFactory()
	{
		return resources_factory;
	}

	@Override
	protected Options getOptions()
	{
		return options;
	}

	@Override
	protected void evaluate(List<List<List<Candidate>>> system, String sufix)
	{
		int true_positives = 0;
		int false_positives = 0;

		final List<Candidate> candidates = system.stream()
				.flatMap(Collection::stream)
				.flatMap(Collection::stream)
				.collect(toList());

		for (Candidate candidate : candidates)
		{

			final Mention mention = candidate.getMention();
			final Set<String> gold_meanings = getGold(mention);
			final String system_meaning = candidate.getMeaning().getReference();
			if (gold_meanings.contains(system_meaning))
				++true_positives;
			else
				++false_positives;
		}

		double precision = (double)true_positives / (double)(true_positives + false_positives);
		double recall = (double)true_positives / (double)(this.gold.size());
		double fscore = 2.0*(precision * recall) / (precision + recall);
		log.info("Evalaution results:");
		log.info("Precision = " + DebugUtils.printDouble(precision));
		log.info("Recall = " + DebugUtils.printDouble(recall));
		log.info("F1 score = " + DebugUtils.printDouble(fscore));
	}
}
