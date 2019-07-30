package edu.upf.taln.textplanning.tools.evaluation.corpus;

import edu.upf.taln.textplanning.common.DocumentResourcesFactory;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.core.structures.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class EvaluationCorpus
{
	private final static Logger log = LogManager.getLogger();

	@XmlRootElement(name = "corpus")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class
	Corpus
	{
		@XmlAttribute
		public String lang;
		@XmlElement(name = "text")
		public List<Text> texts = new ArrayList<>();
		@XmlTransient
		public DocumentResourcesFactory resouces = null;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String filename;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences = new ArrayList<>();
		@XmlTransient
		public DocumentResourcesFactory resources;
		@XmlTransient
		public SemanticGraph graph = null;
		@XmlTransient
		public List<SemanticSubgraph> subgraphs = new ArrayList<>();
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens = new ArrayList<>();
		@XmlTransient
		public List<Mention> mentions = new ArrayList<>();
		@XmlTransient
		public Map<Mention, List<Candidate>> candidates = new HashMap<>();
		@XmlTransient
		public Map<Mention, Candidate> disambiguated = new HashMap<>();
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Token
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String lemma;
		@XmlAttribute
		public String pos;
		@XmlValue
		public String wf;
	}

	// factory method
	public static Corpus createFromXML(Path xml_file)
	{
		log.info("Parsing XML");

		try
		{
			final String xml_contents = FileUtils.readTextFile(xml_file);
			JAXBContext jc = JAXBContext.newInstance(Corpus.class);
			StreamSource xml = new StreamSource(new StringReader(xml_contents));
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			JAXBElement<Corpus> je1 = unmarshaller.unmarshal(xml, Corpus.class);
			return je1.getValue();
		}
		catch (JAXBException e)
		{
			log.error("Failed to parse xml : " + e);
		}

		return new Corpus();
	}

	public static void createGraph(EvaluationCorpus.Text text, CorpusAdjacencyFunction adjacency)
	{
		final Map<Mention, Candidate> mentions2candidates = text.sentences.stream()
				.flatMap(s -> s.disambiguated.values().stream())
				.collect(toMap(Candidate::getMention, c -> c));

		final Map<String, Meaning> meanings = mentions2candidates.keySet().stream()
				.collect(toMap(Mention::getId, m -> mentions2candidates.get(m).getMeaning()));
		final Map<String, Double> weights = mentions2candidates.keySet().stream()
				.map(m -> Pair.of(m.getId(), m.getWeight()))
				.filter(p -> p.getRight().isPresent())
				.collect(toMap(Pair::getLeft, p -> p.getRight().get()));
		final Map<String, List<Mention>> mentions = mentions2candidates.keySet().stream()
				.collect(toMap(Mention::getId, List::of));
		final Map<String, List<String>> sources = mentions2candidates.keySet().stream()
				.collect(toMap(Mention::getId, m -> List.of(m.getContextId())));

		text.graph = new SemanticGraph(meanings, weights, mentions, sources,
				(id1, id2) -> adjacency.test(mentions.get(id1).get(0), mentions.get(id2).get(0)),
				(id1, id2) -> "edge");
	}

	public static void reset(Corpus corpus)
	{
		corpus.texts.forEach(t ->
				t.sentences.forEach(s ->
				{
					s.disambiguated.clear();
					s.candidates.values().forEach(m ->
							m.forEach(c -> c.setWeight(0.0)));
				}));
	}
}
