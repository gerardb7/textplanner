package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.pattern.ItemSetMining;
import edu.upf.taln.textplanning.pattern.PatternExtractor;
import org.apache.commons.io.FilenameUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * General tester for text planner
 */
public class TextPlannerTest
{
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private static final Path word2vecPath = null; //Paths.get("/home/gerard/data/GoogleNews-vectors-negative300.bin");
	private static final Path senseEmbedPath = Paths.get("/home/gerard/data/sensembed/babelfy_vectors_merged_senses_only");
	private static final Path inputPath = Paths.get("/home/gerard/Baixades/test/");
	private static final Set<String> refs = new HashSet<>();//Collections.singleton("01929539n");
	private final static Logger log = LoggerFactory.getLogger(TextPlannerTest.class);

	@Test
	public void generatePatterns() throws Exception
	{
		for (Path f : getInputFiles())
		{
			String inConLL = new String(Files.readAllBytes(f), Charset.forName("UTF-8"));
			ConLLAcces conll = new ConLLAcces();
			List<SemanticTree> semanticTrees = conll.readSemanticTrees(inConLL);
			PatternExtractor extractor = new ItemSetMining();
			Set<SemanticTree> patterns = extractor.getPatterns(semanticTrees);
			String outConLL = conll.writeSemanticTrees(patterns);
			writeToFile("patterns_", f, outConLL);
		}
	}

	@Test
	public void generatePlans() throws Exception
	{
		List<Path> files = getInputFiles();
		ConLLDriver driver = new ConLLDriver(solrUrl, word2vecPath, senseEmbedPath);
		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.00001;
		options.generateStats = true;

		files.forEach(f -> {
			String outConLL = driver.runPlanner(f, refs, options);
			writeToFile("plan_", f, outConLL);
		});
	}

	private List<Path> getInputFiles() throws Exception
	{
		List<Path> files = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(inputPath, 1))
		{
			files.addAll(paths.filter(Files::isRegularFile)
					.collect(Collectors.toList()));
		}

		return files;
	}

	private void writeToFile(String inPrefix, Path inBaseFile, String inContents)
	{
		String outFile = inBaseFile.toAbsolutePath().toString();
		String path = FilenameUtils.getFullPath(outFile);
		final String fileName = FilenameUtils.getBaseName(outFile);
		String outFilePath =  path + inPrefix + fileName + ".conll";

		try (PrintWriter outm = new PrintWriter(outFilePath))
		{
			outm.print(inContents);
		}
		catch (Exception e)
		{
			log.info("Failed to create output file for " + inBaseFile);
			e.printStackTrace();
		}
	}
}