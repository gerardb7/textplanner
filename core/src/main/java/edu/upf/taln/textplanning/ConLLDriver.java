package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.corpora.SEWSolr;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.Combined;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input folder", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFolder.class, required = true)
		private List<Path> input;
		@Parameter(names = "-solr", description = "URL of Solr index", required = true)
		private String solrUrl;
		@Parameter(names = "-wvec", description = "Path to file containing word vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path wordVectorsPath = null;
		@Parameter(names = "-svec", description = "Path to file containing sense vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class, required = true)
		private Path senseVectorsPath;
		@Parameter(names = "-debug", description = "Debug mode")
		private boolean debug;
	}
	private final TextPlanner planner;
	private final ConLLAcces conll = new ConLLAcces();
	private final static Logger log = LoggerFactory.getLogger(ConLLDriver.class);

	/**
	 * Instantiates a planner that takes as input DSynt trees encoded using ConLL
	 * It uses the following resources:
	 * @param inSolrUrl url of solr server containing an index of the Semantically Enriched Wikipedia (SEW)
	 * @param inWord2VecPath path to the file containing the Word2Vec vectors obtained from the Google News Corpus
	 * @param inSenseEmbedPath path to the file containing a merged version of the SensEmbed vectors
	 * @return an instance of the TextPlanner class
	 * @throws Exception
	 */
	public static TextPlanner createConLLPlanner(String inSolrUrl, Path inWord2VecPath, Path inSenseEmbedPath) throws Exception
	{
//		final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
//		final Path word2vecPath = Paths.get("/home/gerard/data/GoogleNews-vectors-negative300.bin");
//		final Path senseEmbedPath = Paths.get("/home/gerard/data/sensembed/babelfy_vectors_merged_senses_only");
//		final Path inputPath = Paths.get("/home/gerard/Baixades/test/");

		Corpus corpus = new SEWSolr(inSolrUrl);
		WeightingFunction corpusMetric = new TFIDF(corpus);
		EntitySimilarity senseVectors = (inSenseEmbedPath != null) ? new SensEmbed(inSenseEmbedPath) : null;
		EntitySimilarity wordVectors = (inWord2VecPath != null) ? new Word2Vec(inWord2VecPath) : null;
		List<EntitySimilarity> metrics = new ArrayList<>();
		if (senseVectors != null)
			metrics.add(senseVectors);
		if (wordVectors != null)
			metrics.add(wordVectors);
		EntitySimilarity combination = new Combined(metrics);
		return new TextPlanner(corpusMetric, combination);
	}

	ConLLDriver(String inSolrUrl, Path inWordVectorsPath, Path inSenseVectorsPath) throws Exception
	{
		planner = ConLLDriver.createConLLPlanner(inSolrUrl, inWordVectorsPath, inSenseVectorsPath);
	}

	public String runPlanner(Path inputFolder, TextPlanner.Options options)
	{
		try
		{
			Stopwatch timer = Stopwatch.createStarted();

			Set<Path> files = new HashSet<>();
			try (Stream<Path> paths = Files.walk(inputFolder, 1))
			{
				files.addAll(paths.filter(Files::isRegularFile)
//					.filter(p -> !p.getFileName().startsWith("plan_"))
						.collect(Collectors.toList()));
			}

			// Read trees from conll files
			List<SemanticTree> trees = new ArrayList<>();
			files.forEach(d -> {
				String inConll = null;
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
			List<SemanticTree> plan = planner.planText(trees, options);			String result = conll.writeTrees(plan);
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
		ConLLDriver driver = new ConLLDriver(cmlArgs.solrUrl, cmlArgs.wordVectorsPath, cmlArgs.senseVectorsPath);
		TextPlanner.Options options = new TextPlanner.Options();
		Path inputFolder = cmlArgs.input.get(0);

		log.info("Generating summary for files in folder " + inputFolder);
		try
		{
			String planConll = driver.runPlanner(inputFolder, options);

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
					outs.print(options.stats);
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