package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.uima.io.TextParser;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

public class DeepMind
{
	private static final String document_extension = ".story";
	private static final String summary_extension = ".summ";

	public static class DeepMindSourceParser extends TextParser
	{
		@Override
		protected String parse(String file_contents)
		{
			final String[] parts = file_contents.split("@highlight");
			if (parts.length == 0)
				return "";
			return Arrays.stream(parts[0].split("[\\r\\n]+"))
					.filter(not(String::isEmpty))
					.collect(joining(System.getProperty("line.separator")));
		}
	}

	public static class DeepMindSummaryParser extends TextParser
	{
		@Override
		protected String parse(String file_contents)
		{
			final String[] parts = file_contents.split("^\\s*@highlight\\s*$");
			if (parts.length < 2)
				return "";

			return Arrays.stream(parts, 1, parts.length)
					.filter(not(String::isEmpty))
					.map(t -> Arrays.stream(t.split("[\\r\\n]+"))
							.filter(not(String::isEmpty))
							.collect(joining(System.getProperty("line.separator"))))
					.collect(joining(System.getProperty("line.separator")));
		}
	}

	public static void split(Path input_folder, Path docs_folder, Path summaries_folder)
	{
		Arrays.stream(Objects.requireNonNull(getFilesInFolder(input_folder, document_extension)))
				.forEach(f -> {
					final String contents = FileUtils.readTextFile(f.toPath());
					final String[] parts = contents.split("@highlight");
					FileUtils.writeTextToFile(docs_folder.resolve(f.toPath().getFileName()), parts[0]);
					final String summary = Arrays.stream(parts, 1, parts.length)
							.collect(joining());

					final String summary_filename = FilenameUtils.removeExtension(f.getName()) + summary_extension;
					FileUtils.writeTextToFile(summaries_folder.resolve(Paths.get(summary_filename)), summary);
				});
	}

	public static void main(String[] args)
	{
		final Path input = Paths.get("/home/gerard/ownCloud/varis_tesi/deep_mind_subset_100/texts/original");
		final Path stories = Paths.get("/home/gerard/ownCloud/varis_tesi/deep_mind_subset_100/texts/stories");
		final Path summaries = Paths.get("/home/gerard/ownCloud/varis_tesi/deep_mind_subset_100/reference");
		DeepMind.split(input, stories, summaries);
	}
}
