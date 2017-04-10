package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.Combined;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
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

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input ConLL document", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFile.class, required = true)
		private List<Path> doc;
		@Parameter(names = "-d", description = "Produce distances instead of similarity")
		private boolean distance = true;
		@Parameter(names = "-wvec", description = "Path to file containing word vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path wordVectorsPath = null;
		@Parameter(names = "-svec", description = "Path to file containing sense vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path senseVectorsPath;
	}

	private final PatternSimilarity sim;
	private final NumberFormat format;

	/**
	 * Takes as argument a similarity function between pairs of patterns
	 *
	 * @param inSimilarityFunction similarity function
	 */
	public SimilarityMatrixGenerator(PatternSimilarity inSimilarityFunction)
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
	public double[][] calculateSimilarityMatrix(List<SemanticTree> inPatterns)
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
	public double[][] calculateDistanceMatrix(List<SemanticTree> inPatterns)
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
	public String formatMatrix(double[][] inMatrix)
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
		EntitySimilarity wordVectors = (cmlArgs.wordVectorsPath != null) ? new Word2Vec(cmlArgs.wordVectorsPath) : null;
		EntitySimilarity senseVectors = (cmlArgs.senseVectorsPath != null) ? new SensEmbed(cmlArgs.senseVectorsPath) : null;
		List<EntitySimilarity> functions = new ArrayList<>();
		functions.add(senseVectors);
		functions.add(wordVectors);
		EntitySimilarity combined = new Combined(functions);
		PatternSimilarity similarity = new PatternSimilarity(combined);
		SimilarityMatrixGenerator calculator = new SimilarityMatrixGenerator(similarity);

		Path inputDoc = cmlArgs.doc.get(0);
		String conll = new String(Files.readAllBytes(inputDoc), Charset.forName("UTF-8"));
		ConLLAcces reader = new ConLLAcces();
		List<AnnotatedTree> inputPatterns = reader.readTrees(conll);
		double[][] matrix = null;
		if (cmlArgs.distance)
		{
			//matrix = calculator.calculateDistanceMatrix(inputPatterns);
		}
		else
		{
			//matrix = calculator.calculateSimilarityMatrix(inputPatterns);
		}
		String matrixString = calculator.formatMatrix(matrix);
		System.out.println(matrixString);
	}
}
