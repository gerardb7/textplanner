package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.corpora.FreqsFile;
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
import java.util.List;
import java.util.stream.Collectors;

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
		@Parameter(names = "-solr", description = "URL of Solr index")
		private String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
		@Parameter(names = "-e", description = "Path to file containing embeddings",
				converter = PathConverter.class, validateWith = PathToExistingFile.class, required = true)
		private Path embeddings = null;
		@Parameter(names = "-f", description = "Path to file containing pre-computed frequencies",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path frequencies = null;
		@Parameter(names = "-t", description = "Type of embeddings", converter = EmbeddingsTypeConverter.class)
		private EmbeddingsType type = EmbeddingsType.merged;
		@Parameter(names = "-debug", description = "Debug mode")
		private boolean debug = false;
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
	 * Instantiates a planner that takes as input DSynt trees encoded using ConLL
	 * It uses the following resources:
	 * @param frequenciesFile file containing pre-computed frequencies of items
	 * @param embeddingsFile path to the file containing the word vectors obtained from the Google News Corpus
	 * @param t type of embeddings
	 * @return an instance of the TextPlanner class
	 */
	@SuppressWarnings("WeakerAccess")
	public static TextPlanner createConLLPlanner(Path frequenciesFile, Path embeddingsFile, EmbeddingsType t) throws Exception
	{
		Corpus corpus = new FreqsFile(frequenciesFile);
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
	@SuppressWarnings({"WeakerAccess", "unused"})
	public static TextPlanner createRandomPlanner() throws Exception
	{
		WeightingFunction corpusMetric = new Random();
		EntitySimilarity sim = new edu.upf.taln.textplanning.similarity.Random();

		return new TextPlanner(corpusMetric, sim);
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

		ConLLAcces conll = new ConLLAcces();
		List<SemanticTree> trees = conlls.stream()
				.map(conll::readTrees)
				.flatMap(List::stream)
				.collect(Collectors.toList());
		// Read trees from conll files

		return runPlanner(p, trees, options);
	}

	@SuppressWarnings("WeakerAccess")
	public static String runPlanner(TextPlanner p, List<SemanticTree> trees, TextPlanner.Options options)
	{
		try
		{
			ConLLAcces conll = new ConLLAcces();
			conll.postProcessTrees(trees);
			List<SemanticTree> plan = p.planText(trees, options);
			return conll.writeTrees(plan);
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
		TextPlanner planner;
		if (cmlArgs.frequencies != null)
			planner = ConLLDriver.createConLLPlanner(cmlArgs.frequencies, cmlArgs.embeddings, cmlArgs.type);
		else
			planner = ConLLDriver.createConLLPlanner(cmlArgs.solrUrl, cmlArgs.embeddings, cmlArgs.type);

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
					.filter(p -> p.toString().endsWith("_deep_r.conll"))
					.sorted()
					.collect(Collectors.toList());

			String planConll = ConLLDriver.runPlanner(planner, options, files);

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