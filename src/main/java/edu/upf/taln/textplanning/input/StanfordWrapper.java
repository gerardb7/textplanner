package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.upf.taln.textplanning.structures.amr.Candidate.Type;
import edu.upf.taln.textplanning.structures.amr.CoreferenceChain;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

class StanfordWrapper
{
	private final StanfordCoreNLP pipeline;
	private final static Logger log = LogManager.getLogger(StanfordWrapper.class);

	StanfordWrapper()
	{
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref");
		props.setProperty("tokenize.whitespace", "true"); // no tokenization
		props.setProperty("ssplit.eolonly", "true"); // no sentence splitting
		props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz"); // PERSON, LOCATION, ORGANIZATION,
		props.setProperty("coref.algorithm", "neural");

		Stopwatch timer = Stopwatch.createStarted();
		RedwoodConfiguration.current().clear().apply(); // shut up, CoreNLP
		pipeline = new StanfordCoreNLP(props);
		log.info("CoreNLP pipeline created in " + timer.stop());
	}

	List<CoreferenceChain> process(List<SemanticGraph> graphs)
	{
		if (pipeline == null)
			return Collections.emptyList();

		Stopwatch timer = Stopwatch.createStarted();

		List<List<String>> tokens = graphs.stream()
				.map(SemanticGraph::getAlignments)
				.map(GraphAlignments::getTokens)
				.collect(toList());

		String text = tokens.stream()
				.map(s -> s.stream()
						.collect(Collectors.joining(" ")))
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
						Pair<Integer, Integer> span = Pair.of(m.startIndex-1, m.endIndex-1);
						a.getTopSpanVertex(span).ifPresent(v ->
						{
							String lemma_v = a.getLemma(span).orElse("");
							String pos_v = a.getPOS(span).orElse("N"); // assume nominal
							Type ner_v = a.getNER(span).orElse(Type.Other);
							Mention mention = new Mention(g.getId(), span, a.getSurfaceForm(span), lemma_v, pos_v, ner_v);
							chain.put(v, mention);
						});
					});

					return chain;
				})
				.filter(c -> c.getSize() > 1)
				.collect(toList());

		log.info("CoreNLP processing took " + timer.stop());
		return chains;
	}
}
