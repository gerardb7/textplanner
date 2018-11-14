package edu.upf.taln.textplanning.amr;

import com.beust.jcommander.*;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.amr.input.GlobalGraphFactory;
import edu.upf.taln.textplanning.amr.input.GraphListFactory;
import edu.upf.taln.textplanning.amr.io.AMRReader;
import edu.upf.taln.textplanning.amr.io.AMRWriter;
import edu.upf.taln.textplanning.amr.structures.AMRSemantics;
import edu.upf.taln.textplanning.amr.structures.GraphList;
import edu.upf.taln.textplanning.amr.utils.CMLCheckers;
import edu.upf.taln.textplanning.amr.utils.EmpiricalStudy;
import edu.upf.taln.textplanning.amr.utils.Serializer;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.core.similarity.RandomAccessVectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.TextVectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.VectorsTypes.Format;
import edu.upf.taln.textplanning.core.similarity.Word2VecVectorsSimilarity;
import edu.upf.taln.textplanning.core.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.weighting.NoWeights;
import edu.upf.taln.textplanning.core.weighting.NumberForms;
import edu.upf.taln.textplanning.core.weighting.WeightingFunction;
import main.AmrMain;
import net.sf.extjwnl.JWNLException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("ALL")
public class Driver
{
	private final static String output_folder = "out/";
	private final static String process_suffix = ".processed.bin";
	private final static String graphs_suffix = ".graphs.bin";
	private final static String graphs_ranked_suffix = ".graphs_ranked.bin";
	private final static String global_suffix = ".global.bin";
	private final static String global_ranked_suffix = ".global_ranked.bin";
	private final static String subgraphs_suffix = ".subgraphs.bin";
	private final static String non_redundant_suffix = ".non_redundant.bin";
	private final static String sorted_suffix = ".sorted.bin";
	private final static String plan_suffix = ".plan.amr";
	private final static String summary_suffix = ".summary.txt";
	private final static List<String> suffixes = Arrays.asList(process_suffix, graphs_suffix, graphs_ranked_suffix,
			global_suffix, global_ranked_suffix, subgraphs_suffix, non_redundant_suffix, sorted_suffix, plan_suffix);

	private final static Logger log = LogManager.getLogger();
	private static final String create_graphs_command = "create_graphs";
	private static final String rank_meanings_command = "rank_meanings";
	private static final String create_global_command = "create_global";
	private static final String rank_variables_command = "rank_variables";
	private static final String extract_subgraphs_command = "extract_subgraphs";
	private static final String remove_redundancy_command = "remove_redundancy";
	private static final String sort_subgraphs_command = "sort_subgraphs";
	private static final String write_amr_command = "write_amr";
	private static final String generate_command = "generate";
	private static final String summarize_command = "summarize";
	private static final String process_command = "process";
	private static final String stats_command = "stats";

	private void create_graphs(Path amr_bank_file, Path bn_config_folder, boolean no_stanford, boolean no_babelnet)
			throws IOException
	{
		log.info("Running from " + amr_bank_file);
		String amr_bank = FileUtils.readFileToString(amr_bank_file.toFile(), StandardCharsets.UTF_8);

		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder, no_stanford, no_babelnet);
		GraphList graphs = factory.getGraphs(amr_bank);

		Path output = createOutputPath(amr_bank_file, graphs_suffix);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void rank_meanings(Path graphs_file, Path freqs, Path vectors, Format format) throws Exception
	{
		log.info("Running from " + graphs_file);
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		WeightingFunction weighting = new NumberForms(r -> true);
		//WeightingFunction weighting = new NoWeights();
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);

		TextPlanner.Options options = new TextPlanner.Options();
		options.damping_meanings = 0.5;
		options.sim_threshold = 0.5;
		TextPlanner.rankMeanings(graphs.getCandidates(), weighting, similarity, options);

		Path output = createOutputPath(graphs_file, graphs_ranked_suffix);
		Serializer.serialize(graphs, output);
		log.info("Ranked graphs serialized to " + output);
	}

	private void create_global(Path graphs_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graphs_file);
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		GlobalSemanticGraph graph = GlobalGraphFactory.create(graphs);

		Path output = createOutputPath(graphs_file, global_suffix);
		Serializer.serialize(graph, output);
		log.info("Global semantic graph serialized to " + output);
	}

	private void rank_variables(Path graph_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graph_file);
		GlobalSemanticGraph graph = (GlobalSemanticGraph) Serializer.deserialize(graph_file);

		TextPlanner.Options options = new TextPlanner.Options();
		TextPlanner.rankVariables(graph, options);

		Path output = createOutputPath(graph_file, global_ranked_suffix);
		Serializer.serialize(graph, output);
		log.info("Ranked global semantic graph serialized to " + output);
	}

	private void extract_subgraphs(Path graph_file, int num_subgraphs) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graph_file);
		GlobalSemanticGraph graph = (GlobalSemanticGraph) Serializer.deserialize(graph_file);

		TextPlanner.Options options = new TextPlanner.Options();
		final Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, new AMRSemantics(), num_subgraphs, options);

		Path output = createOutputPath(graph_file, subgraphs_suffix);
		Serializer.serialize(subgraphs, output);
		log.info("Subgraphs serialized to " + output);
	}

	private void remove_redundancy(Path subgraphs_file, int num_subgraphs, Path vectors, Format format) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);

		TextPlanner.Options options = new TextPlanner.Options();
		subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, num_subgraphs, similarity, options);

		Path output = createOutputPath(subgraphs_file, non_redundant_suffix);
		Serializer.serialize(subgraphs, output);
		log.info("Non-redundant subgraphs serialized to " + output);
	}

	private void sort_subgraphs(Path subgraphs_file, Path vectors, Format format) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);

		TextPlanner.Options options = new TextPlanner.Options();
		final List<SemanticSubgraph> plan = TextPlanner.sortSubgraphs(subgraphs, similarity, options);

		Path output = createOutputPath(subgraphs_file, subgraphs_suffix);
		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
	}

	private void write_amr(Path subgraphs_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + subgraphs_file);
		List<SemanticSubgraph> subgraphs = (List<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);

		AMRWriter writer = new AMRWriter();
		final String out_amr = writer.write(subgraphs);

		Path output = createOutputPath(subgraphs_file, plan_suffix);
		FileUtils.writeStringToFile(output.toFile(), out_amr, Charsets.UTF_8);
		log.info("AMR plan writen to " + output);
	}


	private void generate(Path amr_file, Path resources) throws IOException, JWNLException
	{
		log.info("Running from " + amr_file);
		final String amr = FileUtils.readFileToString(amr_file.toFile(), Charsets.UTF_8);

		AmrMain generator = new AmrMain(resources);
		String text = generator.generate(amr);

		Path output = createOutputPath(amr_file, summary_suffix);
		FileUtils.writeStringToFile(output.toFile(), text, Charsets.UTF_8);
		log.info("Summary text writen to " + output);
	}

	private void summarize(Path amr_bank, Path bn_config_folder, Path freqs, Path vectors, Format format,
	                       boolean no_stanford, boolean no_babelnet, int num_subgraphs_extract, int num_subgraphs,
	                       Path generation_resources) throws Exception
	{
		log.info("*****Running from " + amr_bank + "*****");
		Stopwatch timer = Stopwatch.createStarted();

		// Set up
		log.info("*****Setting up planner*****");
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null, bn_config_folder, no_stanford, no_babelnet);

		WeightingFunction meanings_weighting = new NoWeights();
		//WeightingFunction variables_weighting = new TFIDF(corpus, r -> true);
		SimilarityFunction similarity = chooseSimilarityFunction(vectors, format);
		AmrMain generator = new AmrMain(generation_resources);

		TextPlanner.Options options = new TextPlanner.Options();
		log.info("Options: " + options);
		log.info("*****Set up took " + timer.stop() + "*****");
		timer.reset(); timer.start();

		if (Files.isDirectory(amr_bank))
		{
			final List<Path> files = Files.list(amr_bank)
					.filter(Files::isRegularFile)
					.filter(p -> p.endsWith(".amr"))
					.collect(Collectors.toList());
			log.info("*****Processing " + files.size() + " files in " + amr_bank + "*****");

			final List<Path> failed_files = files.stream()
					.filter(f -> !summarizeAMRFile(f, reader, factory, meanings_weighting, similarity, options, num_subgraphs_extract, num_subgraphs, generator))
					.collect(toList());
			final int num_success = files.size() - failed_files.size();
			log.info("Successfully planned " + num_success + " files out of " + files.size());
			if (!failed_files.isEmpty())
				log.info("Failed files: " +  failed_files.stream().map(Path::getFileName).map(Path::toString).collect(joining(",")));
		}
		else if (Files.isRegularFile(amr_bank))
		{
			log.info("*****Begin processing*****");
			summarizeAMRFile(amr_bank, reader, factory, meanings_weighting, similarity, options, num_subgraphs_extract, num_subgraphs, generator);
		}
		else
			log.error("*****Cannot open " + amr_bank + ", aborting*****");

		log.info("*****Processing took " + timer.stop() + "******");
	}

	private boolean summarizeAMRFile(Path amr_bank_file, AMRReader reader, GraphListFactory factory, WeightingFunction weight,
	                                 SimilarityFunction similarity, TextPlanner.Options options, int num_subgraphs_extract,
	                                 int num_subgraphs, AmrMain generator)
	{
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			log.info("***Planning from " + amr_bank_file.getFileName() + "***");

			// 1- Create semantic graphs
			String amr_bank = FileUtils.readFileToString(amr_bank_file.toFile(), StandardCharsets.UTF_8);
			GraphList graphs = factory.getGraphs(amr_bank);
			Path output_path = createOutputPath(amr_bank_file, graphs_suffix);
			Serializer.serialize(graphs, output_path);

			// 2- Rank meanings
			TextPlanner.rankMeanings(graphs.getCandidates(), weight, similarity, options);
			output_path = createOutputPath(amr_bank_file, graphs_ranked_suffix);
			Serializer.serialize(graphs, output_path);

			// 3- Create global graph
			GlobalSemanticGraph graph = GlobalGraphFactory.create(graphs);
			output_path = createOutputPath(amr_bank_file, global_suffix);
			Serializer.serialize(graph, output_path);

			// 4- Rank variables
			TextPlanner.rankVariables(graph, options);
			output_path = createOutputPath(amr_bank_file, global_ranked_suffix);
			Serializer.serialize(graph, output_path);

			// 5- Extract subgraphs
			Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, new AMRSemantics(), num_subgraphs_extract, options);
			output_path = createOutputPath(amr_bank_file, subgraphs_suffix);
			Serializer.serialize(subgraphs, output_path);

			// 6- Remove redundancy
			subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, num_subgraphs, similarity, options);
			output_path = createOutputPath(amr_bank_file, non_redundant_suffix);
			Serializer.serialize(subgraphs, output_path);

			// 6- sort subgraphs
			List<SemanticSubgraph> sorted_subgraphs = TextPlanner.sortSubgraphs(subgraphs, similarity, options);
			output_path = createOutputPath(amr_bank_file, sorted_suffix);
			Serializer.serialize(sorted_subgraphs, output_path);

			// 7- create AMR plan
			log.info("*Writing AMR*");
			AMRWriter writer = new AMRWriter();
			final String out_amr = writer.write(sorted_subgraphs);
			output_path = createOutputPath(amr_bank_file, plan_suffix);
			FileUtils.writeStringToFile(output_path.toFile(), out_amr, Charsets.UTF_8);
			log.info("Plan serialized as AMR");

			// 8- generate text
			log.info("*Generating text*");
			Stopwatch gen_timer = Stopwatch.createStarted();
			String text = generator.generate(out_amr);
			log.info("*Generation completed in " + gen_timer.stop() + "*");

			log.info("***Planning took " + timer.stop() +"***");
			return true;
		}
		catch (Exception e)
		{
			log.error("***Planning failed " + e + "***");
			return false;
		}
	}



	private static Path createOutputPath(Path input, String suffix) throws IOException
	{
		final Path out = input.getParent().resolve(output_folder);
		if (!Files.exists(out))
			Files.createDirectories(out);

		String basename = input.getFileName().toString();
		final String new_name = suffixes.stream()
				.filter(basename::endsWith)
				.findFirst()
				.map(s -> basename.substring(0, basename.length() - s.length()))
				.orElse(basename) + suffix;

		return out.resolve(new_name);
	}

	public static SimilarityFunction chooseSimilarityFunction(Path vectors, Format format) throws Exception
	{
		SimilarityFunction similarity = null;
		switch (format)
		{
			case Text_Glove:
			case Text_Word2vec:
				return TextVectorsSimilarity.create(vectors, format);
			case Binary_Word2vec:
				return Word2VecVectorsSimilarity.create(vectors);
			case Binary_RandomAccess:
				return RandomAccessVectorsSimilarity.create(vectors);
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
		@Parameter(names = {"-i", "-input"}, description = "Input text-based AMR file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
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
	}

	@Parameters(commandDescription = "Create global semantic graph from a list of semantic graphs")
	private static class CreateGlobalCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
	}

	@Parameters(commandDescription = "Rank vertices in a global semantic graph")
	private static class RankVariablesCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary global graph file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
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
	}

	@Parameters(commandDescription = "Serialize plan as AMR")
	private static class WriteAMRCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary plan file containing sorted subgraphs", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
	}

	@Parameters(commandDescription = "Generate text from AMR")
	private static class GenerateCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to input AMR text file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-g", "-generation"}, description = "Path to generation resources folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path generation_resources;
	}

	@Parameters(commandDescription = "Generate summaries from an AMR bank")
	private static class SummarizeCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Path to input file or folder containing text-based AMRs", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path input;
		@Parameter(names = {"-b", "-babelconfig"}, description = "Path to BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
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
		@Parameter(names = {"-g", "-generation"}, description = "Path to generation resources folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path generation_resources;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Process plain text file with CoreNLP and BabelNet")
	private static class ProcessFileCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Run empirical study from serialized file")
	private static class GetStatsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary file containing results of processing text", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-f", "-frequencies"}, description = "Frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-format"}, description = "Vectors format", arity = 1, required = true,
				converter = Driver.FormatConverter.class, validateWith = Driver.FormatValidator.class)
		private Format format = Format.Text_Glove;
		@Parameter(names = {"-pw", "-pairwise"}, description = "If true calculate stats from pairwise similiarity values")
		private boolean do_pairwise_similarity = false;
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
		WriteAMRCommand write_amr = new WriteAMRCommand();
		GenerateCommand generateCommand = new GenerateCommand();
		/* --- */
		SummarizeCommand summarize = new SummarizeCommand();
		ProcessFileCommand process = new ProcessFileCommand();
		GetStatsCommand stats = new GetStatsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(create_graphs_command, create_graphs);
		jc.addCommand(rank_meanings_command, rank_meanings);
		jc.addCommand(create_global_command, create_global);
		jc.addCommand(rank_variables_command, rank_variables);
		jc.addCommand(extract_subgraphs_command, extract_subgraphs);
		jc.addCommand(remove_redundancy_command, remove_redundancy);
		jc.addCommand(sort_subgraphs_command, sort_subgraphs);
		jc.addCommand(write_amr_command, write_amr);
		jc.addCommand(generate_command, generateCommand);
		/* --- */
		jc.addCommand(summarize_command, summarize);
		jc.addCommand(process_command, process);
		jc.addCommand(stats_command, stats);

		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running " + 	Arrays.stream(args).collect(joining(" ")));
		log.debug(dateFormat.format(date) + " running " + 	Arrays.stream(args).collect(joining(" ")));
		log.debug("*********************************************************");

		Driver driver = new Driver();
		if (jc.getParsedCommand().equals(create_graphs_command))
			driver.create_graphs(create_graphs.inputFile, create_graphs.bnFolder, create_graphs.no_stanford,
					create_graphs.no_babelnet);
		else if (jc.getParsedCommand().equals(rank_meanings_command))
			driver.rank_meanings(rank_meanings.inputFile, rank_meanings.freqsFile, rank_meanings.vectorsPath,
					rank_meanings.format);
		else if (jc.getParsedCommand().equals(create_global_command))
			driver.create_global(create_global.inputFile);
		else if (jc.getParsedCommand().equals(rank_variables_command))
			driver.rank_variables(rank_variables.inputFile);
		else if (jc.getParsedCommand().equals(extract_subgraphs_command))
			driver.extract_subgraphs(extract_subgraphs.inputFile, extract_subgraphs.num_subgraphs);
		else if (jc.getParsedCommand().equals(remove_redundancy_command))
			driver.remove_redundancy(remove_redundancy.inputFile, remove_redundancy.num_subgraphs,
					remove_redundancy.vectorsPath, remove_redundancy.format);
		else if (jc.getParsedCommand().equals(sort_subgraphs_command))
			driver.sort_subgraphs(sort_subgraphs.inputFile, sort_subgraphs.vectorsPath, sort_subgraphs.format);
		else if (jc.getParsedCommand().equals(write_amr_command))
			driver.write_amr(write_amr.inputFile);
		else if (jc.getParsedCommand().equals(generate_command))
			driver.generate(generateCommand.inputFile, generateCommand.generation_resources);
		/* --- */
		else if (jc.getParsedCommand().equals(summarize_command))
			driver.summarize(summarize.input, summarize.bnFolder, summarize.freqsFile, summarize.vectorsPath,
					summarize.format, summarize.no_stanford, summarize.no_babelnet, summarize.num_extract,
					summarize.num_subgraphs, summarize.generation_resources);
		else if (jc.getParsedCommand().equals(process_command))
		{
			final String text = FileUtils.readFileToString(process.inputFile.toFile(), StandardCharsets.UTF_8);

			final Path output = createOutputPath(process.inputFile, process_suffix);
			EmpiricalStudy.processText(text, output, process.bnFolder);
		}
		else if (jc.getParsedCommand().equals(stats_command))
		{
			EmpiricalStudy.calculateStats(stats.inputFile, stats.freqsFile, stats.vectorsPath, stats.format, stats.do_pairwise_similarity);
		}
		else
			jc.usage();

		log.debug("\n\n");
	}
}


