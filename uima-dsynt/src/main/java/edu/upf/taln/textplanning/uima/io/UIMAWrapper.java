package edu.upf.taln.textplanning.uima.io;

import com.ibm.icu.util.ULocale;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.NGram;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.matetools.MateLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.ngrams.NGramAnnotator;
import de.tudarmstadt.ukp.dkpro.core.nlp4j.Nlp4JDependencyParser;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordPosTagger;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordSegmenter;
import de.tudarmstadt.ukp.dkpro.wsd.algorithm.TALNSenseBaseline;
import de.tudarmstadt.ukp.dkpro.wsd.annotator.WSDAnnotatorCollectiveCandidate;
import de.tudarmstadt.ukp.dkpro.wsd.resource.WSDResourceCollectiveCandidate;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.flask_wrapper.type.WSDSpan;
import edu.upf.taln.parser.deep_parser.core.DeepParser;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.uima.TextPlanningAnnotator;
import edu.upf.taln.textplanning.uima.io.DSyntSemanticGraphFactory;
import edu.upf.taln.uima.flask_wrapper.ConceptExtractorAnnotator;
import edu.upf.taln.uima.wsd.annotation_extender.core.WSDResultExtender;
import edu.upf.taln.uima.wsd.candidateDetection.BabelNetCandidateIdentification;
import edu.upf.taln.uima.wsd.si.babelnet.resource.BabelnetSenseInventoryResource;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.ExternalResourceFactory.createExternalResourceDescription;

public class UIMAWrapper
{
	public static class Pipeline
	{
		private final AnalysisEngine pipeline;

		public Pipeline(AnalysisEngine uima_pipeline)
		{
			this.pipeline = uima_pipeline;
		}

		private AnalysisEngine getPipeline() { return pipeline; }
	}

	private final JCas doc;
	private static final int ngram_size = 3;
	private static final String concept_extraction_url = "http://server01-taln.s.upf.edu:8000";
	private static final String noun_pos_tag = "NN"; // PTB
	private final static Logger log = LogManager.getLogger();

	public UIMAWrapper(String text, Pipeline pipeline)
	{
		try
		{
			log.info("Processing text");
			doc = processText(text, pipeline);
		}
		catch (UIMAException e)
		{
			log.error("Failed to process text with UIMA pipeline: " + e);
			throw new RuntimeException(e);
		}
	}

	public static Pipeline createSpanPipeline(ULocale language, boolean use_concept_extractor)
	{
		try
		{
			AnalysisEngineDescription segmenter = createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription pos = createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription lemma = createEngineDescription(MateLemmatizer.class,
					MateLemmatizer.PARAM_LANGUAGE, language.toLanguageTag(),
					MateLemmatizer.PARAM_VARIANT, "default");
			AnalysisEngineDescription spans = use_concept_extractor ?
					createEngineDescription(ConceptExtractorAnnotator.class, ConceptExtractorAnnotator.PARAM_FLASK_URL, concept_extraction_url)
					: createEngineDescription(NGramAnnotator.class, NGramAnnotator.PARAM_N, 3);

			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);
			AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
			AnalysisEngineDescription all = createEngineDescription(componentArray);

			return new Pipeline(createEngine(all));
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public static Pipeline createRankingPipeline(ULocale language, boolean use_concept_extractor, Path babel_config,
	                                             Path freqs_file, Path vectors)
	{
		try
		{
			AnalysisEngineDescription segmenter = createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription pos = createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription lemma = createEngineDescription(MateLemmatizer.class,
					MateLemmatizer.PARAM_LANGUAGE, language.toLanguageTag(),
					MateLemmatizer.PARAM_VARIANT, "default");

			AnalysisEngineDescription spans = use_concept_extractor ?
					createEngineDescription(ConceptExtractorAnnotator.class, ConceptExtractorAnnotator.PARAM_FLASK_URL, concept_extraction_url)
					: createEngineDescription(NGramAnnotator.class, NGramAnnotator.PARAM_N, ngram_size);

			AnalysisEngineDescription babelnet_candidates = use_concept_extractor ?
					createEngineDescription(BabelNetCandidateIdentification.class, WSDSpan.class)
					: createEngineDescription(BabelNetCandidateIdentification.class, NGram.class);

			ExternalResourceDescription babelnet = createExternalResourceDescription(BabelnetSenseInventoryResource.class,
					BabelnetSenseInventoryResource.PARAM_BABELNET_CONFIGPATH, babel_config.toString(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_LANG, language.toLanguageTag().toUpperCase(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_DESCLANG, language.toLanguageTag().toUpperCase());

			ExternalResourceDescription babelnetDisambiguationResources = createExternalResourceDescription(WSDResourceCollectiveCandidate.class,
					WSDResourceCollectiveCandidate.SENSE_INVENTORY_RESOURCE, babelnet,
					WSDResourceCollectiveCandidate.DISAMBIGUATION_METHOD, TALNSenseBaseline.class.getName(),
					WSDResourceCollectiveCandidate.PARAM_FREQUENCIES_FILE, freqs_file.toString(),
					WSDResourceCollectiveCandidate.PARAM_SIMILARITIES_FILE, vectors.toString());

			AnalysisEngineDescription disambiguation = createEngineDescription(WSDAnnotatorCollectiveCandidate.class,
					WSDAnnotatorCollectiveCandidate.WSD_ALGORITHM_RESOURCE, babelnetDisambiguationResources,
					WSDAnnotatorCollectiveCandidate.PARAM_BEST_ONLY, false);

			AnalysisEngineDescription extender = createEngineDescription(WSDResultExtender.class,
					WSDResultExtender.PARAM_BABELNET, babelnet,
					WSDResultExtender.PARAM_LANGUAGES, new Language[]{Language.EN, Language.ES, Language.IT, Language.EL});

			AnalysisEngineDescription textplanner = AnalysisEngineFactory.createEngineDescription(TextPlanningAnnotator.class);

			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);
			components.add(babelnet_candidates);
			components.add(disambiguation);
			components.add(extender);
			components.add(textplanner);
			AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
			AnalysisEngineDescription all = createEngineDescription(componentArray);

			return new Pipeline(createEngine(all));
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public static Pipeline createParsingPipeline(ULocale language)
	{
		try
		{
			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();

			// Pre-processing
			AnalysisEngineDescription segmenter = createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription pos = createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language.toLanguageTag(),
					StanfordPosTagger.PARAM_VARIANT, "taln");
			AnalysisEngineDescription lemma = createEngineDescription(MateLemmatizer.class,
					MateLemmatizer.PARAM_LANGUAGE, language.toLanguageTag(),
					MateLemmatizer.PARAM_VARIANT, "default");
			AnalysisEngineDescription parsing = createEngineDescription(segmenter, pos, lemma);
			components.add(parsing);

			// PTB/NLP4J SSynt parsing
			AnalysisEngineDescription dependency = createEngineDescription(Nlp4JDependencyParser.class,
					Nlp4JDependencyParser.PARAM_LANGUAGE, language.toLanguageTag(),
					Nlp4JDependencyParser.PARAM_VARIANT, "taln");
			components.add(dependency);

			// MATE DSynt parsing
			AnalysisEngineDescription deepParser = createEngineDescription(DeepParser.class,
					DeepParser.PARAM_LANGUAGE, language.toLanguageTag(),
					DeepParser.PARAM_TYPE, "taln");
			components.add(deepParser);

			final AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
			final AnalysisEngineDescription engineDescription = createEngineDescription(componentArray);
			return new Pipeline(createEngine(engineDescription));
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public List<List<String>> getTokens()
	{
		return JCasUtil.select(doc, Sentence.class).stream()
				.map(s -> JCasUtil.selectCovered(Token.class, s).stream()
						.map(Token::getCoveredText)
						.collect(toList()))
				.collect(toList());
	}

	public static class TokenInfo
	{
		public TokenInfo(String wf, String lemma, String pos)
		{
			this.wordForm = wf;
			this.lemma = lemma;
			this.pos = pos;
		}

		public String getWordForm() { return wordForm; }
		public String getLemma() { return lemma; }
		public String getPos() { return pos; }

		final String wordForm;
		final String lemma;
		final String pos;
	}

	public List<List<TokenInfo>> getTokensInfo()
	{
		return JCasUtil.select(doc, Sentence.class).stream()
				.map(s -> JCasUtil.selectCovered(Token.class, s).stream()
						.map(t -> new TokenInfo(t.getCoveredText(), t.getLemmaValue(), t.getPosValue()))
						.collect(toList()))
				.collect(toList());
	}

	public List<List<String>> getNominalTokens()
	{
		return JCasUtil.select(doc, Sentence.class).stream()
				.map(s -> JCasUtil.selectCovered(Token.class, s).stream()
						.filter(t -> t.getPos().getPosValue().startsWith(noun_pos_tag))
						.map(Token::getCoveredText)
						.collect(toList()))
				.collect(toList());
	}

	public List<List<Set<Candidate>>> getDisambiguatedCandidates()
	{
		log.info("Collecting disambiguated candidates");
		DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final List<Sentence> sentences = new ArrayList<>(JCasUtil.select(doc, Sentence.class));
		return sentences.stream()
				.parallel()
				.peek(s -> reporter.report())
				.map(sentence -> JCasUtil.selectCovered(NGram.class, sentence).stream()
						.map(span ->
						{
							final String surface_form = span.getCoveredText();
							final List<Token> surface_tokens = JCasUtil.selectCovered(Token.class, span);
							final String lemma = surface_tokens.stream()
									.map(Token::getLemma)
									.map(Lemma::getValue)
									.collect(Collectors.joining(" "));
							final String pos = surface_tokens.size() == 1 ? surface_tokens.get(0).getPos().getPosValue() : noun_pos_tag;
							final List<Token> sentence_tokens = JCasUtil.selectCovered(Token.class, sentence);
							final int token_based_offset_begin = sentence_tokens.indexOf(surface_tokens.get(0));
							final int token_based_offset_end = sentence_tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1));
							final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);
							final Mention mention = Mention.get("s" + sentences.indexOf(sentence), offsets, surface_form, lemma, pos, false, "");

							return JCasUtil.selectAt(doc, WSDResult.class, span.getBegin(), span.getEnd()).stream()
									.map(a ->
									{
										final BabelNetSense s = (BabelNetSense) a.getBestSense();
										final Meaning meaning = Meaning.get(s.getId(), s.getLabel(), s.getNameEntity());
//										final double rank = JCasUtil.selectAt(doc, ConceptRelevance.class, a.getBegin(), a.getEnd()).get(0).getDomainRelevance();
										Candidate c = new Candidate(mention, meaning);
										c.setWeight(s.getConfidence());
										return c;
									})
									.collect(toSet());
						})
						.filter(l -> !l.isEmpty())
						.collect(toList()))
				.collect(toList());
	}

	public SemanticGraph getSemanticGraph()
	{
		DSyntSemanticGraphFactory factory = new DSyntSemanticGraphFactory();
		return factory.create(doc);
	}

	private static JCas processText(String text, Pipeline pipeline) throws UIMAException
	{
		JCas jCas;
		jCas = JCasFactory.createText(text);
		//jCas.setDocumentLanguage(pipeline.g);
		DocumentMetaData.create(jCas);
		pipeline.getPipeline().process(jCas);

		return jCas;
	}
}
