package edu.upf.taln.textplanning;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.SolrCounts;
import edu.upf.taln.textplanning.input.DocumentProvider;
import edu.upf.taln.textplanning.input.SingleDocProvider;
import edu.upf.taln.textplanning.similarity.TreeEditSimilarity;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		@Parameter(names = "-ref", description = "Reference to entity/sense")
		private List<String> refs;
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
	private final static Logger log = LoggerFactory.getLogger(ConLLDriver.class);

	ConLLDriver(String inSolrUrl, Path inWordVectorsPath, Path inSenseVectorsPath) throws Exception
	{
		planner = TextPlanner.createConLLPlanner(inSolrUrl, inWordVectorsPath, inSenseVectorsPath);
	}

	public String runPlanner(Path inDoc, Set<String> inReferences, TextPlanner.Options inPlannerOptions)
	{
		try
		{
			String conll = new String(Files.readAllBytes(inDoc), Charset.forName("UTF-8"));
			DocumentProvider provider = new SingleDocProvider(conll);

			if (inReferences.isEmpty())
			{
				return planner.planText(provider, inPlannerOptions);
			}
			else
			{
				return planner.planText(inReferences, provider, inPlannerOptions);
			}
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

		log.info("Planning from file " + inputDoc + " and refs " + cmlArgs.refs);
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			Set<String> refs = new HashSet<>();
			if (cmlArgs.refs != null && cmlArgs.refs.isEmpty())
			{
				refs.addAll(cmlArgs.refs);
			}
			String planConll = driver.runPlanner(inputDoc, refs, options);

			log.info("Text planning took " + timer.stop());
			log.info("Solr queries: " + SolrCounts.debug.toString());
			log.info("Word form vector lookups: " + TreeEditSimilarity.numWordSuccessfulLookups + " successful, " +
					TreeEditSimilarity.numWordFailedLookups + " failed");
			log.info("Word sense vector lookups: " + TreeEditSimilarity.numSenseSuccessfulLookups + " successful, " +
					TreeEditSimilarity.numSenseFailedLookups + " failed");
			log.info("********************************************************");
			SolrCounts.debug.reset();
			System.out.println(planConll);

			if (options.generateStats)
			{
				String documentFile = inputDoc.toAbsolutePath().toString();
				final String path = FilenameUtils.getFullPath(documentFile);
				final String fileName = FilenameUtils.getBaseName(documentFile);
				String statsFile = path + fileName + (cmlArgs.refs.isEmpty() ? "" : "_" + cmlArgs.refs) + "_plan.stats";
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