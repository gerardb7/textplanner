package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.similarity.BinaryVectorsSimilarity;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.utils.CMLCheckers;
import edu.upf.taln.textplanning.utils.Serializer;
import edu.upf.taln.textplanning.weighting.TFIDF;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;

public class Driver
{
	private final static Logger log = LogManager.getLogger(Driver.class);

	private void create_graphs(Path amr, Path types, Path bn_config_folder, Path output) throws IOException
	{
		String amr_bank = FileUtils.readFileToString(amr.toFile(), StandardCharsets.UTF_8);
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder);
		GraphList graphs = factory.getGraphs(amr_bank);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void plan(Path amr, Path freqs, Path vectors, Format format, Path types) throws Exception
	{
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(amr);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);

		TFIDF weighting = new TFIDF(corpus, true);
		SimilarityFunction similarity = new BinaryVectorsSimilarity(vectors);
		TextPlanner planner = new TextPlanner(weighting, similarity);
		log.info("Set up of planner took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		planner.plan(graphs, 10, options);
	}


	public static class FormatConverter implements IStringConverter<Format>
	{
		@Override
		public Format convert(String value)
		{
			return Format.valueOf(value);
		}
	}

	public static class FormatValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try{ Format.valueOf(value); }
			catch (Exception e)
			{
				throw new ParameterException("Parameter " + name + " has invalid valued " + value);
			}
		}
	}

	@Parameters(commandDescription = "Create graphs from an AMR bank")
	private static class CreateGraphsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text-based amr file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-t", "-types"}, description = "DBPedia types file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path typesFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
		@Parameter(names = {"-o", "-output"}, description = "Output binary graphs file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Plan from a set of semantic graphs")
	private static class PlanCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors file format", arity = 1, required = true, converter = FormatConverter.class,
				validateWith = FormatValidator.class)
		private Format format = Format.Glove;
		@Parameter(names = {"-t", "-types"}, description = "DBPedia types file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path typesFile;
		@Parameter(names = {"-o", "-output"}, description = "Output binary file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	public static void main(String[] args) throws Exception
	{
		Driver.CreateGraphsCommand create = new Driver.CreateGraphsCommand();
		Driver.PlanCommand plan = new Driver.PlanCommand();

		JCommander jc = new JCommander();
		jc.addCommand("create", create);
		jc.addCommand("plan", plan);
		jc.parse(args);

		Driver driver = new Driver();
		if (jc.getParsedCommand().equals("create"))
			driver.create_graphs(create.inputFile, create.typesFile, create.bnFolder, create.outputFile);
		else if (jc.getParsedCommand().equals("plan"))
			driver.plan(plan.inputFile, plan.freqsFile , plan.vectorsPath, plan.format, plan.typesFile);
	}
}


