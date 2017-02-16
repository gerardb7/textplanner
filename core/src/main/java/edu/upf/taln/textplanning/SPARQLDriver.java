package edu.upf.taln.textplanning;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.SolrCounts;
import edu.upf.taln.textplanning.input.DocumentProvider;
import edu.upf.taln.textplanning.input.SPARQLQueries;
import edu.upf.taln.textplanning.input.SingleDocProvider;
import edu.upf.taln.textplanning.similarity.TreeEditSimilarity;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Executes the text planner from a nif-annotated document stored in an RDF SPARQL endpoint.
 */
public class SPARQLDriver
{
	private final SPARQLRepository repo;
	private final TextPlanner planner;
	private final static Logger log = LoggerFactory.getLogger(SPARQLDriver.class);


	SPARQLDriver(String inSparqlUrl, String inSolrUrl, Path inWord2VecPath, Path inSenseEmbedPath) throws Exception
	{
		repo = new SPARQLRepository(inSparqlUrl);
		repo.initialize();
		planner = TextPlanner.createNIFPlanner(inSolrUrl, inWord2VecPath, inSenseEmbedPath);
	}

	public String runPlanner(String inGraph, Set<String> inReferences, TextPlanner.Options inOptions)
	{
		Model model = SPARQLQueries.getRepoGraph(repo, inGraph);
		StringWriter writer = new StringWriter();
		Rio.write(model, writer, RDFFormat.RDFXML);
		String rdfDoc = writer.toString();
		DocumentProvider provider = new SingleDocProvider(rdfDoc);

		if (inReferences.isEmpty())
		{
			return planner.planText(provider, inOptions);
		}
		else
		{
			return planner.planText(inReferences, provider, inOptions);
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 5)
		{
			System.err.println("Wrong number of parameters. Usage: TextPlanner sparql_url index_url Word2Vec_path SenseEmbed_path doc_id [references]");
			System.exit(-1);
		}

		String sparqlUrl = args[0];
		String solrUrl = args[1];
		Path word2VecPath = Paths.get(args[2]);
		Path senseEmbedPath = Paths.get(args[3]);
		String doc = args[4];

		Set<String> refs = new HashSet<>();
		refs.addAll(Arrays.asList(args).subList(4, args.length));

		SPARQLDriver driver = new SPARQLDriver(sparqlUrl, solrUrl, word2VecPath, senseEmbedPath);
		TextPlanner.Options options = new TextPlanner.Options();

		log.info("Planning from file " + doc + " and refs " + refs);
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			String planConll = driver.runPlanner(doc, refs, options);

			log.info("Text planning took " + timer.stop());
			log.info("Solr queries: " + SolrCounts.debug.toString());
			log.info("Word form vector lookups: " + TreeEditSimilarity.numWordSuccessfulLookups + " successful, " +
					TreeEditSimilarity.numWordFailedLookups + " failed");
			log.info("Word sense vector lookups: " + TreeEditSimilarity.numSenseSuccessfulLookups + " successful, " +
					TreeEditSimilarity.numSenseFailedLookups + " failed");
			log.info("********************************************************");
			SolrCounts.debug.reset();

			System.out.print(planConll);

			if (options.generateStats)
			{
				String statsFile = System.getProperty("user.dir") + doc + (refs.isEmpty() ? "" : "_" + refs) + "_plan.stats";
				try (PrintWriter outs = new PrintWriter(statsFile))
				{
					outs.print(options.stats);
				}
			}
		}
		catch (Exception e)
		{
			log.info("Failed to plan text");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
