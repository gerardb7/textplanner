package edu.upf.taln.textplanning.uima;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.InitialResourcesFactory.BiasResources;
import edu.upf.taln.textplanning.common.InitialResourcesFactory.SimilarityResources;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.uima.io.TextParser;
import edu.upf.taln.textplanning.uima.io.UIMAWrapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.createOutputPath;
import static edu.upf.taln.textplanning.common.FileUtils.serializeMeanings;
import static java.util.stream.Collectors.*;

public class Driver
{
	private static final String text_suffix = ".txt";
	private static final String meanings_suffix = ".candidates";
	private static final String get_candidates_command = "candidates";
	private static final String get_system_UIMA_command = "system_uima";
	private static final String get_system_command = "system";

	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

	@SuppressWarnings("unused")
	@Parameters(commandDescription = "Get candidates from text files using a dictionary")
	private static class GetCandidatesCommand
	{
		@Parameter(names = {"-t", "-texts"}, description = "ath to folder containing text files", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path texts;
		@Parameter(names = {"-o", "-output"}, description = "Path to output folder where candidate files will be stored", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.ValidPathToFolder.class)
		private Path output;
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
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
		@Parameter(names = {"-d", "-dictionary"}, description = "Dictionary folder", arity = 1, required = true,
				converter = CMLCheckers.PathConverter.class, validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path dictionary;
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
		@Parameter(names = {"-svt", "-sentence_vectors_type"}, description = "Type of sentence vectors", arity = 1,
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

	private static void processFiles(Path input_folder, Path output_folder)
	{
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);
		if (pipeline != null)
		{
			UIMAWrapper.processAndSerialize(input_folder, output_folder, text_suffix, TextParser.class, pipeline);
		}
	}

	private static void getSystemMeaningsUIMA(Path input_folder, Path output_folder, Path babel_config, Path freqs_file, Path vectors)
	{
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createRankingPipeline(language, false, babel_config, freqs_file, vectors);
		if (pipeline != null)
		{
			UIMAWrapper.processAndSerialize(input_folder, output_folder, text_suffix, TextParser.class, pipeline);
		}
	}

	private static void getSystemMeanings(Path text_folder, Path xmi_folder, Path output_folder, InitialResourcesFactory resources)
	{
		log.info("Setting up UIMA pipeline");
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createSpanPipeline(language, false);
		if (pipeline == null)
			return;

		log.info("Loading files");
		final List<UIMAWrapper> text_files = UIMAWrapper.process(text_folder, text_suffix, TextParser.class, pipeline).stream()
				.sorted(Comparator.comparing(UIMAWrapper::getId))
				.collect(toList());
		final List<UIMAWrapper> xmi_files = UIMAWrapper.readFromXMI(xmi_folder).stream()
				.sorted(Comparator.comparing(UIMAWrapper::getId))
				.collect(toList());

		// Check all files are paired
		assert (text_files.size() == xmi_files.size());
		for (int i = 0; i < text_files.size(); ++i)
		{
			final String name1 = FilenameUtils.removeExtension(text_files.get(i).getId());
			final String name2 =  FilenameUtils.removeExtension(xmi_files.get(i).getId());
			assert name1.equals(name2);
		}

		log.info("Processing text files");
		IntStream.range(0, text_files.size())
				.forEach(i ->
				{
					log.info("Processing file " + i);

					final List<Candidate> candidates = xmi_files.get(i).getDisambiguatedCandidates().stream()
							.flatMap(l -> l.stream()
									.flatMap(Set::stream))
							.collect(toList());

					UIMAWrapper uima = text_files.get(i);
					final List<String> tokens = uima.getNominalTokens().stream()
							.flatMap(List::stream)
							.collect(toList());

					Options options = new Options();
					DocumentResourcesFactory process = new DocumentResourcesFactory(resources, options, candidates, tokens, null);
					final BiasFunction context_weighter = process.getBiasFunction();
					final SimilarityFunction sim = resources.getSimilarityFunction();
					final BiPredicate<String, String> meanings_filter = process.getMeaningsFilter();
					final Predicate<Candidate> candidates_filter = process.getCandidatesFilter();
					TextPlanner.rankMeanings(candidates, candidates_filter, meanings_filter, context_weighter, sim, options);

					candidates.forEach(c -> c.setWeight(context_weighter.apply(c.getMeaning().getReference())));

					// Let's group and sort the plain list of candidates by sentence and offsets.
					final List<List<Set<Candidate>>> grouped_candidates = candidates.stream()
							.collect(groupingBy(c -> c.getMention().getContextId(), groupingBy(c -> c.getMention().getSpan(), toSet())))
							.entrySet().stream()
							.sorted(Comparator.comparing(Map.Entry::getKey))
							.map(Map.Entry::getValue)
							.map(e -> e.entrySet().stream()
									.sorted(Comparator.comparingInt(e2 -> e2.getKey().getLeft()))
									.map(Map.Entry::getValue)
									.collect(toList()))
							.collect(toList());

					Path out_file = createOutputPath(text_folder.resolve(uima.getId()), output_folder, text_suffix, meanings_suffix);
					log.info("Serializing meanings to  " + out_file);
					serializeMeanings(grouped_candidates, out_file);
				});
	}

	public static void main(String[] args) throws Exception
	{
		GetCandidatesCommand candidates = new GetCandidatesCommand();
		GetSystemMeaningsUIMACommand system_uima = new GetSystemMeaningsUIMACommand();
		GetSystemMeaningsCommand system = new GetSystemMeaningsCommand();

		JCommander jc = new JCommander();
		jc.addCommand(get_candidates_command, candidates);
		jc.addCommand(get_system_UIMA_command, system_uima);
		jc.addCommand(get_system_command, system);
		jc.parse(args);

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		log.info(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug(dateFormat.format(date) + " running " + String.join(" ", args));
		log.debug("*********************************************************");

		switch (jc.getParsedCommand())
		{
			case get_candidates_command:
			{
				processFiles(candidates.texts, candidates.output);
				break;
			}
			case get_system_UIMA_command:
			{
				getSystemMeaningsUIMA(system_uima.texts, system_uima.output, candidates.dictionary, system_uima.freqsFile, system_uima.vectorsPath);
				break;
			}
			case get_system_command:
			{
				BiasResources bias_resources = new BiasResources();
				bias_resources.bias_meanings_path = null;
				bias_resources.idf_file = system.freqsFile;
				bias_resources.word_vectors_path = system.word_vectors_path;
				bias_resources.word_vectors_type = system.word_vector_type;
				bias_resources.sentence_vectors_path = null;
				bias_resources.sentence_vectors_type = system.sentence_vector_type;

				SimilarityResources sim_resources = new SimilarityResources();
				sim_resources.meaning_vectors_path = system.sense_vectors_path;
				sim_resources.meaning_vectors_type = system.sense_vector_type;

				InitialResourcesFactory resources = new InitialResourcesFactory(language, null, bias_resources,
						sim_resources);
				getSystemMeanings(system.texts, system.candidates, system.output, resources);
				break;
			}
			default:
				jc.usage();
				break;
		}

		log.debug("\n\n");
	}
}
