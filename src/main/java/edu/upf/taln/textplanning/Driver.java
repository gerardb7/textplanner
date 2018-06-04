package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.similarity.BinaryVectorsSimilarity;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.CMLCheckers;
import edu.upf.taln.textplanning.utils.Serializer;
import edu.upf.taln.textplanning.weighting.NoWeights;
import edu.upf.taln.textplanning.weighting.NumberForms;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;

@SuppressWarnings("ALL")
public class Driver
{
	private final static Logger log = LogManager.getLogger(Driver.class);

	private void create_graphs(Path amr, Path bn_config_folder, Path output) throws IOException
	{
		log.info("Running from " + amr);
		String amr_bank = FileUtils.readFileToString(amr.toFile(), StandardCharsets.UTF_8);
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder);
		GraphList graphs = factory.getGraphs(amr_bank);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void rank_meanings(Path graphs_file, Path freqs, Path vectors, Format format, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		WeightingFunction weighting = new NoWeights();
		SimilarityFunction similarity = new BinaryVectorsSimilarity(vectors);
		log.info("Loading resources took " + timer.stop());

		final Multimap<Meaning, Mention> map = HashMultimap.create();
		graphs.getCandidates().forEach(m -> map.put(m.getMeaning(), m.getMention()));
		TextPlanner.Options options = new TextPlanner.Options();
		TextPlanner.rankMeanings(map, weighting, similarity, options);
		Serializer.serialize(graphs, output);
		log.info("Ranked graphs serialized to " + output);
	}

	private void create_global(Path graphs_file, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);
		log.info("Loading resources took " + timer.stop());

		GlobalSemanticGraph graph = TextPlanner.createGlobalGraph(graphs);
		Serializer.serialize(graph, output);
		log.info("Global semantic graph serialized to " + output);
	}

	private void rank_variables(Path graph_file, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graph_file);
		Stopwatch timer = Stopwatch.createStarted();
		GlobalSemanticGraph graph = (GlobalSemanticGraph) Serializer.deserialize(graph_file);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		TextPlanner.rankVariables(graph, options);
		Serializer.serialize(graph, output);
		log.info("Ranked global semantic graph serialized to " + output);
	}

	private void extract_subgraphs(Path graph_file, int num_subgraphs, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graph_file);
		Stopwatch timer = Stopwatch.createStarted();
		GlobalSemanticGraph graph = (GlobalSemanticGraph) Serializer.deserialize(graph_file);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		final Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, num_subgraphs, options);
		Serializer.serialize(subgraphs, output);
		log.info("Subgraphs serialized to " + output);
	}

	private void remove_redundancy(Path subgraphs_file, int num_subgraphs, Path vectors, Format format, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + subgraphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = new BinaryVectorsSimilarity(vectors);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, num_subgraphs, similarity, options);
		Serializer.serialize(subgraphs, output);
		log.info("Non-redundant subgraphs serialized to " + output);
	}

	private void sort_subgraphs(Path subgraphs_file, Path vectors, Format format, Path output) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + subgraphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = new BinaryVectorsSimilarity(vectors);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		final List<SemanticSubgraph> plan = TextPlanner.sortSubgraphs(subgraphs, similarity, options);
		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
	}

	private void plan(Path graphs_file, Path freqs, Path vectors, Format format, int num_subgraphs, Path output) throws Exception
	{
		log.info("Running from " + graphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);
		SimilarityFunction similarity = new BinaryVectorsSimilarity(vectors);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		TFIDF weighting = new TFIDF(corpus, (n) -> n.endsWith("n"));
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		List<SemanticSubgraph> plan = TextPlanner.plan(graphs, similarity, weighting, num_subgraphs, options);

		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
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
		@Parameter(names = {"-i", "-input"}, description = "Input text-based amr file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
		@Parameter(names = {"-o", "-output"}, description = "Output binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Rank meanings in a collection of semantic graphs")
	private static class RankMeaningsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Glove;
		@Parameter(names = {"-o", "-output"}, description = "Output binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Create global semantic graph from a list of semantic graphs")
	private static class CreateGlobalCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-o", "-output"}, description = "Output binary graph file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Rank vertices in a global semantic graph")
	private static class RankVariablesCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary global graph file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-o", "-output"}, description = "Output binary graph file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Extract subgraphs from a global semantic graph")
	private static class ExtractCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary global graph file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-n", "-number"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int num_subgraphs;
		@Parameter(names = {"-o", "-output"}, description = "Output binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Remove redundant subgraphs")
	private static class RemoveRedundancyCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-n", "-number"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int num_subgraphs;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Glove;
		@Parameter(names = {"-o", "-output"}, description = "Output binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Sort subgraphs")
	private static class SortCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Glove;
		@Parameter(names = {"-o", "-output"}, description = "Output binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Plan from a collection of semantic graphs")
	private static class PlanCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Glove;
		@Parameter(names = {"-n", "-number"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int num_subgraphs;
		@Parameter(names = {"-o", "-output"}, description = "Output plan file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	public static void main(String[] args) throws Exception
	{
		CreateGraphsCommand create_graphs = new CreateGraphsCommand();
		RankMeaningsCommand rank_meanings = new RankMeaningsCommand();
		CreateGlobalCommand create_global = new CreateGlobalCommand();
		RankVariablesCommand rank_variables = new RankVariablesCommand();
		ExtractCommand extract_subgraphs = new ExtractCommand();
		RemoveRedundancyCommand remove_redundancy = new RemoveRedundancyCommand();
		SortCommand sort_subgraphs = new SortCommand();
		PlanCommand plan = new PlanCommand();

		JCommander jc = new JCommander();
		jc.addCommand("create_graphs", create_graphs);
		jc.addCommand("rank_meanings", rank_meanings);
		jc.addCommand("create_global", create_global);
		jc.addCommand("rank_variables", rank_variables);
		jc.addCommand("extract_subgraphs", extract_subgraphs);
		jc.addCommand("remove_redundancy", remove_redundancy);
		jc.addCommand("sort_subgraphs", sort_subgraphs);
		jc.addCommand("plan", plan);
		jc.parse(args);

		Driver driver = new Driver();
		if (jc.getParsedCommand().equals("create_graphs"))
			driver.create_graphs(create_graphs.inputFile, create_graphs.bnFolder,
					create_graphs.outputFile);
		else if (jc.getParsedCommand().equals("rank_meanings"))
			driver.rank_meanings(rank_meanings.inputFile, rank_meanings.freqsFile, rank_meanings.vectorsPath,
					rank_meanings.format, rank_meanings.outputFile);
		else if (jc.getParsedCommand().equals("create_global"))
			driver.create_global(create_global.inputFile, create_global.outputFile);
		else if (jc.getParsedCommand().equals("rank_variables"))
			driver.rank_variables(rank_variables.inputFile, rank_variables.outputFile);
		else if (jc.getParsedCommand().equals("extract_subgraphs"))
			driver.extract_subgraphs(extract_subgraphs.inputFile, extract_subgraphs.num_subgraphs,
					extract_subgraphs.outputFile);
		else if (jc.getParsedCommand().equals("remove_redundancy"))
			driver.remove_redundancy(remove_redundancy.inputFile, remove_redundancy.num_subgraphs,
					remove_redundancy.vectorsPath, remove_redundancy.format, remove_redundancy.outputFile);
		else if (jc.getParsedCommand().equals("sort_subgraphs"))
			driver.sort_subgraphs(sort_subgraphs.inputFile, sort_subgraphs.vectorsPath, sort_subgraphs.format,
					sort_subgraphs.outputFile);
		else if (jc.getParsedCommand().equals("plan"))
			driver.plan(plan.inputFile, plan.freqsFile, plan.vectorsPath, plan.format, plan.num_subgraphs,
					plan.outputFile);
	}
}


