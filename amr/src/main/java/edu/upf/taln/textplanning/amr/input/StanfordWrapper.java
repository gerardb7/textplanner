package edu.upf.taln.textplanning.amr.input;

import com.google.common.base.Stopwatch;
import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.amr.structures.CoreferenceChain;
import edu.upf.taln.textplanning.amr.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.Candidate.Type;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

class StanfordWrapper
{
	private final StanfordCoreNLP pipeline;
	private final static Logger log = LogManager.getLogger();

	StanfordWrapper(boolean no_stanford)
	{
		log.info("Setting up Stanford CoreNLP");
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref");
		props.setProperty("tokenize.whitespace", "true"); // no tokenization
		props.setProperty("ssplit.eolonly", "true"); // no sentence splitting
		props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz"); // PERSON, LOCATION, ORGANIZATION,
		props.setProperty("coref.algorithm", "neural");

		Stopwatch timer = Stopwatch.createStarted();
		RedwoodConfiguration.current().clear().apply(); // shut up, CoreNLP
		pipeline = no_stanford ? null : new StanfordCoreNLP(props);
		log.info("CoreNLP pipeline created in " + timer.stop());
	}

	List<CoreferenceChain> process(List<SemanticGraph> graphs)
	{
		if (pipeline == null)
			return Collections.emptyList();

		log.info("Processing graphs with CoreNLP");
		Stopwatch timer = Stopwatch.createStarted();

		List<List<String>> tokens = graphs.stream()
				.map(SemanticGraph::getAlignments)
				.map(GraphAlignments::getTokens)
				.collect(toList());

		String text = tokens.stream()
				.map(s -> String.join(" ", s))
				.collect(Collectors.joining("\n"));

		CoreDocument document = new CoreDocument(text);
		pipeline.annotate(document);

		// Collect POS
		List<List<String>> pos = document.sentences().stream()
				.map(CoreSentence::posTags)
				.collect(toList());

		// Collect lemmas
		List<CoreMap> sentences = document.annotation().get(SentencesAnnotation.class);
		List<List<String>> lemma = sentences.stream()
				.map(s -> s.get(TokensAnnotation.class).stream()
						.map(t -> t.get(LemmaAnnotation.class))
						.collect(toList()))
				.collect(toList());

		// Collect NE types
		List<List<Type>> ner = document.sentences().stream()
				.map(s -> s.nerTags().stream()
						.map(t ->
						{
							switch (t)
							{
								case "PERSON":
									return Type.Person;
								case "ORGANIZATION":
									return Type.Organization;
								case "LOCATION":
									return Type.Location;
								default:
									return Type.Other;
							}
						})
						.collect(toList()))
				.collect(toList());

		// Assign POS & NER
		IntStream.range(0, graphs.size()).forEach(i ->
		{
			List<String> lemma_i = lemma.get(i);
			List<String> pos_i = pos.get(i);
			List<Type> ner_i = ner.get(i);
			IntStream.range(0, pos_i.size()).forEach(j ->
			{
				GraphAlignments align = graphs.get(i).getAlignments();

				align.setLemma(j, lemma_i.get(j));
				align.setPOS(j, pos_i.get(j));
				align.setNER(j, ner_i.get(j));
			});
		});

		final Map<Integer, edu.stanford.nlp.coref.data.Mention> corenlp_mentions = document.annotation().get(CorefCoreAnnotations.CorefMentionsAnnotation.class).stream()
				.collect(toMap(m -> m.mentionID, Function.identity()));

		// Collect chains
		Map<Integer, CorefChain> corenlp_chains = document.corefChains();
		List<CoreferenceChain> chains = corenlp_chains.values().stream()
				.map(c ->
				{
					CoreferenceChain chain = new CoreferenceChain();
					c.getMentionsInTextualOrder().forEach(m ->
					{
						SemanticGraph g = graphs.get(m.sentNum-1);
						GraphAlignments a = g.getAlignments();

						final edu.stanford.nlp.coref.data.Mention corenlp_m = corenlp_mentions.get(m.mentionID);
						final int start = document.tokens().indexOf(corenlp_m.originalSpan.get(0));
						final int end = document.tokens().indexOf(corenlp_m.originalSpan.get(corenlp_m.originalSpan.size() - 1));
						Pair<Integer, Integer> span = Pair.of(start, end);

						final Optional<String> top_v = a.getSpanTopVertex(span);
						if (top_v.isPresent())
						{
							final String v = top_v.get();
							String lemma_v = a.getLemma(span).orElse("");
							String pos_v = a.getPOS(span).orElse("N"); // assume nominal
//							Type ner_v = a.getNEType(span).orElse(Type.Other);
							Mention mention = new Mention(g.getSource(), span, a.getSurfaceForm(span), lemma_v, pos_v,
									MentionsCollector.isName(span, g), MentionsCollector.getType(span, g));
							chain.put(v, mention);
						}
					});

					return chain;
				})
				.filter(c -> c.getSize() > 1)
				.collect(toList());

		log.info("CoreNLP processing took " + timer.stop());
		return chains;
	}
}
