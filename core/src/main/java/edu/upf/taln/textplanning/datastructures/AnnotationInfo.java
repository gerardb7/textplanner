package edu.upf.taln.textplanning.datastructures;

/**
 * Immutable class to hold information about an annotation
 */
public final class AnnotationInfo implements Comparable<AnnotationInfo>
{
	private final String id; // annotation id, e.g. NIF annotation URI, ConLL id, etc.
	private final String form;
	private final String lemma;
	private final String pos;
	private final String feats; // features column produced by relation extraction tool
	private final String reference; // disambiguated sense or reference to external resource, e.g. WordNet, DBPedia, BabelNet.
	private final double score; // confidence score assigned to reference annotation
	private final String relationName;
	private final String relationId; // id of relation annotation, e.g. NIF/FrameNet annotation URI

	public AnnotationInfo(String inId, String inForm, String inLemma, String inPOS, String inFeats, String inReference,
	                      double inScore)
	{
		this.id = inId;
		this.form = inForm;
		this.lemma = inLemma;
		this.pos = inPOS;
		this.feats = inFeats.replace("rel==", "");
		this.reference = inReference;
		this.score = inScore;
		this.relationName = null;
		this.relationId = null;
	}

	// Constructor for relations
	public AnnotationInfo(String inId, String inForm, String inLemma, String inPOS, String inFeats, String inReference,
	                      double inScore, String inRelationName, String inRelId)
	{
		this.id = inId;
		this.form = inForm;
		this.lemma = inLemma;
		this.pos = inPOS;
		this.feats = inFeats.replace("rel==", "");
		this.reference = inReference;
		this.score = inScore;
		this.relationName = inRelationName;
		this.relationId = inRelId;
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

	public String getReference()
	{
		return reference;
	}

	public double getScore()
	{
		return score;
	}

	public String getRelationName()
	{
		return relationName;
	}

	public String getRelationId()
	{
		return relationId;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		AnnotationInfo that = (AnnotationInfo) o;

		if (id != null && !id.equals(that.id))
		{
			return false;
		}
		if (form != null && !form.equals(that.form))
		{
			return false;
		}
		if (lemma != null && !lemma.equals(that.lemma))
		{
			return false;
		}
		if (pos != null && !pos.equals(that.pos))
		{
			return false;
		}
		if (feats != null && !feats.equals(that.feats))
		{
			return false;
		}
		if (reference != null && !reference.equals(that.reference))
		{
			return false;
		}
		if (score != that.score)
		{
			return false;
		}

		if (relationName != null && !relationName.equals(that.relationName))
		{
			return false;
		}

		return !(relationId != null && !relationId.equals(that.relationId));

	}

	@Override
	public int hashCode()
	{
		int result = id != null ? id.hashCode() : 0;
		result = 31 * result + (form != null ? form.hashCode() : 0);
		result = 31 * result + (lemma != null ? lemma.hashCode() : 0);
		result = 31 * result + (pos != null ? pos.hashCode() : 0);
		result = 31 * result + (feats != null ? feats.hashCode() : 0);
		result = 31 * result + (reference != null ? reference.hashCode() : 0);
		result = 31 * result + Double.hashCode(score);
		result = 31 * result + (relationName != null ? relationName.hashCode() : 0);
		result = 31 * result + (relationId != null ? relationId.hashCode() : 0);
		return result;
	}

//	// Weaker form of equality: two annotations are equivalent if they annotate the same info.
//	public boolean equivalent(AnnotationInfo inOther)
//	{
//		if (reference != null && inOther.reference != null)
//		{
//			return reference.equals(inOther.reference);
//		}
//		return reference == null && inOther.reference == null && lemma.equals(inOther.lemma);
//	}

	public final String toString()
	{
		return  (reference != null ? reference : "") +
				((lemma != null && reference != null) ? "-" : "") +
				(lemma != null ? lemma : "");
	}

	@Override
	public int compareTo(AnnotationInfo other)
	{
		if (this.equals(other))
		{
			return 0;
		}

		if (reference != null && other.reference != null && !reference.equals(other.reference))
		{
			return reference.compareTo(other.reference);
		}
		if (lemma != null && other.lemma != null && !lemma.equals(other.lemma))
		{
			return lemma.compareTo(other.lemma);
		}
		if (form != null && other.form != null && !form.equals(other.form))
		{
			return form.compareTo(other.form);
		}
		if (pos != null && other.pos != null && !pos.equals(other.pos))
		{
			return pos.compareTo(other.pos);
		}
		if (feats != null && other.feats != null && !feats.equals(other.feats))
		{
			return feats.compareTo(other.feats);
		}
		if (score != other.score)
		{
			return Double.compare(score, other.score);
		}
		if (relationName != null && other.relationName != null && !relationName.equals(other.relationName))
		{
			return relationName.compareTo(other.relationName);
		}
		if (relationId != null && other.relationId != null && !relationId.equals(other.relationId))
		{
			return relationId.compareTo(other.relationId);
		}
		if (id != null && other.id != null && !id.equals(other.id))
		{
			return id.compareTo(other.id);
		}

		return 0;
	}
}
