package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import edu.upf.taln.textplanning.ConLLDriver.EmbeddingsTypeConverter;
import edu.upf.taln.textplanning.input.CoNLLFormat;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathConverter;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToExistingFile;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;

/**
 * Creates a file containing similarity scores between pairs of text fragments, following the format expected
 * in Semantic Textual Similarity (SMT) SemEval tasks.
 */
public class SemanticTextualSimilarityOutput
{
	private enum EmbeddingsType
	{
		Word2Vec, MergedSensEmbed
	}

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input folder", arity = 1, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class, required = true)
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

	public static void main(String[] args) throws Exception
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		//EntitySimilarity sim = new SensEmbed(cmlArgs.embeddings);
		//PatternSimilarity msgSim = new PatternSimilarity(sim);
		String conll = new String(Files.readAllBytes(cmlArgs.input.get(0)), Charset.forName("UTF-8"));
		CoNLLFormat reader = new CoNLLFormat();
		List<LinguisticStructure> trees = reader.readStructures(conll);
		StringWriter writer = new StringWriter();
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.HALF_UP);
		format.setMaximumFractionDigits(1);
		format.setMinimumFractionDigits(1);

		for (int i = 0 ; i < trees.size(); i += 2)
		{
			double similarity = 0.0; // msgSim.getSimilarity(trees.get(i), trees.get(i + 1));
			writer.append(format.format(similarity * 5)); // convert to scale
			writer.append("\n");
		}


		//Files.write(sim., writer.toString().getBytes());
	}
}
