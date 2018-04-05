package edu.upf.taln.textplanning.corpora;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Interface for classes implementing access to corpora semantically annotated with meanings.
 */
public interface Corpus
{
	class Cache
	{
		private final static int maxSize = 10000;
		private boolean maxSizeReached = false;
		private final Map<String, Long> cache = new HashMap<>();
		private final static Logger log = LoggerFactory.getLogger(Cache.class);

		public boolean containsKey(String inKey) { return cache.containsKey(inKey); }
		public long get(String inKey) { return cache.get(inKey); }

		public void put(String inKey, long inCount)
		{
			if (cache.size() >= maxSize)
			{
				int i = new Random().nextInt(cache.size());
				String key = (String) cache.keySet().toArray()[i];
				cache.remove(key);
				if (!maxSizeReached)
				{
					log.info("Cache is full");
				}
				maxSizeReached = true;
			}
			cache.put(inKey, inCount);
		}
	}

	class DebugAid
	{
		private final String id;
		private int numQueries = 0;
		private final List<Long> times = new ArrayList<>();
		private final static Logger log = LoggerFactory.getLogger(DebugAid.class);

		DebugAid(String inId)
		{
			this.id = inId;
		}

		void registerQuery(long inTime)
		{
			if (++numQueries % 25 == 0)
			{
				log.info(id + ": ran " + numQueries + " queries");
			}
			times.add(inTime);
		}

		public void reset()
		{
			numQueries = 0;
			times.clear();
		}

		long getAverageTime()
		{
			return (long) times.stream()
					.mapToLong(l -> l)
					.average().orElse(0.0);
		}

		long getMaxTime()
		{
			return times.stream()
					.mapToLong(l -> l)
					.max().orElse(0);
		}

		public String toString()
		{
			return "Ran " + numQueries + " queries, avg time=" + String.format(Locale.ROOT, "%d%s", getAverageTime(), "ms")
					+ " max time=" + String.format(Locale.ROOT, "%d%s", getMaxTime(), "ms");
		}
	}

	boolean hasMeaning(String meaning) throws Exception;
	boolean hasMeaningDocument(String meaning);
	boolean hasFormMeaning(String form, String meaning);
	boolean hasForm(String form);
	long getMeaningCount(String meaning);
	long getMeaningDocumentCount(String meaning);
	long getFormMeaningCount(String form, String meaning);
	long getFormCount(String form);
	long getNumDocs();
}
