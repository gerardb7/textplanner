package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Utility class to create similarity matrices
 */
public class SimilarityMatrixGenerator
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
		Word2Vec, SensEmbed, MergedSensEmbed;
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
		@Parameter(description = "Input folder", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFolder.class, required = true)
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

	private final PatternSimilarity sim;
	private final NumberFormat format;

	/**
	 * Takes as argument a similarity function between pairs of patterns
	 *
	 * @param inSimilarityFunction similarity function
	 */
	private SimilarityMatrixGenerator(PatternSimilarity inSimilarityFunction)
	{
		assert inSimilarityFunction != null;
		sim = inSimilarityFunction;
		format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);
	}

	/**
	 * Calculates a square matrix with pairwise similarity values between patterns
	 *
	 * @param inPatterns patterns to compare
	 * @return similarity matrix
	 */
	private double[][] calculateSimilarityMatrix(List<SemanticTree> inPatterns)
	{
		assert inPatterns != null;

		double[][] matrix = new double[inPatterns.size()][inPatterns.size()];
		IntStream.range(0, inPatterns.size()).forEach(i ->
				IntStream.range(0, inPatterns.size()).forEach(j ->
						matrix[i][j] = sim.getSimilarity(inPatterns.get(i), inPatterns.get(j))));
		return matrix;
	}

	/**
	 * Calculates a square matrix with pairwise similarity values between patterns
	 *
	 * @param inPatterns patterns to compare
	 * @return similarity matrix
	 */
	private double[][] calculateDistanceMatrix(List<SemanticTree> inPatterns)
	{
		assert inPatterns != null;

		double[][] matrix = new double[inPatterns.size()][inPatterns.size()];
		IntStream.range(0, inPatterns.size()).forEach(i ->
				IntStream.range(0, inPatterns.size()).forEach(j ->
						matrix[i][j] = 1.0 - sim.getSimilarity(inPatterns.get(i), inPatterns.get(j))));
		return matrix;
	}


	/**
	 * Generates a String representation of a format matrix that can be used to generate files for ELKI
	 *
	 * @param inMatrix matrix to convert
	 * @return string representation of matrix
	 */
	private String formatMatrix(double[][] inMatrix)
	{
		assert inMatrix != null;

		StringWriter w = new StringWriter();
		IntStream.range(0, inMatrix.length).forEach(i ->
				IntStream.range(0, inMatrix.length).forEach(j ->
						w.append(Integer.toString(i))
								.append(" ")
								.append(Integer.toString(j))
								.append(" ")
								.append(format.format(inMatrix[i][j]))
								.append("\n")));

		return w.toString();
	}

	public static void main(String[] args) throws Exception
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		EntitySimilarity sim = null;
		switch (cmlArgs.type)
		{
			case Word2Vec: sim = new Word2Vec(cmlArgs.embeddings); break;
			case SensEmbed: sim = new SensEmbed(cmlArgs.embeddings, false); break;
			case MergedSensEmbed: sim = new SensEmbed(cmlArgs.embeddings, true);	break;
		}
		PatternSimilarity similarity = new PatternSimilarity(sim);
		SimilarityMatrixGenerator calculator = new SimilarityMatrixGenerator(similarity);

		Path inputDoc = cmlArgs.input.get(0);
		String conll = new String(Files.readAllBytes(inputDoc), Charset.forName("UTF-8"));
		ConLLAcces reader = new ConLLAcces();
		List<SemanticTree> inputPatterns = reader.readTrees(conll);
		double[][] matrix;

		// @todo fix this
//		if (true)//cmlArgs.distance)
//		{
			matrix = calculator.calculateDistanceMatrix(inputPatterns);
//		}
//		else
//		{
			matrix = calculator.calculateSimilarityMatrix(inputPatterns);
//		}
		String matrixString = calculator.formatMatrix(matrix);
		System.out.println(matrixString);
	}
}
