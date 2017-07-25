package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * An word in a document
 */
public final class AnnotatedWord
{
	private final Document document;
	private LinguisticStructure structure;
	private final String form;
	private final String lemma;
	private final String POS;
	private final String feats; // features column produced by relation extraction tool
	private final String role;
	private final String conll; // the conll line this annotation was created from
	private final long offsetStart;
	private final long offsetEnd;
	private Type type = Type.Other;
	private final Set<Mention> mentions = new HashSet<>();
	private final Set<Candidate> candidates = new HashSet<>(); // candidate senses and NE types associated tot his word or multiwords (Mention) having this word as their head

	public AnnotatedWord(Document d, LinguisticStructure s, String form, String lemma, String pos, String feats, String role,
	                     String conll, long offsetStart, long offsetEnd)
	{
		document = d;
		structure = s;
		this.form = form;
		this.lemma = lemma;
		this.POS = pos;
		this.feats = feats.replace("rel==", "");
		this.role = role;
		this.conll = conll;
		this.offsetStart = offsetStart;
		this.offsetEnd = offsetEnd;
	}

	public Document getDocument() { return document; }
	public LinguisticStructure getStructure() { return structure; }
	public void  setStructure(LinguisticStructure s) { structure = s; }
	public String getForm()
	{
		return form;
	}
	public String getLemma()
	{
		return lemma;
	}
	public String getPOS()
	{
		return POS;
	}
	public String getFeats()
	{
		return feats;
	}
	public String getRole() { return role; }
	public String getConll() { return conll; }
	public long getOffsetStart() {return offsetStart; }
	public long getOffsetEnd() { return offsetEnd;}
	public Type getType() { return type; }
	public void setType(Type type) { this.type = type; }
	public Optional<Candidate> getBestCandidate() { return candidates.stream().max(Comparator.comparingDouble(Candidate::getValue)); }
	public Set<Candidate> getCandidates()
	{
		return new HashSet<>(candidates);
	}

	public Set<Candidate> getCandidates(Mention m)
	{
		return candidates.stream()
				.filter(c -> c.getMention() == m)
				.collect(toSet());
	}

	public Set<Mention> getMentions() {	return new HashSet<>(mentions); }

	public Optional<Mention> getMention(Entity e)
	{
		return candidates.stream()
				.filter(c -> c.getEntity().equals(e))
				.map(Candidate::getMention)
				.findFirst();
	}

	public Optional<Mention> getMention(List<AnnotatedWord> words)
	{
		return mentions.stream()
				.filter(m -> m.getNumTokens() == words.size())
				.filter(m -> m.getTokens().equals(words))
				.findFirst();
	}

	public Mention addMention(List<AnnotatedWord> words)
	{
		Mention m = getMention(words).orElse(new Mention(this.structure, words, words.indexOf(this)));
		mentions.add(m); // does nothing if m already in mentions
		return m;
	}

	public void addCandidate(Entity e, Mention m)
	{
		mentions.add(m);
		candidates.add(new Candidate(m, e));
	}

	/**
	 * @return true if node has an argument role assigned to it
	 */
	public boolean isArgument()
	{
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}

	@Override
	public String toString()
	{
		return form + "_" + POS;
	}
}
