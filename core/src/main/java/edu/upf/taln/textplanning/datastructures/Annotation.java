package edu.upf.taln.textplanning.datastructures;

/**
 * Immutable class to hold information about an annotation
 */
public final class Annotation implements Comparable<Annotation>
{
	private static int counter = 0;
	private final String id; // annotation id, e.g. NIF annotation URI, ConLL id, etc.
	private final String form;
	private final String lemma;
	private final String pos;
	private final String feats; // features column produced by relation extraction tool
	private final String sense; // disambiguated reference to a sense in some external resource, e.g. WordNet, DBPedia, BabelNet.
	private final double score; // confidence score assigned to sense annotation
	private final String relation;
	private final String role;

	// Constructor for relations
	public Annotation(String inId, String inForm, String inLemma, String inPOS, String inFeats, String inSense,
	                  double inScore, String inRelationName, String inRole)
	{
		this.id = inId + "_" + ++counter;
		this.form = inForm;
		this.lemma = inLemma;
		this.pos = inPOS;
		this.feats = inFeats.replace("rel==", "");
		this.sense = inSense;
		this.relation = inRelationName;
		this.role = inRole;
		this.score = inScore;
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

	public String getSense()
	{
		return sense;
	}

	public String getRelation()
	{
		return relation;
	}

	public String getRole() { return role; }

	public double getScore()
	{
		return score;
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

		Annotation that = (Annotation) o;

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
		if (sense != null && !sense.equals(that.sense))
		{
			return false;
		}
		if (relation != null && !relation.equals(that.relation))
		{
			return false;
		}
		if (role != null && !role.equals(that.role))
		{
			return false;
		}
		return score == that.score;

	}

	@Override
	public int hashCode()
	{
		int result = id != null ? id.hashCode() : 0;
		result = 31 * result + (form != null ? form.hashCode() : 0);
		result = 31 * result + (lemma != null ? lemma.hashCode() : 0);
		result = 31 * result + (pos != null ? pos.hashCode() : 0);
		result = 31 * result + (feats != null ? feats.hashCode() : 0);
		result = 31 * result + (sense != null ? sense.hashCode() : 0);
		result = 31 * result + (relation != null ? relation.hashCode() : 0);
		result = 31 * result + (role != null ? role.hashCode() : 0);
		result = 31 * result + Double.hashCode(score);
		return result;
	}


	public final String toString()
	{
		return  (sense != null ? sense : "") +
				((lemma != null && sense != null) ? "-" : "") +
				(lemma != null ? lemma : "");
	}

	@Override
	public int compareTo(Annotation other)
	{
		if (this.equals(other))
		{
			return 0;
		}

		if (sense != null && other.sense != null && !sense.equals(other.sense))
		{
			return sense.compareTo(other.sense);
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
		if (relation != null && other.relation != null && !relation.equals(other.relation))
		{
			return relation.compareTo(other.relation);
		}
		if (role != null && other.role != null && !role.equals(other.role))
		{
			return role.compareTo(other.role);
		}
		if (score != other.score)
		{
			return Double.compare(score, other.score);
		}
		if (id != null && other.id != null && !id.equals(other.id))
		{
			return id.compareTo(other.id);
		}

		return 0;
	}
}
