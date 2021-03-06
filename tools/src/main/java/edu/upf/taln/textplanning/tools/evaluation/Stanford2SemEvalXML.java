package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.common.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static java.util.stream.Collectors.toList;

public class Stanford2SemEvalXML
{
	private static final String text_suffix = ".story";
	private final StanfordCoreNLP pipeline;
	private final static Logger log = LogManager.getLogger();

	public Stanford2SemEvalXML()
	{
		log.info("Setting up Stanford CoreNLP");
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma");

		Stopwatch timer = Stopwatch.createStarted();
		RedwoodConfiguration.current().clear().apply(); // shut up, CoreNLP
		pipeline = new StanfordCoreNLP(props);
		log.info("CoreNLP pipeline created in " + timer.stop());
	}

	public void convert(Path input_folder, Path outputFile) throws Exception
	{
		final File[] files = FileUtils.getFilesInFolder(input_folder, text_suffix);
		if (files == null)
			throw new Exception("Invalid input path " + input_folder);
		final List<String> texts = Arrays.stream(files)
				.map(File::toPath)
				.map(FileUtils::readTextFile)
				.collect(toList());
		final StringWriter swriter = new StringWriter();
		BufferedWriter writer = new BufferedWriter(swriter);

		writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"); writer.newLine();
		writer.append("<corpus lang=\"en\">"); writer.newLine();
		int text_counter = 1;

		for (String text : texts)
		{
			final String text_id = String.format("d%03d", text_counter++);
			writer.append("<text id=\"d").append(text_id).append("\">");
			writer.newLine();

			log.info("Processing text " + (text_counter-1));
			CoreDocument document = new CoreDocument(text);
			pipeline.annotate(document);
			int sentence_counter = 1;

			for (CoreSentence sentence : document.sentences())
			{
				final String sentence_id = text_id + "." + String.format("s%03d", sentence_counter++);
				writer.append("<sentence id=\"").append(sentence_id).append("\">");
				writer.newLine();
				int token_counter = 1;

				for (CoreLabel token : sentence.tokens())
				{
					final String token_id = sentence_id + "." + String.format("t%03d", token_counter++);
					writer.append("<wf id=\"").append(token_id)
							.append("\" lemma=\"").append(token.lemma())
							.append("\" pos=\"").append(convertPOS(token.tag()))
							.append("\">").append(token.originalText())
							.append("</wf>");
					writer.newLine();
				}
				writer.append("</sentence>");
				writer.newLine();
			}

			writer.append("</text>");
			writer.newLine();
		}
		writer.append("</corpus>"); writer.newLine();
		writer.flush();
		FileUtils.writeTextToFile(outputFile, swriter.toString());
		log.info(outputFile + " file created");
	}

	private static String convertPOS(String tag)
	{
		if (tag.startsWith("N")) return "N";
		else if (tag.startsWith("V")) return "V";
		else if	(tag.startsWith("J")) return "J";
		else if	(tag.startsWith("R")) return "R";
		else return "X";
	}

	public static void main(String[] args) throws Exception
	{
		final Stanford2SemEvalXML converter = new Stanford2SemEvalXML();
		converter.convert(Paths.get(args[0]), Paths.get(args[1]));
	}
}
