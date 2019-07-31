package edu.upf.taln.textplanning.common;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

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

	public static class DoubleConverter implements IStringConverter<Double>
	{
		@Override
		public Double convert(String value) { return Double.parseDouble(value); }
	}

	public static class ULocaleConverter implements IStringConverter<ULocale>
	{
		@Override
		public ULocale convert(String value) { return new ULocale(value); }
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

	public static class IntegerGreaterOrEqualThanZero implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			int n = Integer.parseInt(value);
			if (n < 0)
				throw new ParameterException("Value must be greater or equal to 0: " + value);
		}
	}

	public static class IntegerGreaterThanZero implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			int n = Integer.parseInt(value);
			if (n < 1)
				throw new ParameterException("Value must be greater than 0: " + value);
		}
	}

	public static class DoubleGreaterOrEqualThanZero implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			double n = Double.parseDouble(value);
			if (n < 0)
				throw new ParameterException("Value must be greater or equal to 0.0: " + value);
		}
	}

	public static class NormalizedDouble implements IParameterValidator
	{

		@Override
		public void validate(String name, String value) throws ParameterException
		{
			double n = Double.parseDouble(value);
			if (n < 0.0 || n > 1.0)
				throw new ParameterException("Value must be in the range [0.0, 1.0]: " + value);
		}
	}

	public static class ULocaleValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try
			{
				Locale l = new Locale(value);
				ULocale u = ULocale.forLocale(l);
				if (u == null)
					throw new NullPointerException();
			}
			catch (Exception e)
			{
				throw new ParameterException("Unrecognized language code " + value);
			}
		}
	}

	public static class BiasTypeConverter implements IStringConverter<BiasFunction.Type>
	{
		@Override
		public BiasFunction.Type convert(String value)
		{
			return BiasFunction.Type.valueOf(value);
		}
	}

	public static class VectorTypeConverter implements IStringConverter<VectorType>
	{
		@Override
		public VectorType convert(String value)
		{
			return VectorType.valueOf(value);
		}
	}

	public static class SentenceVectorTypeConverter implements IStringConverter<SentenceVectorType>
	{
		@Override
		public SentenceVectorType convert(String value)
		{
			return SentenceVectorType.valueOf(value);
		}
	}

	public static class BiasTypeValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try{ BiasFunction.Type.valueOf(value); }
			catch (Exception e)
			{
				throw new ParameterException("Parameter " + name + " has invalid valued " + value);
			}
		}
	}

	public static class VectorTypeValidator implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			try{ VectorType.valueOf(value); }
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
			try{ SentenceVectorType.valueOf(value); }
			catch (Exception e)
			{
				throw new ParameterException("Parameter " + name + " has invalid valued " + value);
			}
		}
	}

}
