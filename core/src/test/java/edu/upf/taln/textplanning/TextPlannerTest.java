package edu.upf.taln.textplanning;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * General tester for text planner
 */
public class TextPlannerTest
{
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private static final Path word2vecPath = null; //Paths.get("/home/gerard/data/GoogleNews-vectors-negative300.bin");
	private static final Path senseEmbedPath = Paths.get("/home/gerard/data/sensembed/babelfy_vectors_merged_senses_only");
	private static final Path inputPath = Paths.get("/home/gerard/Baixades/test/");
	private final static Logger log = LoggerFactory.getLogger(TextPlannerTest.class);

	@Test
	public void generatePlans() throws Exception
	{
		Set<Path> files = getInputFiles();
		ConLLDriver driver = new ConLLDriver(solrUrl, word2vecPath, senseEmbedPath);
		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.00001;
		options.generateStats = true;

		String outConLL = driver.runPlanner(files, options);
		writeToFile(outConLL);
	}

	private Set<Path> getInputFiles() throws Exception
	{
		Set<Path> files = new HashSet<>();
		try (Stream<Path> paths = Files.walk(inputPath, 1))
		{
			files.addAll(paths.filter(Files::isRegularFile)
//					.filter(p -> !p.getFileName().startsWith("plan_"))
					.collect(Collectors.toList()));
		}

		return files;
	}

	private void writeToFile(String inContents)
	{
		try (PrintWriter outm = new PrintWriter(inputPath.toAbsolutePath().toString() + "plan.conll"))
		{
			outm.print(inContents);
		}
		catch (Exception e)
		{
			log.error("Failed to create plan file");
			e.printStackTrace();
		}
	}
}