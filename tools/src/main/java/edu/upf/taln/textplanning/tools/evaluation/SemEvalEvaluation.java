package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.corpus.Corpora.Corpus;
import edu.upf.taln.textplanning.core.corpus.Corpora.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static java.util.stream.Collectors.*;

@SuppressWarnings("ALL")
public class SemEvalEvaluation extends DisambiguationEvaluation
{
	private final Path gold_file;
	private final Path xml_file;
	private final Path output_path;
	private final Corpus corpus;
	private final DocumentResourcesFactory resources;
	private final Options options = new Options();
	private final Map<String, String> gold;
	private final Set<POS.Tag> eval_POS;
	private final int max_span_size = 3;
	private final boolean rank_together = false;
	private boolean exclude_multiwords = false;
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	public SemEvalEvaluation(   Path gold_file, Path xml_file, Path output_path, InitialResourcesFactory initial_resources)
	{
		this.options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		this.options.min_bias_threshold = 0.8; // minimum bias value below which candidate meanings are ignored
		this.options.num_first_meanings = 1;
		this.options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		this.options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);
		// Include these Tag in the ranking of meanings
		options.ranking_POS_Tags = Set.of(POS.Tag.NOUN); //, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV);
		// Evaluate these Tag tags only
		this.eval_POS = Set.of(POS.Tag.NOUN, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV);

		this.corpus = Corpora.createFromXML(xml_file);
		this.gold_file = gold_file;
		this.xml_file = xml_file;
		this.output_path = output_path;

		log.info("Parsing gold file");
		gold = Arrays.stream(FileUtils.readTextFile(gold_file).split("\n"))
				.map(l -> l.split("\t"))
				.filter(a -> a.length >= 3)
				.collect(toMap(a -> a[0].equals(a[1]) ? a[0] : (a[0] + "-" + a[1]), a -> a[2]));
		log.info(gold.keySet().size() + " lines read from gold");

		resources = EvaluationTools.createJointResources(corpus, tagset, initial_resources, max_span_size, excluded_mention_POS, options);
	}

	@Override
	protected Set<String> getGold(Mention m)
	{
		if (!gold.containsKey(m.getId()))
			return Set.of();

		return Set.of(gold.get(m.getId()));
	}

	@Override
	protected Corpus getCorpus()
	{
		return corpus;
	}

	@Override
	protected DocumentResourcesFactory getResources(Corpora.Text text)
	{
		return resources;
	}

	@Override
	protected Options getOptions()
	{
		return options;
	}

	@Override
	protected void checkCandidates(Corpus corpus)
	{
		log.info("Checking candidate mentions against gold");
		corpus.texts.stream()
				.flatMap(text -> text.sentences.stream()
						.flatMap(sentence -> sentence.candidate_meanings.values().stream()
								.flatMap(List::stream)))
				.map(Candidate::getMention)
				.distinct()
				.forEach(m -> {
	//				if (!gold.containsKey(m.getId()))
	//					log.info("\tMention " + m + " not in gold");
				});
	}

	@Override
	protected Set<POS.Tag> getEvaluatePOS() { return eval_POS; }

	@Override
	protected boolean evaluateMultiwordsOnly()
	{
		return false;
	}

	@Override
	protected void evaluate(List<Candidate> system, String sufix)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = system.stream()
				.collect(groupingBy(Candidate::getMention));
		final List<Pair<Mention, List<Candidate>>> sorted_candidates = mentions2candidates.keySet().stream()
				.sorted(Comparator.comparing(Mention::getId).thenComparing(Mention::getSpan))
				.map(m -> Pair.of(m, mentions2candidates.get(m).stream()
						.sorted(Comparator.<Candidate>comparingDouble(c -> c.getWeight().orElse(0.0)).reversed())
						.collect(toList())))
				.collect(toList());

		String results = sorted_candidates.stream()// mention list is already sorted
				.filter(p -> eval_POS.contains(p.getLeft().getPOS()))
				.filter(p -> !(exclude_multiwords && p.getLeft().isMultiWord())) // exclude multiwords if necessary
				.map(Pair::getRight)
				.filter(l -> !l.isEmpty())
				.map(l -> l.get(0)) // top candidate from sorted candidate list
				.map(c ->
				{
					final Mention mention = c.getMention();
					final String sourceId = mention.getContextId();
					final Text document = corpus.texts.stream()
							.filter(d -> sourceId.startsWith(d.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Corpora.Sentence sentence = document.sentences.stream()
							.filter(s -> sourceId.equals(s.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Corpora.Token first_token = sentence.tokens.get(mention.getSpan().getLeft());
					final Corpora.Token last_token = sentence.tokens.get(mention.getSpan().getRight() - 1);

					return first_token.id + "\t" + last_token.id + "\t" + c.getMeaning().getReference();
				})
				.collect(joining("\n"));

		final Path results_file = FileUtils.createOutputPath(xml_file, output_path, "xml", sufix);
		FileUtils.writeTextToFile(results_file, results);
		log.info("Results file written to " + results_file);
		log.info("Evaluated Tag : " + eval_POS);


		try
		{
			SemEvalScorer.main(new String[]{gold_file.toString(), results_file.toString()});
		}
		catch (Exception e)
		{
			log.error("Error while running SemEvalScore: " + e);
			e.printStackTrace();
		}
	}
}
