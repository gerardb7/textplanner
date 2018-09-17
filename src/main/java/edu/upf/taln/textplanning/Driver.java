package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.similarity.RandomAccessVectorsSimilarity;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.similarity.TextVectorsSimilarity;
import edu.upf.taln.textplanning.similarity.Word2VecVectorsSimilarity;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.CMLCheckers;
import edu.upf.taln.textplanning.utils.Serializer;
import edu.upf.taln.textplanning.weighting.NoWeights;
import edu.upf.taln.textplanning.weighting.NumberForms;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;
import static java.util.stream.Collectors.joining;

@SuppressWarnings("ALL")
public class Driver
{
	private final static String semantic_graphs_suffix = "_graphs.bin";
	private final static String semantic_graphs_ranked_suffix = "_graphs_ranked.bin";
	private final static String global_graph_suffix = "_global.bin";
	private final static String global_graph_ranked_suffix = "_global_ranked.bin";
	private final static String subgraphs_suffix = "_subgraphs.bin";
	private final static String non_redundant_suffix = "_non_redundant.bin";
	private final static String sorted_suffix = "_sorted.bin";
	private final static String plan_suffix = "_plan.bin";

	private final static Logger log = LogManager.getLogger();

	private void create_graphs(Path amr_bank_file, Path bn_config_folder, Path output, boolean no_stanford, boolean no_babelnet)
			throws IOException
	{
		log.info("Running from " + amr_bank_file);
		String amr_bank = FileUtils.readFileToString(amr_bank_file.toFile(), StandardCharsets.UTF_8);
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder, no_stanford, no_babelnet);
		GraphList graphs = factory.getGraphs(amr_bank);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void rank_meanings(Path graphs_file, Path freqs, Path vectors, Format format, Path output) throws Exception
	{
		log.info("Running from " + graphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		//Path randoma_access_vectors = Paths.get("/home/gerard/data/NASARIembed+UMBC_w2v_bin");
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/sensembed-vectors-merged_bin");
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/sew-embed.nasari_bin");
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/sew-embed.w2v_bin");

		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		WeightingFunction weighting = new NumberForms(r -> true);
		//WeightingFunction weighting = new NoWeights();
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		options.damping_meanings = 0.5;
		options.sim_threshold = 0.5;
		TextPlanner.rankMeanings(graphs.getCandidates(), weighting, similarity, options);
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

	private void remove_redundancy(Path subgraphs_file, int num_subgraphs, Path vectors, Format format, Path output) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, num_subgraphs, similarity, options);
		Serializer.serialize(subgraphs, output);
		log.info("Non-redundant subgraphs serialized to " + output);
	}

	private void sort_subgraphs(Path subgraphs_file, Path vectors, Format format, Path output) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		final List<SemanticSubgraph> plan = TextPlanner.sortSubgraphs(subgraphs, similarity, options);
		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
	}

	private void amr2global(Path amr_bank_file, Path bn_config_folder, Path freqs, Path vectors, Format format,
	                        boolean no_stanford, boolean no_babelnet) throws Exception
	{
		log.info("Running from " + amr_bank_file);
		Stopwatch timer = Stopwatch.createStarted();

		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder, no_stanford, no_babelnet);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		WeightingFunction weighting = new NoWeights();
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);

		log.info("Loading resources took " + timer.reset());
		log.info("***********************");

		timer.start();
		String amr_bank = FileUtils.readFileToString(amr_bank_file.toFile(), StandardCharsets.UTF_8);
		GraphList graphs = factory.getGraphs(amr_bank);

		Path output_path = createOutputPath(amr_bank_file, semantic_graphs_suffix);
		Serializer.serialize(graphs, output_path);
		log.info("Graphs serialized to " + output_path);

		TextPlanner.Options options = new TextPlanner.Options();
		log.info("Options:\n" + options);
		TextPlanner.rankMeanings(graphs.getCandidates(), weighting, similarity, options);

		output_path = createOutputPath(amr_bank_file, semantic_graphs_ranked_suffix);
		Serializer.serialize(graphs, output_path);
		log.info("Ranked graphs serialized to " + output_path);

		GlobalSemanticGraph graph = TextPlanner.createGlobalGraph(graphs);

		output_path = createOutputPath(amr_bank_file, global_graph_suffix);
		Serializer.serialize(graph, output_path);
		log.info("Global semantic graph serialized to " + output_path);
		log.info("Planning took " + timer.stop());
	}

	private void amr2plan(Path amr_bank_file, Path bn_config_folder, Path freqs, Path vectors, Format format,
	                        boolean no_stanford, boolean no_babelnet, int num_subgraphs_extract, int num_subgraphs) throws Exception
	{
		log.info("Running from " + amr_bank_file);
		Stopwatch timer = Stopwatch.createStarted();

		// 0- Set up
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder, no_stanford, no_babelnet);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		WeightingFunction weighting = new NoWeights();
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);

		log.info("Loading resources took " + timer.stop());
		log.info("***********************");

		// 1- Create semantic graphs
		timer.reset();timer.start();
		String amr_bank = FileUtils.readFileToString(amr_bank_file.toFile(), StandardCharsets.UTF_8);
		GraphList graphs = factory.getGraphs(amr_bank);
		Path output_path = createOutputPath(amr_bank_file, semantic_graphs_suffix);
		Serializer.serialize(graphs, output_path);
		log.info("Graphs serialized to " + output_path);

		// 2- Rank meanings
		TextPlanner.Options options = new TextPlanner.Options();
		log.info("Options:\n" + options);
		TextPlanner.rankMeanings(graphs.getCandidates(), weighting, similarity, options);
		output_path = createOutputPath(amr_bank_file, semantic_graphs_ranked_suffix);
		Serializer.serialize(graphs, output_path);
		log.info("Ranked graphs serialized to " + output_path);

		// 3- Create global graph
		GlobalSemanticGraph graph = TextPlanner.createGlobalGraph(graphs);
		output_path = createOutputPath(amr_bank_file, global_graph_suffix);
		Serializer.serialize(graph, output_path);
		log.info("Global semantic graph serialized to " + output_path);

		// 4- Rank variables
		TextPlanner.rankVariables(graph, options);
		output_path = createOutputPath(amr_bank_file, global_graph_ranked_suffix);
		Serializer.serialize(graph, output_path);
		log.info("Ranked global graph serialized to " + output_path);

		// 5- Extract subgraphs
		Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, num_subgraphs_extract, options);
		output_path = createOutputPath(amr_bank_file, subgraphs_suffix);
		Serializer.serialize(subgraphs, output_path);
		log.info("Subgraphs serialized to " + output_path);

		// 6- Remove redundancy
		subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, num_subgraphs, similarity, options);
		output_path = createOutputPath(amr_bank_file, non_redundant_suffix);
		Serializer.serialize(subgraphs, output_path);
		log.info("Non-redundant subgraphs serialized to " + output_path);

		// 6- sort subgraphs
		subgraphs = TextPlanner.sortSubgraphs(subgraphs, similarity, options);
		output_path = createOutputPath(amr_bank_file, sorted_suffix);
		Serializer.serialize(subgraphs, output_path);
		log.info("Sorted subgraphs serialized to " + output_path);

		log.info("Planning took " + timer.stop());
	}

	private void plan(Path graphs_file, Path freqs, Path vectors, Format format, int num_subgraphs, Path output) throws Exception
	{
		log.info("Running from " + graphs_file);
		Stopwatch timer = Stopwatch.createStarted();
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		TFIDF weighting = new TFIDF(corpus, (n) -> n.endsWith("n"));
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);
		log.info("Loading resources took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		log.info("Options:\n" + options);
		List<SemanticSubgraph> plan = TextPlanner.plan(graphs, similarity, weighting, num_subgraphs, options);

		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
	}

	private static Path createOutputPath(Path input, String suffix)
	{
		String baseName = FilenameUtils.getBaseName(input.toAbsolutePath().toString());
		return input.getParent().resolve(Paths.get(baseName + suffix)).toAbsolutePath();
	}

	private SimilarityFunction chooseSimilarityFunction(Path vectors, Format format) throws Exception
	{
		SimilarityFunction similarity = null;
		switch (format)
		{
			case Text_Glove:
			case Text_Word2vec:
				return new TextVectorsSimilarity(vectors, format);
			case Binary_Word2vec:
				return new Word2VecVectorsSimilarity(vectors);
			case Binary_RandomAccess:
				return new RandomAccessVectorsSimilarity(vectors);
		}

		return null;
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

	@Parameters(commandDescription = "Create semantic graphs from an AMR bank")
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
		@Parameter(names = {"-ns", "-nostanford"}, description = "Do not load Stanford CoreNLP pipeline")
		private boolean no_stanford = false;
		@Parameter(names = {"-nb", "-nobabelnet"}, description = "Do not query BabelNet")
		private boolean no_babelnet = false;
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
		private Format format = Format.Text_Glove;
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
		private Format format = Format.Text_Glove;
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
		private Format format = Format.Text_Glove;
		@Parameter(names = {"-o", "-output"}, description = "Output binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Create a global graph from an AMR bank")
	private static class AMR2GlobalCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text-based amr file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Text_Glove;
		@Parameter(names = {"-ns", "-nostanford"}, description = "Do not load Stanford CoreNLP pipeline")
		private boolean no_stanford = false;
		@Parameter(names = {"-nb", "-nobabelnet"}, description = "Do not query BabelNet")
		private boolean no_babelnet = false;
	}

	@Parameters(commandDescription = "Plan from an AMR bank")
	private static class AMR2PlanCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text-based amr file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = FormatConverter.class, validateWith = FormatValidator.class)
		private Format format = Format.Text_Glove;
		@Parameter(names = {"-ns", "-nostanford"}, description = "Do not load Stanford CoreNLP pipeline")
		private boolean no_stanford = false;
		@Parameter(names = {"-nb", "-nobabelnet"}, description = "Do not query BabelNet")
		private boolean no_babelnet = false;
		@Parameter(names = {"-ne", "-number_extract"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int num_extract;
		@Parameter(names = {"-n", "-number_subgraphs"}, description = "Final number of subgraphs in plan", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.GreaterThanZero.class)
		private int num_subgraphs;
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
		private Format format = Format.Text_Glove;
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
		AMR2GlobalCommand amr2global = new AMR2GlobalCommand();
		AMR2PlanCommand amr2plan = new AMR2PlanCommand();
		PlanCommand plan = new PlanCommand();

		JCommander jc = new JCommander();
		jc.addCommand("create_graphs", create_graphs);
		jc.addCommand("rank_meanings", rank_meanings);
		jc.addCommand("create_global", create_global);
		jc.addCommand("rank_variables", rank_variables);
		jc.addCommand("extract_subgraphs", extract_subgraphs);
		jc.addCommand("remove_redundancy", remove_redundancy);
		jc.addCommand("sort_subgraphs", sort_subgraphs);
		jc.addCommand("amr2global", amr2global);
		jc.addCommand("amr2plan", amr2plan);
		jc.addCommand("plan", plan);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.debug(dateFormat.format(date) + " running " + 	Arrays.stream(args).collect(joining(" ")));
		log.debug("*********************************************************");

		Driver driver = new Driver();
		if (jc.getParsedCommand().equals("create_graphs"))
			driver.create_graphs(create_graphs.inputFile, create_graphs.bnFolder, create_graphs.outputFile,
					create_graphs.no_stanford, create_graphs.no_babelnet);
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
		else if (jc.getParsedCommand().equals("amr2global"))
			driver.amr2global(amr2global.inputFile, amr2global.bnFolder, amr2global.freqsFile, amr2global.vectorsPath,
					amr2global.format, amr2global.no_stanford, amr2global.no_babelnet);
		else if (jc.getParsedCommand().equals("amr2plan"))
			driver.amr2plan(amr2plan.inputFile, amr2plan.bnFolder, amr2plan.freqsFile, amr2plan.vectorsPath,
					amr2plan.format, amr2plan.no_stanford, amr2plan.no_babelnet, amr2plan.num_extract, amr2plan.num_subgraphs);
		else if (jc.getParsedCommand().equals("plan"))
			driver.plan(plan.inputFile, plan.freqsFile, plan.vectorsPath, plan.format, plan.num_subgraphs,
					plan.outputFile);
		else
			jc.usage();

		log.debug("\n\n");
	}
}


