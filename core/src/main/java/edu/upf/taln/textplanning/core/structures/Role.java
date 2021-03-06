package edu.upf.taln.textplanning.core.structures;

import org.jgrapht.graph.DefaultEdge;

import java.util.concurrent.atomic.AtomicInteger;

public class Role extends DefaultEdge
{
	private static AtomicInteger counter = new AtomicInteger(0);
	private String label;
	private int id; // the purpose of this id is to keep all Role objects distinct

	public static Role create(String label) { return new Role(label); }

	private Role(String label)
	{
		this.label = label;
		this.id = counter.getAndIncrement();
	}

	public String getLabel() { return this.label; }

	@Override public String toString() { return label; }

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Role role = (Role) o;

		if (id != role.id) return false;
		return label.equals(role.label);
	}

	@Override
	public int hashCode()
	{
		int result = label.hashCode();
		result = 31 * result + id;
		return result;
	}
}
