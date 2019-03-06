package edu.upf.taln.textplanning.uima;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.parser.deep_parser.types.DeepDependency;
import edu.upf.taln.parser.deep_parser.types.DeepToken;
import edu.upf.taln.textplanning.core.io.SemanticGraphFactory;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.uima.types.ConceptRelevance;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class DSyntSemanticGraphListFactory implements SemanticGraphFactory<JCas>
{
	private final static Logger log = LogManager.getLogger();

	@Override
	public SemanticGraph create(JCas jcas)
	{
		SemanticGraph graph = new SemanticGraph();
		for (Sentence sentence : JCasUtil.select(jcas, Sentence.class))
		{
			try
			{
				JCasUtil.selectCovered(DeepDependency.class, sentence).forEach(d ->
				{
					// Add vertices
					final String governor_id = createVertex(graph, jcas, sentence, d.getGovernor());
					final String dependent_id = createVertex(graph, jcas, sentence, d.getDependent());

					// Add edge
					Role role = Role.create(d.getDependencyType());
					graph.addEdge(governor_id, dependent_id, role);
				});
			}
			catch (Exception e)
			{
				log.error("Reading DSynt tree file for sentence " + sentence.getId() + ": " + e);
			}
		}

		return graph;
	}

	private static String createVertex(SemanticGraph graph, JCas jcas, Sentence sentence, DeepToken deep_token)
	{
		final String id = createVertexId(deep_token, sentence);
		if (!graph.containsVertex(id))
		{
			final Mention mention = createMention(jcas, sentence, deep_token);
			final Optional<Pair<Meaning, Double>> meaning = createMeaning(jcas, deep_token);

			graph.addVertex(id);
			graph.addMention(id, mention);
			meaning.ifPresent(p -> graph.setMeaning(id, p.getLeft()));
			meaning.ifPresent(p -> graph.setWeight(id, p.getRight()));
			graph.addSource(id, sentence.getId());
			// TODO decide if type needs setting
		}

		return id;
	}

	private static String createVertexId(DeepToken deep_token, Sentence sentence)
	{
		return "s" + sentence.getId() + "_t" + deep_token.getBegin() + "-" + deep_token.getEnd();
	}

	private static Mention createMention(JCas jcas, Sentence sentence, DeepToken deep_token)
	{
		// Collect surface linguistic info and create mention object
		final String surface_form = deep_token.getCoveredText();
		final List<Token> surface_tokens = JCasUtil.selectAt(jcas, Token.class, deep_token.getBegin(), deep_token.getEnd());
		final String lemma = surface_tokens.stream()
				.map(Token::getLemma)
				.map(Lemma::getValue)
				.collect(Collectors.joining(" "));
		final String pos = deep_token.getPos().getPosValue();
		final List<Token> sentence_tokens = JCasUtil.selectCovering(jcas, Token.class, sentence);
		final int token_based_offset_begin = sentence_tokens.indexOf(surface_tokens.get(0));
		final int token_based_offset_end = sentence_tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1));
		final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);

		return Mention.get(sentence.getId(), offsets, surface_form, lemma, pos, false, "");
	}

	private static Optional<Pair<Meaning, Double>> createMeaning(JCas jcas, DeepToken deep_token)
	{
		final List<WSDResult> annotations = JCasUtil.selectAt(jcas, WSDResult.class, deep_token.getBegin(), deep_token.getEnd());
		if (!annotations.isEmpty())
		{
			final WSDResult ann = annotations.get(0);
			BabelNetSense babel_synset = (BabelNetSense) ann.getBestSense();
			final Meaning meaning = Meaning.get(babel_synset.getId(), babel_synset.getLabel(), babel_synset.getNameEntity());
			final double rank = babel_synset.getConfidence();
			return Optional.of(Pair.of(meaning, rank));
		}

		return Optional.empty();
	}

	private static OptionalDouble getConceptScore(JCas jcas, DeepToken deep_token)
	{

		final List<ConceptRelevance> annotations = JCasUtil.selectAt(jcas, ConceptRelevance.class, deep_token.getBegin(), deep_token.getEnd());
		if (!annotations.isEmpty())
		{
			final ConceptRelevance ann = annotations.get(0);
			return OptionalDouble.of(ann.getDomainRelevance());
		}

		return OptionalDouble.empty();
	}
}
