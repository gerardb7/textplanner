package edu.upf.taln.textplanning.common;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CMLCheckers
{
	public static class PathConverter implements IStringConverter<Path>
	{
		@Override
		public Path convert(String value)
		{
			return Paths.get(value);
		}
	}

	public static class IntegerConverter implements IStringConverter<Integer>
	{
		@Override
		public Integer convert(String value) { return Integer.parseInt(value); }
	}

	public static class ValidPathToFile implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if ((Files.exists(path) && Files.isDirectory(path)) || !Files.exists(path.getParent()))
			{
				throw new ParameterException("Cannot write to file " + name + " = " + value);
			}
		}
	}

	public static class ValidPathToFolder implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if ((Files.exists(path) && Files.isRegularFile(path)) || !Files.exists(path.getParent()))
			{
				throw new ParameterException("Cannot use output folder " + name + " = " + value);
			}
		}
	}

	public static class PathToNewFile implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (Files.exists(path) || Files.isDirectory(path))
			{
				throw new ParameterException("Cannot create file " + name + " = " + value);
			}
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

	public static class PathToExistingFileOrFolder implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (!Files.exists(path) || (!Files.isRegularFile(path) && !Files.isDirectory(path)))
			{
				throw new ParameterException("Invalid path " + name + " = " + value);
			}
		}
	}

	public static class GreaterOrEqualThanZero implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			int n = Integer.parseInt(value);
			if (n < 0)
				throw new ParameterException("Value must be greater or equal to 0: " + value);
		}
	}

	public static class GreaterThanZero implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			int n = Integer.parseInt(value);
			if (n < 1)
				throw new ParameterException("Value must be greater than 0: " + value);
		}
	}

	public static class VectorTypeConverter implements IStringConverter<Vectors.VectorType>
	{
		@Override
		public Vectors.VectorType convert(String value)
		{
			return Vectors.VectorType.valueOf(value);
		}
	}

	public static class SentenceVectorTypeConverter implements IStringConverter<SentenceVectors.VectorType>
	{
		@Override
		public SentenceVectors.VectorType convert(String value)
		{
			return SentenceVectors.VectorType.valueOf(value);
		}
	}

	public static class VectorTypeValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try{ Vectors.VectorType.valueOf(value); }
			catch (Exception e)
			{
				throw new ParameterException("Parameter " + name + " has invalid valued " + value);
			}
		}
	}

	public static class SentenceVectorTypeValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try{ SentenceVectors.VectorType.valueOf(value); }
			catch (Exception e)
			{
				throw new ParameterException("Parameter " + name + " has invalid valued " + value);
			}
		}
	}

}
