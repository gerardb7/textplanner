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
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.weighting.Context;
import it.uniroma1.lcl.jlt.util.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.*;
import static java.util.stream.Collectors.*;

public class Driver
{
	private static final String text_suffix = ".story";
	private static final String gold_suffix = ".gold";
	private static final String meanings_suffix = ".candidates";
	private static final String stats_suffix = ".stats";

	private static final String get_candidates_command = "candidates";
	private static final String get_system_UIMA_command = "system_uima";
	private static final String get_system_command = "system";
	private static final String get_gold_candidates_command = "gold_candidates";
	private static final String evaluate_command = "evaluate";
	private static final String semeval_command = "semeval";

	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	private static void getCandidates(Path input_folder, Path output_folder, Path babelnet_config)
	{
		log.info("Loading files");
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);
		if (text_files == null)
			log.error("No files found");
		else
		{

			MeaningDictionary bn = new BabelNetDictionary(babelnet_config);

			log.info("Processing texts with UIMA and looking up candidate meanings");
			final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);

			Arrays.stream(text_files)
					.sorted(Comparator.comparing(File::getName))
					.map(File::toPath)
					.forEach(f ->
					{
						log.info("Processing " + f);
						final String text = readTextFile(f);
						UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
						final List<List<Set<Candidate>>> candidates = uima.getCandidates(bn);

						Path out_file = createOutputPath(f, output_folder, text_suffix, meanings_suffix);
						log.info("Serializing meanings to  " + out_file);
						serializeMeanings(candidates, out_file);
					});
		}
	}

	private static void getSystemMeaningsUIMA(Path input_folder, Path output_folder, Path babel_config, Path freqs_file, Path vectors)
	{
		log.info("Loading files");
		final File[] text_files = getFilesInFolder(input_folder, text_suffix);
		if (text_files == null)
			log.error("No files found");
		else
		{
			log.info("Processing texts with UIMA and collecting system meanings");
			final UIMAWrapper.Pipeline pipeline =
					UIMAWrapper.createRankingPipeline(language, false, babel_config, freqs_file, vectors);

			Arrays.stream(text_files)
					.sorted(Comparator.comparing(File::getName))
					.map(File::toPath)
					.forEach(f ->
					{
						log.info("Processing " + f);
						final String text = readTextFile(f);
						UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
						final List<List<Set<Candidate>>> candidates = uima.getDisambiguatedCandidates();

						Path out_file = createOutputPath(f, output_folder, text_suffix, meanings_suffix);
						log.info("Serializing meanings to  " + out_file);
						serializeMeanings(candidates, out_file);
					});
		}
	}

	private static void getSystemMeanings(Path text_folder, Path candidate_folder, Path output_folder, Path idf_file,
	                                      Path word_vectors_path, VectorType word_vectors_type,
	                                      Path sense_context_vectors_path, VectorType sense_context_vectors_type,
	                                      Path sense_vectors_path, VectorType sense_vectors_type) throws Exception
	{
		log.info("Loading files");
		final List<Path> text_files = Arrays.stream(Objects.requireNonNull(getFilesInFolder(text_folder, text_suffix)))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());
		final List<Path> candidate_files = Arrays.stream(Objects.requireNonNull(getFilesInFolder(candidate_folder, meanings_suffix)))
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
//		final Vectors sense_vectors = Vectors.get(sense_vectors_path, sense_vectors_type, 300);
//		final VectorsCosineSimilarity sim = new VectorsCosineSimilarity(sense_vectors);

		log.info("Processing text files");
		IntStream.range(0, text_files.size())
				.forEach(i ->
				{
					final Path file = text_files.get(i);
					log.info("Processing " + file);
					final String text = readTextFile(file);
					final List<Candidate> candidates = deserializeMeanings(candidate_files.get(i)).stream()
							.flatMap(l -> l.stream()
									.flatMap(Set::stream))
							.collect(toList());

					UIMAWrapper uima = new UIMAWrapper(text, language, pipeline);
					final List<String> context = uima.getNominalTokens().stream()
							.flatMap(List::stream)
							.collect(toList());

					final Context context_weighter = new Context(candidates, sense_context_vectors, sif_vectors, w -> context);
//					TextPlanner.rankMeanings(candidates, context_weighter::weight, sim::of, new TextPlanner.Options());
					candidates.stream()
							.map(Candidate::getMeaning)
							.forEach(m -> m.setWeight(context_weighter.weight(m.getReference())));

//					final String stats = print_stats(candidates, context_weighter);
//					final Path stats_file = createOutputPath(file, output_folder, text_suffix, stats_suffix);
//					log.info("Writing stats to " + stats_file);
//					if (stats_file != null)
//						FileUtils.writeTextToFile(stats_file, stats);

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

					Path out_file = createOutputPath(file, output_folder, text_suffix, meanings_suffix);
					log.info("Serializing meanings to  " + out_file);
					serializeMeanings(grouped_candidates, out_file);
				});
	}

	public static String print_stats(List<Candidate> candidates, Context context_weighter)
	{
		final List<Meaning> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.distinct()
				.collect(toList());
		final Map<Meaning, Double> context_weights = meanings.stream()
				.collect(Collectors.toMap(m -> m, m -> context_weighter.weight(m.getReference())));

		Function<Mention, String> print_mention = m -> m.getSentenceId() + " " + m.getSpan() + " " + m.getSurface_form();

		return candidates.stream()
				.map(c -> {
					final Mention mention = c.getMention();
					final Meaning meaning = c.getMeaning();
					return print_mention.apply(mention) + "\t" +
							meaning + "\t" +
							context_weights.get(meaning) + "\t" +
							meaning.getWeight();
				})
				.collect(Collectors.joining("\n"));
	}

	private static void getGoldCandidates(Path gold_folder, Path output_folder, Path babelnet_config)
	{
		log.info("Loading files");
		final File[] gold_files = getFilesInFolder(gold_folder, gold_suffix);

		MeaningDictionary bn = new BabelNetDictionary(babelnet_config);


		final Predicate<String> is_meta = Pattern.compile("^@").asPredicate();
		assert gold_files != null;
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

					Path out_file = createOutputPath(gold_files[i].toPath(), output_folder, text_suffix, meanings_suffix);
					log.info("Serializing meanings to  " + out_file);
					FileUtils.serializeMeanings(candidates, out_file);
				});
	}

	private static void evaluate(Path system_folder, Path gold_folder)
	{
		log.info("Loading files");
		final List<Path> system_files = Arrays.stream(Objects.requireNonNull(getFilesInFolder(system_folder, meanings_suffix)))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());
		final List<Path> gold_files = Arrays.stream(Objects.requireNonNull(getFilesInFolder(gold_folder, gold_suffix)))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());

		// Check all files are paired
		assert (system_files.size() == gold_files.size());
		for (int i = 0; i < system_files.size(); ++i)
		{
			final String name1 = Files.removeExtension(system_files.get(i).getFileName().toString());
			final String name2 = Files.removeExtension(gold_files.get(i).getFileName().toString());
			assert name1.equals(name2);
		}

		log.info("running evaluation");
		Evaluation.evaluate(system_files, gold_files);
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Get candidates from text files using BabelNet")
	private static class GetCandidatesCommand
	{
		@Parameter(names = {"-t", "-texts"}, description = "ath to folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path texts;
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
		@Parameter(names = {"-t", "-texts"}, description = "Path to folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path texts;
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
		@Parameter(names = {"-s", "-system"}, description = "Path to folder containing binary files with system meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path system;
		@Parameter(names = {"-g", "-gold"}, description = "Path to input folder containing text files with gold meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run SemEval WSD/EL evaluation")
	private static class SemEvalEvaluationCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path gold_file;
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path babelnet;
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
		SemEvalEvaluationCommand semEval = new SemEvalEvaluationCommand();

		JCommander jc = new JCommander();
		jc.addCommand(get_candidates_command, candidates);
		jc.addCommand(get_system_UIMA_command, system_uima);
		jc.addCommand(get_system_command, system);
		jc.addCommand(get_gold_candidates_command, gold_candidates);
		jc.addCommand(evaluate_command, evaluate);
		jc.addCommand(semeval_command, semEval);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug("*********************************************************");

		switch (jc.getParsedCommand())
		{
			case get_candidates_command:
				getCandidates(candidates.texts, candidates.output, candidates.babelnet);
				break;
			case get_system_UIMA_command:
				getSystemMeaningsUIMA(system_uima.texts, system_uima.output, system_uima.babelConfigPath, system_uima.freqsFile, system_uima.vectorsPath);
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
				evaluate(evaluate.system, evaluate.gold);
				break;
			case semeval_command:
				SemEvalEvaluation.evaluate(semEval.gold_file, semEval.input_file, semEval.babelnet, semEval.output, semEval.freqsFile,
						semEval.word_vectors_path, semEval.word_vector_type,
						semEval.context_vectors_path, semEval.context_vector_type,
						semEval.sense_vectors_path, semEval.sense_vector_type);
				break;
			default:
				jc.usage();
				break;
		}

		log.debug("\n\n");
	}
}
