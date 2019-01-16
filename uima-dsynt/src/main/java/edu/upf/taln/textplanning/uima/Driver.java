package edu.upf.taln.textplanning.uima;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.*;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.similarity.VectorsCosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.SIFVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.weighting.Context;
import it.uniroma1.lcl.jlt.util.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.*;
import static java.util.stream.Collectors.*;

public class Driver
{
	private static final String text_suffix = ".story";
	private static final String gold_suffix = ".gold";
	private static final String candidates_suffix = ".candidates";
	private static final String get_candidates_command = "candidates";
	private static final String get_system_UIMA_command = "system_uima";
	private static final String get_system_command = "system";
	private static final String get_gold_candidates_command = "gold_candidates";
	private static final String evaluate_command = "evaluate";
//	private static final String visualize_command = "visualize";
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	private static void getCandidates(Path input_folder, Path output_folder, Path babelnet_config)
	{
		log.info("Loading files");
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);

		MeaningDictionary bn = new BabelNetDictionary(babelnet_config);

		log.info("Processing texts with UIMA and looking up candidate meanings");
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);

		Arrays.stream(text_files)
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.forEach(f ->
				{
					final String text = readTextFile(f);
					UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
					final List<List<Set<Candidate>>> candidates = uima.getCandidates(bn);
					serializeCandidates(candidates, f, output_folder);
				});
	}

	private static void getSystemMeaningsUIMA(Path input_folder, Path output_folder, Path babel_config, Path freqs_file, Path vectors)
	{
		log.info("Loading files");
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);

		log.info("Processing texts with UIMA and collecting system meanings");
		final UIMAWrapper.Pipeline pipeline =
				UIMAWrapper.createRankingPipeline(language, false, babel_config, freqs_file, vectors);

		Arrays.stream(text_files)
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.forEach(f ->
				{
					final String text = readTextFile(f);
					UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
					final List<List<Set<Candidate>>> candidates = uima.getDisambiguatedCandidates();
					serializeCandidates(candidates, f, output_folder);
				});
	}

	private static void getSystemMeanings(Path text_folder, Path candidate_folder, Path output_folder, Path idf_file,
	                                      Path word_vectors_path, VectorType word_vectors_type,
	                                      Path sense_context_vectors_path, VectorType sense_context_vectors_type,
	                                      Path sense_vectors_path, VectorType sense_vectors_type) throws Exception
	{
		log.info("Loading files");
		final List<Path> text_files = Arrays.stream(getFilesInFolder(text_folder, text_suffix))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());
		final List<Path> candidate_files = Arrays.stream(getFilesInFolder(candidate_folder, candidates_suffix))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());

		// Check all files are paired
		assert (text_files.size() == candidate_files.size());
		for (int i = 0; i < text_files.size(); ++i)
		{
			final String name1 = Files.removeExtension(text_files.get(i).getFileName().toString());
			final String name2 = Files.removeExtension(candidate_files.get(i).getFileName().toString());
			assert name1.equals(name2);
		}

		log.info("Setting up UIMA pipeline");
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);

		log.info("Loading resources");
		final Vectors word_vectors = Vectors.get(word_vectors_path, word_vectors_type, 300);
		final Map<String, Double> weights = ContextVectorsProducer.getWeights(idf_file);
		final Double default_weight = Collections.min(weights.values());
		final SIFVectors sif_vectors = new SIFVectors(word_vectors, w -> weights.getOrDefault(w, default_weight));
		final Vectors sense_context_vectors = Vectors.get(sense_context_vectors_path, sense_context_vectors_type, 300);
		final Vectors sense_vectors = Vectors.get(sense_vectors_path, sense_vectors_type, 300);
		final VectorsCosineSimilarity sim = new VectorsCosineSimilarity(sense_vectors);

		log.info("Processing text files");
		IntStream.range(0, text_files.size())
				.forEach(i ->
				{
					final String text = readTextFile(text_files.get(i));
					final List<Candidate> candidates = deserializeCandidates(candidate_files.get(i)).stream()
							.flatMap(l -> l.stream()
									.flatMap(Set::stream))
							.collect(toList());

					UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
					final List<String> context = uima.getTokens().stream()
							.flatMap(List::stream)
							.collect(toList());

					final Context context_weighter = new Context(candidates, sense_context_vectors, sif_vectors, w -> context);
					TextPlanner.rankMeanings(candidates, context_weighter::weight, sim::of, new TextPlanner.Options());

					// Let's group and sort the plain list of candidates by sentence and offsets.
					final List<List<Set<Candidate>>> grouped_candidates = candidates.stream()
							.collect(groupingBy(c -> c.getMention().getSentenceId(), groupingBy(c -> c.getMention().getSpan(), toSet())))
							.entrySet().stream()
							.sorted(Comparator.comparing(Map.Entry::getKey))
							.map(Map.Entry::getValue)
							.map(e -> e.entrySet().stream()
									.sorted(Comparator.comparingInt(e2 -> e2.getKey().getLeft()))
									.map(Map.Entry::getValue)
									.collect(toList()))
							.collect(toList());

					serializeCandidates(grouped_candidates, text_files.get(i), output_folder);
				});
	}

	private static void getGoldCandidates(Path gold_folder, Path output_folder, Path babelnet_config)
	{
		log.info("Loading files");
		final File[] gold_files = getFilesInFolder(gold_folder, gold_suffix);

		MeaningDictionary bn = new BabelNetDictionary(babelnet_config);


		final Predicate<String> is_meta = Pattern.compile("^@").asPredicate();
		final List<String> summaries = Arrays.stream(gold_files)
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.map(FileUtils::readTextFile)
				.map(text ->
						Pattern.compile("\n+").splitAsStream(text)
								.filter(l -> !l.isEmpty() && !is_meta.test(l))
								.collect(joining(" .\n")))
				.collect(toList());

		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);

		IntStream.range(0, gold_files.length)
				.forEach(i -> {
					final UIMAWrapper uimaWrapper = new UIMAWrapper(summaries.get(i), language, pipeline);
					final List<List<Set<Candidate>>> candidates = uimaWrapper.getCandidates(bn);
					serializeCandidates(candidates, gold_files[i].toPath(), output_folder);
				});
	}

//	private static void visualize(Path gold_folder, Path gold_candidates_folder, Path system_folder, Path candidates_folder,
//	                              Path freqs_file, Path vectors_path)
//	{
//		log.info("Loading files");
//		final File[] gold_files = getFilesInFolder(gold_folder, gold_suffix);
//		final File[] gold_candidates_files = getFilesInFolder(gold_candidates_folder, candidates_suffix);
//		final File[] system_files = getFilesInFolder(system_folder, candidates_suffix);
//		final File[] candidates_files = getFilesInFolder(candidates_folder, candidates_suffix);
//
//		if (gold_files.length != system_files.length)
//		{
//			log.error("Mismatch between number of text and meanings files");
//			System.exit(1);
//		}
//
//		final List<List<Pair<String, String>>> all_gold_meanings = readGoldMeanings(gold_files).stream()
//				.map(l -> l.stream()
//						.flatMap(List::stream)
//						.flatMap(Set::stream)
//						.distinct()
//						.collect(toList()))
//				.collect(toList());
//
//		final List<List<Pair<String, String>>> all_gold_candidates = deserializeCandidates(gold_candidates_files).stream()
//				.map(l -> l.stream()
//						.flatMap(List::stream)
//						.flatMap(m -> m.keySet().stream())
//						.map(Candidate::getMeaning)
//						.map(m -> Pair.of(m.getReference(), m.toString()))
//						.distinct()
//						.collect(toList()))
//				.collect(toList());
//
//		final List<List<Pair<String, String>>> all_system_meanings = deserializeCandidates(system_files).stream()
//				.map(l -> l.stream()
//						.flatMap(List::stream)
//						.flatMap(m -> m.keySet().stream())
//						.map(Candidate::getMeaning)
//						.map(m -> Pair.of(m.getReference(), m.toString()))
//						.distinct()
//						.collect(toList()))
//				.collect(toList());
//
//		final List<List<Pair<String, String>>> all_candidate_meanings = deserializeCandidates(candidates_files).stream()
//				.map(l -> l.stream()
//						.flatMap(List::stream)
//						.flatMap(m -> m.keySet().stream())
//						.map(Candidate::getMeaning)
//						.map(m -> Pair.of(m.getReference(), m.toString()))
//						.distinct()
//						.collect(toList()))
//				.collect(toList());
//
//		log.info("Visualizing similarity matrices");
//		IntStream.range(0, gold_files.length).forEach(i -> {
//			final List<Pair<String, String>> gold_meanings = all_gold_meanings.get(i);
//			final List<Pair<String, String>> gold_candidates = all_gold_candidates.get(i);
////			final List<Pair<String, String>> system_meanings = all_system_meanings.get(i);
//			final List<Pair<String, String>> candidate_meanings = all_candidate_meanings.get(i);
//			log.info("Creating matrix for file "+ gold_files[i].getName() + " with size " + candidate_meanings.size() + gold_meanings.size());
//
////			List<Pair<String, String>> candidates_with_system = new ArrayList<>(candidate_meanings);
////			candidates_with_system.removeAll(system_meanings);
////			candidates_with_system.addAll(system_meanings);
////
////			final List<String> cws_meanings = candidates_with_system.stream()
////					.map(Pair::getLeft)
////					.collect(Collectors.toList());
////			final List<String> cws_labels = candidates_with_system.stream()
////					.map(Pair::getRight)
////					.collect(Collectors.toList());
//			VectorsCosineSimilarity sim;
//			try
//			{
//				RandomAccessVectors vectors = new RandomAccessVectors(vectors_path, 300);
//				sim = new VectorsCosineSimilarity(vectors);
//			}
//			catch (Exception e)
//			{
//				throw new RuntimeException(e);
//			}
////			VisualizationUtils.visualizeSimilarityMatrix("Candidate and system meanings", cws_meanings, cws_labels, sim);
//
//			List<Pair<String, String>> gold_and_candidates = new ArrayList<>(gold_meanings);
//			gold_candidates.stream()
//					.filter(p -> !gold_and_candidates.contains(p))
//					.map(p -> Pair.of(p.getLeft(), "cand-" + p.getRight()))
//					.forEach(gold_and_candidates::add);
//
//			final List<String> cwg_meanings = gold_and_candidates.stream()
//					.map(Pair::getLeft)
//					.collect(Collectors.toList());
//			final List<String> cwg_labels = gold_and_candidates.stream()
//					.map(Pair::getRight)
//					.collect(Collectors.toList());
//			VisualizationUtils.visualizeSimilarityMatrix("Gold meanings and candidates", cwg_meanings, cwg_labels, sim::of);
//		});
//	}

//	private static List<List<List<Set<Pair<String, String>>>>> readGoldMeanings(File[] gold_files)
//	{
//		return Arrays.stream(gold_files)
//				.map(File::toPath)
//				.map(FileUtils::readTextFile)
//				.map(text ->
//				{
//					final String regex = "(bn:\\d+[r|a|v|n](\\|bn:\\d+[r|a|v|n])*)-\"([^\"]+)\"";
//					final Pattern pattern = Pattern.compile(regex);
//					final Predicate<String> is_meanings = Pattern.compile("^@bn:.*").asPredicate();
//
//					return Pattern.compile("\n+").splitAsStream(text)
//							.filter(is_meanings)
//							.map(pattern::matcher)
//							.map(m ->
//							{
//								final List<Set<Pair<String, String>>> meanings = new ArrayList<>();
//								while (m.find())
//								{
//									final String meanings_string = m.group(1);
//									final String[] meanings_parts = meanings_string.split("\\|");
//									final Set<String> alternatives = Arrays.stream(meanings_parts)
//											.map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
//											.collect(toSet());
//									final String covered_text = m.group(3);
//									meanings.add(alternatives.stream()
//											.map(a -> Pair.of(a, covered_text))
//											.collect(toSet()));
//								}
//
//								return meanings;
//							})
//							.collect(toList());
//				})
//				.collect(toList());
//	}

	private static void serializeCandidates(List<List<Set<Candidate>>> candidates, Path text_file, Path output_folder)
	{
		log.info("Serializing candidates for  " + text_file);
		try
		{
			Path out_file = createOutputPath(text_file, output_folder, text_suffix, candidates_suffix);
			Serializer.serialize(candidates, out_file);
		}
		catch (Exception e)
		{
			log.error("Cannot store candidates for file " + text_file);
		}
	}

	private static List<List<Set<Candidate>>> deserializeCandidates(Path file)
	{
		try
		{
			return (List<List<Set<Candidate>>>)Serializer.deserialize(file);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot load candidates from file " + file + ": " + e);
		}
	}

	private static void evaluate(Path text_folder, Path gold_folder)
	{
//		log.info("Loading files");
//		final File[] text_files = gold_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(text_suffix));
//		final File[] meanings_files = gold_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(candidates_suffix));
//		assert text_files != null && meanings_files != null;
//		if (text_files.length != meanings_files.length)
//		{
//			log.error("Mismatch between number of text and meanings files");
//			System.exit(1);
//		}
//
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
//		VectorsCosineSimilarity context_similarity_function = VectorsCosineSimilarity.create(vectors);
//		VisualizationUtils.visualizeSimilarityMatrix("Candidate and system meanings", cws_meanings, cws_labels, context_similarity_function);
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
	@Parameters(commandDescription = "Get meanings chosen by the system (using UIMA modules only)")
	private static class GetSystemMeaningsUIMACommand
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
	@Parameters(commandDescription = "Get meanings chosen by the system (using text planning classes directly)")
	private static class GetSystemMeaningsCommand
	{
		@Parameter(names = {"-t", "-texts"}, description = "Path to folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path texts;
		@Parameter(names = {"-c", "-candidates"}, description = "Path to folder containing binary files with candidate meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path candidates;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.FormatConverter.class, validateWith = CMLCheckers.FormatValidator.class)
		private VectorType word_vector_type = VectorType.Binary_RandomAccess;
		@Parameter(names = {"-cv", "-context_vectors"}, description = "Path to sense context vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path context_vectors_path;
		@Parameter(names = {"-ct", "-context_vectors_type"}, description = "Type of sense context vectors", arity = 1,
				converter = CMLCheckers.FormatConverter.class, validateWith = CMLCheckers.FormatValidator.class)
		private VectorType context_vector_type = VectorType.Binary_RandomAccess;
		@Parameter(names = {"-sv", "-sense_vectors"}, description = "Path to sense vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-st", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1,
				converter = CMLCheckers.FormatConverter.class, validateWith = CMLCheckers.FormatValidator.class)
		private VectorType sense_vector_type = VectorType.Binary_RandomAccess;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class GetGoldCandidatesCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to folder containing gold annotated summaries", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where candidate files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path babelnet;
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

//	@SuppressWarnings("unused")
//	@Parameters(commandDescription = "Run empirical study from serialized file")
//	private static class VisualizeVectorsCommand
//	{
//		@Parameter(names = {"-g", "-gold"}, description = "Path to input folder containing text files with gold meanings", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
//		private Path gold;
//		@Parameter(names = {"-gc", "-gold_candidates"}, description = "Path to folder containing binary files with candidate meanings for gold summaries", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
//		private Path gold_candidates;
//		@Parameter(names = {"-s", "-system"}, description = "Path to folder containing binary files with system meanings", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
//		private Path system;
//		@Parameter(names = {"-c", "-candidates"}, description = "Path to folder containing binary files with candidate meanings", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
//		private Path candidates;
//		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
//		private Path freqsFile;
//		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
//				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
//		private Path vectorsPath;
//	}

	public static void main(String[] args) throws Exception
	{
//		String corpus_folder = "/home/gerard/ownCloud/varis_tesi/deep_mind_annotated/development_set";
//		String babelnet = "/home/gerard/data/babelconfig";
//		String freqs = "/home/gerard/data/freqs.bin";
//		String vectors = "/home/gerard/data/sew-embed.nasari_bin";

		GetCandidatesCommand candidates = new GetCandidatesCommand();
		GetSystemMeaningsUIMACommand system_uima = new GetSystemMeaningsUIMACommand();
		GetSystemMeaningsCommand system = new GetSystemMeaningsCommand();
		GetGoldCandidatesCommand gold_candidates = new GetGoldCandidatesCommand();
		RunEvaluationCommand evaluate = new RunEvaluationCommand();
//		VisualizeVectorsCommand visualize = new VisualizeVectorsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(get_candidates_command, candidates);
		jc.addCommand(get_system_UIMA_command, system_uima);
		jc.addCommand(get_system_command, system);
		jc.addCommand(get_gold_candidates_command, gold_candidates);
		jc.addCommand(evaluate_command, evaluate);
//		jc.addCommand(visualize_command, visualize);
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
			case get_system_UIMA_command:
				getSystemMeaningsUIMA(system_uima.input, system_uima.output, system_uima.babelConfigPath, system_uima.freqsFile, system_uima.vectorsPath);
				break;
			case get_system_command:
				getSystemMeanings(system.texts, system.candidates, system.output, system.freqsFile,
						system.word_vectors_path, system.word_vector_type,
						system.context_vectors_path, system.context_vector_type,
						system.sense_vectors_path, system.sense_vector_type);
				break;
			case get_gold_candidates_command:
				getGoldCandidates(gold_candidates.gold, gold_candidates.output, gold_candidates.babelnet);
				break;
			case evaluate_command:
				evaluate(evaluate.gold, evaluate.system);
				break;
//			case visualize_command:
//				visualize(visualize.gold, visualize.gold_candidates, visualize.system, visualize.candidates, visualize.freqsFile, visualize.vectorsPath);
//				break;
			default:
				jc.usage();
				break;
		}

		log.debug("\n\n");
	}
}
