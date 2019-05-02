package edu.upf.taln.textplanning.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.tools.evaluation.ExtractiveEvaluation;
import edu.upf.taln.textplanning.tools.evaluation.GoldDisambiguationEvaluation;
import edu.upf.taln.textplanning.tools.evaluation.RankingEvaluation;
import edu.upf.taln.textplanning.tools.evaluation.SemEvalEvaluation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Driver
{
	private final static ULocale language = ULocale.ENGLISH;
	private static final String semeval_command = "semeval";
	public static final String disambiguation_eval_command = "wsdeval";
	private static final String rank_eval_command = "rankeval";
	private static final String extract_eval_command = "extracteval";
	private static final String process_files_command = "process";
	private static final String collect_meanings_vectors = "meanings";
	private static final String create_context_vectors = "context";
	private final static Logger log = LogManager.getLogger();

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
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-bias"}, description = "Path to text file containing meanings used to bias ranking", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path biasFile;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-sev", "-sense_vectors"}, description = "Path to sense vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-set", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType sense_vector_type = VectorType.Random;
		@Parameter(names = {"-sv", "-sentence_vectors"}, description = "Path to sentence vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sentence_vectors_path;
		@Parameter(names = {"-st", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-b", "-batch"}, description = "If true, a batch test is ran", arity = 1)
		private boolean batch = false;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run disambiguation evaluation")
	private static class DisambiguationEvaluationCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path gold_file;
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-bias"}, description = "Path to text file containing meanings used to bias ranking", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path biasFile;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-sv", "-sentence_vectors"}, description = "Path to sentence vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sentence_vectors_path;
		@Parameter(names = {"-st", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-sev", "-sense_vectors"}, description = "Path to sense vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-set", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType sense_vector_type = VectorType.Random;
		@Parameter(names = {"-b", "-batch"}, description = "If true, a batch test is ran", arity = 1)
		private boolean batch = false;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run evaluation of meanings ranking")
	private static class RankEvaluationCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold_folder;
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-bias"}, description = "Path to text file containing meanings used to bias ranking", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path biasFile;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-sv", "-sentence_vectors"}, description = "Path to sentence vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sentence_vectors_path;
		@Parameter(names = {"-st", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-sev", "-sense_vectors"}, description = "Path to sense vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-set", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType sense_vector_type = VectorType.Random;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run evaluation of ranking-based extractive summarization")
	private static class ExtractiveEvaluationCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to folder with xmi files containing source documents", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path input;
		@Parameter(names = {"-g", "-gold"}, description = "Path to folder with text files containing gold summaries", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-o", "-output"}, description = "Path to folder where text files will be created containing system summaries", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-bias"}, description = "Path to text file containing meanings used to bias ranking", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path biasFile;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-sv", "-sentence_vectors"}, description = "Path to sentence vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sentence_vectors_path;
		@Parameter(names = {"-st", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-sev", "-sense_vectors"}, description = "Path to sense vectors", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-set", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType sense_vector_type = VectorType.Random;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Process text files with UIMA pipeline")
	private static class ProcessFilesCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to folder with text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path input;
		@Parameter(names = {"-o", "-output"}, description = "Path to folder where xmi files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Collect meanings info from a dictionary and stores them into a binary file")
	private static class CollectMeaningsCommand
	{
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-o", "-output"}, description = "Path to output file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path output;
		@Parameter(names = {"-mx", "-max_meanings"}, description = "Maximum number of meanings to collect", arity = 1,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
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
		@Parameter(names = {"-go", "-glosses_only"}, description = "If true, only glosses are used to generate context vectors", arity = 1, required = true)
		private boolean glosses_only = true;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-svt", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,  required = true,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-c", "-chunk_size"}, description = "Chunk size used when computing vectors", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int chunk_size = 0;
	}

	public static void main(String[] args) throws Exception
	{
		SemEvalEvaluationCommand semEval = new SemEvalEvaluationCommand();
		DisambiguationEvaluationCommand wsdEval = new DisambiguationEvaluationCommand();
		RankEvaluationCommand rankEval = new RankEvaluationCommand();
		ExtractiveEvaluationCommand extractEval = new ExtractiveEvaluationCommand();
		ProcessFilesCommand process = new ProcessFilesCommand();
		CollectMeaningsCommand meanings = new CollectMeaningsCommand();
		CreateContextVectorsCommand context = new CreateContextVectorsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(semeval_command, semEval);
		jc.addCommand(disambiguation_eval_command, wsdEval);
		jc.addCommand(rank_eval_command, rankEval);
		jc.addCommand(extract_eval_command, extractEval);
		jc.addCommand(process_files_command, process);
		jc.addCommand(collect_meanings_vectors, meanings);
		jc.addCommand(create_context_vectors, context);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running \n\t" + String.join("\n\t", args));
		log.debug("*********************************************************");

		switch (jc.getParsedCommand())
		{
			case semeval_command:
			{
				InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
				bias_resources.bias_meanings_path = semEval.biasFile;
				bias_resources.word_vectors_path = semEval.word_vectors_path;
				bias_resources.word_vectors_type = semEval.word_vector_type;
				bias_resources.sentence_vectors_path = null;
				bias_resources.sentence_vectors_type = semEval.sentence_vector_type;
				bias_resources.idf_file = semEval.freqsFile;

				InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
				sim_resources.meaning_vectors_path = semEval.sense_vectors_path;
				sim_resources.meaning_vectors_type = semEval.sense_vector_type;

				InitialResourcesFactory resources = new InitialResourcesFactory(language, semEval.dictionary, bias_resources,
						sim_resources);

				Options options = new Options();
				SemEvalEvaluation eval = new SemEvalEvaluation(semEval.gold_file, semEval.input_file, semEval.output,
						resources, options);
				if (semEval.batch)
					eval.run_batch();
				else
					eval.run();
				break;
			}
			case disambiguation_eval_command:
			{
				InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
				bias_resources.bias_meanings_path = wsdEval.biasFile;
				bias_resources.word_vectors_path = wsdEval.word_vectors_path;
				bias_resources.word_vectors_type = wsdEval.word_vector_type;
				bias_resources.sentence_vectors_type = wsdEval.sentence_vector_type;
				bias_resources.idf_file = wsdEval.freqsFile;

				InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
				sim_resources.meaning_vectors_path = wsdEval.sense_vectors_path;
				sim_resources.meaning_vectors_type = wsdEval.sense_vector_type;

				InitialResourcesFactory resources = new InitialResourcesFactory(language, wsdEval.dictionary, bias_resources,
						sim_resources);

				Options options = new Options();
				GoldDisambiguationEvaluation eval = new GoldDisambiguationEvaluation(wsdEval.gold_file, wsdEval.input_file, wsdEval.output,
						resources, options);
				if (wsdEval.batch)
					eval.run_batch();
				else
					eval.run();
				break;
			}
			case rank_eval_command:
			{
				InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
				bias_resources.bias_meanings_path = rankEval.biasFile;
				bias_resources.word_vectors_path = rankEval.word_vectors_path;
				bias_resources.word_vectors_type = rankEval.word_vector_type;
				bias_resources.sentence_vectors_type = rankEval.sentence_vector_type;
				bias_resources.idf_file = rankEval.freqsFile;

				InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
				sim_resources.meaning_vectors_path = rankEval.sense_vectors_path;
				sim_resources.meaning_vectors_type = rankEval.sense_vector_type;

				InitialResourcesFactory resources = new InitialResourcesFactory(language, rankEval.dictionary, bias_resources,
						sim_resources);

				RankingEvaluation.run(rankEval.gold_folder, rankEval.input_file, rankEval.output, resources);
				break;
			}
			case extract_eval_command:
			{
				InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
				bias_resources.bias_meanings_path = extractEval.biasFile;
				bias_resources.word_vectors_path = extractEval.word_vectors_path;
				bias_resources.word_vectors_type = extractEval.word_vector_type;
				bias_resources.sentence_vectors_type = extractEval.sentence_vector_type;
				bias_resources.idf_file = extractEval.freqsFile;

				InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
				sim_resources.meaning_vectors_path = extractEval.sense_vectors_path;
				sim_resources.meaning_vectors_type = extractEval.sense_vector_type;

				InitialResourcesFactory resources = new InitialResourcesFactory(language, extractEval.dictionary, bias_resources,
						sim_resources);
				ExtractiveEvaluation.run(extractEval.input, extractEval.gold, extractEval.output, resources);
				break;
			}
			case process_files_command:
			{
				ExtractiveEvaluation.preprocess(process.input, process.output);
				break;
			}
			case collect_meanings_vectors:
			{
				InitialResourcesFactory resources = new InitialResourcesFactory(language, meanings.dictionary);
				MeaningsCollector.collectMeanings(meanings.output, resources, meanings.max_meanings);
				break;
			}
			case create_context_vectors:
			{
				InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
				bias_resources.word_vectors_path = extractEval.word_vectors_path;
				bias_resources.word_vectors_type = extractEval.word_vector_type;
				bias_resources.sentence_vectors_type = extractEval.sentence_vector_type;
				bias_resources.idf_file = extractEval.freqsFile;

				InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();

				InitialResourcesFactory resources = new InitialResourcesFactory(language, extractEval.dictionary, bias_resources,
						sim_resources);
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
