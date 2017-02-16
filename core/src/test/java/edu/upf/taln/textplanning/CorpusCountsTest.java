package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CorpusCounts;
import edu.upf.taln.textplanning.corpora.MSSparqlCounts;
import edu.upf.taln.textplanning.corpora.SolrCounts;
import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.input.DocumentAccess;
import edu.upf.taln.textplanning.input.NIFAcces;
import edu.upf.taln.textplanning.input.SPARQLQueries;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Test for metrics based on solr index of SEW and MULTISENSOR RDF repository
 */
public class CorpusCountsTest
{
	private static final String sparqlURL = "http://multisensor.ontotext.com/repositories/ms-final-prototype";
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewAnnSen";
	private static final ValueFactory factory = SimpleValueFactory.getInstance();
	private static final Logger log = LoggerFactory.getLogger(CorpusCountsTest.class);

	private final List<String> docsUC1 = Arrays.asList(
			"6eb17f86bb8eb8fd817ba07422d0a68856fd7c89",
			"2fc7f09fbda3c9816324efbe5925eca45bb4951d",
			"ab29994ce7765be7809c6e5f26993e5cf7b13a7e",
			"8dbc56d292097a4a709ae5a5e854db88f6d6a727",
			"3810ac14c49431a1657d17804e7b2fbdcb56f7d9");
	private final List<String> docsUC2 = Arrays.asList(
			"3d4d2124fb35c4e622c90a386acca1a9c3b8b02b",
			"32a16e41d34e4e8c431134ce7192379bc9a96ee9",
			"980b04594e431317fd16752e372a0c72a606b618",
			"fdae345974ddfc24ee952afcfef7b32614b928c9",
			"5c510c5281d0f2222c2f258037d0955818227edf");


	private void createConll(String inDocument)
	{
		try (PrintWriter out = new PrintWriter("src/test/resources/" + inDocument + ".conll"))
		{
			IRI documentIRI = factory.createIRI("<http://data.multisensorproject.eu/content/" + inDocument + ">");
			Model model = SPARQLQueries.getRepoGraph(new HTTPRepository(sparqlURL), documentIRI.toString());
			StringWriter writer = new StringWriter();
			Rio.write(model, writer, RDFFormat.RDFXML);
			String rdfDoc = writer.toString();

			NIFAcces reader = new NIFAcces(RDFFormat.RDFXML);
			List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> graphs = reader.readSemanticDAGs(rdfDoc);
			String conll = ConLLAcces.write(graphs);
			out.print(conll);
		}
		catch (Exception e)
		{
			log.error("Cannot create conll for document " + inDocument);
		}
	}

	private static List<List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>>>
	readTreesFromFiles(List<String> inDocs)
	{
		ConLLAcces reader = new ConLLAcces();
		return inDocs.stream()
				.map(d -> "src/test/resources/" + d + ".conll")
				.map(reader::readSemanticDAGs)
				.collect(Collectors.toList());
	}

	private static Set<String> getReferences(List<List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>>> inGraphs) throws Exception
	{
		return inGraphs.stream()
				.map(d -> d.stream()
						.map(g -> g.vertexSet().stream()
								.filter(v -> v.getReference() != null)
								.map(AnnotationInfo::getReference)
								.filter(r -> r.endsWith("n")) // nouns only
								.collect(Collectors.toSet()))
						.flatMap(Set::stream)
						.limit(1) // Only first 5 entities of each document
						.collect(Collectors.toSet()))
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
	}

	@Test
	public void testGetCounts() throws Exception
	{
//		docsUC1.forEach(this::createConll);
//		docsUC2.forEach(this::createConll);

		log.info("Reading conll files");
		List<List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>>> treesUC1 = readTreesFromFiles(docsUC1);
		List<List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>>> treesUC2 = readTreesFromFiles(docsUC2);

		log.info("Getting references");
		Set<String> refsUC1 = getReferences(treesUC1);
		log.info("got " + refsUC1.size() + " refs from UC1 docs");
		Set<String> refsUC2 = getReferences(treesUC2);
		log.info("got " + refsUC2.size() + " refs from UC2 docs");

		// Now get counts from Solr index
		log.info("Getting Solr counts");
		Stopwatch timer = Stopwatch.createStarted();
		SolrCounts solrCounts = new SolrCounts(solrUrl);

		// Java 8 ugliness (pseudo-currying)
		BiFunction<String, String, BiFunction<CorpusCounts, String, CorpusCounts.Counts>> safeCounts = (e1, e2) -> (corpus, domain) -> {
			try
			{
				return corpus.getCounts(e1, e2, domain);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		};

		// Get counts for each pair of references in refsUC1
		List<CorpusCounts.Counts> solrCountsUC1 = refsUC1.stream()
				.map(r1 ->
						refsUC1.stream()
								.filter(r2 -> !r1.equals(r2)) // do not search for pairs of the same entity
								.peek(r2 -> log.info("Getting count for " + r1 + " and " + r2))
								.map(r2 -> safeCounts.apply(r1, r2).apply(solrCounts, "UC1 - Energy Policy"))
								.peek(r2 -> log.info("done"))
								.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.toList());

		// Same thing for pairs in refsUC2
		List<CorpusCounts.Counts> solrCountsUC2 = refsUC2.stream()
				.map(r1 ->
						refsUC1.stream()
								.filter(r2 -> !r1.equals(r2)) // do not search for pairs of the same entity
								.peek(r2 -> log.info("Getting count for " + r1 + " and " + r2))
								.map(r2 -> safeCounts.apply(r1, r2).apply(solrCounts, "UC1 - Household Appliances"))
								.peek(r2 -> log.info("done"))
								.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.toList());

		log.info("Ran " + (solrCountsUC1.size() + solrCountsUC2.size()) * 2 + " solr queries");
		log.info("Queries took " + timer.stop());
		log.info("\n\n");

		//-------------------------------------------------------------------------------------------------------

		// Get counts from SPARQL endpoint
		log.info("Getting SPARQL counts");
		timer.reset();
		timer.start();
		MSSparqlCounts msCounts = new MSSparqlCounts(sparqlURL);

		// Get counts for each pair of references in refsUC1
		List<CorpusCounts.Counts> sparqlCountsUC1 = refsUC1.stream()
				.map(r1 ->
						refsUC1.stream()
								.filter(r2 -> !r1.equals(r2)) // do not search for pairs of the same entity
								.peek(r2 -> log.info("Getting count for " + r1 + " and " + r2))
								.map(r2 -> safeCounts.apply(r1, r2).apply(msCounts, "UC1 - Energy Policy"))
								.peek(r2 -> log.info("done"))
								.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.toList());

		// Same thing for pairs in refsUC2
		List<CorpusCounts.Counts> sparqlCountsUC2 = refsUC2.stream()
				.map(r1 ->
						refsUC1.stream()
								.filter(r2 -> !r1.equals(r2)) // do not search for pairs of the same entity
								.peek(r2 -> log.info("Getting count for " + r1 + " and " + r2))
								.map(r2 -> safeCounts.apply(r1, r2).apply(msCounts, "UC1 - Household Appliances"))
								.peek(r2 -> log.info("done"))
								.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.toList());
		log.info("Ran " + (solrCountsUC1.size() + solrCountsUC2.size()) * 2 + " sparql queries");
		log.info("Queries took " + timer.stop());
		log.info("\n\n");

		log.info("***UC1***");
		log.info("Solr counts: ");
		for (CorpusCounts.Counts solrCount : solrCountsUC1)
		{
			log.info(solrCount.toString());
		}
		log.info("***");
		log.info("SPARQL counts: ");
		for (CorpusCounts.Counts sparqlCount : sparqlCountsUC1)
		{
			log.info(sparqlCount.toString());
		}
		log.info("***UC2***");
		log.info("Solr counts: ");
		for (CorpusCounts.Counts solrCount : solrCountsUC2)
		{
			log.info(solrCount.toString());
		}
		log.info("***");
		log.info("SPARQL counts: ");
		for (CorpusCounts.Counts sparqlCount : sparqlCountsUC2)
		{
			log.info(sparqlCount.toString());
		}

		OptionalDouble avgCooccurSolrUC1 = solrCountsUC1.stream()
				.mapToLong(c -> c.cooccur)
				.average();
		OptionalDouble avgFreqSolrUC1 = solrCountsUC1.stream()
				.mapToLong(c -> c.freq)
				.average();
		OptionalDouble avgCooccurSparqlUC1 = sparqlCountsUC1.stream()
				.mapToLong(c -> c.cooccur)
				.average();
		OptionalDouble avgFreqSparqlLUC1 = sparqlCountsUC1.stream()
				.mapToLong(c -> c.freq)
				.average();
		OptionalDouble avgCooccurSolrUC2 = solrCountsUC2.stream()
				.mapToLong(c -> c.cooccur)
				.average();
		OptionalDouble avgFreqSolrUC2 = solrCountsUC2.stream()
				.mapToLong(c -> c.freq)
				.average();
		OptionalDouble avgCooccurSparqlUC2 = sparqlCountsUC2.stream()
				.mapToLong(c -> c.cooccur)
				.average();
		OptionalDouble avgFreqSparqlLUC2 = sparqlCountsUC2.stream()
				.mapToLong(c -> c.freq)
				.average();

		log.info("***UC1***");
		log.info("Solr produced " + solrCountsUC1.size() + " counts");
		log.info("Average freq solr = " + avgFreqSolrUC1.getAsDouble() + " cooccur solr = " +
				avgCooccurSolrUC1.getAsDouble());
		log.info("Sparql produced " + sparqlCountsUC1.size() + " counts");
		log.info("Average freq sparql = " + avgFreqSparqlLUC1.getAsDouble() + " cooccur sparql = " +
				avgCooccurSparqlUC1.getAsDouble());

		log.info("***UC2***");
		log.info("Solr produced " + solrCountsUC2.size() + " counts");
		log.info("Average freq solr = " + avgFreqSolrUC2.getAsDouble() + " cooccur solr = " +
				avgCooccurSolrUC2.getAsDouble());
		log.info("Sparql produced " + sparqlCountsUC2.size() + " counts");
		log.info("Average freq sparql = " + avgFreqSparqlLUC2.getAsDouble() + " cooccur sparql = " +
				avgCooccurSparqlUC2.getAsDouble());
	}
}