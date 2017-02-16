package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Immutable class, NIF-encoded semantic graphs from RDF data returned from an SPARQL endpoint
 */
public class NIFAcces implements DocumentAccess
{
	private RDFFormat format;
	protected final ValueFactory factory = SimpleValueFactory.getInstance();
	private static final Logger log = LoggerFactory.getLogger(NIFAcces.class);

	private final IRI nifContext = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Context");
	private final IRI nifIsString = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#isString");
	private final IRI nifSentence = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Sentence");
	private final IRI nifWord = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Word");
	private final IRI nifPhrase = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Phrase");
	private final IRI nifBeginIndex = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#beginIndex");
	private final IRI nifEndIndex = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#endIndex");
	private final IRI nifAnchorOf = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf");
	private final IRI nifLemma = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#lemma");
	private final IRI nifOliaLink = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#oliaLink");
	private final IRI nifLiteralAnnotation = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#literalAnnotation");

	private final IRI fnAnnotationSet = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/AnnotationSet");
	private final IRI fnAnnotationSetFrame = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/annotationSetFrame");
	private final IRI fnhasLayer = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/hasLayer");
	private final IRI fnLabel = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/Label");
	private final IRI fnhasLabel = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/hasLabel");
	private final IRI fnlabel_FE = factory.createIRI("http://www.ontologydesignpatterns.org/ont/framenet/tbox/label_FE");

	private final IRI taIdentRef = factory.createIRI("http://www.w3.org/2005/11/its/rdf#taIdentRef");
	private final IRI nifAnnUnit = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#annotationUnit");
	private final IRI nifAnnConfidence = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#confidence");
	private final IRI nifAnntaIdentConf = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#taIdentConf");
	private final IRI nifAnnProvenance = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#provenance");
	private final IRI nifAnntaIdentProv = factory.createIRI("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#taIdentProv");

	public NIFAcces(RDFFormat inFormat)
	{
		format = inFormat;
	}

	/**
	 * Reads RDF/NIF-encoded semantic DAGs .
	 *
	 * @param inDocumentContents String containing NIF/RDF serialization of trees
	 * @return semantic DAGs
	 */
	public List<DirectedAcyclicGraph<AnnotationInfo, LabelledEdge>> readSemanticDAGs(String inDocumentContents)
	{
		try (StringReader reader = new StringReader(inDocumentContents))
		{
			Model model = Rio.parse(reader, "", format);
			return readModel(model);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<SemanticTree> readSemanticTrees(String inDocumentContents)
	{
		//@TODO: implement reading of deep syntactic trees from nif
		return null;
	}

	public String readText(String inDocumentContents)
	{
		try (StringReader reader = new StringReader(inDocumentContents))
		{
			Model model = Rio.parse(reader, "", format);
			Resource context = Models.subject(model.filter(null, RDF.TYPE, nifContext)).get();
			return Models.objectString(model.filter(context, nifIsString, null)).get();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	protected List<DirectedAcyclicGraph<AnnotationInfo, LabelledEdge>> readModel(Model inModel)
	{
		// Functions used to get NIF sentence, word and phrase annotations related to each reference annotation
		Function<IRI, Pair<IRI, Pair<Integer, Integer>>> getOffsets =
				r -> {
					Literal begLiteral = Models.objectLiteral(inModel.filter(r, nifBeginIndex, null)).orElseThrow(RuntimeException::new);
					Literal endLiteral = Models.objectLiteral(inModel.filter(r, nifEndIndex, null)).orElseThrow(RuntimeException::new);
					return Pair.of(r, Pair.of(begLiteral.intValue(), endLiteral.intValue()));
				};
		BiPredicate<Pair<Integer, Integer>, Pair<Integer, Integer>> overlaps =
				(o1, o2) -> o1.getLeft() <= o2.getLeft() && o1.getRight() >= o2.getRight();

		// Collect nif:Word and nif:Phrase annotations which are part of a semantic structure, and group them by sentence
		Set<Resource> sentences = inModel.filter(null, RDF.TYPE, nifSentence).subjects();
		Set<Resource> wordsAndPhrases = new HashSet<>();
		wordsAndPhrases.addAll(inModel.filter(null, RDF.TYPE, nifWord).subjects());
		wordsAndPhrases.addAll(inModel.filter(null, RDF.TYPE, nifPhrase).subjects());
		Set<Resource> annotations = wordsAndPhrases.stream()
				.filter(w -> isRelationalAnnotation(inModel, (IRI) w))
				.collect(Collectors.toSet());

		List<Map<IRI, AnnotationInfo>> sentenceAnns = sentences.stream()
				.map(IRI.class::cast)
				.map(getOffsets::apply)
				.map(s -> annotations.stream()    // map each sentence to...
						.map(IRI.class::cast)
						.map(getOffsets::apply) // get offsets for each annotation
						.filter(a -> overlaps.test(s.getRight(), a.getRight()))   // keep only anns in sentence
						.collect(Collectors.toMap(Pair::getLeft, a -> getAnnotationInfo(inModel, a.getLeft()),
								(v1, v2) -> v1))) // Ignore duplicates caused by multiple words being overlap by same phrase, as they should have same annotation info
				.collect(Collectors.toList());// ... collect all annotations

		// Create a graph for each sentence where nodes correspond to annotations in the sentences
		List<DirectedAcyclicGraph<AnnotationInfo, LabelledEdge>> graphs = new ArrayList<>();
		sentenceAnns.stream()
				.forEach(s -> {
					DirectedAcyclicGraph<AnnotationInfo, LabelledEdge> g = new DirectedAcyclicGraph<>(LabelledEdge.class);
					s.values().forEach(g::addVertex);
					graphs.add(g);
				});

		// For each pair of governor and dependent annotations, add an edge to the sentence graph labelled with the role
		IntStream.range(0, sentenceAnns.size()).forEach(i -> // Iterate over annotations and graphs in parallel
				sentenceAnns.get(i).values().stream()
						.filter(annInfo -> annInfo.getRelationId() != null)
						.forEach(annInfo -> { //
							IRI layer = Models.objectIRI(inModel.filter(factory.createIRI(annInfo.getRelationId()), fnhasLayer, null)).orElseThrow(RuntimeException::new);
							inModel.filter(layer, fnhasLabel, null).objects().stream()
								.map(IRI.class::cast)
								.forEach(label -> {
									IRI role = Models.objectIRI(inModel.filter(label, fnlabel_FE, null)).orElseThrow(RuntimeException::new);
									IRI dependent = Models.subjectIRI(inModel.filter(null, nifOliaLink, label)).orElseThrow(RuntimeException::new);
									AnnotationInfo dependentInfo = sentenceAnns.get(i).get(dependent);
									LabelledEdge e = new LabelledEdge(role.getLocalName().split("\\.")[0]);
									if (!annInfo.equals(dependentInfo))
									{
										graphs.get(i).addEdge(annInfo, dependentInfo, e);
									}
									else
									{
										log.warn("Annotation is argument of itself: " + annInfo);
									}
								});
						})
		);
		return graphs;
	}

	private boolean isRelationalAnnotation(Model inModel, IRI inNIFAnnotation)
	{
		return inModel.filter(inNIFAnnotation, nifOliaLink, null).objects().stream()
				.anyMatch(l -> inModel.contains((Resource) l, RDF.TYPE, fnAnnotationSet) ||
						inModel.contains((Resource) l, RDF.TYPE, fnLabel));
	}

	private AnnotationInfo getAnnotationInfo(Model inModel, IRI inNIFAnnotation)
	{
		// Get basic info: anchor, lemma, pos, feats
		String anchor = Models.objectString(inModel.filter(inNIFAnnotation, nifAnchorOf, null)).orElse(null);
		String lemma = Models.objectString(inModel.filter(inNIFAnnotation, nifLemma, null)).orElse("");
		String pos = inModel.filter(inNIFAnnotation, nifOliaLink, null).objects().stream()
				.map(Value::stringValue)
				.filter(r -> r.startsWith("http://purl.org/olia/penn.owl"))
				.findFirst().orElse(null);
		//String feats = Models.objectString(inModel.filter(inNIFAnnotation, nifLiteralAnnotation, null)).orElse("");
		String feats = inModel.filter(inNIFAnnotation, nifLiteralAnnotation, null).objects().stream()
				.map(Literal.class::cast)
				.map(Literal::stringValue)
				.filter(l -> l.contains("word="))
				.findFirst().orElse("");
		Pair<IRI, Double> ref = getBabelfyReference(inModel, inNIFAnnotation);
		Pair<IRI, IRI> rel = getRelation(inModel, inNIFAnnotation);

		return new AnnotationInfo(inNIFAnnotation.toString(), anchor == null ? lemma : anchor, lemma,
				pos == null ? null : factory.createIRI(pos).getLocalName(), feats,
				ref == null ? null : ref.getLeft().getLocalName(), ref == null ? 0.0 : ref.getRight(),
				rel == null ? null : rel.getRight().getLocalName(),
				rel == null ? null : rel.getLeft().toString()); // @todo Fix sentence number when reading from RDF/NIF
	}

	private Pair<IRI, Double> getBabelfyReference(Model inModel, IRI inNIFAnnotation)
	{
		// Find where is the babelfy annotation
		IRI refAnn;
		IRI prov = Models.objectIRI(inModel.filter(inNIFAnnotation, nifAnnProvenance, null)).orElse(null);
		if (prov == null)
		{
			prov = Models.objectIRI(inModel.filter(inNIFAnnotation, nifAnntaIdentProv, null)).orElse(null);
		}
		if (prov != null && prov.toString().equals("http://babelfy.org/"))
		{
			refAnn = inNIFAnnotation;
		}
		else
		{
			refAnn = inModel.filter(inNIFAnnotation, nifAnnUnit, null).objects().stream()
					.map(IRI.class::cast)
					.filter(a -> {
						IRI iri = Models.objectIRI(inModel.filter(a, nifAnnProvenance, null)).orElse(null);
						if (iri == null)
						{
							iri = Models.objectIRI(inModel.filter(a, nifAnntaIdentProv, null)).orElse(null);
						}
						return iri != null && iri.stringValue().equals("http://babelfy.org/");
					})
					.findFirst().orElse(null);
		}

		// Get the referred BabelNet sysnet and the confidence score issued by Babelfy
		IRI ref = null;
		double conf = 0.0;
		if (refAnn != null)
		{
			ref = Models.objectIRI(inModel.filter(refAnn, taIdentRef, null)).orElseThrow(RuntimeException::new);
			Optional<Literal> literal = Models.objectLiteral(inModel.filter(refAnn, nifAnnConfidence, null));
			if (!literal.isPresent())
			{
				literal = Models.objectLiteral(inModel.filter(refAnn, nifAnntaIdentConf, null));
			}
			if (!literal.isPresent())
			{
				throw new RuntimeException();
			}
			conf = literal.get().doubleValue();
		}

		if (ref == null)
		{
			return null;
		}
		else
		{
			return Pair.of(ref, conf);
		}
	}

	private Pair<IRI, IRI> getRelation(Model inModel, IRI inNIFAnnotation)
	{
		// Get FrameNet annotation and associated frame, if any
		IRI relationAnn = inModel.filter(inNIFAnnotation, nifOliaLink, null).objects().stream()
				.filter(r -> inModel.contains((Resource) r, RDF.TYPE, fnAnnotationSet))
				.map(IRI.class::cast)
				.findFirst().orElse(null);
		IRI relation = null;
		if (relationAnn != null)
		{
			// TODO decide what to do when no frame other than Linguistic_situation is found
			relation = inModel.filter(relationAnn, fnAnnotationSetFrame, null).objects().stream()
					.filter(v -> !v.toString().equals("http://taln.upf.edu/frame#Linguistic_situation"))
					.map(IRI.class::cast)
					.findFirst().orElse(factory.createIRI("http://taln.upf.edu/frame#Linguistic_situation"));
		}

		if (relationAnn == null || relation == null)
		{
			return null;
		}
		else
		{
			return Pair.of(relationAnn, relation);
		}
	}
}
