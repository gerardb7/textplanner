package edu.upf.taln.textplanning.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.PlanningProperties;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
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
	private static final String collect_meanings_vectors = "meanings";
	private static final String create_context_vectors = "context";
	private final static Logger log = LogManager.getLogger();

	private static abstract class BaseCommand
	{
		@Parameter(names = {"-p", "-properties"}, description = "Path to properties file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		protected Path properties;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run SemEval WSD/EL evaluation")
	private static class SemEvalEvaluationCommand extends BaseCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path gold_file;
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where system files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-b", "-batch"}, description = "If true, a batch test is ran", arity = 1)
		private boolean batch = false;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run disambiguation evaluation")
	private static class DisambiguationEvaluationCommand extends BaseCommand
	{
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path gold_file;
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-b", "-batch"}, description = "If true, a batch test is ran", arity = 1)
		private boolean batch = false;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run evaluation of meanings ranking")
	private static class RankEvaluationCommand extends BaseCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to XML input file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path input_file;
		@Parameter(names = {"-g", "-gold"}, description = "Path to gold file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path gold_file;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run evaluation of ranking-based extractive summarization")
	private static class ExtractiveEvaluationCommand extends BaseCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to input. Can be a folder containing text files or a single XML file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path input;
		@Parameter(names = {"-g", "-gold"}, description = "Path to folder containing gold summaries", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path gold;
		@Parameter(names = {"-o", "-output"}, description = "Path to folder where system summaries are created", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-t", "-tmp"}, description = "Path to folder where temporary files are stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path tmp;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Collect meanings info from a dictionary and stores them into a binary file")
	private static class CollectMeaningsCommand extends BaseCommand
	{
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Create context vectors for a set of meanings")
	private static class CreateContextVectorsCommand extends BaseCommand
	{
		@Parameter(names = {"-m", "-meanings"}, description = "Path to binary file containing meanings", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path meanings;
		@Parameter(names = {"-o", "-output"}, description = "Path to output context vectors file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToNewFile.class)
		private Path output;
		@Parameter(names = {"-go", "-glosses_only"}, description = "If true, only glosses are used to generate context vectors", arity = 1, required = true)
		private boolean glosses_only = true;
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
		CollectMeaningsCommand meanings = new CollectMeaningsCommand();
		CreateContextVectorsCommand context = new CreateContextVectorsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(semeval_command, semEval);
		jc.addCommand(disambiguation_eval_command, wsdEval);
		jc.addCommand(rank_eval_command, rankEval);
		jc.addCommand(extract_eval_command, extractEval);
		jc.addCommand(collect_meanings_vectors, meanings);
		jc.addCommand(create_context_vectors, context);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running \n\t" + String.join("\n\t", args));
		log.info("\n*********************************************************");

		switch (jc.getParsedCommand())
		{
			case semeval_command:
			{
				PlanningProperties properties = new PlanningProperties(semEval.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
				SemEvalEvaluation eval = new SemEvalEvaluation(semEval.gold_file, semEval.input_file, semEval.output, resources);
				if (semEval.batch)
					eval.run_batch();
				else
					eval.run();
				break;
			}
			case disambiguation_eval_command:
			{
				PlanningProperties properties = new PlanningProperties(wsdEval.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
				GoldDisambiguationEvaluation eval = new GoldDisambiguationEvaluation(wsdEval.gold_file, wsdEval.input_file, resources);
				if (wsdEval.batch)
					eval.run_batch();
				else
					eval.run();
				break;
			}
			case rank_eval_command:
			{
				PlanningProperties properties = new PlanningProperties(rankEval.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
				RankingEvaluation.run(rankEval.gold_file, rankEval.input_file, resources);
				break;
			}
			case extract_eval_command:
			{
				PlanningProperties properties = new PlanningProperties(extractEval.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
				ExtractiveEvaluation.run(extractEval.input, extractEval.gold, extractEval.output, extractEval.tmp, resources);
				resources.serializeCache();
				break;
			}
			case collect_meanings_vectors:
			{
				PlanningProperties properties = new PlanningProperties(meanings.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
				CandidatesCollector.collect(resources.getDictionary(), language, resources.getCache(), properties.getDictionaryCache());
				resources.serializeCache();
				break;
			}
			case create_context_vectors:
			{
				PlanningProperties properties = new PlanningProperties(context.properties);
				InitialResourcesFactory resources = new InitialResourcesFactory(language, properties);
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
