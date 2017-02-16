package edu.upf.taln.textplanning.corpora;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Interface for classes implementing access to corpora semantically annotated with references of entities.
 */
public interface CorpusCounts
{
	class Counts
	{
		public final String id1;
		public final String id2;
		public long freq; // freq of id2
		public long cooccur;

		public Counts(String inId1, String inId2, long inFreq, long inCooccur)
		{
			id1 = inId1;
			id2 = inId2;
			freq = inFreq;
			cooccur = inCooccur;
		}

		public Counts(Counts inOtherCounts)
		{
			id1 = inOtherCounts.id1;
			id2 = inOtherCounts.id2;
			freq = inOtherCounts.freq;
			cooccur = inOtherCounts.cooccur; // shallow copy
		}

		@Override
		public String toString()
		{
			return id1 + " " + id2 + " : " + freq + ", " + cooccur;
		}
	}

	class Cache
	{
		private final static int maxSize = 10000;
		private boolean maxSizeFreqReached = false;
		private boolean maxSizeCooccurReached = false;
		private final Map<String, Long> freqCache = new HashMap<>();
		private final Map<String, Counts> coocurCache = new HashMap<>();
		private final static Logger log = LoggerFactory.getLogger(Cache.class);

		public boolean containsCounts(String inEntity1, String inDomain, int inDistance)
		{
			return freqCache.containsKey(createdId(inEntity1, inDomain, inDistance));
		}

		public boolean containsCounts(String inEntity1, String inEntity2, String inDomain, int inDistance)
		{
			return coocurCache.containsKey(createdId(inEntity1, inEntity2, inDomain, inDistance));
		}

		public long get(String inEntity1, String inDomain, int inDistance)
		{
			return freqCache.get(createdId(inEntity1, inDomain, inDistance));
		}

		public Counts get(String inEntity1, String inEntity2, String inDomain, int inDistance)
		{
			return coocurCache.get(createdId(inEntity1, inEntity2, inDomain, inDistance));
		}

		public void put(String inEntity, String inDomain, int inDistance, long inCount)
		{
			if (freqCache.size() >= maxSize)
			{
				int i = new Random().nextInt(freqCache.size());
				String key = (String) freqCache.keySet().toArray()[i];
				freqCache.remove(key);
				if (!maxSizeFreqReached)
				{
					log.info("Freq cache is full");
				}
				maxSizeFreqReached = true;
			}
			freqCache.put(createdId(inEntity, inDomain, inDistance), inCount);
		}

		public void put(String inEntity1, String inEntity2, String inDomain, int inDistance, Counts inCounts)
		{
			if (coocurCache.size() >= maxSize)
			{
				int i = new Random().nextInt(coocurCache.size());
				String key = (String) coocurCache.keySet().toArray()[i];
				coocurCache.remove(key);
				if (!maxSizeCooccurReached)
				{
					log.info("Cooccur cache is full");
				}
				maxSizeCooccurReached = true;
			}
			coocurCache.put(createdId(inEntity1, inEntity2, inDomain, inDistance), inCounts);
		}

		private String createdId(String inEntity1, String inDomain, int inDistance)
		{
			return inEntity1 + "-" + (inDomain != null ? inDomain + "-" : "") + inDistance;
		}

		private String createdId(String inEntity1, String inEntity2, String inDomain, int inDistance)
		{
			return inEntity1 + "-" + inEntity2 + (inDomain != null ? inDomain : "-") + inDistance;
		}
	}

	class DebugAid
	{
		private final String id;
		private int numQueries = 0;
		private final List<Long> times = new ArrayList<>();
		private final static Logger log = LoggerFactory.getLogger(DebugAid.class);

		public DebugAid(String inId)
		{
			this.id = inId;
		}

		public void registerQuery(long inTime)
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

		public long getAverageTime()
		{
			return (long) times.stream()
					.mapToLong(l -> l)
					.average().orElse(0.0);
		}

		public long getMaxTime()
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

	Counts getCounts(String inEntity1, String inEntity2, String inDomain);

	Counts getCounts(String inEntity1, String inEntity2, String inDomain, int inDistance);

	Counts getOrderedCounts(String inEntity1, String inEntity2, String inDomain, int inDistance);
}
