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

	public long getFrequency(String inSense)
	{
		try
		{
			// This method assumes that references are BabelNet ids encoded as 'bn:' followed by 8 digits and a letter
			String e = inSense;
			if (e.startsWith("s"))
			{
				e = "bn:" + e.substring(1, e.length());
			}
			if (!e.startsWith("bn:"))
			{
				e = "bn:" + e;
			}

			if (cache.containsKey(e))
				return cache.get(e);
			else
			{
				long freq = queryFrequency(e);
				cache.put(e, freq);
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

	private long queryFrequency(String inSense) throws IOException, SolrServerException
	{
		String queryString = "annotationId:" + inSense.replace(":", "\\:");
		SolrQuery query = new SolrQuery(queryString);
		query.setRows(0); // don't request  data
		Stopwatch timer = Stopwatch.createStarted();
		long count = server.query(query).getResults().getNumFound();
		debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));

		return count;
	}
}
