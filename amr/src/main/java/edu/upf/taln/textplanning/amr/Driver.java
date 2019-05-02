package edu.upf.taln.textplanning.amr;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.amr.io.*;
import edu.upf.taln.textplanning.amr.structures.AMRAlignments;
import edu.upf.taln.textplanning.amr.structures.AMRGraph;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.amr.utils.EmpiricalStudy;
import edu.upf.taln.textplanning.common.*;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.ranking.DifferentMentionsFilter;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import main.AmrMain;
import net.sf.extjwnl.JWNLException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("ALL")
public class Driver
{
	private final static ULocale language = ULocale.ENGLISH;
	private final static String output_folder = "out/";
	private final static String process_suffix = ".processed.bin";
	private final static String graphs_suffix = ".graphs.bin";
	private final static String graphs_ranked1_suffix = ".graphs_ranked1.bin";
	private final static String graphs_ranked2_suffix = ".graphs_ranked2.bin";
	private final static String global_suffix = ".global.bin";
	private final static String subgraphs_suffix = ".subgraphs.bin";
	private final static String non_redundant_suffix = ".non_redundant.bin";
	private final static String sorted_suffix = ".sorted.bin";
	private final static String plan_suffix = ".plan.amr";
	private final static String summary_suffix = ".summary.txt";
	private final static String truncated_summary_suffix = ".trunc_summary.txt";
	private final static List<String> suffixes = Arrays.asList(process_suffix, graphs_suffix, graphs_ranked1_suffix,
			graphs_ranked2_suffix, global_suffix, subgraphs_suffix, non_redundant_suffix, sorted_suffix, plan_suffix);

	private final static Logger log = LogManager.getLogger();
	private static final String create_graphs_command = "create_graphs";
	private static final String rank_meanings_command = "rank_meanings";
	private static final String create_global_command = "create_global";
	private static final String rank_variables_command = "rank_mentions";
	private static final String extract_subgraphs_command = "extract_subgraphs";
	private static final String remove_redundancy_command = "remove_redundancy";
	private static final String sort_subgraphs_command = "sort_subgraphs";
	private static final String write_amr_command = "write_amr";
	private static final String generate_command = "generate";
	private static final String summarize_command = "summarize";
	private static final String process_command = "process";
	private static final String stats_command = "stats";

	private void create_graphs(Path amr_bank_file, InitialResourcesFactory resources, boolean no_stanford)
			throws IOException
	{
		log.info("Running from " + amr_bank_file);
		String amr_bank = FileUtils.readTextFile(amr_bank_file);

		AMRReader reader = new AMRReader();
		AMRGraphListFactory factory = new AMRGraphListFactory(reader, language, null, resources.getDictionary(), no_stanford);
		AMRGraphList graphs = factory.create(amr_bank);

		Path output = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
				FilenameUtils.getExtension(amr_bank_file.toFile().getName()), graphs_suffix);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void rank_meanings(Path graphs_file, InitialResourcesFactory resources) throws Exception
	{
		log.info("Running from " + graphs_file);
		AMRGraphList graphs = (AMRGraphList) Serializer.deserialize(graphs_file);

		// We'll use the whole text as a context
		final List<String> tokens = graphs.getGraphs().stream()
				.map(AMRGraph::getAlignments)
				.map(AMRAlignments::getTokens)
				.flatMap(List::stream)
				.collect(toList());

		Options options = new Options();
		final List<Candidate> candidates = new ArrayList<>(graphs.getCandidates());
		DocumentResourcesFactory process = new DocumentResourcesFactory(resources, options, candidates, tokens, null);

		final BiasFunction context_weighter = process.getBiasFunction();
		final SimilarityFunction sim = resources.getSimilarityFunction();
		final BiPredicate<String, String> meanings_filter = process.getMeaningsFilter();
		final Predicate<Candidate> candidates_filter = process.getCandidatesFilter();
		TextPlanner.rankMeanings(candidates, candidates_filter, meanings_filter, context_weighter, sim, options);

		Path output = FileUtils.createOutputPath(graphs_file, graphs_file.getParent(),
				FilenameUtils.getExtension(graphs_file.toFile().getName()), graphs_ranked1_suffix);
		Serializer.serialize(graphs, output);
		log.info("Graphs with ranked meanings serialized to " + output);
	}

	private void rank_mentions(Path graphs_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graphs_file);
		AMRGraphList graphs = (AMRGraphList) Serializer.deserialize(graphs_file);

		Options options = new Options();
		graphs.getGraphs().forEach(g ->
		{
			final List<Candidate> graph_candidates = graphs.getCandidates(g);
			TextPlanner.rankMentions(graph_candidates, graphs::adjacent, options);
		});

		Path output = FileUtils.createOutputPath(graphs_file, graphs_file.getParent(),
				FilenameUtils.getExtension(graphs_file.toFile().getName()), graphs_ranked2_suffix);
		Serializer.serialize(graphs, output);
		log.info("Graphs with ranked mentions serialized to " + output);
	}

	private void create_global(Path graphs_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graphs_file);
		AMRGraphList graphs = (AMRGraphList) Serializer.deserialize(graphs_file);

		AMRSemanticGraphFactory factory = new AMRSemanticGraphFactory();
		SemanticGraph graph = factory.create(graphs);

		Path output = FileUtils.createOutputPath(graphs_file, graphs_file.getParent(),
				FilenameUtils.getExtension(graphs_file.toFile().getName()), global_suffix);
		Serializer.serialize(graph, output);
		log.info("Global semantic graph serialized to " + output);
	}

	private void extract_subgraphs(Path graph_file, int num_subgraphs) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + graph_file);
		SemanticGraph graph = (SemanticGraph) Serializer.deserialize(graph_file);

		Options options = new Options();
		options.num_subgraphs_extract = num_subgraphs;
		final Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, new AMRSemantics(), options);

		Path output = FileUtils.createOutputPath(graph_file, graph_file.getParent(),
				FilenameUtils.getExtension(graph_file.toFile().getName()), subgraphs_suffix);
		Serializer.serialize(subgraphs, output);
		log.info("Subgraphs serialized to " + output);
	}

	private void remove_redundancy(Path subgraphs_file, int num_subgraphs, InitialResourcesFactory resources) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		BiFunction<String, String, OptionalDouble> sim = resources.getSimilarityFunction();

		Options options = new Options();
		options.num_subgraphs = num_subgraphs;
		subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, (e1, e2) -> sim.apply(e1, e2), options);

		Path output = FileUtils.createOutputPath(subgraphs_file, subgraphs_file.getParent(),
				FilenameUtils.getExtension(subgraphs_file.toFile().getName()), non_redundant_suffix);
		Serializer.serialize(subgraphs, output);
		log.info("Non-redundant subgraphs serialized to " + output);
	}

	private void sort_subgraphs(Path subgraphs_file, InitialResourcesFactory resources) throws Exception
	{
		log.info("Running from " + subgraphs_file);
		Collection<SemanticSubgraph> subgraphs = (Collection<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);
		BiFunction<String, String, OptionalDouble> sim = resources.getSimilarityFunction();;

		Options options = new Options();
		final List<SemanticSubgraph> plan = TextPlanner.sortSubgraphs(subgraphs, (e1, e2) -> sim.apply(e1, e2), options);

		Path output = FileUtils.createOutputPath(subgraphs_file, subgraphs_file.getParent(),
				FilenameUtils.getExtension(subgraphs_file.toFile().getName()), sorted_suffix);
		Serializer.serialize(plan, output);
		log.info("Text plan serialized to " + output);
	}

	private void write_amr(Path subgraphs_file) throws IOException, ClassNotFoundException
	{
		log.info("Running from " + subgraphs_file);
		List<SemanticSubgraph> subgraphs = (List<SemanticSubgraph>) Serializer.deserialize(subgraphs_file);

		AMRWriter writer = new AMRWriter();
		final String out_amr = writer.write(subgraphs);

		Path output = FileUtils.createOutputPath(subgraphs_file, subgraphs_file.getParent(),
				FilenameUtils.getExtension(subgraphs_file.toFile().getName()), plan_suffix);
		FileUtils.writeTextToFile(output, out_amr);
		log.info("AMR plan writen to " + output);
	}


	private void generate(Path amr_file, Path resources) throws IOException, JWNLException
	{
		log.info("Running from " + amr_file);
		final String amr = FileUtils.readTextFile(amr_file);

		AmrMain generator = new AmrMain(resources);
		String text = generator.generate(amr);

		Path output = FileUtils.createOutputPath(amr_file, amr_file.getParent(),
				FilenameUtils.getExtension(amr_file.toFile().getName()), summary_suffix);
		FileUtils.writeTextToFile(output, text);
		log.info("Summary text writen to " + output);
	}

	private void summarize(Path amr_bank, InitialResourcesFactory resources,
	                       boolean no_stanford, int num_subgraphs_extract, int num_subgraphs,
	                       Path generation_resources, int max_words) throws Exception
	{
		log.info("*****Running from " + amr_bank + "*****");
		Stopwatch timer = Stopwatch.createStarted();

		// Set up
		log.info("*****Setting up planner*****");
//		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);
		AMRReader reader = new AMRReader();
		AMRGraphListFactory factory = new AMRGraphListFactory(reader, language,null, resources.getDictionary(), no_stanford);
		AMRSemanticGraphFactory globalFactory = new AMRSemanticGraphFactory();

		BiFunction<String, String, OptionalDouble> sim = resources.getSimilarityFunction();
		AmrMain generator = new AmrMain(generation_resources);

		Options options = new Options();
		options.num_subgraphs_extract = num_subgraphs_extract;
		options.num_subgraphs = num_subgraphs;
		log.info("Options: " + options);
		log.info("*****Set up took " + timer.stop() + "*****");
		timer.reset(); timer.start();

		if (Files.isDirectory(amr_bank))
		{
			final List<Path> files = Files.list(amr_bank)
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".amr"))
					.collect(Collectors.toList());
			log.info("*****Processing " + files.size() + " files in " + amr_bank + "*****");

			final List<Path> failed_files = files.stream()
					.filter(f -> !summarizeFile(f, reader, factory, globalFactory, e -> 0.0, (e1, e2) -> sim.apply(e1, e2), options, num_subgraphs_extract, num_subgraphs, generator, max_words))
					.collect(toList());
			final int num_success = files.size() - failed_files.size();
			log.info("Successfully planned " + num_success + " files out of " + files.size());
			if (!failed_files.isEmpty())
				log.info("Failed files: " +  failed_files.stream().map(Path::getFileName).map(Path::toString).collect(joining(",")));
		}
		else if (Files.isRegularFile(amr_bank))
		{
			log.info("*****Begin processing*****");

			summarizeFile(amr_bank, reader, factory, globalFactory, e -> 0.0, (e1, e2) -> sim.apply(e1, e2), options, num_subgraphs_extract, num_subgraphs, generator, max_words);
		}
		else
			log.error("*****Cannot open " + amr_bank + ", aborting*****");

		log.info("*****Processing took " + timer.stop() + "******");
	}

	private boolean summarizeFile(Path amr_bank_file, AMRReader reader, AMRGraphListFactory graphListFactory,
	                              AMRSemanticGraphFactory globalGraphFactory, Function<String, Double> weight,
	                              BiFunction<String, String, OptionalDouble> similarity, Options options,
	                              int num_subgraphs_extract, int num_subgraphs, AmrMain generator, int max_words)
	{
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			log.info("***Planning from " + amr_bank_file.getFileName() + "***");

			// 1- Create semantic graphs
			String amr_bank = FileUtils.readTextFile(amr_bank_file);
			AMRGraphList graphs = graphListFactory.create(amr_bank);
			Path output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), graphs_suffix);
			Serializer.serialize(graphs, output_path);

			// 2- Rank meanings
			List<Candidate> candidates = new ArrayList<>(graphs.getCandidates());

			// We'll use the whole text as a context
			final List<String> context = graphs.getGraphs().stream()
					.map(AMRGraph::getAlignments)
					.map(AMRAlignments::getTokens)
					.flatMap(List::stream)
					.collect(toList());
//			final ContextBias context_weighter = new ContextBias(candidates, resources.getSenseContextVectors(),
//					resources.getSentenceVectors(), w -> context, resources.getWordVectorsSimilarityFunction());

//			TopCandidatesFilter candidates_filter = new TopCandidatesFilter(candidates, context_weighter::weight, 5);
			DifferentMentionsFilter meanings_filter = new DifferentMentionsFilter(candidates);

//			TextPlanner.rankMeanings(candidates, candidates_filter, meanings_filter, weight, similarity, options);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), graphs_ranked1_suffix);
			Serializer.serialize(graphs, output_path);

			// 3- Rank mentions in each sentence
			graphs.getGraphs().forEach(g ->
			{
				final List<Candidate> graph_candidates = graphs.getCandidates(g);
				TextPlanner.rankMentions(graph_candidates, graphs::adjacent, options);
			});
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), graphs_ranked2_suffix);
			Serializer.serialize(graphs, output_path);

			// 4- Create global graph
			SemanticGraph graph = globalGraphFactory.create(graphs);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), global_suffix);
			Serializer.serialize(graph, output_path);

			// 5- Extract subgraphs
			Collection<SemanticSubgraph> subgraphs = TextPlanner.extractSubgraphs(graph, new AMRSemantics(), options);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), subgraphs_suffix);
			Serializer.serialize(subgraphs, output_path);

			// 6- Remove redundancy
			subgraphs = TextPlanner.removeRedundantSubgraphs(subgraphs, similarity, options);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), non_redundant_suffix);
			Serializer.serialize(subgraphs, output_path);

			// 6- sort subgraphs
			List<SemanticSubgraph> sorted_subgraphs = TextPlanner.sortSubgraphs(subgraphs, similarity, options);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), sorted_suffix);
			Serializer.serialize(sorted_subgraphs, output_path);

			// 7- create AMR plan
			log.info("*Writing AMR*");
			AMRWriter writer = new AMRWriter();
			final String out_amr = writer.write(sorted_subgraphs);
			output_path = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), plan_suffix);
			FileUtils.writeTextToFile(output_path, out_amr);
			log.info("Plan serialized as AMR");

			// 8- generate text
			log.info("*Generating text*");
			Stopwatch gen_timer = Stopwatch.createStarted();
			String text = generator.generate(out_amr);
			String truncated_text = Arrays.stream(text.split(" "))
					.limit(max_words)
					.collect(joining(" "));

			Path output = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), summary_suffix);
			FileUtils.writeTextToFile(output, text);
			output = FileUtils.createOutputPath(amr_bank_file, amr_bank_file.getParent().resolve(output_folder),
					FilenameUtils.getExtension(amr_bank_file.toFile().getName()), truncated_summary_suffix);
			FileUtils.writeTextToFile(output, truncated_text);

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

	@Parameters(commandDescription = "Create semantic graphs from an AMR bank")
	private static class CreateGraphsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text-based AMR file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-ns", "-nostanford"}, description = "Do not load Stanford CoreNLP pipeline")
		private boolean no_stanford = false;
	}

	@Parameters(commandDescription = "Rank meanings in a collection of semantic graphs")
	private static class RankMeaningsCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary graphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-b", "-bias"}, description = "Path to text file containing meanings used to bias ranking", arity = 1,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path biasFile;
		@Parameter(names = {"-st", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
				converter = CMLCheckers.SentenceVectorTypeConverter.class, validateWith = CMLCheckers.SentenceVectorTypeValidator.class)
		private SentenceVectorType sentence_vector_type = SentenceVectorType.Random;
		@Parameter(names = {"-wv", "-word_vectors"}, description = "Path to word vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path word_vectors_path;
		@Parameter(names = {"-wt", "-word_vectors_type"}, description = "Type of word vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType word_vector_type = VectorType.Random;
		@Parameter(names = {"-cv", "-context_vectors"}, description = "Path to sense context vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path context_vectors_path;
		@Parameter(names = {"-ct", "-context_vectors_type"}, description = "Type of sense context vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType context_vector_type = VectorType.Random;
		@Parameter(names = {"-sv", "-sense_vectors"}, description = "Path to sense vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path sense_vectors_path;
		@Parameter(names = {"-st", "-sense_vectors_type"}, description = "Type of sense vectors", arity = 1,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType sense_vector_type = VectorType.Random;
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
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int num_subgraphs;
	}

	@Parameters(commandDescription = "Remove redundant subgraphs")
	private static class RemoveRedundancyCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input binary subgraphs file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-n", "-number"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int num_subgraphs;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-vectorType"}, description = "Vectors vectorType", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType vectorType = VectorType.Random;
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
		@Parameter(names = {"-vf", "-vectorType"}, description = "Vectors vectorType", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType vectorType = VectorType.Random;
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
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
		@Parameter(names = {"-f", "-frequencies"}, description = "Path to frequencies file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path freqsFile;
		@Parameter(names = {"-v", "-vectors"}, description = "Path to vectors", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFileOrFolder.class)
		private Path vectorsPath;
		@Parameter(names = {"-vf", "-vectorType"}, description = "Vectors vectorType", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType vectorType = VectorType.Random;
		@Parameter(names = {"-ns", "-nostanford"}, description = "Do not load Stanford CoreNLP pipeline")
		private boolean no_stanford = false;
		@Parameter(names = {"-ne", "-number_extract"}, description = "Number of subgraphs to extract", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int num_extract;
		@Parameter(names = {"-n", "-number_subgraphs"}, description = "Number of subgraphs in plan", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int num_subgraphs;
		@Parameter(names = {"-w", "-max_words"}, description = "Maximum number of words in summary", arity = 1, required = true,
				converter = CMLCheckers.IntegerConverter.class, validateWith = CMLCheckers.IntegerGreaterThanZero.class)
		private int max_words;
		@Parameter(names = {"-g", "-generation"}, description = "Path to generation resources folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path generation_resources;
	}

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Process plain text file with CoreNLP and a dictionary")
	private static class ProcessFileCommand
	{
		@Parameter(names = {"-i", "-input"}, description = "Input text file", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
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
		@Parameter(names = {"-vf", "-vectorType"}, description = "Vectors vectorType", arity = 1, required = true,
				converter = CMLCheckers.VectorTypeConverter.class, validateWith = CMLCheckers.VectorTypeValidator.class)
		private VectorType vectorType = VectorType.Random;
		@Parameter(names = {"-pw", "-pairwise"}, description = "If true of stats from pairwise similiarity values")
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
		{
			InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
			InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
			InitialResourcesFactory resources = new InitialResourcesFactory(language, create_graphs.dictionary, bias_resources,
					sim_resources);
			driver.create_graphs(create_graphs.inputFile, resources, create_graphs.no_stanford);
		}
		else if (jc.getParsedCommand().equals(rank_meanings_command))
		{
			InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();
			bias_resources.bias_meanings_path = rank_meanings.biasFile;
			bias_resources.word_vectors_path = rank_meanings.word_vectors_path;
			bias_resources.word_vectors_type = rank_meanings.word_vector_type;
			bias_resources.sentence_vectors_type = rank_meanings.sentence_vector_type;
			bias_resources.idf_file = rank_meanings.freqsFile;

			InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
			sim_resources.meaning_vectors_path = rank_meanings.sense_vectors_path;
			sim_resources.meaning_vectors_type = rank_meanings.sense_vector_type;

			InitialResourcesFactory resources = new InitialResourcesFactory(language, null, bias_resources,
					sim_resources);
			driver.rank_meanings(rank_meanings.inputFile, resources);
		}
		else if (jc.getParsedCommand().equals(create_global_command))
			driver.create_global(create_global.inputFile);
		else if (jc.getParsedCommand().equals(rank_variables_command))
			driver.rank_mentions(rank_variables.inputFile);
		else if (jc.getParsedCommand().equals(extract_subgraphs_command))
			driver.extract_subgraphs(extract_subgraphs.inputFile, extract_subgraphs.num_subgraphs);
		else if (jc.getParsedCommand().equals(remove_redundancy_command))
		{
			InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();

			InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
			sim_resources.meaning_vectors_path = remove_redundancy.vectorsPath;
			sim_resources.meaning_vectors_type = remove_redundancy.vectorType;

			InitialResourcesFactory resources = new InitialResourcesFactory(language, null, bias_resources,
					sim_resources);
			driver.remove_redundancy(remove_redundancy.inputFile, remove_redundancy.num_subgraphs, resources);
		}
		else if (jc.getParsedCommand().equals(sort_subgraphs_command))
		{
			InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();

			InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
			sim_resources.meaning_vectors_path = sort_subgraphs.vectorsPath;
			sim_resources.meaning_vectors_type = sort_subgraphs.vectorType;

			InitialResourcesFactory resources = new InitialResourcesFactory(language, null, bias_resources,
					sim_resources);
			driver.sort_subgraphs(sort_subgraphs.inputFile, resources);
		}
		else if (jc.getParsedCommand().equals(write_amr_command))
			driver.write_amr(write_amr.inputFile);
		else if (jc.getParsedCommand().equals(generate_command))
			driver.generate(generateCommand.inputFile, generateCommand.generation_resources);
		/* --- */
		else if (jc.getParsedCommand().equals(summarize_command))
		{
			InitialResourcesFactory.BiasResources bias_resources = new InitialResourcesFactory.BiasResources();

			InitialResourcesFactory.SimilarityResources sim_resources = new InitialResourcesFactory.SimilarityResources();
			sim_resources.meaning_vectors_path = summarize.vectorsPath;
			sim_resources.meaning_vectors_type = summarize.vectorType;

			InitialResourcesFactory resources = new InitialResourcesFactory(language, summarize.dictionary, bias_resources,
					sim_resources);

			driver.summarize(summarize.input, resources, summarize.no_stanford,summarize.num_extract,
					summarize.num_subgraphs, summarize.generation_resources, summarize.max_words);
		}
		else if (jc.getParsedCommand().equals(process_command))
		{
			final String text = FileUtils.readTextFile(process.inputFile);
			final Path output = FileUtils.createOutputPath(process.inputFile, process.inputFile.getParent(),
					FilenameUtils.getExtension(process.inputFile.toFile().getName()), process_suffix);
			EmpiricalStudy.processText(text, output, process.dictionary);
		}
		else if (jc.getParsedCommand().equals(stats_command))
		{
			EmpiricalStudy.calculateStats(stats.inputFile, stats.freqsFile, stats.vectorsPath, stats.vectorType, stats.do_pairwise_similarity);
		}
		else
			jc.usage();

		log.debug("\n\n");
	}
}


