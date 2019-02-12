package edu.upf.taln.textplanning.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import it.uniroma1.lcl.jlt.util.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.stream.Collectors.toList;

public class Driver
{
	private static final String gold_suffix = ".gold";
	private static final String meanings_suffix = ".candidates";
	private static final String evaluate_command = "evaluate";
	private static final String semeval_command = "semeval";
	private static final String collect_meanings_vectors = "meanings";
	private static final String create_context_vectors = "context";
	private final static Logger log = LogManager.getLogger();


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
		@Parameter(names = {"-svt", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1, required = true,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectors.VectorType sentence_vector_type = SentenceVectors.VectorType.SIF;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private Vectors.VectorType word_vector_type = Vectors.VectorType.Binary_RandomAccess;
		@Parameter(names = {"-cv", "-context_vectors"}, description = "Path to sense context vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path context_vectors_path;
		@Parameter(names = {"-ct", "-context_vectors_type"}, description = "Type of sense context vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private Vectors.VectorType context_vector_type = Vectors.VectorType.Binary_RandomAccess;
		@Parameter(names = {"-sv", "-sense_vectors"}, description = "Path to sense vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-st", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private Vectors.VectorType sense_vector_type = Vectors.VectorType.Binary_RandomAccess;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Collect meanings info from BabelNet and stores them into a binary file")
	private static class CollectMeaningsCommand
	{
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path babelnet;
		@Parameter(names = {"-o", "-output"}, description = "Path to output file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path output;
		@Parameter(names = {"-go", "-glosses_only"}, description = "If true, only glosses are collected. Lemmas are als collected otherwise", arity = 1)
		private boolean glosses_only = false;
		@Parameter(names = {"-mx", "-max_meanings"}, description = "Maximum number of meanings to collect", arity = 1,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int max_meanings = Integer.MAX_VALUE;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Create context vectors for a set of meanings")
	private static class CreateContextVectorsCommand
	{
		@Parameter(names = {"-m", "-meanings"}, description = "Path to binary file containing meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path meanings;
		@Parameter(names = {"-o", "-output"}, description = "Path to output context vectors file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToNewFile.class)
		private Path output;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-go", "-glosses_only"}, description = "If true, only glosses are used to generate context vectors", arity = 1, required = true)
		private boolean glosses_only = true;
		@Parameter(names = {"-svt", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,  required = true,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectors.VectorType sentence_vector_type = SentenceVectors.VectorType.SIF;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private Vectors.VectorType word_vector_type = Vectors.VectorType.Binary_RandomAccess;
		@Parameter(names = {"-c", "-chunk_size"}, description = "Chunk size used when computing vectors", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int chunk_size = 0;
	}

	private static void evaluate(Path system_folder, Path gold_folder)
	{
		log.info("Loading files");
		final List<Path> system_files = Arrays.stream(Objects.requireNonNull(FileUtils.getFilesInFolder(system_folder, meanings_suffix)))
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toList());
		final List<Path> gold_files = Arrays.stream(Objects.requireNonNull(FileUtils.getFilesInFolder(gold_folder, gold_suffix)))
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

	public static void main(String[] args) throws Exception
	{
		RunEvaluationCommand evaluate = new RunEvaluationCommand();
		SemEvalEvaluationCommand semEval = new SemEvalEvaluationCommand();
		CollectMeaningsCommand meanings = new CollectMeaningsCommand();
		CreateContextVectorsCommand context = new CreateContextVectorsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(evaluate_command, evaluate);
		jc.addCommand(semeval_command, semEval);
		jc.addCommand(collect_meanings_vectors, meanings);
		jc.addCommand(create_context_vectors, context);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running \n\t" + String.join("\n\t", args));
		log.debug("*********************************************************");

		switch (jc.getParsedCommand())
		{
			case evaluate_command:
				evaluate(evaluate.system, evaluate.gold);
				break;
			case semeval_command:
			{
				ResourcesFactory resources = new ResourcesFactory(semEval.freqsFile, semEval.sentence_vector_type,
						semEval.word_vectors_path,  semEval.word_vector_type,
						semEval.context_vectors_path,  semEval.context_vector_type,
						semEval.sense_vectors_path,  semEval.sense_vector_type);
				SemEvalEvaluation.evaluate(semEval.gold_file, semEval.input_file, semEval.babelnet, semEval.output,
						resources);
				break;
			}
			case collect_meanings_vectors:
			{
				BabelNetMeaningsCollector.collectMeanings(meanings.babelnet, meanings.output, meanings.glosses_only, meanings.max_meanings);
				break;
			}
			case create_context_vectors:
			{
				ResourcesFactory resources = new ResourcesFactory(context.freqsFile, context.sentence_vector_type,
						context.word_vectors_path,  context.word_vector_type,
						null,  null,
						null,  null);
				ContextVectorsProducer.createVectors(context.meanings, context.chunk_size, context.output, resources, context.glosses_only);
				break;
			}
			default:
				jc.usage();
				break;
		}

		log.debug("\n\n");
	}

}
