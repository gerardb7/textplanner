package edu.upf.taln.textplanning.core.corpus;

import com.google.common.base.Charsets;
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
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class Corpora implements Serializable
{
	private final static long serialVersionUID = 1L;
	private final static Logger log = LogManager.getLogger();

	@XmlRootElement(name = "corpus")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Corpus implements Serializable
	{
		@XmlAttribute
		public String lang;
		@XmlElement(name = "text")
		public List<Text> texts = new ArrayList<>();
		@XmlTransient
		private final static long serialVersionUID = 1L;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Text implements Serializable
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String filename;
		@XmlElement(name = "sentence")
		public List<Sentence> sentences = new ArrayList<>();
		@XmlTransient
		public SemanticGraph graph = null;
		@XmlTransient
		public List<SemanticSubgraph> subgraphs = new ArrayList<>();
		@XmlTransient
		private final static long serialVersionUID = 1L;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Sentence implements Serializable
	{
		@XmlAttribute
		public String id;
		@XmlElement(name = "wf")
		public List<Token> tokens = new ArrayList<>();
		@XmlElement(name = "dependency")
		public List<Dependency> dependencies = new ArrayList<>();
		@XmlTransient
		public List<Mention> ranked_words = new ArrayList<>(); // single words, excluding function words
		@XmlTransient
		public Map<Mention, List<Candidate>> candidate_meanings = new HashMap<>(); // single and multiwords and their candidate meanings
		@XmlTransient
		public Map<Mention, Candidate> disambiguated_meanings = new HashMap<>(); // single and multiwords and their disambiguated meanings
		@XmlTransient
		private final static long serialVersionUID = 1L;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Token implements Serializable
	{
		@XmlAttribute
		public String id;
		@XmlAttribute
		public String lemma;
		@XmlAttribute
		public String pos;
		@XmlValue
		public String wf;
		@XmlTransient
		private final static long serialVersionUID = 1L;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Dependency implements Serializable
	{
		@XmlAttribute
		public String governor;
		@XmlAttribute
		public String relation;
		@XmlAttribute
		public String dependent;
		@XmlTransient
		private final static long serialVersionUID = 1L;
	}

	// factory method
	public static Corpus createFromXML(Path xml_file)
	{
		log.info("Parsing XML");

		try
		{
			final String xml_contents = org.apache.commons.io.FileUtils.readFileToString(xml_file.toFile(), Charsets.UTF_8);
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
		catch (IOException e)
		{
			log.error("Can't read file " + xml_file + ": " + e);
		}

		return new Corpus();
	}

	public static void createGraph(Corpora.Text text, AdjacencyFunction adjacency)
	{
		final List<Mention> mentions = adjacency.getSortedWordMentions();
		final Map<String, List<Mention>> nodes2mentions = mentions.stream()
				.collect(toMap(Mention::getId, List::of));

		final SameMeaningPredicate same_meaning = new SameMeaningPredicate(text);
		final Map<Mention, Candidate> mentions2candidates = same_meaning.getWordMeanings();
		final Map<String, Meaning> nodes2meanings = mentions2candidates.entrySet().stream()
				.collect(toMap(e -> e.getKey().getId(), e -> e.getValue().getMeaning()));
		final Map<String, Double> nodes2weights = mentions2candidates.keySet().stream()
				.map(m -> Pair.of(m.getId(), m.getWeight()))
				.filter(p -> p.getRight().isPresent())
				.collect(toMap(Pair::getLeft, p -> p.getRight().get()));

		text.graph = new SemanticGraph(nodes2mentions.keySet(), nodes2meanings, nodes2weights, nodes2mentions,
				(id1, id2) -> adjacency.test(nodes2mentions.get(id1).get(0), nodes2mentions.get(id2).get(0)),
				(id1, id2) -> adjacency.getLabel(nodes2mentions.get(id1).get(0), nodes2mentions.get(id2).get(0)));
	}

	public static void reset(Corpus corpus)
	{
		corpus.texts.forEach(t ->
				t.sentences.forEach(s ->
				{
					s.disambiguated_meanings.clear();
					s.candidate_meanings.values().forEach(m ->
							m.forEach(c -> c.setWeight(0.0)));
				}));
	}
}
