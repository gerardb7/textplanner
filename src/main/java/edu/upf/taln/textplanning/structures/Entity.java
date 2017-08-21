package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * An entity can be a word sense, an individual in KB or dataset, a database cells, etc.
 */
public final class Entity
{
	private final String id; // should be unique
	private final String reference;
	private Type type;
	private double weight;
	private static final Map<String, Entity> ids = new HashMap<>(); // to ensure unique Entity objects per id

	// Factory methods
	public static Entity get(String id, String reference, Type type)
	{
		return get(id, reference, type, 0.0);
	}

	public static Entity get(String id, String reference, Type type, double weight)
	{
		if (ids.containsKey(id))
			return ids.get(id);

		Entity e = new Entity(id, reference, type, weight);
		ids.put(id, e);
		return e;
	}

	// Constructor, kept private
	private Entity(String id, String reference, Type type, double weight)
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
}
