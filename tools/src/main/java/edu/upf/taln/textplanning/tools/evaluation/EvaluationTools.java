package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.resources.DocumentResourcesFactory;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.FileUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

public class EvaluationTools
{
	public static class AlternativeMeanings
	{
		final Set<String> alternatives = new HashSet<>();
		final String text; // covered text or label
		final String begin;
		final String end;

		AlternativeMeanings(Collection<String> meanings, String text, String begin, String end)
		{
			alternatives.addAll(meanings);
			this.text = text;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public String toString()
		{
			return text + " " + begin + "-" + end + " = " + alternatives;
		}
	}

	private static final String corpus_filename = "corpus.xml";
	private final static Logger log = LogManager.getLogger();

	public static Corpora.Corpus loadResourcesFromRawText(Path text_folder, Path output_path)
	{
		String corpus_contents = null;
		try
		{
			Stanford2SemEvalXML stanford = new Stanford2SemEvalXML();
			corpus_contents = stanford.convert(text_folder, "txt", output_path.resolve(corpus_filename));
		}
		catch (Exception e)
		{
			log.error("Failed to preprocess text files " + e);
			e.printStackTrace();
		}

		if (corpus_contents != null)
			return Corpora.createFromXML(output_path);
		return null;

	}



	public static void printMeaningRankings(Corpora.Corpus corpus, DocumentResourcesFactory resources,
	                                        Map<String, Set<AlternativeMeanings>> gold, boolean multiwords_only,
	                                        Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
				printMeaningRankings(corpus.texts.get(i), resources, gold, multiwords_only, eval_POS));
	}

	public static void printMeaningRankings(Corpora.Text text, DocumentResourcesFactory resources,
	                                        Map<String, Set<AlternativeMeanings>> gold, boolean multiwords_only,
	                                        Set<POS.Tag> eval_POS)
	{
			log.debug("TEXT " + text.id);
			final Set<String> text_gold = gold.get(text.id).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());


			final int max_length = text.sentences.stream()
					.flatMap(s -> s.disambiguated_meanings.values().stream())
					.map(Candidate::getMeaning)
					.map(Meaning::toString)
					.mapToInt(String::length)
					.max().orElse(5) + 4;

			final Function<String, Double> weighter = resources.getBiasFunction();

			final Map<Meaning, Double> weights = text.sentences.stream()
					.flatMap(s -> s.disambiguated_meanings.values().stream())
					.filter(m -> (multiwords_only && m.getMention().isMultiWord()) || (!multiwords_only && eval_POS.contains(m.getMention().getPOS())))
					.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));

			final List<Meaning> meanings = new ArrayList<>(weights.keySet());
			Function<Meaning, String> inGold = m -> text_gold.contains(m.getReference()) ? "GOLD" : "";

			log.debug(meanings.stream()
//					.filter(m -> weights.get(m) > 0.0)
					.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
					.map(m -> String.format("%-" + max_length + "s%-11s%-11s%-8s",
							m.toString(),
							DebugUtils.printDouble(weights.get(m)),
							DebugUtils.printDouble(weighter.apply(m.getReference())),
							inGold.apply(m)))
					.collect(joining("\n\t", "Meaning ranking by ranking score (and bias) :\n\t",
							"\n--------------------------------------------------------------------------------------------")));
	}

	public static void printDisambiguationResults(Corpora.Corpus corpus, DocumentResourcesFactory resources,
	                                              Function<Mention, Set<String>> gold,
	                                              boolean multiwords_only, Set<POS.Tag> eval_POS)
	{
		IntStream.range(0, corpus.texts.size()).forEach(i ->
				printDisambiguationResults(corpus.texts.get(i), resources, gold, multiwords_only, eval_POS));
	}

	public static void printDisambiguationResults(Corpora.Text text, DocumentResourcesFactory resources,
	                                              Function<Mention, Set<String>> gold, boolean multiwords_only,
	                                              Set<POS.Tag> eval_POS)
	{
		log.info("TEXT " + text.id);

		final int max_length = text.sentences.stream()
				.flatMap(s -> s.candidate_meanings.values().stream())
				.flatMap(Collection::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.mapToInt(String::length)
				.max().orElse(5) + 4;

		final Function<String, Double> bias = resources.getBiasFunction();

		text.sentences.forEach(s ->
				s.candidate_meanings.forEach((mention, candidates) ->
				{
					if (candidates.isEmpty())
						return;

					if ((multiwords_only && !mention.isMultiWord() || (!multiwords_only && !eval_POS.contains(mention.getPOS()))))
						return;

					final String max_bias = candidates.stream()
							.map(Candidate::getMeaning)
							.map(Meaning::getReference)
							.max(comparingDouble(bias::apply))
							.orElse("");
					final String max_rank = candidates.stream()
							.max(comparingDouble(c -> c.getWeight().orElse(0.0)))
							.map(Candidate::getMeaning)
							.map(Meaning::getReference).orElse("");
					final String first_m = candidates.get(0).getMeaning().getReference();

					final Set<String> gold_set = gold.apply(mention);
					final String result = gold_set.contains(max_rank) ? "OK" : "FAIL";
					final String ranked = max_rank.equals(first_m) ? "" : "RANKED";
					Function<String, String> marker = (r) ->
							(gold_set.contains(r) ? "GOLD " : "") +
									(r.equals(max_bias) ? "BIAS " : "") +
									(r.equals(max_rank) ? "RANK" : "");


					log.info(mention.getId() + " \"" + mention + "\" " + mention.getPOS() + " " + result + " " + ranked +
							candidates.stream()
									.map(c ->
									{
										final String mark = marker.apply(c.getMeaning().getReference());
										final String bias_value = DebugUtils.printDouble(bias.apply(c.getMeaning().getReference()));
										final String rank_value = c.getWeight().map(DebugUtils::printDouble).orElse("");

										return String.format("%-15s%-" + max_length + "s%-15s%-15s", mark,
												c.getMeaning().toString(), bias_value, rank_value);
									})
									.collect(joining("\n\t", "\t\n\t" + String.format("%-15s%-" + max_length + "s%-15s%-15s\n\t", "Status", "Candidate", "Bias", "Rank"), "")));
				}));

	}

	public static void writeDisambiguatedResultsToFile(Corpora.Corpus corpus, Path output_file)
	{
		final String str = corpus.texts.stream()
				.map(text -> text.sentences.stream()
						.map(sentence -> sentence.disambiguated_meanings.keySet().stream()
								.sorted(Comparator.comparing(Mention::getId))
								.map(mention ->
								{
									String id = mention.getId();
									if (id.contains("-"))
									{
										final String[] parts = id.split("-");
										id = parts[0] + "\t" + parts[1];
									}
									else
										id = id + "\t" + id;
									return id + "\t" +
											sentence.disambiguated_meanings.get(mention).getMeaning().getReference() + "\t\"" +
											mention.getSurfaceForm() + "\"";
								})
								.collect(joining("\n")))
						.collect(joining("\n")))
				.collect(joining("\n"));

		FileUtils.writeTextToFile(output_file, str);
	}
}
