package edu.upf.taln.textplanning.core.resources;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.corpus.CandidatesCollector;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.corpus.CorpusContextFunction;
import edu.upf.taln.textplanning.core.corpus.MentionsCollector;
import edu.upf.taln.textplanning.core.dictionaries.CompactDictionary;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class CorpusResourcesFactory
{
	private final static Logger log = LogManager.getLogger();

	public static Map<Corpora.Text, DocumentResourcesFactory> create(Corpora.Corpus corpus, POS.Tagset tagset, InitialResourcesFactory initial_resources,
	                                                                 int max_span_size, Set<POS.Tag> ignored_POS_Tags, Options options)
	{
		log.info("Creating resources for corpus");
		Stopwatch timer = Stopwatch.createStarted();

		if (initial_resources.getDictionary() == null)
			throw new RuntimeException("Cannot create resources without a dictionary");

		// single content words and multiwords
		final List<List<Mention>> mentions = corpus.texts.stream()
				.map(text -> MentionsCollector.collect(text, tagset, max_span_size, ignored_POS_Tags, initial_resources.getLanguage()))
				.collect(toList());
		createCache(mentions, initial_resources);

		Map<Corpora.Text, DocumentResourcesFactory> texts2resources = new HashMap<>();
		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Corpora.Text text = corpus.texts.get(i);
			texts2resources.put(text, create(text, mentions.get(i), initial_resources, true, options));
		}

		log.info("Corpus resources created in " + timer.stop() + "\n");
		return texts2resources;
	}


	public static DocumentResourcesFactory createJointResources(Corpora.Corpus corpus, POS.Tagset tagset,
	                                                            InitialResourcesFactory initial_resources, int max_span_size,
	                                                            Set<POS.Tag> ignored_POS_Tags, Options options)
	{
		log.info("\nCreating resources for corpus");
		Stopwatch timer = Stopwatch.createStarted();

		if (initial_resources.getDictionary() == null)
			throw new RuntimeException("Cannot create resources without a dictionary");

		// single content words and multiwords
		final List<List<Mention>> mentions = corpus.texts.stream()
				.map(text -> MentionsCollector.collect(text, tagset, max_span_size, ignored_POS_Tags, initial_resources.getLanguage()))
				.collect(toList());
		createCache(mentions, initial_resources);

		for (int i = 0; i < corpus.texts.size(); ++i)
		{
			final Corpora.Text text = corpus.texts.get(i);
			create(text, mentions.get(i), initial_resources, false, options);
		}

		final List<Corpora.Sentence> sentences = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.collect(toList());
		final List<Candidate> candidates_list = corpus.texts.stream()
				.flatMap(t -> t.sentences.stream())
				.flatMap(s -> s.candidate_meanings.values().stream())
				.flatMap(List::stream)
				.collect(toList());

		CorpusContextFunction context = new CorpusContextFunction(sentences, candidates_list, initial_resources.getLanguage(), options.min_context_freq, options.window_size);
		DocumentResourcesFactory resources = new DocumentResourcesFactory(initial_resources, options, candidates_list, context);

		log.info("Corpus resources created in " + timer.stop() + "\n");

		return resources;
	}

	private static DocumentResourcesFactory create(Corpora.Text text, List<Mention> mentions,
	                                               InitialResourcesFactory initial_resources, boolean create_context, Options options)
	{
		log.info("\nCreating resources for document " + text.id  + " ( " + text.filename +  ")");
		Stopwatch timer = Stopwatch.createStarted();

		try
		{
			final ULocale language = initial_resources.getLanguage();
			final Map<Mention, List<Candidate>> candidates = CandidatesCollector.collect(initial_resources.getDictionary(), language, mentions);

			// assign mentions and candidates to sentences in corpus
			text.sentences.forEach(sentence ->
			{
				sentence.ranked_words = mentions.stream()
						.filter(m -> m.getContextId().equals(sentence.id))
						.filter(not(Mention::isMultiWord))
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.collect(toList());

				candidates.keySet().stream()
						.filter(m -> m.getContextId().equals(sentence.id))
						.forEach(m -> sentence.candidate_meanings.put(m, candidates.get(m)));

			});

			final List<Candidate> text_candidates = text.sentences.stream()
					.flatMap(s -> s.candidate_meanings.values().stream().flatMap(List::stream))
					.collect(toList());

			DocumentResourcesFactory resources = null;
			if (create_context)
			{
				CorpusContextFunction context = new CorpusContextFunction(text.sentences, text_candidates, language, options.min_context_freq, options.window_size);
				resources = new DocumentResourcesFactory(initial_resources, options, text_candidates, context);
			}

			log.info("Document resources created in " + timer.stop() + "\n");
			return resources;
		}
		catch (Exception e)
		{
			log.error("Cannot load resources for text " + text.id  + " ( " + text.filename +  "): " + e);
			e.printStackTrace();
			return null;
		}
	}

	private static void createCache(List<List<Mention>> mentions, InitialResourcesFactory initial_resources)
	{
		if (initial_resources.getCache() == null && initial_resources.isCachePathSet())
		{
			log.info("Creating cache from corpus candidates");
			final Set<Pair<String, POS.Tag>> forms = mentions.stream()
					.flatMap(List::stream)
					.map(m -> Pair.of(m.getSurfaceForm(), m.getPOS()))
					.collect(toSet());
			CompactDictionary cache = new CompactDictionary(initial_resources.getLanguage(), forms,
					initial_resources.getBase(), initial_resources.getProperties().getCachePath());
			initial_resources.setCache(cache);
		}
	}
}
