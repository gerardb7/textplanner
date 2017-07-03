package edu.upf.taln.textplanning.datastructures;

/**
 * An entity can be a word sense, an individual in KB or dataset, a database cells, etc.
 * Immutable class
 */
public final class Entity
{
	private final String label;
	private double weight;

	public Entity(String label) { this(label, 0.0); }
	public Entity(String label, double weight) { this.label = label; this.weight = weight; }

	public String getLabel() { return label; }
	public double getWeight() { return weight; }
	public void setWeight(double w) { weight = w; }

	@Override
	public String toString() { return label; }
}
