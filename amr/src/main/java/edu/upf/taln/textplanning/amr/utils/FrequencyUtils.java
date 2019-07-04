package edu.upf.taln.textplanning.amr.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.common.CMLCheckers;
import edu.upf.taln.textplanning.core.bias.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.core.bias.corpora.FreqsFile;
import edu.upf.taln.textplanning.common.Serializer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
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

import static java.util.stream.Collectors.*;

/**
 * Utils class for running tests with resources related to frequencies, such as the Solr index of SEW or freq files.
 */
public class FrequencyUtils
{
	private static final ULocale language = ULocale.ENGLISH;
	private final static Logger log = LogManager.getLogger();

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
		AMRGraphList graphs = (AMRGraphList) Serializer.deserialize(graphs_file);

		Map<String, Set<String>> forms = graphs.getCandidates().stream()
				.collect(groupingBy(c -> c.getMention().getSurfaceForm(),
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

//	private static void getStats(Path amr_bank, Path freqs_file, Path babel_config) throws IOException, ClassNotFoundException
//	{
//		AMRReader reader = new AMRReader();
//		AMRGraphListFactory factory = new AMRGraphListFactory(reader, language, null, babel_config, false, false);
//		String contents = FileUtils.readFileToString(amr_bank.toFile(), Charsets.UTF_8);
//		AMRGraphList graphs = factory.create(contents);
//
//		long num_tokens = graphs.getGraphs().stream()
//				.mapToLong(g ->
//				{
//					AMRAlignments a = g.getAlignments();
//					List<String> tokens = a.getTokens();
//					return tokens.size();
//				})
//				.sum();
//
//		long num_aligned_tokens = graphs.getGraphs().stream()
//				.mapToLong(g ->
//				{
//					AMRAlignments a = g.getAlignments();
//					List<String> tokens = a.getTokens();
//					return IntStream.range(0, tokens.size())
//							.mapToObj(a::getAlignedVertices)
//							.filter(l -> !l.isEmpty())
//							.count();
//				})
//				.sum();
//
//		long num_tokens_with_meaning = graphs.getGraphs().stream()
//				.mapToLong(g ->
//				{
//					AMRAlignments a = g.getAlignments();
//					List<String> tokens = a.getTokens();
//					return IntStream.range(0, tokens.size())
//							.mapToObj(a::getAlignedVertices)
//							.filter(l -> !l.isEmpty())
//							.flatMap(Set::stream)
//							.map(graphs::getCandidates)
//							.filter(l -> !l.isEmpty())
//							.count();
//				})
//				.sum();
//
//
//		Map<Meaning, Long> reference_freqs = graphs.getCandidates().stream()
//				.collect(groupingBy(Candidate::getMeaning, counting()));
//		Map<String, Long> form_freqs = graphs.getGraphs().stream()
//				.map(g -> g.getAlignments().getTokens())
//				.flatMap(List::stream)
//				.collect(groupingBy(s -> s, counting()));
//		Map<String, Long> lemma_freqs = graphs.getGraphs().stream()
//				.map(g ->
//				{
//					AMRAlignments a = g.getAlignments();
//					List<String> tokens = a.getTokens();
//					return IntStream.range(0, tokens.size())
//							.mapToObj(a::getLemma)
//							.collect(toList());
//				})
//				.flatMap(List::stream)
//				.collect(groupingBy(s -> s, counting()));
//
//		Set<String> references = graphs.getCandidates().stream()
//				.map(Candidate::getMeaning)
//				.map(Meaning::getReference)
//				.collect(Collectors.toSet());
//
//
//		CompactFrequencies corpus = (CompactFrequencies) Serializer.deserialize(freqs_file);
//		Map<String, OptionalInt> reference_idfs = references.stream()
//				.collect(toMap(s -> s, corpus::getMeaningDocumentCount));
//		long num_ref_in_corpus = reference_idfs.keySet().stream()
//				.filter(s -> reference_idfs.get(s).isPresent())
//				.count();
//		List<String> sorted_refs_idf = reference_idfs.keySet().stream()
//				.sorted(Comparator.comparingInt(s -> reference_idfs.get(s).orElse(-1)))
//				.collect(Collectors.toList());
//
//		final TFIDF tfidf = new TFIDF(graphs.getCandidates(), corpus);
//				Map<String, Double> sense_tf_idfs = references.stream()
//						.collect(toMap(s -> s, tfidf::weight));
//		List<String> sorted_refs_tf_idf = sense_tf_idfs.keySet().stream()
//				.sorted(Comparator.comparingDouble(sense_tf_idfs::get))
//				.collect(Collectors.toList());
//
//	}

	@Parameters(commandDescription = "Obtain frequencies from SEW")
	private static class GetFrequenciesCommand
	{
		@Parameter(names = {"-s", "-sew"}, description = "Folder containing SEW files", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path sewFolder;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output binary file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	@Parameters(commandDescription = "Create a subset of a JSON frequencies file based on a file containing graphs")
	private static class SubsetCommand
	{
		@Parameter(names = {"-g", "-graphsFile"}, description = "Graphs file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path graphs;
		@Parameter(names = {"-i", "-inputFile"}, description = "Input frequencies file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private Path inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output frequencies file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.ValidPathToFile.class)
		private Path outputFile;
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException
	{
		GetFrequenciesCommand freqs = new GetFrequenciesCommand();
		SubsetCommand subset = new SubsetCommand();

		JCommander jc = new JCommander();
		jc.addCommand("freqs", freqs);
		jc.addCommand("subset", subset);
		jc.parse(args);

		switch (jc.getParsedCommand())
		{
			case "freqs":
				FrequencyUtils.getFrequenciesFromSEW(freqs.sewFolder, freqs.outputFile);
				break;
			case "subset":
				FrequencyUtils.getFrequenciesSubset(subset.graphs, subset.inputFile, subset.outputFile);
				break;
		}
	}
}
