package edu.upf.taln.textplanning.datastructures;

/**
 * Immutable class to hold information about an annotation
 */
public final class Annotation
{
	private static int counter = 0;
	private final String id; // annotation id, e.g. NIF annotation URI, ConLL id, etc.
	private final String form;
	private final String lemma;
	private final String pos;
	private final String feats; // features column produced by relation extraction tool
	private final String relation;
	private final String role;
	private final String conll; // the conll line this annotation was created from
	private final long offsetStart;
	private final long offsetEnd;

	// Constructor for relations
	public Annotation(String inId, String inForm, String inLemma, String inPOS, String inFeats,
	                  String inRelationName, String inRole, String conll, long offsetStart, long offsetEnd)
	{
		this.id = inId + "_" + ++counter;
		this.form = inForm;
		this.lemma = inLemma;
		this.pos = inPOS;
		this.feats = inFeats.replace("rel==", "");
		this.relation = inRelationName;
		this.role = inRole;
		this.conll = conll;
		this.offsetStart = offsetStart;
		this.offsetEnd = offsetEnd;
	}

	public String getId()
	{
		return id;
	}
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
		return pos;
	}
	public String getFeats()
	{
		return feats;
	}
	@SuppressWarnings("unused")
	public String getRelation()
	{
		return relation;
	}
	public String getRole() { return role; }
	@SuppressWarnings("unused")
	public String getConll() { return conll; }
	public long getOffsetStart() {return offsetStart; }
	@SuppressWarnings("unused")
	public long getOffsetEnd() { return offsetEnd;}

	/**
	 * @return true if node has an argument role assigned to it
	 */
	public boolean isArgument()
	{
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}
}
