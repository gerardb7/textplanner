package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

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
}
