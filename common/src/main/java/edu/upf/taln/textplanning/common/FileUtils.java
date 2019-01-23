package edu.upf.taln.textplanning.common;

import com.google.common.base.Charsets;
import edu.upf.taln.textplanning.core.structures.Candidate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class FileUtils
{
	private final static Logger log = LogManager.getLogger();

	public static File[] getFilesInFolder(Path input_folder, String suffix)
	{
		final File[] text_files = input_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(suffix));
		if (text_files == null)
		{
			log.error("Failed to find in any text files in " + input_folder);
			return null;
		}

		return text_files;
	}

	public static String readTextFile(Path file)
	{
		try
		{
			return org.apache.commons.io.FileUtils.readFileToString(file.toFile(), Charsets.UTF_8);
		}
		catch (IOException e)
		{
			log.error("Cannot read file " + file + ": " + e);
			return null;
		}
	}

	public static void writeTextToFile(Path file, String text)
	{
		try
		{
			org.apache.commons.io.FileUtils.writeStringToFile(file.toFile(), text, Charsets.UTF_8);
		}
		catch (IOException e)
		{
			log.error("Cannot write to file " + file + ": " + e);
		}
	}

	public static void appendTextToFile(Path file, String text)
	{
		try
		{
			org.apache.commons.io.FileUtils.writeStringToFile(file.toFile(), text, Charsets.UTF_8, true);
		}
		catch (IOException e)
		{
			log.error("Cannot write to file " + file + ": " + e);
		}
	}

	public static Path createOutputPath(Path input_file, Path output_folder, String old_suffix, String new_suffix)
	{
		try
		{
			if (!Files.exists(output_folder))
			{
				Files.createDirectories(output_folder);
			}

			final String basename = input_file.getFileName().toString();
			final String out_filename = basename.substring(0, basename.length() - old_suffix.length()) + new_suffix;

			return output_folder.resolve(out_filename);
		}
		catch (IOException e)
		{
			log.error("Cannote create output path: " + e);
			return null;
		}
	}

	public static void serializeMeanings(List<List<Set<Candidate>>> candidates, Path out_file)
	{
		try
		{
			Serializer.serialize(candidates, out_file);
		}
		catch (Exception e)
		{
			log.error("Cannot store meanings in file " + out_file);
		}
	}

	public static List<List<Set<Candidate>>> deserializeMeanings(Path file)
	{
		try
		{
			return (List<List<Set<Candidate>>>)Serializer.deserialize(file);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot load meanings from file " + file + ": " + e);
		}
	}
}
