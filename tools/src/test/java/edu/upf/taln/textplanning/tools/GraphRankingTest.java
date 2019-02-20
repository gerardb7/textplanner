package edu.upf.taln.textplanning.tools;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.BabelNetDictionary;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.similarity.CosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.VectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GraphRankingTest
{
	private static Map<String, String> music;

	static
	{
		Map<String, String> m = new HashMap<>();

		// concepts
		m.put("bn:00056443n", "music");
		m.put("bn:00056469n", "musical scale");
		m.put("bn:14651975n", "rythm");
		m.put("bn:00002191n", "melody");
		m.put("bn:01664904n", "breve");
		m.put("bn:02563071n", "groove");

		// music genres
		m.put("bn:01640606n", "baroque");
		m.put("bn:00063557n", "pop");
		m.put("bn:00442162n", "synth pop");
		m.put("bn:01945414n", "romantic music");
		m.put("bn:00035010n", "flamenco");

		// people
		m.put("bn:00007700n", "Bach");
		m.put("bn:16252690n", "Beyoncé");
		m.put("bn:03331280n", "Coltrane");
		m.put("bn:03685791n", "Eliott Smith");
		m.put("bn:00056176n", "Mozart");

		// compositions
		m.put("bn:01018766n", "Four seasons");
		m.put("bn:21706498n", "Revolver");
		m.put("bn:08296886n", "Cantares");
		m.put("bn:02416450n", "Bitches Brew");
		m.put("bn:03462586n", "Cantigas de Santa María");

		// keyboards
		m.put("bn:00035986n", "piano");
		m.put("bn:00059461n", "organ");
		m.put("bn:00075745n", "synthesizer");

		// string
		m.put("bn:00034250n", "violin");
		m.put("bn:00017047n", "cello");
		m.put("bn:00042150n", "guitar");

		// percusion
		m.put("bn:00024792n", "cymbals");
		m.put("bn:00028891n", "drum");

		// brasswind
		m.put("bn:00078389n", "trombone");
		m.put("bn:00022750n", "trumpet");
		m.put("bn:00008924n", "tuba");

		// woodwind
		m.put("bn:00043159n", "oboe");
		m.put("bn:00069426n", "saxophone");
		m.put("bn:00019455n", "clarinet");

		music = Collections.unmodifiableMap(m);
	}

	private static Map<String, String> politics;

	static
	{
		Map<String, String> m = new HashMap<>();

		// concepts
		m.put("bn:00063351n", "politics");
		m.put("bn:00001423n", "government");
		m.put("bn:00048503n", "judiciary");
		m.put("bn:00026749n", "devolution");
		m.put("bn:00010392n", "bill");
		m.put("bn:00801631n", "parliamentary debate");

		// political affiliations
		m.put("bn:00072531n", "social democracy");
		m.put("bn:00072559n", "socialism");
		m.put("bn:00078420n", "Trotskyism");
		m.put("bn:00057253n", "neoliberalism");
		m.put("bn:00050953n", "libertarianism");

		// politicans
		m.put("bn:00004559n", "Tony Blair");
		m.put("bn:03370815n", "Hollande");
		m.put("bn:02303317n", "Palin");
		m.put("bn:02963560n", "Jaume Matas");
		m.put("bn:00882895n", "Kirchner");
		m.put("bn:01991031n", "Shinzō Abe");

		// political parties
		m.put("bn:00400605n", "Norway liberals");
		m.put("bn:01156217n", "Libdems");
		m.put("bn:03139123n", "Bahujan Samaj Party");
		m.put("bn:01108294n", "Partido popular cristiano");
		m.put("bn:03853009n", "Society of Seminary Teachers of Qom");

		politics = Collections.unmodifiableMap(m);
	}

	private final static Logger log = LogManager.getLogger();

	private void rankMeanings() throws Exception
	{
		final Path babel_config_path = Paths.get("/home/gerard/data/babelconfig/");
		//final Path vectors_path = Paths.get("/home/gerard/data/sensembed-vectors-merged_bin");
		//final Path vectors_path = Paths.get("/home/gerard/data/sew-embed.nasari_bin");
		final Path vectors_path = Paths.get("/home/gerard/data/NASARIembed+UMBC_w2v_bin");
		MeaningDictionary bn = new BabelNetDictionary(babel_config_path, false);
		GloveKeysReader reader = new GloveKeysReader(vectors_path);
		final Vectors vectors = null; // ResourcesFactory.get(vectors_path, Vectors.VectorType.Binary_RandomAccess, 300);
		final VectorsSimilarity sim = new VectorsSimilarity(vectors, new CosineSimilarity());

		music.keySet().stream()
				.filter(s -> !vectors.isDefinedFor(s))
				.forEach(s -> log.error("Vectors not defined for " + s + " " + music.get(s)));
		politics.keySet().stream()
				.filter(s -> !vectors.isDefinedFor(s))
				.forEach(s -> log.error("Vectors not defined for " + s + " " + politics.get(s)));


		Random rand = new Random();
		final Map<String, String> random = IntStream.range(0, 150)
				.map(i -> rand.nextInt(reader.getNumKeys()))
				.mapToObj(reader::get)
				.filter(vectors::isDefinedFor)
				.distinct()
				.limit(100)
				.collect(Collectors.toMap(Function.identity(), s -> s + "-" + bn.getLabel(s, ULocale.ENGLISH)));

		// 1- music
		final List<String> music_synsets = music.keySet().stream()
				.filter(vectors::isDefinedFor)
				.collect(Collectors.toList());
		final List<String> music_labels = music_synsets.stream()
				.map(music::get)
				.collect(Collectors.toList());
		VisualizationUtils.visualizeSimilarityMatrix("Music", music_synsets, music_labels, sim::of);

		// 2- politics
		final List<String> politics_synsets = politics.keySet().stream()
				.filter(vectors::isDefinedFor)
				.collect(Collectors.toList());
		final List<String> politics_labels = politics_synsets.stream()
				.map(politics::get)
				.collect(Collectors.toList());
		VisualizationUtils.visualizeSimilarityMatrix("Politics", politics_synsets, politics_labels, sim::of);

		// 3 & 4- music + random, politics + random
		final List<String> random_synsets = random.keySet().stream()
				.filter(vectors::isDefinedFor)
				.collect(Collectors.toList());
		final List<String> random_labels = random_synsets.stream()
				.map(random::get)
				.collect(Collectors.toList());
		final List<String> music_random_synsets = new ArrayList<>(music_synsets);
		music_random_synsets.addAll(random_synsets);
		final List<String> music_random_labels = new ArrayList<>(music_labels);
		music_random_labels.addAll(random_labels);
		VisualizationUtils.visualizeSimilarityMatrix("Music + random", music_random_synsets, music_random_labels, sim::of);

		final List<String> politics_random_synsets = new ArrayList<>(politics_synsets);
		politics_random_synsets.addAll(random_synsets);
		final List<String> politics_random_labels = new ArrayList<>(politics_labels);
		politics_random_labels.addAll(random_labels);
		VisualizationUtils.visualizeSimilarityMatrix("Politics + random", politics_random_synsets, politics_random_labels, sim::of);

		TextPlanner.Options o = new TextPlanner.Options();
		o.sim_threshold = 0;
		BiPredicate<String, String> filter = (s1, s2) -> true;
		VisualizationUtils.visualizeRankingVector("Music ranking", music_synsets, music_labels, m -> 0.0, sim::of, filter, o.sim_threshold, o.damping_meanings);
		VisualizationUtils.visualizeRankingVector("Politics ranking", politics_synsets, politics_labels, m -> 0.0, sim::of, filter, o.sim_threshold, o.damping_meanings);
		VisualizationUtils.visualizeRankingVector("Music + random ranking", music_random_synsets, music_random_labels, m -> 0.0, sim::of, filter, o.sim_threshold, o.damping_meanings);
		VisualizationUtils.visualizeRankingVector("Politics + random ranking", politics_random_synsets, politics_random_labels, m -> 0.0, sim::of, filter, o.sim_threshold, o.damping_meanings);
	}


	public static void main(String[] args) throws Exception
	{
		GraphRankingTest test = new GraphRankingTest();
		test.rankMeanings();
	}
}