package edu.upf.taln.textplanning;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.corpora.SEWSolr;
import edu.upf.taln.textplanning.disambiguation.BabelNetAnnotator;
import edu.upf.taln.textplanning.disambiguation.DBPediaType;
import edu.upf.taln.textplanning.input.CoNLLReader;
import edu.upf.taln.textplanning.input.CoNLLWriter;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.structures.ContentPattern;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathConverter;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToExistingFile;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToExistingFolder;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes the text planner from one or more ConLL encoded files.
 */
public class ConLLDriver
{
	public enum EmbeddingsType
	{
		word, sense;
		// converter that will be used later
		public static EmbeddingsType fromString(String code) {

			for(EmbeddingsType output : EmbeddingsType.values())
			{
				if(output.toString().equalsIgnoreCase(code))
				return output;
			}
			return null;
		}
	}

	public static class EmbeddingsTypeConverter implements IStringConverter<EmbeddingsType>
	{
		@Override
		public EmbeddingsType convert(String value)
		{
			EmbeddingsType convertedValue = EmbeddingsType.fromString(value);

			if(convertedValue == null)
			{
				throw new ParameterException("Value " + value + " can not be converted to EmbeddingsType. " +
						"Available values are: word, sense, merged.");
			}
			return convertedValue;
		}
	}

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input folder", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFolder.class, required = true)
		private List<Path> input;
		@Parameter(names = "-e", description = "Path to file containing embeddings",
				converter = PathConverter.class, validateWith = PathToExistingFile.class, required = true)
		private Path embeddings = null;
		@Parameter(names = "-f", description = "Path to file containing pre-computed frequencies",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path frequencies = null;
		@Parameter(names = "-t", description = "Path to file containing DBPedia types",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path types = null;
		@Parameter(names = "-debug", description = "Debug mode")
		private boolean debug = false;
	}

	private static final String input_suffix = "_sem_babel.conll";
	private final static Logger log = LoggerFactory.getLogger(ConLLDriver.class);

	/**
	 * Instantiates a planner that takes as input DSynt trees encoded using ConLL
	 * It uses the following resources:
	 * @param frequenciesFile file containing pre-computed frequencies of items
	 * @param embeddingsFile path to the file containing the word vectors obtained from the Google News Corpus
	 * @return an instance of the TextPlanner class
	 */
	@SuppressWarnings("WeakerAccess")
	public static TextPlanner createConLLPlanner(Path frequenciesFile, Path embeddingsFile, Path typesFile) throws Exception
	{
		Corpus corpus = frequenciesFile == null ? new SEWSolr() : new FreqsFile(frequenciesFile);
		WeightingFunction corpusMetric = new TFIDF(corpus);
		EntitySimilarity esim = new SensEmbed(embeddingsFile);
		DBPediaType type = typesFile == null ? new DBPediaType() : new DBPediaType(typesFile);
		return new TextPlanner(corpus, corpusMetric, esim, new BabelNetAnnotator(type));
	}

	@SuppressWarnings("WeakerAccess")
	public static String runPlanner(TextPlanner p, TextPlanner.Options options, List<Path> files)
	{
		List<String> conlls = files.stream()
				.map(f -> {
					try	{ return new String(Files.readAllBytes(f), Charset.forName("UTF-8")); }
					catch (IOException e) {	throw new RuntimeException(e); }
				})
				.collect(Collectors.toList());

		CoNLLReader conll = new CoNLLReader();
		Set<LinguisticStructure> graphs = conlls.stream()
				.map(conll::readStructures)
				.flatMap(List::stream)
				.collect(Collectors.toSet());
		// Read structures from conll files

		return runPlanner(p, graphs, options);
	}

	@SuppressWarnings("WeakerAccess")
	public static String runPlanner(TextPlanner p, Set<LinguisticStructure> graphs, TextPlanner.Options options)
	{
		try
		{
			List<ContentPattern> plan = p.planAndDisambiguateOptimizer(graphs, options);
			CoNLLWriter conll = new CoNLLWriter();
			return conll.writePatterns(plan);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws Exception
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);
		TextPlanner planner = ConLLDriver.createConLLPlanner(cmlArgs.frequencies, cmlArgs.embeddings, cmlArgs.types);

		TextPlanner.Options options = new TextPlanner.Options();
		options.generateStats = true;
		log.info("Planning parameters " + options);

		Path inputFolder = cmlArgs.input.get(0);
		log.info("Generating summary for files in folder " + inputFolder);
		try
		{
			// Collect files from folder
			List<Path> files = Files.walk(inputFolder, 1)
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(input_suffix))
					.sorted()
					.collect(Collectors.toList());

			String planConll = ConLLDriver.runPlanner(planner, options, files);

			Path outputFile = Paths.get(System.getProperty("user.dir") + File.separator + "planPageRank.conll");
			try (PrintWriter outs = new PrintWriter(outputFile.toString()))
			{
				outs.print(planConll);
				log.info("Created planPageRank file " + outputFile);
			}

			if (options.generateStats)
			{
				Path statsFile = Paths.get(System.getProperty("user.dir") + File.separator + "stats.txt");
				try (PrintWriter outs = new PrintWriter(statsFile.toString()))
				{
					outs.print(options);
					log.info("Created stats file " + statsFile);
				}
			}
		}
		catch (Exception e)
		{
			log.info("Failed to planPageRank text");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}