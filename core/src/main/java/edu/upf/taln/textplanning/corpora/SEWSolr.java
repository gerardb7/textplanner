package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


/**
 * Wrapper class for the Solr index of the SEW corpus.
 * Immutable class.
 */
public final class SEWSolr implements Corpus
{
	private final HttpSolrClient server;
	private final long numDocs;
	private final static Logger log = LoggerFactory.getLogger(SEWSolr.class);
	private final static Cache cache = new Cache();
	public final static DebugAid debug = new DebugAid(SEWSolr.class.getName()); // encapsulates non-immutable behavior used for debugging purposes

	public SEWSolr(String inURL) throws RuntimeException
	{
		try
		{
			server = new HttpSolrClient(inURL);
			SolrQuery q = new SolrQuery("*:*");
			q.setRows(0);
			numDocs = server.query(q).getResults().getNumFound();
		}
		catch (Exception e)
		{
			log.error("Failed to set up access to solr index: " + e);
			throw new RuntimeException(e);
		}
	}

	public long getFrequency(String inItem)
	{
		try
		{
			if (cache.containsKey(inItem))
				return cache.get(inItem);
			else
			{
				boolean isSense =  inItem.startsWith("bn:");
				long freq = queryFrequency(inItem, isSense);
				cache.put(inItem, freq);
				return freq;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public long getNumDocs() { return this.numDocs; }

	private long queryFrequency(String inItem, boolean isSense) throws IOException, SolrServerException
	{
		String queryString;
		if (isSense)
			queryString = "annotationId:" + inItem.replace(":", "\\:");
		else
			queryString = "text:" + inItem.replace(":", "\\:").replace("-", "\\:");
		SolrQuery query = new SolrQuery(queryString);
		query.setRows(0); // don't request  data
		Stopwatch timer = Stopwatch.createStarted();
		long count = 0;
		try
		{
			count = server.query(query).getResults().getNumFound();
		}
		catch (Exception e)
		{
			System.out.println("Query " + query + " failed: " + e);
			e.printStackTrace();
		}
		debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));

		return count;
	}
}
