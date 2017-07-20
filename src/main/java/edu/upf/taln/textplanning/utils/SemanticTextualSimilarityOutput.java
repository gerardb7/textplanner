package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.ConLLDriver;
import edu.upf.taln.textplanning.input.CoNLLFormat;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.List;

/**
 * Creates a file containing similarity scores between pairs of text fragments, following the format expected
 * in Semantic Textual Similarity (SMT) SemEval tasks.
 */
public class SemanticTextualSimilarityOutput
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

	private enum EmbeddingsType
	{
		Word2Vec, MergedSensEmbed;
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
				throw new ParameterException("Value " + value + "can not be converted to EmbeddingsType. " +
						"Available values are: word, sense, merged.");
			}
			return convertedValue;
		}
	}

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input folder", arity = 1, converter = ConLLDriver.PathConverter.class,
				validateWith = ConLLDriver.PathToExistingFolder.class, required = true)
		private List<Path> input;
		@Parameter(names = "-solr", description = "URL of Solr index", required = true)
		private String solrUrl;
		@Parameter(names = "-e", description = "Path to file containing embeddings",
				converter = ConLLDriver.PathConverter.class, validateWith = ConLLDriver.PathToExistingFile.class, required = true)
		private Path embeddings = null;
		@Parameter(names = "-t", description = "Type of embeddings", converter = ConLLDriver.EmbeddingsTypeConverter.class,
				required = true)
		private EmbeddingsType type;
		@Parameter(names = "-debug", description = "Debug mode")
		private boolean debug;
	}

	public static void main(String[] args) throws Exception
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		EntitySimilarity sim = new SensEmbed(cmlArgs.embeddings);
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
