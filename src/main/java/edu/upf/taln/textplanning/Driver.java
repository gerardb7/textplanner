package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.similarity.TextVectorsSimilarity;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.utils.Serializer;
import edu.upf.taln.textplanning.weighting.TFIDF;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.status.StatusLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;

public class Driver
{
	private final static Logger log = LogManager.getLogger(TextPlanner.class);

	private void create_graphs(Path amr, Path types, Path output) throws IOException
	{
		Stopwatch timer = Stopwatch.createStarted();
		String amr_bank = FileUtils.readFileToString(amr.toFile(), StandardCharsets.UTF_8);
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null);
		GraphList graphs = factory.getGraphs(amr_bank);
		Serializer.serialize(graphs, output);
		log.info("Graphs serialized to " + output);
	}

	private void plan(Path amr, Path freqs, Path embeddings, Format format, Path types) throws Exception
	{
		Stopwatch timer = Stopwatch.createStarted();
		AMRReader reader = new AMRReader();
		//FreqsFile corpus = new FreqsFile(freqs);
		CompactFrequencies corpus = (CompactFrequencies)Serializer.deserialize(freqs);

		TFIDF weighting = new TFIDF(corpus);
		TextVectorsSimilarity similarity = new TextVectorsSimilarity(embeddings, format);
		TextPlanner planner = new TextPlanner(reader, null, weighting, similarity);
		log.info("Set up took " + timer.stop());

		TextPlanner.Options options = new TextPlanner.Options();
		GraphList graphs = (GraphList) Serializer.deserialize(amr);
		planner.plan(graphs, 10, options);
	}

	public static void main(String[] args) throws Exception
	{
		// configure logging
		Configurator.setRootLevel(Level.DEBUG);
		StatusLogger.getLogger().setLevel(Level.FATAL);

		Driver driver = new Driver();
		Path types = Paths.get(driver.getClass().getResource("/types.txt").toURI());

//	    Path amr = Paths.get(driver.getClass().getResource("/test_amr.txt").toURI());
//		Path graphs_out = Files.createTempFile("graphs", ".bin");
//		driver.create_graphs(amr, types, graphs_out);

		//Path freqs = Paths.get("/media/gerard/data_cluster/freqs.json");
		Path freqs = Paths.get(driver.getClass().getResource("/freqs_subset-json").toURI());
		Path embeddings = Paths.get("//media/gerard/data_cluster/sense_embeddings/nasari-vectors/NASARIembed+UMBC_w2v.txt");
		Path graphs_in = Paths.get(driver.getClass().getResource("/graphs.bin").toURI());
		driver.plan(graphs_in, freqs, embeddings, Format.Glove, types);
	}
}


