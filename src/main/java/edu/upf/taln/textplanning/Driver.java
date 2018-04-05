package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.similarity.Word2VecSimilarity;
import edu.upf.taln.textplanning.weighting.TFIDF;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Driver
{
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static void main(String[] args) throws Exception
	{
		Driver driver = new Driver();
		Path amr = Paths.get(driver.getClass().getResource("/test_amr.txt").toURI());
		Path freqs = Paths.get(driver.getClass().getResource("/freqs.txt").toURI());
		Path embeddings = Paths.get("/media/gerard/Coral/data/NASARIembed+UMBC_w2v.bin");
		Path types = Paths.get(driver.getClass().getResource("/types.txt").toURI());

		Stopwatch timer = Stopwatch.createStarted();
		AMRReader reader = new AMRReader();
//		FreqsFile corpus = new FreqsFile(freqs);
//		TFIDF weighting = new TFIDF(corpus);
//		Word2VecSimilarity similarity = new Word2VecSimilarity(embeddings);
		TextPlanner planner = new TextPlanner(reader, null, null, null);
		log.info("Set up completed in " + timer.stop());

		String amr_bank = FileUtils.readFileToString(amr.toFile(), StandardCharsets.UTF_8);
		TextPlanner.Options options = new TextPlanner.Options();
		planner.plan(amr_bank, 10, options);
	}
}


