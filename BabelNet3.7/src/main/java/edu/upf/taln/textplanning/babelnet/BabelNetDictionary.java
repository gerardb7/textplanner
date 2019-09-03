package edu.upf.taln.textplanning.babelnet;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.dictionaries.MeaningDictionary;
import edu.upf.taln.textplanning.core.utils.POS;
import it.uniroma1.lcl.babelnet.*;
import it.uniroma1.lcl.babelnet.data.BabelGloss;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.babelnet.iterators.BabelLexiconIterator;
import it.uniroma1.lcl.babelnet.iterators.BabelSynsetIterator;
import it.uniroma1.lcl.jlt.Configuration;
import it.uniroma1.lcl.jlt.ling.Word;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class BabelNetDictionary implements MeaningDictionary
{
	private final BabelNet bn;
	private final static Logger log = LogManager.getLogger();
	public static final int LOGGING_STEP_SIZE = 100000;

	public BabelNetDictionary(Path config_folder)
	{
		bn = getBabelNetInstance(config_folder);
	}

	private static BabelNet getBabelNetInstance(Path config_folder)
	{
		log.info("Setting up BabelNet");
		Stopwatch timer = Stopwatch.createStarted();
		PrintStream oldOut = System.err;
		System.setErr(new PrintStream(new OutputStream()
		{
			public void write(int b) {}
		})); // shut up, BabelNet

		BabelNet instance;
		try
		{
			Path jlt_path = config_folder.resolve("jlt.properties");
			Configuration jltConf = Configuration.getInstance();
			jltConf.setConfigurationFile(jlt_path.toFile());
			BabelNetConfiguration bnConf = BabelNetConfiguration.getInstance();
			Path properties_path = config_folder.resolve("babelnet.properties");
			bnConf.setConfigurationFile(properties_path.toFile());
			bnConf.setBasePath(config_folder.toAbsolutePath() + "/");

			instance = BabelNet.getInstance();
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
	public Stream<String> getMeaningsStream()
	{
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(bn.getSynsetIterator(), 4096), false)
				.filter(Objects::nonNull)
				.map(BabelSynset::getId)
				.map(BabelSynsetID::getID);
	}

	@Override
	public Stream<String> getMeaningsStream(ULocale language)
	{
		final Language bnLang = Language.fromISO(language.toLanguageTag());
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(bn.getSynsetIterator(), 4096), false)
				.filter(Objects::nonNull)
				.filter(s -> s.getSenses().stream().distinct().map(BabelSense::getLanguage).anyMatch(l -> l.equals(bnLang)))
				.map(BabelSynset::getId)
				.map(BabelSynsetID::getID);
	}

	@Override
	public Set<String> getMeanings(ULocale language)
	{
		try
		{
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			int counter = 0;
			final Stopwatch timer = Stopwatch.createStarted();

			Set<String> synsets = new HashSet<>();
			final BabelSynsetIterator it = bn.getSynsetIterator();
			while (it.hasNext())
			{
				final BabelSynset synset = it.next();
				if (synset.getSenses().stream().distinct().map(BabelSense::getLanguage).anyMatch(l -> l.equals(bnLang)))
				{
					synsets.add(synset.getId().getID());
					if (++counter % LOGGING_STEP_SIZE == 0)
						log.info("\t" + counter + " synsets iterated in " + timer);
				}
			}

			return synsets;
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
			synsets.sort(new BabelSynsetComparator(form, bnLang));

			return synsets.stream()
					.map(BabelSynset::getId)
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
			BabelPOS bnPOS = BabelPOS.valueOf(POS.toTag.get(pos));
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
					.map(BabelSynset::getId)
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
			final BabelSense sense = synset.getMainSense(Language.fromISO(language.toLanguageTag()));
			if (sense == null)
				return Optional.empty();
			return Optional.of(sense.getSimpleLemma());
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
			return Optional.of(synset.getSynsetType() == BabelSynsetType.NAMED_ENTITY);
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
			return synset.getDBPediaURIs(Language.EN);
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
	public Stream<Triple<String, POS.Tag, ULocale>> getLexicalizationsStream()
	{
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(bn.getLexiconIterator(), Spliterator.CONCURRENT), false)
				.filter(Objects::nonNull)
				.map(w -> Triple.of(w.getWord(), POS.BabelNet.get(w.getPOS().getTag()), new ULocale(w.getLanguage().name())));
	}

	@Override
	public Stream<Pair<String, POS.Tag>> getLexicalizationsStream(ULocale language)
	{
		final Language bnLang = Language.fromISO(language.toLanguageTag());
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(bn.getLexiconIterator(), Spliterator.CONCURRENT), false)
				.filter(Objects::nonNull)
				.filter(w -> w.getLanguage().equals(bnLang))
				.map(w -> Pair.of(w.getWord(), POS.BabelNet.get(w.getPOS().getTag())));
	}

	@Override
	public Set<Pair<String, POS.Tag>> getLexicalizations(ULocale language)
	{
		try
		{
			final Language bnLang = Language.fromISO(language.toLanguageTag());
			int counter = 0;
			final Stopwatch timer = Stopwatch.createStarted();

			Set<Pair<String, POS.Tag>> lexicalizations = new HashSet<>();
			final BabelLexiconIterator it = bn.getLexiconIterator();
			while (it.hasNext())
			{
				final Word item = it.next();
				if (item.getLanguage().equals(bnLang))
				{
					if (++counter % LOGGING_STEP_SIZE == 0)
						log.info("\t" + counter + " senses iterated in " + timer);

					final Pair<String, POS.Tag> word_tag = Pair.of(item.getWord(), POS.BabelNet.get(item.getPOS().getTag()));
					lexicalizations.add(word_tag); // senses of different synsets may have the same word form and POS tag
				}
			}

			return lexicalizations;
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
