package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

public class StopWordsFilter  implements Predicate<Mention>
{
	private final List<String> stop_words;

	public StopWordsFilter(Path file) throws IOException
	{
		final String contents = FileUtils.readFileToString(file.toFile(), UTF_8);
		stop_words = Arrays.asList(contents.split("\\r?\\n"));
	}

	@Override
	public boolean test(Mention mention)
	{
		return !stop_words.contains(mention.getSurface_form());
	}
}
