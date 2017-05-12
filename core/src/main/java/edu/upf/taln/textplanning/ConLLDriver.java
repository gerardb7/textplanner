package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.corpora.SEWSolr;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
import edu.upf.taln.textplanning.weighting.Random;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes the text planner from one or more ConLL encoded files.
 */
public class ConLLDriver
{
	public static class PathConverter implements IStringConverter<Path>
	{
		@Override
		public Path convert(String value)
		{
			return Paths.get(value);
		}
	}

	public static class PathToExistingFile implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (!Files.exists(path) || !Files.isRegularFile(path))
			{
				throw new ParameterException("Cannot open file " + name + " = " + value);
			}
		}
	}

	public static class PathToExistingFolder implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (!Files.exists(path) || !Files.isDirectory(path))
			{
				throw new ParameterException("Cannot open folder " + name + " = " + value);
			}
		}
	}

	public enum EmbeddingsType
	{
		word, sense, merged;
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
		@Parameter(names = "-solr", description = "URL of Solr index", required = true)
		private String solrUrl;
		@Parameter(names = "-e", description = "Path to file containing embeddings",
				converter = PathConverter.class, validateWith = PathToExistingFile.class, required = true)
		private Path embeddings = null;
		@Parameter(names = "-t", description = "Type of embeddings", converter = EmbeddingsTypeConverter.class,
				required = true)
		private EmbeddingsType type;
		@Parameter(names = "-debug", description = "Debug mode")
		private boolean debug;
	}


	private final static Logger log = LoggerFactory.getLogger(ConLLDriver.class);

	/**
	 * Instantiates a planner that takes as input DSynt trees encoded using ConLL
	 * It uses the following resources:
	 * @param solrUrl url of solr server containing an index of the Semantically Enriched Wikipedia (SEW)
	 * @param embeddingsFile path to the file containing the word vectors obtained from the Google News Corpus
	 * @param t type of embeddings
	 * @return an instance of the TextPlanner class
	 */
	@SuppressWarnings("WeakerAccess")
	public static TextPlanner createConLLPlanner(String solrUrl, Path embeddingsFile, EmbeddingsType t) throws Exception
	{
//		final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
//		final Path word2vecPath = Paths.get("/home/gerard/data/GoogleNews-vectors-negative300.bin");
//		final Path senseEmbedPath = Paths.get("/home/gerard/data/sense/babelfy_vectors_merged_senses_only");
//		final Path inputPath = Paths.get("/home/gerard/Baixades/test/");

		Corpus corpus = new SEWSolr(solrUrl);
		WeightingFunction corpusMetric = new TFIDF(corpus);
		EntitySimilarity sim = null;
		switch (t)
		{
			case word: sim = new Word2Vec(embeddingsFile); break;
			case sense: sim = new SensEmbed(embeddingsFile, false); break;
			case merged: sim = new SensEmbed(embeddingsFile, true);	break;
		}

		return new TextPlanner(corpusMetric, sim);
	}


	/**
	 * Instantiates a planner that uses random relevance weighting and similarity calculations.
	 * @return an instance of the TextPlanner class
	 */
	@SuppressWarnings("WeakerAccess")
	public static TextPlanner createRandomPlanner() throws Exception
	{
		WeightingFunction corpusMetric = new Random();
		EntitySimilarity sim = new edu.upf.taln.textplanning.similarity.Random();

		return new TextPlanner(corpusMetric, sim);
	}


	@SuppressWarnings("WeakerAccess")
	public static String runPlanner(TextPlanner p, List<Path> inputFiles, TextPlanner.Options options)
	{
		try
		{
			Stopwatch timer = Stopwatch.createStarted();

			// Read trees from conll files
			ConLLAcces conll = new ConLLAcces();
			List<SemanticTree> trees = new ArrayList<>();
			inputFiles.forEach(d -> {
				String inConll;
				try
				{
					inConll = new String(Files.readAllBytes(d), Charset.forName("UTF-8"));
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
				trees.addAll(conll.readTrees(inConll));
			});

			conll.postProcessTrees(trees);
			List<SemanticTree> plan = p.planText(trees, options);
			String result = conll.writeTrees(plan);
			log.info("Planning took " + timer.stop());

			return result;
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
		TextPlanner planner = ConLLDriver.createConLLPlanner(cmlArgs.solrUrl, cmlArgs.embeddings, cmlArgs.type);
		TextPlanner.Options options = new TextPlanner.Options();
		log.info("Planning parameters " + options);

		Path inputFolder = cmlArgs.input.get(0);
		log.info("Generating summary for files in folder " + inputFolder);
		try
		{
			// Collect files from folder
			List<Path> files = new ArrayList<>();
			try (Stream<Path> paths = Files.walk(inputFolder, 1))
			{
				files.addAll(paths.filter(Files::isRegularFile)
//					.filter(p -> !p.getFileName().startsWith("plan_"))
						.collect(Collectors.toList()));
			}

			String planConll = ConLLDriver.runPlanner(planner, files, options);

//			log.info("Solr queries: " + SEWSolr.debug.toString());
//			log.info("Word form vector lookups: " + PatternSimilarity.numWordSuccessfulLookups + " successful, " +
//					PatternSimilarity.numWordFailedLookups + " failed");
//			log.info("Word sense vector lookups: " + PatternSimilarity.numSenseSuccessfulLookups + " successful, " +
//					PatternSimilarity.numSenseFailedLookups + " failed");
//			log.info("********************************************************");

			Path outputFile = Paths.get(System.getProperty("user.dir") + File.separator + "plan.conll");
			try (PrintWriter outs = new PrintWriter(outputFile.toString()))
			{
				outs.print(planConll);
				log.info("Created plan file " + outputFile);
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
			log.info("Failed to plan text");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}