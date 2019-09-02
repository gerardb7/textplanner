package edu.upf.taln.textplanning.babelnet;

import com.babelscape.util.UniversalPOS;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.dictionaries.MeaningDictionary;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import it.uniroma1.lcl.babelnet.*;
import it.uniroma1.lcl.babelnet.data.BabelGloss;
import it.uniroma1.lcl.jlt.Configuration;
import it.uniroma1.lcl.jlt.util.Language;
import it.uniroma1.lcl.kb.ResourceID;
import it.uniroma1.lcl.kb.SynsetType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class BabelNetDictionary implements MeaningDictionary
{
	private final it.uniroma1.lcl.babelnet.BabelNet bn;
	private final static Logger log = LogManager.getLogger();
	public static final int LOGGING_STEP_SIZE = 100000;

	public BabelNetDictionary(Path config_folder)
	{
		bn = getBabelNetInstance(config_folder);
	}

	private static it.uniroma1.lcl.babelnet.BabelNet getBabelNetInstance(Path config_folder)
	{
		log.info("Setting up BabelNet");
		Stopwatch timer = Stopwatch.createStarted();
		PrintStream oldOut = System.err;
		System.setErr(new PrintStream(new OutputStream()
		{
			public void write(int b) {}
		})); // shut up, BabelNet

		it.uniroma1.lcl.babelnet.BabelNet instance;
		try
		{
			Path jlt_path = config_folder.resolve("jlt.properties");
			Configuration jltConf = Configuration.getInstance();
			jltConf.setConfigurationFile(jlt_path.toFile());
			BabelNetConfiguration bnConf = BabelNetConfiguration.getInstance();
			Path properties_path = config_folder.resolve("babelnet.properties");
			bnConf.setConfigurationFile(properties_path.toFile());
			bnConf.setBasePath(config_folder.toAbsolutePath() + "/");

			instance = it.uniroma1.lcl.babelnet.BabelNet.getInstance();
		}
		catch (Exception e)
		{
			System.setErr(oldOut);
			log.error("BabelNet set up failed: " + e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		finally
		{
			System.setErr(oldOut);
		}

		log.info("BabelNet set up in " + timer.stop());
		return instance;
	}

	@Override
	public Iterator<String> meaning_iterator()
	{
		return Iterators.transform(bn.iterator(), s -> Objects.requireNonNull(s).getID().getID());
	}

	@Override
	public Set<String> getMeanings(ULocale language)
	{
		try
		{
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			final AtomicInteger counter = new AtomicInteger(0);
			final Stopwatch timer = Stopwatch.createStarted();
			final DebugUtils.ThreadReporter report = new DebugUtils.ThreadReporter(log);

			return bn.stream().parallel()
					.peek(l -> report.report())
					.filter(s -> s.getLanguages().contains(bnLang))
					.map(BabelSynset::getID)
					.map(ResourceID::getID)
					.peek(m -> {
						long i = counter.incrementAndGet();
						if (i % LOGGING_STEP_SIZE == 0)
							log.info("\t" + i + " synsets iterated in " + timer);
					})
					.collect(toSet());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new HashSet<>();
		}
	}

	@Override
	@SuppressWarnings("unused")
	public List<String> getMeanings(String form, ULocale language)
	{
		// Get candidate entities using strict matching
		try
		{
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			final List<BabelSynset> synsets = bn.getSynsets(form, bnLang);

			try
			{
				synsets.sort(new BabelSynsetComparator(form, bnLang));
			}
			catch (Exception e)
			{
				log.warn("Sorting failed for synsets of \"" + form + "\":" + e);
			}

			return synsets.stream()
					.map(BabelSynset::getID)
					.map(BabelSynsetID::getID)
					.collect(toList());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}

	@Override
	public List<String> getMeanings(String form, POS.Tag pos, ULocale language)
	{
		// Get candidate entities using strict matching
		try
		{
			UniversalPOS bnPOS = UniversalPOS.valueOf(POS.toTag.get(pos));
			if (bnPOS == null)
				log.error("Failed to map Tag tag " + pos);
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			if (bnLang == null)
				log.error("Failed to map language tag " + language.toLanguageTag());

			final List<BabelSynset> synsets = bn.getSynsets(form, bnLang, bnPOS);
			try
			{
				synsets.sort(new BabelSynsetComparator(form, bnLang));
			}
			catch (Exception ignored)
			{ }
			return synsets.stream()
					.map(BabelSynset::getID)
					.map(BabelSynsetID::getID)
					.collect(toList());

		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}

	@Override
	public boolean contains(String id)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			return synset != null;
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public Optional<String> getLabel(String id, ULocale language)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Optional.empty();
			return synset.getMainSense(Language.fromISO(language.toLanguageTag()))
					.map(BabelSense::getSimpleLemma);
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return Optional.empty();
		}
	}

	@Override
	public Optional<Boolean> isNE(String id)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Optional.empty();
			return Optional.of(synset.getType() == SynsetType.NAMED_ENTITY);
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return Optional.empty();
		}
	}

	public List<String> getdbPediaURIs(String id)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return new ArrayList<>();
			return synset.toURIs(BabelExternalResource.DBPEDIA, Language.EN);
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}

	@Override
	public List<String> getGlosses(String id, ULocale language)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return new ArrayList<>();
			return synset.getGlosses(Language.fromISO(language.toLanguageTag())).stream()
					.map(BabelGloss::getGloss)
					.collect(toList());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}

	@Override
	public Iterator<Triple<String, POS.Tag, ULocale>> lexicon_iterator()
	{
		return Iterators.transform(bn.getLexiconIterator(), w -> {
			Objects.requireNonNull(w);
			return Triple.of(w.getWord(), POS.BabelNet.get(w.getPOS().getTag()), new ULocale(w.getLanguage().name()));
		});
	}

	@Override
	public Set<Pair<String, POS.Tag>> getLexicalizations(ULocale language)
	{
		try
		{
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			final AtomicInteger counter = new AtomicInteger(0);
			final Stopwatch timer = Stopwatch.createStarted();
			final DebugUtils.ThreadReporter report = new DebugUtils.ThreadReporter(log);

			return bn.lexiconStream().parallel()
					.peek(l -> report.report())
					.filter(l -> l.getLanguage().equals(bnLang))
					.peek(m -> {
						long i = counter.incrementAndGet();
						if (i % LOGGING_STEP_SIZE == 0)
							log.info("\t" + i + " senses iterated in " + timer);
					})
					.map(w -> Pair.of(w.getWord(), POS.BabelNet.get(w.getPOS().getTag()))) // senses of different synsets may have the same word form and POS tag
					.collect(toSet());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new HashSet<>();
		}
	}

	@Override
	public List<Pair<String, POS.Tag>> getLexicalizations(String id)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return new ArrayList<>();
			return synset.getSenses().stream()
					.map(sense -> Pair.of(sense.getSimpleLemma(), POS.BabelNet.get(sense.getPOS().getTag())))
					.collect(toList());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}

	@Override
	public List<Pair<String, POS.Tag>> getLexicalizations(String id, ULocale language)
	{
		try
		{
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return new ArrayList<>();
			return synset.getSenses(Language.fromISO(language.toLanguageTag())).stream()
					.map(sense -> Pair.of(sense.getSimpleLemma(), POS.BabelNet.get(sense.getPOS().getTag())))
					.collect(toList());
		}
		catch (Exception e)
		{
			log.warn("BabelNet error: " + e);
			return new ArrayList<>();
		}
	}
}
