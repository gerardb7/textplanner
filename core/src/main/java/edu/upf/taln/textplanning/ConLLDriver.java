package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.corpora.SEWSolr;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.Combined;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
import edu.upf.taln.textplanning.weighting.Linear;
import edu.upf.taln.textplanning.weighting.Position;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input ConLL document", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFile.class, required = true)
		private List<Path> doc;
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
		Corpus corpus = new SEWSolr(inSolrUrl);

		WeightingFunction corpusMetric = new TFIDF(corpus);
		WeightingFunction positionMetric = new Position();
		Map<WeightingFunction, Double> functions = new HashMap<>();
		functions.put(corpusMetric, 0.8);
		functions.put(positionMetric, 0.2);
		WeightingFunction linear = new Linear(functions);

		EntitySimilarity senseVectors = (inSenseEmbedPath != null) ? new SensEmbed(inSenseEmbedPath) : null;
		EntitySimilarity wordVectors = (inWord2VecPath != null) ? new Word2Vec(inWord2VecPath) : null;
		List<EntitySimilarity> metrics = new ArrayList<>();
		if (senseVectors != null)
			metrics.add(senseVectors);
		if (wordVectors != null)
			metrics.add(wordVectors);
		EntitySimilarity combination = new Combined(metrics);
		return new TextPlanner(linear, combination);
	}

	ConLLDriver(String inSolrUrl, Path inWordVectorsPath, Path inSenseVectorsPath) throws Exception
	{
		planner = ConLLDriver.createConLLPlanner(inSolrUrl, inWordVectorsPath, inSenseVectorsPath);
	}

	public String runPlanner(Path inDoc, TextPlanner.Options inPlannerOptions)
	{
		try
		{
			String inConll = new String(Files.readAllBytes(inDoc), Charset.forName("UTF-8"));
			List<SemanticTree> annotatedTrees = conll.readTrees(inConll);
			List<SemanticTree> plan = planner.planText(annotatedTrees, inPlannerOptions);

			String conll = "";
			for (SemanticTree t : plan)
			{
				for (Edge e : t.getPreOrder())
				{
					conll += t.getEdgeSource(e).getEntity().getEntityLabel();
				}
				conll += "\n"; // Treat each pattern as a separate sentence
			}

			return conll;
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
		Path inputDoc = cmlArgs.doc.get(0);

		log.info("Planning from file " + inputDoc);
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			String planConll = driver.runPlanner(inputDoc, options);

			log.info("Text planning took " + timer.stop());
			log.info("Solr queries: " + SEWSolr.debug.toString());
//			log.info("Word form vector lookups: " + PatternSimilarity.numWordSuccessfulLookups + " successful, " +
//					PatternSimilarity.numWordFailedLookups + " failed");
//			log.info("Word sense vector lookups: " + PatternSimilarity.numSenseSuccessfulLookups + " successful, " +
//					PatternSimilarity.numSenseFailedLookups + " failed");
//			log.info("********************************************************");
			SEWSolr.debug.reset();
			System.out.println(planConll);

			if (options.generateStats)
			{
				String documentFile = inputDoc.toAbsolutePath().toString();
				final String path = FilenameUtils.getFullPath(documentFile);
				final String fileName = FilenameUtils.getBaseName(documentFile);
				String statsFile = path + fileName + "_plan.stats";
				try (PrintWriter outs = new PrintWriter(statsFile))
				{
					outs.print(options.stats);
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