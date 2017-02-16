package edu.upf.taln.textplanning;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * General tester for text planner
 */
public class TextPlannerTest
{
	private static final String sparqlURL = "http://multisensor.ontotext.com/repositories/ms-final-prototype";
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private static final Path word2vecPath = null; //Paths.get("/home/gerard/data/GoogleNews-vectors-negative300.bin");
	private static final Path senseEmbedPath = Paths.get("/home/gerard/data/sensembed/babelfy_vectors_merged_senses_only");

	private final static Logger log = LoggerFactory.getLogger(TextPlannerTest.class);

	@Test
	public void testPlanTextFromFile() throws Exception
	{
		Path inputPath = Paths.get("/home/gerard/Baixades/test/");
//		Path inputPath = Paths.get("/home/gerard/Baixades/texts_dsynt/");
//		Path inputPath = Paths.get("/home/gerard/Baixades/multilingual/");
		Set<String> refs = new HashSet();//Collections.singleton("01929539n");

		List<Path> files = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(inputPath, 1))
		{
			files.addAll(paths.filter(Files::isRegularFile)
					.collect(Collectors.toList()));
		}

		ConLLDriver driver = new ConLLDriver(solrUrl, word2vecPath, senseEmbedPath);
		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.00001;
		options.generateStats = true;

		files.forEach(f -> {
			String documentFile = f.toAbsolutePath().toString();
			final String path = FilenameUtils.getFullPath(documentFile);
			final String fileName = FilenameUtils.getBaseName(documentFile);
			String outputFile = path + fileName + (refs.isEmpty() ? "" : "_" + refs.iterator().next()) + "_plan.conll";
			try (PrintWriter outm = new PrintWriter(outputFile))
			{
				String conllPlan = driver.runPlanner(f, refs, options);
				outm.print(conllPlan);
			}
			catch (Exception e)
			{
				log.info("Failed to plan text for " + f);
				e.printStackTrace();
			}
		});
	}

	@Test
	public void testPlanTextFromIRI() throws Exception
	{
		List<Pair<String, String>> documents = Arrays.asList(
				Pair.of("6eb17f86bb8eb8fd817ba07422d0a68856fd7c89", "01929539n")); // renewable energies
//				Pair.of("1f7cf118dfe6f68a98267c6490c142bb1c82f1fa", "01929539n"),
//				Pair.of("eb12eb15ac2c0fff7b82b00e2be7719ae501acd8", "01929539n"),
//				Pair.of("8000ed5d22ce9f9ec45d5dafbf3ed79f6d0e4b6b", "01929539n"),
//				Pair.of("8e8ac54cdd058e7091ec6f411cad8c08f31e996f", "01929539n"),
//				Pair.of("4265ea8b9bf6ec792c1353eeb5a26a6a10cd0e4f", "01929539n"),
//				Pair.of("93faf64c720479e24298a56d2d45e8dbc4f33b0e", "01929539n"),
//				Pair.of("238c25b4d5b5146e367cdaac86432c592f9e4672", "01929539n"),
//				Pair.of("2b0fd1fbf8edb15e170d66360ff92be67120db4d", "01929539n"),
//				Pair.of("b4c8777dcc7409eff12c31e88325862cd52c96a4", "01929539n"),
//				Pair.of("04011d32b94850bc924ee2c205aa98effd70b578", "01929539n"),
//				Pair.of("fa647c7feeed933576cba6a20a79c1d16a7bbfb9", "01929539n"),
//				Pair.of("0e8489854d12a8ea56e3f595c46431c3d12628e4", "01929539n"),
//				Pair.of("13c011b08faf88ad053c7591aea9d898cd2cf0e3", "01929539n"),
//				Pair.of("55a452f7f4cafd7f6a04742adc86e9259f1ab970", "01929539n"),
//				Pair.of("cf5f2eb9da64fb9aa8cdf277948543be61f2bc37", "01929539n"),
//				Pair.of("c44c4af2256d6012c68255194ec94c72bb720b89", "01929539n"));

		SPARQLDriver driver = new SPARQLDriver(sparqlURL, solrUrl, word2vecPath, senseEmbedPath);
		TextPlanner.Options options = new TextPlanner.Options();
		options.input = TextPlanner.Options.InputType.Graphs;
		options.rankingStopThreshold = 0.00001;
		options.generateStats = true;

		documents.forEach(d -> {
			String outputFile = "src/test/resources/" + d.getLeft() + "_" + d.getRight() + "_plan.conll";
			try (PrintWriter outm = new PrintWriter(outputFile))
			{
				Set<String> refs = Collections.singleton(d.getRight());
				String conll = driver.runPlanner("ms-content:" + d.getLeft(), refs, options);
				outm.print(conll);
			}
			catch (Exception e)
			{
				log.info("Failed to plan text for " + d);
				e.printStackTrace();
			}
		});
	}
}