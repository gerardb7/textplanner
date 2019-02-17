package edu.upf.taln.textplanning.tools;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.BabelNetDictionary;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.ranking.TopCandidatesFilter;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.weighting.Context;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

@SuppressWarnings("ALL")
public class SemEvalEvaluation
{
	@XmlRootElement(name = "corpus")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Corpus
	{
		@XmlAttribute
		public String lang;
		@XmlElement(name = "text")
		public List<Text> texts;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlElement(name="sentence")
		public List<Sentence> sentences;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name="wf")
		public List<Token> tokens;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Token
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String lemma;
		@XmlAttribute
		public String pos;
		@XmlValue
		public String wf;
	}

	private static final int max_span_size = 3;
	private static final String noun_pos_tag = "N";
	private static final String other_pos_tag = "X";
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	public static void evaluate(Path gold_file, Path xml_file, Path babel_config, Path output_path,
	                            ResourcesFactory resources, int max_meanings) throws Exception
	{
		BabelNetDictionary bn = new BabelNetDictionary(babel_config);

		log.info("Parsing XML file");
		final Corpus corpus = parse(xml_file);

		log.info("Collecting mentions");
		final List<List<List<Mention>>> mentions = collectMentions(corpus);

		log.info("Looking up meanings");
		List<List<List<List<Candidate>>>> candidates = collectCandidates(bn, corpus, mentions);

		log.info("Creating contexts");
		List<Context> contexts = createContexts(corpus, candidates, resources);

		log.info("Ranking meanings (random)");
		random_rank(candidates);
		log.info("Results (random)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "random.results", false, false);
		log.info("Results (random, no multiwords)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "random_nomw.results", true, false);

		log.info("Ranking meanings (first sense)");
		first_sense_rank(candidates, bn);
		log.info("Results (first sense)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "first_sense.results", false, false);
		log.info("Results (first sense, no multiwords)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "first_sense_nomw.results", true, false);

		log.info("Ranking meanings (context only)");
		context_rank(candidates, contexts, resources);
		log.info("Results (context only)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "context.results", false, false);
		log.info("Results (context only, no multiwords)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "context_nomw.results", true, false);

		log.info("Ranking meanings (full)");
		full_rank(candidates, contexts, resources, max_meanings, output_path);
		log.info("Results (full)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "full.results", false, false);
		log.info("Results (full, no multiwords)");
		evaluate(corpus, candidates, gold_file, xml_file, output_path, "full_nomw.results", true, false);
		log.info("DONE");
	}

	public static Corpus parse(Path xml_file) throws JAXBException
	{
		JAXBContext jc = JAXBContext.newInstance(Corpus.class);
		StreamSource xml = new StreamSource(xml_file.toString());
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
		return je1.getValue();
	}

	private static List<List<List<Mention>>> collectMentions(Corpus corpus)
	{
		// create mention objects
		return corpus.texts.stream()
				.map(d -> d.sentences.stream()
						.map(s -> IntStream.range(0, s.tokens.size())
								.mapToObj(start -> IntStream.range(start + 1, Math.min(start + max_span_size + 1, s.tokens.size()))
										.filter(end ->
										{
											// single words must have a pos tag other than 'X'
											if (end - start == 1)
												return !s.tokens.get(start).pos.equalsIgnoreCase(other_pos_tag);
												// multiwords must contain at least one nominal token
											else
												return s.tokens.subList(start, end).stream()
														.anyMatch(t -> t.pos.equalsIgnoreCase(noun_pos_tag));
										})
										.mapToObj(end ->
										{
											final List<Token> tokens = s.tokens.subList(start, end);
											final String form = tokens.stream()
													.map(t -> t.wf)
													.collect(joining(" "));
											final String lemma = tokens.stream()
													.map(t -> t.lemma != null ? t.lemma : t.wf)
													.collect(joining(" "));
											String pos = tokens.size() == 1 ? tokens.get(0).pos : noun_pos_tag;
											return Mention.get(s.id, Pair.of(start, end), form, lemma, pos, false, "");
										}))
								.flatMap(stream -> stream)
								.collect(toList()))
						.collect(toList()))
				.collect(toList());
	}

	private static List<List<List<List<Candidate>>>> collectCandidates(BabelNetDictionary bn, Corpus corpus, List<List<List<Mention>>> mentions)
	{
		log.info("Collecting mentions");
		final Map<Triple<String, String, String>, List<Mention>> forms2mentions = mentions.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()))
				.collect(Collectors.groupingBy(m -> Triple.of(m.getSurface_form(), m.getLemma(), m.getPOS())));

		log.info("\tQuerying " + forms2mentions.keySet().size() + " forms");
		final Map<Triple<String, String, String>, List<Meaning>> forms2meanings = forms2mentions.keySet().stream()
				.collect(toMap(t -> t, t -> getSynsets(bn, t).stream()
						.map(meaning -> Meaning.get(meaning, bn.getLabel(meaning, language).orElse(""), false))
						.collect(toList())));

		// use traditional loops to make sure that the order of lists of candidates is preserved
		Map<Mention, List<Candidate>> mentions2candidates = new HashMap<>();
		for (Triple<String, String, String> t : forms2mentions.keySet())
		{
			final List<Mention> form_mentions = forms2mentions.get(t);
			final List<Meaning> form_meanings = forms2meanings.get(t);
			for (Mention mention : form_mentions)
			{
				final List<Candidate> mention_candidates = form_meanings.stream().map(meaning -> new Candidate(mention, meaning)).collect(toList());
				mentions2candidates.put(mention, mention_candidates);
			}
		}

		return mentions.stream()
				.map(text_mentions ->
						text_mentions.stream()
								.map(sentence_mentions ->
										sentence_mentions.stream()
												.map(mention -> mentions2candidates.get(mention))
												.collect(toList()))
								.collect(toList()))
				.collect(toList());
	}

	/**
	 * Returns sorted list of meanings for mention using lemma and form
	 */
	private static List<String> getSynsets(BabelNetDictionary bn, Triple<String, String, String> mention)
	{
		// Use surface form of mention as label
		String form = mention.getLeft();
		String lemma = mention.getMiddle();
		String pos = mention.getRight();
		// Lemma meanings first, sorted by dictionary criteria (BabelNet -> best sense)
		List<String> synsets = bn.getMeanings(lemma, pos, language);

		// Form meanings go after
		if (!lemma.equalsIgnoreCase(form))
		{
			List<String> lemma_synsets = bn.getMeanings(form, pos, language);
			lemma_synsets.removeAll(synsets);
			synsets.addAll(lemma_synsets);
		}

		return synsets;
	}

	private static List<Context> createContexts(Corpus corpus, List<List<List<List<Candidate>>>> candidates,
	                                            ResourcesFactory resources)
	{
		final List<Context> contexts = new ArrayList<>();
		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final List<Candidate> document_candidates = candidates.get(i).stream()
					.flatMap(l -> l.stream()
							.flatMap(s -> s.stream()))
					.collect(toList());
			final List<Meaning> document_meanings = document_candidates.stream()
					.map(Candidate::getMeaning)
					.distinct()
					.collect(toList());
			final Text document = corpus.texts.get(i);

			final List<String> context = document.sentences.stream()
					.flatMap(s -> s.tokens.stream()
							.map(t -> t.wf))
					.collect(toList());

			final Context context_weighter = new Context(document_candidates, resources.getSenseContextVectors(),
					resources.getSentenceVectors(), w -> context, resources.getSimilarityFunction());
			contexts.add(context_weighter);
		}

		return contexts;
	}

	private static void random_rank(List<List<List<List<Candidate>>>> candidates)
	{
		Random rand = new Random();
		candidates.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()
								.flatMap(s -> s.stream()
										.map(c -> c.getMeaning()))))
				.distinct()
				.forEach(c ->  c.setWeight(rand.nextDouble()));
	}

	private static void first_sense_rank(List<List<List<List<Candidate>>>> candidates, BabelNetDictionary bn)
	{
		candidates.forEach(doc ->
				doc.forEach(sentence ->
						sentence.forEach(candidate_list ->
								{
									final Iterator<Candidate> it = candidate_list.iterator();
									if (it.hasNext())
									{
										it.next().getMeaning().setWeight(1.0);
										it.forEachRemaining(c -> c.getMeaning().setWeight(0.0));
									}
								})));
	}

	private static void context_rank(List<List<List<List<Candidate>>>> candidates, List<Context> contexts,
	                                 ResourcesFactory resources)
	{
		// Rank and serialize candidate meanings
		for (int i = 0; i < candidates.size(); ++i)
		{
			final Context document_context = contexts.get(i);
			candidates.get(i).forEach(sentence ->
					sentence.forEach(candidate_set ->
							candidate_set.stream()
									.map(Candidate::getMeaning)
									.distinct()
									.forEach(m -> m.setWeight(document_context.weight(m.getReference())))));
		}
	}

	private static void full_rank(List<List<List<List<Candidate>>>> candidates, List<Context> contexts,
	                         ResourcesFactory resources, int max_meanings, Path output_path)
			throws IOException
	{
		// Rank and serialize candidate meanings
		for (int i = 0; i < candidates.size(); ++i)
		{
			final List<Candidate> candidates_i = candidates.get(i).stream()
					.flatMap(l -> l.stream()
							.flatMap(s -> s.stream()))
					.collect(toList());

			final Context context_weighter = contexts.get(i);
			final VectorsSimilarity sim = new VectorsSimilarity(resources.getSenseVectors(), resources.getSimilarityFunction());
			final TopCandidatesFilter candidates_filter = new TopCandidatesFilter(candidates_i, context_weighter::weight, max_meanings);
			final DifferentMentionsFilter meanings_filter = new DifferentMentionsFilter(candidates_i);

			final long num_filtered_candidates = candidates_i.stream()
					.filter(candidates_filter)
					.count();
			final long num_meanings = candidates_i.stream()
					.filter(candidates_filter)
					.map(Candidate::getMeaning)
					.distinct()
					.count();

			log.info("\tRanking document " + (i + 1) + " with " + candidates_i.size() + "candidates, " +
					num_filtered_candidates + " filtered candidates, and " +
					num_meanings + " distinct meanings");
			TextPlanner.rankMeanings(candidates_i, candidates_filter, meanings_filter, context_weighter::weight,
					sim::of, new TextPlanner.Options());

//			final List<List<Set<Candidate>>> grouped_candidates = candidates_i.stream()
//					.collect(groupingBy(c -> c.getMention().getSentenceId(), groupingBy(c -> c.getMention().getSpan(), toSet())))
//					.entrySet().stream()
//					.sorted(Comparator.comparing(Map.Entry::getKey))
//					.map(Map.Entry::getValue)
//					.map(e -> e.entrySet().stream()
//							.sorted(Comparator.comparingInt(e2 -> e2.getKey().getLeft()))
//							.map(Map.Entry::getValue)
//							.collect(toList()))
//					.collect(toList());
//
//			if (!Files.exists(output_path))
//				Files.createDirectories(output_path);
//
//			final String out_filename = document.id + ".candidates";
//			FileUtils.serializeMeanings(grouped_candidates, output_path.resolve(out_filename));
		}
	}

	private static void evaluate(Corpus corpus, List<List<List<List<Candidate>>>> candidates, Path gold_file, Path xml_file,
	                             Path output_path, String sufix, boolean exclude_multiwords, boolean print_debug) throws IOException
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.flatMap(l -> l.stream()
						.flatMap(l2 -> l2.stream()
								.flatMap(s -> s.stream())))
				.collect(groupingBy(Candidate::getMention));
		final List<Pair<Mention, List<Candidate>>> sorted_candidates = mentions2candidates.keySet().stream()
				.sorted(Comparator.comparing(Mention::getSentenceId).thenComparing(Mention::getSpan))
				.map(m -> Pair.of(m, mentions2candidates.get(m).stream()
						.sorted(Comparator.<Candidate>comparingDouble(c -> c.getMeaning().getWeight()).reversed())
						.collect(toList())))
				.collect(toList());

		if (print_debug)
		{
			log.info("Texts:" + corpus.texts.stream()
					.map(d -> d.sentences.stream()
							.map(s -> s.tokens.stream()
									.map(t -> t.wf)
									.collect(joining(" ")))
							.collect(joining("\n\t", "\t", "")))
					.collect(joining("\n---\n", "\n", "\n")));

			sorted_candidates.forEach(p -> log.info("\"" + p.getLeft() + "\" " + p.getLeft().getPOS() + p.getRight().stream()
					.map(Candidate::getMeaning)
					.map(m -> m.toString() + " " + DebugUtils.printDouble(m.getWeight()))
					.collect(joining("\n\t", "\n\t", ""))));
		}

		String results = sorted_candidates.stream()// mention list is already sorted
				.filter(p -> !(exclude_multiwords && p.getLeft().isMultiWord())) // exclude multiwords if necessary
				.map(Pair::getRight)
				.filter(l -> !l.isEmpty())
				.map(l -> l.get(0)) // top candidate from sorted candidate list
				.map(c -> {
					final Mention mention = c.getMention();
					final String sentenceId = mention.getSentenceId();
					final Text document = corpus.texts.stream()
							.filter(d -> sentenceId.startsWith(d.id))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Sentence sentence = document.sentences.stream()
							.filter(s -> s.id.equals(sentenceId))
							.findFirst().orElseThrow(() -> new RuntimeException());
					final Token first_token = sentence.tokens.get(mention.getSpan().getLeft());
					final Token last_token = sentence.tokens.get(mention.getSpan().getRight() - 1);

					return first_token.id + "\t" + last_token.id + "\t" + c.getMeaning().getReference();
				})
				.collect(joining("\n"));

		final Path results_file = FileUtils.createOutputPath(xml_file, output_path, "xml", sufix);
		FileUtils.writeTextToFile(results_file, results);
		log.info("Results file written to " + results_file);
		Scorer.main(new String[]{gold_file.toString(), results_file.toString()});
	}
}
