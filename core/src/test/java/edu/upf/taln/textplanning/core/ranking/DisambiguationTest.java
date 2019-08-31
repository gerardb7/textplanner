package edu.upf.taln.textplanning.core.ranking;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.bias.DomainBias;
import edu.upf.taln.textplanning.core.dictionaries.CachedDictionary;
import edu.upf.taln.textplanning.core.dictionaries.CompactDictionary;
import edu.upf.taln.textplanning.core.dictionaries.CandidatesCollector;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.*;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class DisambiguationTest
{

	// Random domain disambiguation
	@Test
	public void randomDisambiguate()
	{
		final List<Candidate> candidates = IntStream.range(0, 10)
				.mapToObj(i -> IntStream.range(0, 3)
						.mapToObj(j -> Meaning.get("mention" + i + "_meaning" + j, "", false))
						.map(meaning -> new Candidate(new Mention("mention" + i, "", Pair.of(i, i + 1), List.of(), "", POS.Tag.NOUN, false, ""), meaning)))
				.flatMap(s -> s)
				.collect(toList());
		Set<String> domain_synsets = Set.of();
		Vectors sense_vectors = new RandomVectors();
		CosineSimilarity cosine = new CosineSimilarity();
		BiasFunction bias = new DomainBias(candidates, domain_synsets, sense_vectors, cosine);
		SimilarityFunction sim = new VectorsSimilarity(sense_vectors, cosine);
		Options options = new Options();
		TextPlanner.rankMeanings(candidates, c -> true, (c1, c2) -> true, bias, sim, options);
		Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
		disambiguation.disambiguate(candidates);
	}

	// Domain disambiguation
	@Test
	public void domainDisambiguate() throws Exception
	{
		final Path dictionary_cache_file = Paths.get("src/test/resources/dictionary_cache.bin");
		final Path vectors_cache_file = Paths.get("src/test/resources/vectors_cache.bin");

		// Mentions to look uo
		// 0-Riesgo de 2-incendio en 4-el 5-Saler , 7-altas 8-temperaturas y 10-fuerte 11-viento , 13-estad 14-alerta
		Mention mention1 = new Mention("mention1", "s1", Pair.of(0,1), List.of("Riesgo"), "riesgo", POS.Tag.NOUN, false, "");
		Mention mention2 = new Mention("mention2", "s1", Pair.of(2,3), List.of("incendio"), "incendio", POS.Tag.NOUN, false, "");
		Mention mention3 = new Mention("mention3", "s1", Pair.of(4,6), List.of("el Saler"), "el Saler", POS.Tag.NOUN, true, "");
		Mention mention4 = new Mention("mention4", "s1", Pair.of(5,6), List.of("Saler"), "Saler", POS.Tag.NOUN, false, "");
		Mention mention5 = new Mention("mention5", "s1", Pair.of(7,8), List.of("altas"), "alta", POS.Tag.ADJ, false, "");
		Mention mention6 = new Mention("mention6", "s1", Pair.of(8,9), List.of("temperaturas"), "temperatura", POS.Tag.NOUN, false, "");
		Mention mention7 = new Mention("mention7", "s1", Pair.of(10,11), List.of("fuerte"), "fuerte", POS.Tag.ADJ, false, "");
		Mention mention8 = new Mention("mention8", "s1", Pair.of(11,12), List.of("viento"), "viento", POS.Tag.NOUN, false, "");
		Mention mention9 = new Mention("mention9", "s1", Pair.of(13,14), List.of("estad"), "estar", POS.Tag.VERB, false, "");
		Mention mention10 = new Mention("mention10", "s1", Pair.of(14,15), List.of("alerta"), "alerta", POS.Tag.ADJ, false, "");
		Mention mention11 = new Mention("mention11", "s1", Pair.of(13,15), List.of("estad alerta"), "estar alerta", POS.Tag.VERB, false, "");
		final List<Mention> mentions = List.of(mention1, mention2, mention3, mention4, mention5, mention6, mention7, mention8, mention9, mention10, mention11);

		// València, forest fire, heat, emergency
		final Set<String> domain_synsets = Set.of("bn:00079484n", "bn:00035870n", "bn:00043416n", "bn:00030525n");
		final ULocale language = new ULocale("es");

		// Create dictionary and collect candidate senses
		final FileInputStream fis = new FileInputStream(dictionary_cache_file.toFile());
		final ObjectInputStream ois = new ObjectInputStream(fis);
		final CompactDictionary cache = (CompactDictionary) ois.readObject();
		ois.close();
		final CachedDictionary dictionary = new CachedDictionary(cache);
		final Map<Mention, List<Candidate>> mentions2candidates = CandidatesCollector.collect(dictionary, language, mentions);
		final List<Candidate> candidates = mentions2candidates.values().stream().flatMap(List::stream).collect(toList());
		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());
		meanings.addAll(domain_synsets);

		// Create vectors
		final Vectors word_vectors = new TextVectors(vectors_cache_file, Vectors.VectorType.Text_Word2vec);
		final SentenceVectors sentence_vectors = new BoWVectors(word_vectors);
		final Vectors sense_vectors = new SenseGlossesVectors(language, meanings, s -> dictionary.getGlosses(s, language), sentence_vectors);
		final CosineSimilarity cosine = new CosineSimilarity();
		final BiasFunction bias = new DomainBias(candidates, domain_synsets, sense_vectors, cosine);
		final SimilarityFunction sim = new VectorsSimilarity(sense_vectors, cosine);
		final Options options = new Options();

		// Create filters
		final Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurfaceForm(), language);
		final TopCandidatesFilter top_filter =
				new TopCandidatesFilter(mentions2candidates, bias, options.num_first_meanings, options.min_bias_threshold);
		final Predicate<Candidate> pos_filter =	c -> options.ranking_POS_Tags.contains(c.getMention().getPOS());
		final Predicate<Candidate> candidates_filter = top_filter.and(pos_filter).and(function_words_filter);
		final DifferentMentionsFilter mentions_filter = new DifferentMentionsFilter(candidates);

		// Rank and disambiguate
		TextPlanner.rankMeanings(candidates, candidates_filter, mentions_filter, bias, sim, options);
		final Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
		disambiguation.disambiguate(candidates);
	}
}