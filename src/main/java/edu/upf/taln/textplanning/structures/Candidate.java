package edu.upf.taln.textplanning.structures;

/**
 * Wrapper for a pair of a mention and a candidate entity
 */
public class Candidate
{
	public enum Type {Location, Organization, Person, Other}

	private final Mention m;
	private final Entity e;
	private double value = 0.0;

	public Candidate(Mention m, Entity e)
	{
		this.m = m;
		this.e = e;
	}

	public Mention getMention() { return m; }
	public Entity getEntity() { return e; }
	public AnnotatedWord getNode() { return m.getHead(); }
	public double getValue() { return value; }
	public void setValue(double v) { value = v; }

	@Override
	public String toString()
	{
		return m.getSurfaceForm() + "-" + e.getId();
	}
}
