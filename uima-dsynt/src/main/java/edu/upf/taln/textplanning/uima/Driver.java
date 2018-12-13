package edu.upf.taln.textplanning.uima;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import edu.upf.taln.textplanning.common.BabelNetWrapper;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.core.similarity.RandomAccessVectorsSimilarity;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.utils.Serializer;
import edu.upf.taln.textplanning.core.utils.VisualizationUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Driver
{
	private static final String text_suffix = ".story";
	private static final String gold_suffix = ".gold";
	private static final String candidates_suffix = ".candidates";
	private static final String get_candidates_command = "candidates";
	private static final String get_system_command = "system";
	private static final String evaluate_command = "evaluate";
	private static final String visualize_command = "visualize";
	private final static Logger log = LogManager.getLogger();

	private static void getCandidates(Path input_folder, Path output_folder, Path babelnet_config) throws IOException
	{
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);

		BabelNetWrapper bn = new BabelNetWrapper(babelnet_config);

		log.info("Processing texts with UIMA and looking up candidate meanings");
		final UIMAPipelines uima = UIMAPipelines.createSpanPipeline(false);
		if (uima == null)
			System.exit(-1);

		Arrays.stream(text_files)
				.sorted(Comparator.comparing(File::getName))
				.forEach(f ->
				{
					final String text = readTextFile(f);
					final List<List<Set<Candidate>>> candidates = uima.getCandidates(text, bn);
					serializeCandidates(candidates, f, output_folder);
				});
	}

	private static void getSystemMeanings(Path input_folder, Path output_folder, Path babel_config, Path freqs_file, Path vectors)
	{
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);

		log.info("Processing texts with UIMA and collecting system meanings");
		final UIMAPipelines uima = UIMAPipelines.createRankingPipeline(false, babel_config, freqs_file, vectors);
		if (uima == null)
			System.exit(-1);

		Arrays.stream(text_files)
				.sorted(Comparator.comparing(File::getName))
				.forEach(f ->
				{
					final String text = readTextFile(f);
					final List<List<Set<Candidate>>> candidates = uima.getDisambiguatedCandidates(text);
					serializeCandidates(candidates, f, output_folder);
				});
	}

	private static void visualize(Path gold_folder, Path system_folder, Path candidates_folder, Path freqs_file, Path vectors)
	{
		log.info("Loading files");
		final File[] gold_files = getFilesInFolder(gold_folder, gold_suffix);
		final File[] system_files = getFilesInFolder(system_folder, candidates_suffix);
		final File[] candidates_files = getFilesInFolder(candidates_folder, candidates_suffix);

		if (gold_files.length != system_files.length)
		{
			log.error("Mismatch between number of text and meanings files");
			System.exit(1);
		}

		final List<Pair<String, String>> gold_meanings = readGoldMeanings(gold_files).stream()
				.flatMap(List::stream)
				.flatMap(List::stream)
				.flatMap(Set::stream)
				.collect(toList());
		final List<Pair<String, String>> candidate_meanings = deserializeCandidates(system_files).stream()
				.flatMap(List::stream)
				.flatMap(List::stream)
				.flatMap(Set::stream)
				.map(Candidate::getMeaning)
				.map(m -> Pair.of(m.getReference(), m.toString()))
				.collect(toList());
		final List<Pair<String, String>> system_meanings = deserializeCandidates(candidates_files).stream()
				.flatMap(List::stream)
				.flatMap(List::stream)
				.flatMap(Set::stream)
				.map(Candidate::getMeaning)
				.map(m -> Pair.of(m.getReference(), m.toString()))
				.collect(toList());

		log.info("Visualizing similarity matrices");
		List<Pair<String, String>> candidates_with_system = new ArrayList<>(candidate_meanings);
		candidates_with_system.removeAll(system_meanings);
		candidates_with_system.addAll(system_meanings);

		final List<String> cws_meanings = candidates_with_system.stream()
				.map(Pair::getLeft)
				.collect(Collectors.toList());
		final List<String> cws_labels = candidates_with_system.stream()
				.map(Pair::getRight)
				.collect(Collectors.toList());
		RandomAccessVectorsSimilarity sim = RandomAccessVectorsSimilarity.create(vectors);
		VisualizationUtils.visualizeSimilarityMatrix("Candidate and system meanings", cws_meanings, cws_labels, sim);

		List<Pair<String, String>> candidates_with_gold = new ArrayList<>(candidate_meanings);
		candidates_with_gold.removeAll(gold_meanings);
		candidates_with_gold.addAll(gold_meanings);

		final List<String> cwg_meanings = candidates_with_gold.stream()
				.map(Pair::getLeft)
				.collect(Collectors.toList());
		final List<String> cwg_labels = candidates_with_gold.stream()
				.map(Pair::getRight)
				.collect(Collectors.toList());
		VisualizationUtils.visualizeSimilarityMatrix("Candidate and gold meanings", cwg_meanings, cwg_labels, sim);
	}

	private static List<List<List<Set<Pair<String, String>>>>> readGoldMeanings(File[] gold_files)
	{
		return Arrays.stream(gold_files)
				.map(Driver::readTextFile)
				.map(text ->
				{
					final String regex = "(bn:\\d+[r|a|v|n](\\|bn:\\d+[r|a|v|n])*)-\"([^\"]+)\"";
					final Pattern pattern = Pattern.compile(regex);
					final Predicate<String> is_meanings = pattern.asPredicate();

					return Pattern.compile("\n+").splitAsStream(text)
							.filter(is_meanings)
							.map(pattern::matcher)
							.map(m ->
							{
								final List<Set<Pair<String, String>>> meanings = new ArrayList<>();
								while (m.find())
								{
									final String meanings_string = m.group(1);
									final String[] meanings_parts = meanings_string.split("\\|");
									final Set<String> alternatives = Arrays.stream(meanings_parts)
											.map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
											.collect(toSet());
									final String covered_text = m.group(2);
									meanings.add(alternatives.stream()
											.map(a -> Pair.of(a, covered_text))
											.collect(toSet()));
								}

								return meanings;
							})
							.collect(toList());
				})
				.collect(toList());
	}

	private static void serializeCandidates(List<List<Set<Candidate>>> candidates, File text_file, Path output_folder)
	{
		log.info("Serializing candidates for  " + text_file);
		try
		{
			Path out_file = createOutputPath(text_file.toPath(), output_folder, text_suffix, candidates_suffix);
			Serializer.serialize(candidates, out_file);
		}
		catch (Exception e)
		{
			log.error("Cannot store candidates for file " + text_file);
		}
	}

	private static List<List<List<Set<Candidate>>>> deserializeCandidates(File[] files)
	{
		List<List<List<Set<Candidate>>>> candidates = new ArrayList<>();
		Arrays.stream(files).forEach(f ->
		{
			try
			{
				@SuppressWarnings("unchecked")
				List<List<Set<Candidate>>> c = (List<List<Set<Candidate>>>)Serializer.deserialize(f.toPath());
				candidates.add(c);
			}
			catch (Exception e)
			{
				log.error("Cannot store candidates for file " + f);
			}
		});

		return candidates;
	}

	private static File[] getFilesInFolder(Path input_folder, String suffix)
	{
		log.info("Loading files");
		final File[] text_files = input_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(suffix));
		if (text_files == null)
		{
			log.error("Failed to find in any text files in " + input_folder);
			System.exit(1);
		}

		return text_files;
	}

	private static String readTextFile(File file)
	{
		try
		{
			return FileUtils.readFileToString(file, Charsets.UTF_8);
		}
		catch (IOException e)
		{
			log.error("Cannot read file " + file + ": " + e);
			return "";
		}
	}

	private static Path createOutputPath(Path input_file, Path output_folder, String old_suffix, String new_suffix) throws IOException
	{
		if (!Files.exists(output_folder))
			Files.createDirectories(output_folder);

		final String basename = input_file.getFileName().toString();
		final String out_filename = basename.substring(0, basename.length() - old_suffix.length()) + new_suffix;

		return output_folder.resolve(out_filename);
	}

	private static void evaluate(Path gold_folder, Path system_folder)
	{
//		log.info("Loading files");
//		final File[] text_files = gold_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(text_suffix));
//		final File[] meanings_files = gold_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(meanings_suffix));
//		assert text_files != null && meanings_files != null;
//		if (text_files.length != meanings_files.length)
//		{
//			log.error("Mismatch between number of text and meanings files");
//			System.exit(1);
//		}
//
//		List<String> gold = new ArrayList<>();
//		for (File text_file : text_files)
//		{
//			gold.add(FileUtils.readFileToString(text_file, Charsets.UTF_8));
//		}
//
//		log.info("Creating pipeline");
//		final AnalysisEngine ranking_pipeline = createRankingPipeline(babel_config, freqs_file, vectors);
//
//		log.info("Analyzing texts and collecting system meanings");
//		final List<JCas> documents = Arrays.stream(text_files)
//				.sorted(Comparator.comparing(File::getName))
//				.limit(1)
//				.map(f -> createAnalyzedDocument(f, ranking_pipeline))
//				.collect(Collectors.toList());
//
//		final List<List<Pair<String, String>>> system_meanings = documents.stream()
//				.map(Driver::readSystemMeanings)
//				.flatMap(List::stream)
//				.collect(Collectors.toList());
//
//		log.info("running evaluation");
//		Evaluation.evaluate(system_meanings, gold);
//
//		log.info("Collecting candidate meanings");
//		final ResourceManager resourceManager = ranking_pipeline.getResourceManager();
//		BabelnetSenseInventoryResource babelnet = (BabelnetSenseInventoryResource)resourceManager.getResource(BabelnetSenseInventoryResource.class.getName());
//		final List<Pair<String, String>> candidates = documents.stream()
//				.map(d -> getCandidateMeanings(d, babelnet))
//				.flatMap(List::stream)
//				.flatMap(List::stream)
//				.sorted(Comparator.comparing(Pair::getRight))
//				.collect(Collectors.toList());
//
//		final List<Pair<String, String>> system_meanings_list = system_meanings.stream()
//				.flatMap(List::stream)
//				.sorted(Comparator.comparing(Pair::getRight))
//				.collect(Collectors.toList());
//
//		log.info("Visualizing similarity matrices");
//		List<Pair<String, String>> candidates_with_system = new ArrayList<>(candidates);
//		candidates_with_system.removeAll(system_meanings_list);
//		candidates_with_system.addAll(system_meanings_list);
//
//		final List<String> cws_meanings = candidates_with_system.stream()
//				.map(Pair::getLeft)
//				.collect(Collectors.toList());
//		final List<String> cws_labels = candidates_with_system.stream()
//				.map(Pair::getRight)
//				.collect(Collectors.toList());
//		RandomAccessVectorsSimilarity sim = RandomAccessVectorsSimilarity.create(vectors);
//		VisualizationUtils.visualizeSimilarityMatrix("Candidate and system meanings", cws_meanings, cws_labels, sim);
//
//		List<Pair<String, String>> candidates_with_gold = new ArrayList<>(candidates);
//		candidates_with_system.removeAll(system_meanings_list);
//		candidates_with_system.addAll(system_meanings_list);
//
//		final List<String> cwg_meanings = candidates_with_gold.stream()
//				.map(Pair::getLeft)
//				.collect(Collectors.toList());
//		final List<String> cwg_labels = candidates_with_gold.stream()
//				.map(Pair::getRight)
//				.collect(Collectors.toList());
//		VisualizationUtils.visualizeSimilarityMatrix("Candidate and gold meanings", cwg_meanings, cwg_labels, sim);
//
//		Path output = createOutputPath(graph_file, global_ranked_suffix, false);
//		Serializer.serialize(graph, output);
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class GetCandidatesCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to input folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path input;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where candidate files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path babelnet;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class GetSystemMeaningsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to input folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path input;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path babelConfigPath;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class RunEvaluationCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to input folder containing text files with gold meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
		@Parameter(names = {"-s", "-system"}, description = "Path to folder containing binary files with system meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path system;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class VisualizeVectorsCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to input folder containing text files with gold meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
		@Parameter(names = {"-s", "-system"}, description = "Path to folder containing binary files with system meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path system;
		@Parameter(names = {"-c", "-candidates"}, description = "Path to folder containing binary files with candidate meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path candidates;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
	}

	public static void main(String[] args) throws IOException
	{
//		String corpus_folder = "/home/gerard/ownCloud/varis_tesi/deep_mind_annotated/development_set";
//		String babelnet = "/home/gerard/data/babelconfig";
//		String freqs = "/home/gerard/data/freqs.bin";
//		String vectors = "/home/gerard/data/sew-embed.nasari_bin";

		GetCandidatesCommand candidates = new GetCandidatesCommand();
		GetSystemMeaningsCommand system = new GetSystemMeaningsCommand();
		RunEvaluationCommand evaluate = new RunEvaluationCommand();
		VisualizeVectorsCommand visualize = new VisualizeVectorsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(get_candidates_command, candidates);
		jc.addCommand(get_system_command, system);
		jc.addCommand(evaluate_command, evaluate);
		jc.addCommand(visualize_command, visualize);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug("*********************************************************");

		switch (jc.getParsedCommand())
		{
			case get_candidates_command:
				getCandidates(candidates.input, candidates.output, candidates.babelnet);
				break;
			case get_system_command:
				getSystemMeanings(system.input, system.output, system.babelConfigPath, system.freqsFile, system.vectorsPath);
				break;
			case evaluate_command:
				evaluate(evaluate.gold, evaluate.system);
				break;
			case visualize_command:
				visualize(visualize.gold, visualize.system, visualize.system, visualize.freqsFile, visualize.vectorsPath);
				break;
			default:
				jc.usage();
				break;
		}

		log.debug("\n\n");
	}
}
