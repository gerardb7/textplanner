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
import edu.upf.taln.textplanning.core.weighting.Context;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	public static Corpus parse(Path xml_file) throws JAXBException
	{
		JAXBContext jc = JAXBContext.newInstance(Corpus.class);
		StreamSource xml = new StreamSource(xml_file.toString());
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
		return je1.getValue();
	}

	public static void evaluate(Path gold_file, Path xml_file, Path babel_config, Path output_path, ResourcesFactory resources) throws Exception
	{
		Paths.get("");
		// parse xml
		log.info("Parsing XML file");
		Corpus corpus = parse(xml_file);

		// create mention objects
		log.info("Collecting mentions");
		final List<List<Mention>> mentions = corpus.texts.stream()
				.map(d -> d.sentences.stream()
						.flatMap(s -> IntStream.range(0, s.tokens.size())
								.mapToObj(start -> IntStream.range(start + 1, Math.min(start + max_span_size + 1, s.tokens.size()))
										.mapToObj(end ->
										{
											final List<Token> tokens = s.tokens.subList(start, end);
											final String form = tokens.stream()
													.map(t -> t.wf)
													.collect(Collectors.joining(" "));
											final String lemma = tokens.stream()
													.map(t -> t.lemma != null ? t.lemma : t.wf)
													.collect(Collectors.joining(" "));
											String pos = tokens.size() == 1 ? tokens.get(0).pos : noun_pos_tag;
											return Mention.get(s.id, Pair.of(start, end), form, lemma, pos, false, "");
										}))
								.flatMap(stream -> stream))
						.collect(toList()))
				.collect(toList());

		// Look up candidate meanings and create Candidate objects
		log.info("Looking up meanings");
		BabelNetDictionary bn = new BabelNetDictionary(babel_config);
		List<List<Candidate>> candidates = new ArrayList<>();

		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Map<Pair<String, String>, List<Mention>> forms2mentions = mentions.get(i).stream()
					.collect(Collectors.groupingBy(m -> Pair.of(m.getSurface_form(), m.getPOS())));
			log.info("\tQuerying " + forms2mentions.keySet().size() + " forms for document " + corpus.texts.get(i).id);

			final List<List<Set<Candidate>>> candidate_sets = forms2mentions.keySet().stream()
					.map(p -> bn.getMeanings(p.getLeft(), p.getRight(), language).stream()
							.map(meaning -> Meaning.get(meaning, bn.getLabel(meaning, language).orElse(""), false))
							.map(meaning -> forms2mentions.get(p).stream()
									.map(mention -> new Candidate(meaning, mention))
									.collect(toSet()))
							.collect(toList()))
					.collect(toList());

			candidates.add(candidate_sets.stream()
					.flatMap(l -> l.stream()
							.flatMap(Set::stream))
					.collect(toList()));
		};

		// Rank and serialize candidate meanings
		log.info("Ranking meanings");
		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final List<Candidate> candidates_i = candidates.get(i);
			final List<Meaning> meanings_i = candidates_i.stream()
					.map(Candidate::getMeaning)
					.distinct()
					.collect(toList());
			final Text document = corpus.texts.get(i);
			log.info("\tRanking " + meanings_i.size() + " candidates for document " + document.id);
			final List<String> context = document.sentences.stream()
					.flatMap(s -> s.tokens.stream()
							.map(t -> t.wf))
					.collect(toList());
			final Context context_weighter = new Context(candidates_i, resources.getSenseContextVectors(),
					resources.getSentenceVectors(), w -> context, resources.getSimilarityFunction());
			final VectorsSimilarity sim = new VectorsSimilarity(resources.getSenseVectors(), resources.getSimilarityFunction());
			TopCandidatesFilter candidates_filter = new TopCandidatesFilter(candidates_i, context_weighter::weight, 5);
			DifferentMentionsFilter meanings_filter = new DifferentMentionsFilter(candidates_i);
			TextPlanner.rankMeanings(candidates_i, candidates_filter, meanings_filter, context_weighter::weight,
					sim::of, new TextPlanner.Options());
//
//			meanings_i.stream()
//					.forEach(m -> m.setWeight(context_weighter.weight(m.getReference())));

			final List<List<Set<Candidate>>> grouped_candidates = candidates_i.stream()
					.collect(groupingBy(c -> c.getMention().getSentenceId(), groupingBy(c -> c.getMention().getSpan(), toSet())))
					.entrySet().stream()
					.sorted(Comparator.comparing(Map.Entry::getKey))
					.map(Map.Entry::getValue)
					.map(e -> e.entrySet().stream()
							.sorted(Comparator.comparingInt(e2 -> e2.getKey().getLeft()))
							.map(Map.Entry::getValue)
							.collect(toList()))
					.collect(toList());

			if (!Files.exists(output_path))
				Files.createDirectories(output_path);

			final String out_filename = document.id + ".candidates";
			FileUtils.serializeMeanings(grouped_candidates, output_path.resolve(out_filename));
		}

		// Select top meanings as disambiguation results and save to evaluation file
		final Map<Mention, Optional<Candidate>> top_candidates = candidates.stream()
				.flatMap(List::stream)
				.collect(groupingBy(c -> c.getMention(),
						maxBy(Comparator.comparingDouble(c -> c.getMeaning().getWeight()))));

		String results = top_candidates.values().stream()
				.filter(Optional::isPresent)
				.map(Optional::get)
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
				.collect(Collectors.joining("\n"));

		final Path results_file = FileUtils.createOutputPath(xml_file, output_path, "xml", "results");
		FileUtils.writeTextToFile(results_file, results);
		log.info("Results file written to " + results_file);
		Scorer.main(new String[]{gold_file.toString(), results_file.toString()});
		log.info("DONE");
	}
}
