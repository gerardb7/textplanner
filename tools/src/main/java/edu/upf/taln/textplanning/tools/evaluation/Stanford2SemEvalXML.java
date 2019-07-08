package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.common.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class Stanford2SemEvalXML
{
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

	public String convert(Path input_folder, String text_suffix, Path outputFile) throws Exception
	{
		final File[] files = FileUtils.getFilesInFolder(input_folder, text_suffix);
		if (files == null)
			throw new Exception("Invalid input path " + input_folder);
		final Map<String, String> texts = Arrays.stream(files)
				.map(File::toPath)
				.collect(toMap(p -> p.getFileName().toString(), FileUtils::readTextFile));
		final StringWriter swriter = new StringWriter();
		BufferedWriter writer = new BufferedWriter(swriter);

		writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"); writer.newLine();
		writer.append("<corpus lang=\"en\">"); writer.newLine();
		int text_counter = 1;

		for (String filename : texts.keySet().stream().sorted().collect(Collectors.toList()))
		{
			try
			{
				final String text = texts.get(filename);
				final String text_id = String.format("d%03d", text_counter++);
				writer.append("<text id=\"").append(text_id).append("\" filename=\"").append(filename).append("\">");
				writer.newLine();

				log.info("Processing text " + (text_counter - 1));
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
								.append("\" lemma=\"").append(StringEscapeUtils.escapeXml(token.lemma()))
								.append("\" pos=\"").append(convertPOS(token.tag()))
								.append("\">").append(StringEscapeUtils.escapeXml(token.originalText()))
								.append("</wf>");
						writer.newLine();
					}
					writer.append("</sentence>");
					writer.newLine();
				}

				writer.append("</text>");
				writer.newLine();
			}
			catch(Exception e)
			{
				log.error("Failed to preprocess file " + filename + ": " + e);
				e.printStackTrace();
			}
		}
		writer.append("</corpus>"); writer.newLine();
		writer.flush();
		final String text = swriter.toString();
		FileUtils.writeTextToFile(outputFile, text);
		log.info(outputFile + " file created");

		return text;
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
		final Path docs_in = Paths.get("/home/gerard/ownCloud/Feina/tensor/tensor_evaluation/texts");
		final Path doc_out = Paths.get("/home/gerard/ownCloud/Feina/tensor/tensor_evaluation/input.xml");
		final String texts_suffix = "txt";
//		final Path docs_in = Paths.get("/home/gerard/ownCloud/varis_tesi/deep_mind_annotated/reference");
//		final Path doc_out = Paths.get("/home/gerard/ownCloud/varis_tesi/deep_mind_annotated/input_summaries.xml");
//		final String texts_suffix = "summ";
		final Stanford2SemEvalXML converter = new Stanford2SemEvalXML();
		converter.convert(docs_in, texts_suffix, doc_out);
	}
}
