package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.core.utils.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duc2002
{
	private static final String doc_extension = ".txt";
	private static final String summ_suffix = "_reference1.summ";
	private static final Locale language = Locale.US;
	private final static Logger log = LogManager.getLogger();

	public static void prepareDocs(Path input, Path output)
	{
		try
		{
			final Pattern id_pattern = Pattern.compile("<DOCNO>([^<]+)<\\/DOCNO>");
			final Pattern text_pattern = Pattern.compile("<TEXT>([^<]+)<\\/TEXT>");

			Files.walk(input)
					.filter(Files::isRegularFile)
//					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), ""))
					.forEach(f -> {
						try
						{
							final String contents = FileUtils.readTextFile(f);
							final String contents_f = contents.replace("<P>", "").replace("</P>", "");
							final String text = getMatch(contents_f, text_pattern).orElseThrow().trim();
							final String id = getMatch(contents_f, id_pattern).orElseThrow().trim().toLowerCase();
							final String group_id = FilenameUtils.getName(f.getParent().toString()).trim().toLowerCase();
							FileUtils.writeTextToFile(output.resolve(group_id + "-" + id + doc_extension), text);
						}
						catch (Exception e)
						{
							log.error("Failed to parse file " + f + ": " + e);
						}
					});
		}
		catch (IOException e)
		{
			log.error("Failed to prepare docs: " + e);
			e.printStackTrace();
		}
	}

	public static void prepareSummaries(Path input, Path output)
	{
		try
		{
			final Pattern p = Pattern.compile("(<SUM[^>]+>)([^<]+)</SUM>");
			final Pattern id_pattern = Pattern.compile("DOCREF=\"([^\"]+)\"");
			final Pattern group_pattern = Pattern.compile("DOCSET=\"([^\"]+)\"");
			final Pattern selector_pattern = Pattern.compile("SELECTOR=\"([^\"]+)\"");

			Files.walk(input)
					.filter(Files::isRegularFile)
					.filter(f -> f.getFileName().toString().equals("perdocs"))
					.forEach(f -> {
						try
						{
							final String contents = FileUtils.readTextFile(f);

							final Matcher matcher = p.matcher(contents);
							matcher.results()
									.forEach(r -> {
										final String header = r.group(1);
										final String id = getMatch(header, id_pattern).orElseThrow().trim().toLowerCase();
										final String group = getMatch(header, group_pattern).orElseThrow().trim().toLowerCase();
										final String selector = getMatch(header, selector_pattern).orElseThrow().trim().toLowerCase();
										final String text = r.group(2)
												.trim()
												.replace("<\\/?\\S+>", "")
												.replace("\n", "")
												.replace("\r", "");


										StringBuilder split = new StringBuilder();
										BreakIterator iterator = BreakIterator.getSentenceInstance(language);
										iterator.setText(text);
										int start = iterator.first();
										for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next())
										{
											split.append(text, start, end).append(System.getProperty("line.separator"));
										}

										FileUtils.writeTextToFile(output.resolve(group + selector + "-" + id + summ_suffix), split.toString());
									});
						}
						catch (Exception e)
						{
							log.error("Failed to parse file " + f + ": " + e);
						}
					});
		}
		catch (IOException e)
		{
			log.error("Failed to prepare docs: " + e);
			e.printStackTrace();
		}
	}

	private static Optional<String> getMatch(String text, Pattern p)
	{
		final Matcher m = p.matcher(text);
		final Optional<MatchResult> first = m.results().findFirst();
		final Optional<String> s = first.map(r -> r.group(1));
		return s;
	}

	public static void main(String[] args)
	{
		final Path docs_in = Paths.get("/home/gerard/ownCloud/varis_tesi/duc2002/docs");
		final Path docs_out = Paths.get("/home/gerard/ownCloud/varis_tesi/duc2002/texts");
		//Duc2002.prepareDocs(docs_in, docs_out);
		final Path summaries_in = Paths.get("/home/gerard/ownCloud/varis_tesi/duc2002/extracts_and_abstracts");
		final Path summaries_out = Paths.get("/home/gerard/ownCloud/varis_tesi/duc2002/reference3");
		Duc2002.prepareSummaries(summaries_in, summaries_out);
	}

}
