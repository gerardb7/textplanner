package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.*;

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
				throw new ParameterException("Cannot read from " + name + " = " + value);
			}
		}
	}

	public static class PathToFile implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (!Files.isRegularFile(path))
			{
				throw new ParameterException("Cannot write to " + name + " = " + value);
			}
		}
	}

	private static class CMLArgs
	{
		@Parameter(description = "Input conll pairs file", arity = 1, converter = PathConverter.class,
				validateWith = PathToExistingFile.class, required = true)
		private List<Path> inputConll;
		@Parameter(description = "Output file", converter = PathConverter.class,
				validateWith = PathToFile.class, required = true)
		private Path output;
		@Parameter(names = "-wvec", description = "Path to file containing word vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class)
		private Path wordVectorsPath = null;
		@Parameter(names = "-svec", description = "Path to file containing sense vectors",
				converter = PathConverter.class, validateWith = PathToExistingFile.class, required = true)
		private Path senseVectorsPath;
	}

	public static void main(String[] args) throws Exception
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);
		ItemSimilarity senseSim = new SensEmbedSimilarity(cmlArgs.senseVectorsPath);
		ItemSimilarity wordSim = new Word2VecSimilarity(cmlArgs.wordVectorsPath);
		PatternSimilarity msgSim = new TreeEditSimilarity(wordSim, senseSim);
		String conll = new String(Files.readAllBytes(cmlArgs.inputConll.get(0)), Charset.forName("UTF-8"));
		ConLLAcces reader = new ConLLAcces();
		List<SemanticTree> trees = reader.readSemanticTrees(conll);
		StringWriter writer = new StringWriter();
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.HALF_UP);
		format.setMaximumFractionDigits(1);
		format.setMinimumFractionDigits(1);

		for (int i = 0 ; i < trees.size(); i += 2)
		{
			double similarity = msgSim.getSimilarity(trees.get(i), trees.get(i + 1));
			writer.append(format.format(similarity * 5)); // convert to scale
			writer.append("\n");
		}

		Files.write(cmlArgs.output, writer.toString().getBytes());
	}
}
