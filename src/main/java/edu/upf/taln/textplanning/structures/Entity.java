package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;

/**
 * An entity can be a word sense, an individual in KB or dataset, a database cells, etc.
 */
public final class Entity
{
	private final String id;
	private final String reference;
	private Type type;
	private double weight;

	public Entity(String id, String reference, Type type) { this(id, reference, type, 0.0); }
	public Entity(String id, String reference, Type type, double weight)
	{
		this.id = id;
		this.reference = reference;
		this.type = type;
		this.weight = weight;
	}

	public String getId() { return id; }
	public String getReference() { return reference; }
	public Type getType() { return type; }
	public void setType(Type type) { this.type = type; }
	public double getWeight() { return weight; }
	public void setWeight(double w) { weight = w; }

	@Override
	public String toString() { return id; }

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

		Entity entity = (Entity) o;

		return Double.compare(entity.weight, weight) == 0 && id.equals(entity.id) && reference.equals(entity.reference)
				&& type == entity.type;
	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		result = id.hashCode();
		result = 31 * result + reference.hashCode();
		result = 31 * result + type.hashCode();
		temp = Double.doubleToLongBits(weight);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
}
