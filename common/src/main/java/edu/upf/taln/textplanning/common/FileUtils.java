package edu.upf.taln.textplanning.common;

import com.google.common.base.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils
{
	private final static Logger log = LogManager.getLogger();

	public static File[] getFilesInFolder(Path input_folder, String suffix)
	{
		final File[] text_files = input_folder.toFile().listFiles(pathname -> pathname.getName().toLowerCase().endsWith(suffix));
		if (text_files == null)
		{
			log.error("Failed to find in any text files in " + input_folder);
			System.exit(1);
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
			return "";
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
			org.apache.commons.io.FileUtils.writeStringToFile(file.toFile(), text, Charsets.UTF_8,true);
		}
		catch (IOException e)
		{
			log.error("Cannot write to file " + file + ": " + e);
		}
	}

	public static Path createOutputPath(Path input_file, Path output_folder, String old_suffix, String new_suffix) throws IOException
	{
		if (!Files.exists(output_folder))
			Files.createDirectories(output_folder);

		final String basename = input_file.getFileName().toString();
		final String out_filename = basename.substring(0, basename.length() - old_suffix.length()) + new_suffix;

		return output_folder.resolve(out_filename);
	}
}
