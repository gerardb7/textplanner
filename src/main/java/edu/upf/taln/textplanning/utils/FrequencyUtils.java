package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.amr.GraphList;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathConverter;
import edu.upf.taln.textplanning.weighting.TFIDF;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * Utils class for running tests with resources related to frequencies, such as the Solr index of SEW or freq files.
 */
public class FrequencyUtils
{
	private final static Logger log = LogManager.getLogger(FrequencyUtils.class);


	private static void getFrequenciesFromSEW(Path sew_folder, Path freqs_file)
	{
		Stopwatch timer = Stopwatch.createStarted();
		log.info("Loading file list");
		Collection<File> files = FileUtils.listFiles(
				sew_folder.toFile(),
				new SuffixFileFilter(".xml"),
				DirectoryFileFilter.INSTANCE
		);
		log.info("File list loaded in " + timer);

		log.info("Processing " + files.size() + " files");
		final String regex = "<annotation>\\s+<babelNetID>(bn:\\d+.)<\\/babelNetID>\\s+<mention>([^<]*)<\\/mention>";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher("");

		int num_file_processed = 0;
		Map<String, Integer> total_meaning_counts = new HashMap<>();
		Map<String, Integer> partial_meaning_counts = new HashMap<>();
		Map<String, Integer> doc_meaning_counts = new HashMap<>();
		Map<String, Map<String, Integer>> total_form_counts = new HashMap<>();

		List<String> failed_files = new ArrayList<>();
		for (File file : files)
		{
			++num_file_processed;

			try
			{
				partial_meaning_counts.clear();
				String contents = FileUtils.readFileToString(file, Charsets.UTF_8);
				matcher.reset(contents);

				while (matcher.find())
				{
					String synset = matcher.group(1);
					partial_meaning_counts.merge(synset, 1, (v1, v2) -> v1 + v2);

					String mention = matcher.group(2);
					total_form_counts.putIfAbsent(mention, new HashMap<>());
					Map<String, Integer> mention_map = total_form_counts.get(mention);
					mention_map.merge(synset, 1, (v1, v2) -> v1 + v2);
				}

				partial_meaning_counts.forEach((key, value) ->
				{
					total_meaning_counts.merge(key, value, (v1, v2) -> v1 + v2);
					doc_meaning_counts.merge(key, 1, (v1, v2) -> v1 + v2); // one more doc
				});

				if (num_file_processed % 1000 == 0)
				{
					log.info(num_file_processed + " files processed out of " + files.size() + "(" + timer + ")"); // should be 4.320.838
				}
			}
			catch (IOException e)
			{
				log.error("Cannot process file " + file.getName());
				failed_files.add(file.getName());
			}
		}

		log.info("Files pocessed in "  + timer + ". Failed files: " + failed_files.toString());

		log.info("Compacting frequencies");
		CompactFrequencies freqs = new CompactFrequencies(files.size(), total_meaning_counts, doc_meaning_counts, total_form_counts);
		log.info("Frequencies compacted in " + timer);

		log.info("Storing frequencies into file");
		try
		{
			Serializer.serialize(freqs, freqs_file);

			log.info("All completed in " + timer.stop());
		}
		catch (Exception e)
		{
			log.error("Serialization failed");
		}

		// Do some checks
		try
		{
			final CompactFrequencies freqs2 = (CompactFrequencies)Serializer.deserialize(freqs_file);
			total_meaning_counts.forEach((m,c) ->
			{
				int c2 = freqs2.getMeaningCount(m).orElse(-1);
				if (c2 !=  c)
					log.error("Bad total count for " + m);

				int dc = doc_meaning_counts.get(m);
				int dc2 = freqs2.getMeaningDocumentCount(m).orElse(-1);
				if (dc2 != dc)
					log.error("Bad document count for " + m);
			});

			total_form_counts.forEach((f,l) ->
					l.forEach((m,c) ->
					{
						int c2 = freqs2.getFormMeaningCount(f, m).orElse(-1);
						if (c2 != c)
							log.error("Bad form-meaning count for form " +  f + " and meaning " + m);
					}));
		}
		catch (Exception e)
		{
			log.error("Deserialization failed: " + e);
		}
	}

	/**
	 */
	private static void getFrequenciesSubset(Path graphs_file, Path inputFile, Path outputFile) throws IOException, ClassNotFoundException
	{
		log.info("Reading graphs");
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		Map<String, Set<String>> forms = graphs.getCandidates().stream()
				.collect(groupingBy(c -> c.getMention().getSurface_form(),
									mapping(c -> c.getMeaning().getReference(), toSet())));
		long num_pairs = forms.values().stream()
				.mapToLong(Set::size)
				.sum();
		Set<String> meanings = forms.values().stream()
				.flatMap(Set::stream)
				.distinct()
				.collect(toSet());

		log.info("Reading frequencies file");
		FreqsFile freqsFile = new FreqsFile(inputFile);

		long num_pairs_in_corpus = forms.keySet().stream()
				.mapToLong(f -> forms.get(f).stream()
						.map(m -> freqsFile.getFormMeaningCount(f, m))
						.filter(OptionalInt::isPresent)
						.count())
				.sum();

		Map<String, Map<String, Integer>> formCounts =
				forms.keySet().stream()
				.collect(toMap(f -> f, f -> forms.get(f).stream()
						.collect(toMap( m -> m,
										m -> freqsFile.getFormMeaningCount(f, m).orElse(0)))));

		Map<String, Integer> meaningCounts = meanings.stream()
				.collect(toMap(m -> m, m -> freqsFile.getMeaningCount(m).orElse(0)));

		long num_meanings_in_corpus = meanings.stream()
				.map(freqsFile::getMeaningCount)
				.filter(OptionalInt::isPresent)
				.count();

		log.info("AMR bank has " + meanings.size() + " meanings and " + num_pairs + " form-meaning pairs.");
		log.info("Corpus has counts for " + num_meanings_in_corpus + " meanings and " + num_pairs_in_corpus + " form-meaning pairs.");

		// @todo rewrite using GJson
		JSONObject top = new JSONObject();
		top.put("docs", freqsFile.getNumDocs());
		top.put("meanings", meaningCounts);

		JSONObject jForms = new JSONObject();
		for (String form : formCounts.keySet())
		{
			Map<String, Integer> counts = formCounts.get(form);
			JSONArray jCounts = new JSONArray();

			for (String meaning : counts.keySet())
			{
				Integer count = counts.get(meaning);
				JSONArray jCount = new JSONArray();
				jCount.put(meaning);
				jCount.put(count);
				jCounts.put(jCount);
			}

			jForms.put(form, jCounts);
		}

		top.put("mentions", jForms);
		log.info("Done.");

		FileUtils.writeStringToFile(outputFile.toFile(), top.toString(), StandardCharsets.UTF_8);
		log.info("Json written to file" + outputFile);
	}

	private static void compactJSONFreqs(Path json_file, Path binary_file) throws IOException
	{
		Stopwatch timer = Stopwatch.createStarted();

		String json = FileUtils.readFileToString(json_file.toFile(), StandardCharsets.UTF_8);
		JSONObject obj = new JSONObject(json);
		//int numDocs = (int) obj.getLong("docs");

		Map<String, Pair<Integer, Integer>> sense_counts = new HashMap<>();
		JSONObject meaning_counts = obj.getJSONObject("meanings");
		for (String id : meaning_counts.keySet())
		{
			int count = (int) meaning_counts.getLong(id);
			sense_counts.put(id, Pair.of(0, count));
		}

		JSONObject form_counts = obj.getJSONObject("mentions");

		Map<String, Map<String, Integer>> form_sense_counts = new HashMap<>();
		for (String form : form_counts.keySet())
		{
			JSONArray arr = form_counts.getJSONArray(form);
			Map<String, Integer> counts = new HashMap<>();

			for (int i = 0; i < arr.length(); ++i)
			{
				JSONArray form_count = arr.getJSONArray(i);
				String key = form_count.getString(0);
				int value = (int) form_count.getLong(1);
				counts.put(key, value);
				Pair<Integer, Integer> ci = sense_counts.get(key);
				sense_counts.put(key, Pair.of(ci.getLeft() + 1, ci.getRight()));
			}
			form_sense_counts.put(form, counts);
		}

		log.info(   "Loaded " + sense_counts.size() + " meanings and " + form_sense_counts.size() +
				" forms from frequencies file in " + timer.stop());

		//CompactFrequencies freqs = new CompactFrequencies(numDocs, sense_counts, form_sense_counts);
		//Serializer.serialize(freqs, binary_file);

		log.info("Done");
	}

	private static void getStats(Path amr_bank, Path freqs_file, Path vectors_file) throws IOException, ClassNotFoundException
	{
		AMRReader reader = new AMRReader();
		GraphListFactory factory = new GraphListFactory(reader, null);
		String contents = FileUtils.readFileToString(amr_bank.toFile(), Charsets.UTF_8);
		GraphList graphs = factory.getGraphs(contents);

		long num_tokens = graphs.getGraphs().stream()
				.mapToLong(g ->
				{
					GraphAlignments a = g.getAlignments();
					List<String> tokens = a.getTokens();
					return tokens.size();
				})
				.sum();

		long num_aligned_tokens = graphs.getGraphs().stream()
				.mapToLong(g ->
				{
					GraphAlignments a = g.getAlignments();
					List<String> tokens = a.getTokens();
					return IntStream.range(0, tokens.size())
							.mapToObj(a::getVertices)
							.filter(l -> !l.isEmpty())
							.count();
				})
				.sum();

		long num_tokens_with_meaning = graphs.getGraphs().stream()
				.mapToLong(g ->
				{
					GraphAlignments a = g.getAlignments();
					List<String> tokens = a.getTokens();
					return IntStream.range(0, tokens.size())
							.mapToObj(a::getVertices)
							.filter(l -> !l.isEmpty())
							.flatMap(Set::stream)
							.map(graphs::getCandidates)
							.filter(l -> !l.isEmpty())
							.count();
				})
				.sum();


		Map<Meaning, Long> sense_freqs = graphs.getCandidates().stream()
				.collect(groupingBy(Candidate::getMeaning, counting()));
		Map<String, Long> form_freqs = graphs.getGraphs().stream()
				.map(g -> g.getAlignments().getTokens())
				.flatMap(List::stream)
				.collect(groupingBy(s -> s, counting()));
		Map<String, Long> lemma_freqs = graphs.getGraphs().stream()
				.map(g ->
				{
					GraphAlignments a = g.getAlignments();
					List<String> tokens = a.getTokens();
					return IntStream.range(0, tokens.size())
							.mapToObj(a::getLemma)
							.collect(toList());
				})
				.flatMap(List::stream)
				.collect(groupingBy(s -> s, counting()));

		Set<String> senses = graphs.getCandidates().stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toSet());


		CompactFrequencies corpus = (CompactFrequencies) Serializer.deserialize(freqs_file);
		Map<String, OptionalInt> sense_idfs = senses.stream()
				.collect(toMap(s -> s, corpus::getMeaningDocumentCount));
		long num_sense_in_corpus = sense_idfs.keySet().stream()
				.filter(s -> sense_idfs.get(s).isPresent())
				.count();
		List<String> sorted_senses_idf = sense_idfs.keySet().stream()
				.sorted(Comparator.comparingInt(s -> sense_idfs.get(s).orElse(-1)))
				.collect(Collectors.toList());

		final TFIDF tfidf = new TFIDF(corpus, false);
		tfidf.setContents(graphs);
		Map<String, Double> sense_tf_idfs = senses.stream()
				.collect(toMap(s -> s, tfidf::weight));
		List<String> sorted_senses_tf_idf = sense_tf_idfs.keySet().stream()
				.sorted(Comparator.comparingDouble(sense_tf_idfs::get))
				.collect(Collectors.toList());

	}

	@Parameters(commandDescription = "Obtain frequencies from SEW")
	private static class GetFrequenciesCommand
	{
		@Parameter(names = {"-s", "-sew"}, description = "Folder containing SEW files", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path sewFolder;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output binary file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Create a subset of a JSON frequencies file based on a file containing graphs")
	private static class SubsetCommand
	{
		@Parameter(names = {"-g", "-graphsFile"}, description = "Graphs file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path graphs;
		@Parameter(names = {"-i", "-inputFile"}, description = "Input frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Convert a JSON frequencies file to a compact binary file")
	private static class ConvertCommand
	{
		@Parameter(names = {"-i", "-inputFile"}, description = "Input JSON file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output binary file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException
	{
		GetFrequenciesCommand freqs = new GetFrequenciesCommand();
		SubsetCommand subset = new SubsetCommand();
		ConvertCommand convert = new ConvertCommand();

		JCommander jc = new JCommander();
		jc.addCommand("freqs", freqs);
		jc.addCommand("subset", subset);
        jc.addCommand("convert", convert);
		jc.parse(args);

		switch (jc.getParsedCommand())
		{
			case "freqs":
				FrequencyUtils.getFrequenciesFromSEW(freqs.sewFolder, freqs.outputFile);
				break;
			case "subset":
				FrequencyUtils.getFrequenciesSubset(subset.graphs, subset.inputFile, subset.outputFile);
				break;
			case "convert":
				FrequencyUtils.compactJSONFreqs(convert.inputFile, convert.outputFile);
				break;
		}
	}
}
