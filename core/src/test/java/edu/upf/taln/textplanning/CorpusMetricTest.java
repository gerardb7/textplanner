package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.corpora.CorporaCounts;
import edu.upf.taln.textplanning.corpora.CorpusCounts;
import edu.upf.taln.textplanning.corpora.MSSparqlCounts;
import edu.upf.taln.textplanning.corpora.SolrCounts;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.metrics.CorpusMetric;
import org.junit.Test;

import java.util.*;

/**
 * Tester for CorpusMetric class
 */
public class CorpusMetricTest
{
	private static final String sparqlURL = "http://multisensor.ontotext.com/repositories/ms-final-prototype";
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewAnnSen";

	@Test
	public void testScore() throws Exception
	{
		Set<String> references = Collections.singleton("s00193152n");
		String documentId = "6eb17f86bb8eb8fd817ba07422d0a68856fd7c89";
		ConLLAcces reader = new ConLLAcces();
		List<SemanticTree> messages =
				reader.readSemanticTrees("src/test/resources/" + documentId + "_" + references + "_conll.conll");
		List<CorpusCounts> corpora = new ArrayList<>();
		corpora.add(new SolrCounts(solrUrl));
		corpora.add(new MSSparqlCounts(sparqlURL));
		CorpusCounts corporaCounts = new CorporaCounts(corpora);
		CorpusMetric metric = new CorpusMetric(corporaCounts, CorpusMetric.Metric.Cooccurrence, "UC1 - Energy Policy");
		Map<SemanticTree, Double> scores = metric.assess(references, messages);
		System.out.print(scores);
		metric = new CorpusMetric(corporaCounts, CorpusMetric.Metric.WeightedCooccurrence, "UC1 - Energy Policy");
		Map<SemanticTree, Double> scores2 = metric.assess(references, messages);
		System.out.print(scores2);
		metric = new CorpusMetric(corporaCounts, CorpusMetric.Metric.InterpolatedBigrams, "UC1 - Energy Policy");
		Map<SemanticTree, Double> scores3 = metric.assess(references, messages);
		System.out.print(scores3);
	}
}