package edu.upf.taln.textplanning.uima.io;

import com.ibm.icu.util.ULocale;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.NGram;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.dkpro.core.matetools.MateLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.ngrams.NGramAnnotator;
import de.tudarmstadt.ukp.dkpro.core.nlp4j.Nlp4JDependencyParser;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordPosTagger;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordSegmenter;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.parser.deep_parser.core.DeepParser;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.uima.TextPlanningAnnotator;
import edu.upf.taln.uima.disambiguation.core.WSDAnnotatorCollectiveContext;
import edu.upf.taln.uima.disambiguation.core.inventory.BabelnetSenseInventoryResource;
import edu.upf.taln.uima.flask_wrapper.ConceptExtractorAnnotator;
import edu.upf.taln.uima.wsd.annotation_extender.core.WSDResultExtender;
import edu.upf.taln.uima.wsd.item_annotator.WSDItemAnnotator;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.JCasIterable;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.ExternalResourceFactory.createExternalResourceDescription;

public class UIMAWrapper
{
	public static class Pipeline
	{
		private final List<AnalysisEngineDescription> pipeline;
		private final ULocale language;
		private final POS.Tagset tagset;

		public Pipeline(List<AnalysisEngineDescription> uima_pipeline, ULocale language, POS.Tagset tagset)
		{
			this.pipeline = uima_pipeline;
			this.language = language;
			this.tagset = tagset;
		}

		private List<AnalysisEngineDescription> getPipeline() { return pipeline; }

		public ULocale getLanguage()
		{
			return language;
		}

		public POS.Tagset getTagset() { return tagset; }
	}

	private final String id;
	private final List<List<TokenInfo>> tokens_info;
	private final List<List<Set<Candidate>>> disambiguated_meanings;
	private final SemanticGraph graph;
	private static final int ngram_size = 3;
	private static final String concept_extraction_url = "http://server01-taln.s.upf.edu:8000";
	private static final String noun_pos_tag = "NN"; // PTB
	private final static Logger log = LogManager.getLogger();

	private UIMAWrapper(JCas doc, POS.Tagset tagset)
	{
		id = DocumentMetaData.get(doc).getDocumentId();
		tokens_info = JCasUtil.select(doc, Sentence.class).stream()
				.map(s -> JCasUtil.selectCovered(Token.class, s).stream()
						.map(t -> new TokenInfo(t.getCoveredText(), t.getLemmaValue(), t.getPosValue()))
						.collect(toList()))
				.collect(toList());

		//Collect disambiguated candidates
		final List<Sentence> sentences = new ArrayList<>(JCasUtil.select(doc, Sentence.class));
		disambiguated_meanings = sentences.stream()
				.map(sentence -> JCasUtil.selectCovered(NGram.class, sentence).stream()
						.map(span ->
						{
							final String surface_form = span.getCoveredText();

							final List<Token> surface_tokens = JCasUtil.selectCovered(Token.class, span);
							final List<String> token_forms = surface_tokens.stream()
									.map(Token::getFormValue)
									.collect(toList());
							final String lemma = surface_tokens.stream()
									.map(Token::getLemma)
									.map(Lemma::getValue)
									.collect(Collectors.joining(" "));
							final String pos = surface_tokens.size() == 1 ? surface_tokens.get(0).getPos().getPosValue() : noun_pos_tag;
							final POS.Tag tag = POS.get(pos, tagset);

							final List<Token> tokens = new ArrayList<>(JCasUtil.select(doc, Token.class));
							final int token_based_offset_begin = tokens.indexOf(surface_tokens.get(0));
							final int token_based_offset_end = tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1));
							final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);
							final Mention mention = new Mention(surface_form, "s" + sentences.indexOf(sentence), offsets, token_forms, lemma, tag, false, "");

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

		DSyntSemanticGraphFactory factory = new DSyntSemanticGraphFactory(tagset);
		graph = factory.create(doc);

	}

	public static void processAndSerialize(Path input_folder, Path output_folder, String suffix, Class <? extends TextReader> parser, Pipeline pipeline)
	{
		try
		{
			CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
					parser,
					TextParser.PARAM_SOURCE_LOCATION, input_folder.toString(),
					TextParser.PARAM_LANGUAGE, pipeline.language.toLanguageTag(),
					TextParser.PARAM_PATTERNS, "*" + suffix);

			AnalysisEngineDescription writer = AnalysisEngineFactory.createEngineDescription(
					XmiWriter.class,
					XmiWriter.PARAM_TARGET_LOCATION, output_folder.toAbsolutePath().toString(),
					XmiWriter.PARAM_OVERWRITE, true);

			final List<AnalysisEngineDescription> components = pipeline.getPipeline();
			components.add(writer);
			AnalysisEngineDescription desc = createEngineDescription(components.toArray(new AnalysisEngineDescription[0]));

			//noinspection StatementWithEmptyBody
			for (@SuppressWarnings("unused") JCas jCas : SimplePipeline.iteratePipeline(reader, desc))
			{
			}
		}
		catch (UIMAException e)
		{
			log.error("Failed to process texts with UIMA pipeline: " + e);
			throw new RuntimeException(e);
		}
	}

	public static List<UIMAWrapper> process(Path input_folder, String suffix, Class <? extends TextReader> parser, Pipeline pipeline)
	{
		final List<UIMAWrapper> docs = new ArrayList<>();

		try
		{
			CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
					parser,
					TextParser.PARAM_SOURCE_LOCATION, input_folder.toString(),
					TextParser.PARAM_LANGUAGE, pipeline.language.toLanguageTag(),
					TextParser.PARAM_PATTERNS, "*" + suffix);

			final List<AnalysisEngineDescription> components = pipeline.getPipeline();
			AnalysisEngineDescription desc = createEngineDescription(components.toArray(new AnalysisEngineDescription[0]));

			final JCasIterable jCas = SimplePipeline.iteratePipeline(reader, desc);
			jCas.forEach(d -> docs.add(new UIMAWrapper(d, pipeline.tagset)));
		}
		catch (UIMAException e)
		{
			log.error("Failed to process texts with UIMA pipeline: " + e);
			throw new RuntimeException(e);
		}

		return docs;
	}


	public static List<UIMAWrapper> readFromXMI(Path input_folder, POS.Tagset tagset)
	{
		try
		{
			log.info("Reading documents from XMI in " + input_folder);
			CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
					XmiReader.class,
					XmiReader.PARAM_SOURCE_LOCATION, input_folder.toString(),
					XmiReader.PARAM_LANGUAGE, "en",
					XmiReader.PARAM_PATTERNS, XmiReader.INCLUDE_PREFIX + "*.xmi",
					XmiReader.PARAM_TYPE_SYSTEM_FILE, input_folder.resolve("TypeSystem.xml").toAbsolutePath().toString(),
					XmiReader.PARAM_MERGE_TYPE_SYSTEM, true);

			final JCasIterator jcasIt = SimplePipeline.iteratePipeline(reader, createEngineDescription(StanfordLemmatizer.class)).iterator();
			List<UIMAWrapper> wrappers = new ArrayList<>();
			while (jcasIt.hasNext())
			{
				final JCas next = jcasIt.next();
				wrappers.add(new UIMAWrapper(next, tagset));
			}

			log.info(wrappers.size() + " docs read");
			return wrappers;
		}
		catch (UIMAException e)
		{
			log.error("Failed to process texts with UIMA pipeline: " + e);
			throw new RuntimeException(e);
		}
	}

	public static Pipeline createSpanPipeline(ULocale language, POS.Tagset tagset, boolean use_concept_extractor)
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

			List<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);

			return new Pipeline(components, language, tagset);
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public static Pipeline createRankingPipeline(ULocale language, POS.Tagset tagset, boolean use_concept_extractor, Path babel_config,
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

			AnalysisEngineDescription spanCandidates = createEngineDescription(WSDItemAnnotator.class,
					WSDItemAnnotator.PARAM_CLASS_NAMES, new String[]{"edu.upf.taln.flask_wrapper.type.WSDSpan", "de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity"});

			ExternalResourceDescription babelnet = createExternalResourceDescription(BabelnetSenseInventoryResource.class,
					BabelnetSenseInventoryResource.PARAM_BABELNET_CONFIGPATH, babel_config.toString(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_LANG, language.toLanguageTag().toUpperCase(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_DESCLANG, language.toLanguageTag().toUpperCase());

			ExternalResourceDescription babelnetDisambiguationResources = null;

			AnalysisEngineDescription disambiguation = createEngineDescription(WSDAnnotatorCollectiveContext.class,
					WSDAnnotatorCollectiveContext.WSD_ALGORITHM_RESOURCE, babelnetDisambiguationResources,
					WSDAnnotatorCollectiveContext.PARAM_BEST_ONLY, false);

			AnalysisEngineDescription extender = createEngineDescription(WSDResultExtender.class,
					WSDResultExtender.PARAM_BABELNET, babelnet,
					WSDResultExtender.PARAM_LANGUAGES, new Language[]{Language.EN, Language.ES, Language.IT, Language.EL});

			AnalysisEngineDescription textplanner = AnalysisEngineFactory.createEngineDescription(TextPlanningAnnotator.class);

			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);
			components.add(spanCandidates);
			components.add(disambiguation);
			components.add(extender);
			components.add(textplanner);

			return new Pipeline(components, language, tagset);
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public static Pipeline createParsingPipeline(ULocale language, POS.Tagset tagset)
	{
		try
		{
			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();

			// Pre-processing
			AnalysisEngineDescription segmenter = createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language.toLanguageTag());
			AnalysisEngineDescription pos = createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language.toLanguageTag());
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
					DeepParser.PARAM_TYPE, "taln");
			components.add(deepParser);

			return new Pipeline(components, language, tagset);
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			e.printStackTrace();
			return null;
		}
	}

	public String getId()
	{
		return id;
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
		return tokens_info;
	}

	public List<List<String>> getTokens()
	{
		return tokens_info.stream()
				.map(s -> s.stream()
						.map(t -> t.wordForm)
						.collect(toList()))
				.collect(toList());
	}

	public List<List<String>> getNominalTokens()
	{
		return tokens_info.stream()
				.map(s -> s.stream()
						.filter(t -> t.pos.startsWith(noun_pos_tag))
						.map(t -> t.wordForm)
						.collect(toList()))
				.collect(toList());
	}

	public List<List<Set<Candidate>>> getDisambiguatedCandidates()
	{
		return disambiguated_meanings;
	}

	public SemanticGraph getSemanticGraph()
	{
		return graph;
	}
}
