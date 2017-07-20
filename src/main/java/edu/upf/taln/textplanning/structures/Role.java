package edu.upf.taln.textplanning.structures;

import org.jgrapht.graph.DefaultEdge;

/**
 * A role is a binary relation from a node in a graph to another governing node. The role indicated by instances of
 * this class is adopted by the governed node.
 *
 * Roles may be linguistic (syntactic, predicate-argument, semantic, discourse) or ontological (as part of n-ary relations).
 * Immutable class
 */
public final class Role extends DefaultEdge
{
	private final String role; // role type
	private final boolean core; // does the role indicate a core participant in a relation, e.g. an argument of a predicative structure?

	public Role(String role, boolean core)
	{
		this.role = role;
		this.core = core;
	}

	public String getRole()
	{
		return role;
	}

	public boolean isCore()
	{
		return core;
	}

	@Override
	public String toString()
	{
		return role;
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

		Role edge = (Role) o;
		if (core != edge.core || !role.equals(edge.role))
		{
			return false;
		}

		//noinspection SimplifiableIfStatement
		if ((getSource() == null && edge.getSource() != null) || (getTarget() == null && edge.getTarget() != null))
		{
			return false;
		}
		return getSource().equals(edge.getSource()) && getTarget().equals(edge.getTarget());
	}

	@Override
	public int hashCode()
	{
		int result = role.hashCode();
		result = 31 * result + (core ? 1 : 0);
		result = 31 * result + (getSource() != null ? getSource().hashCode() : 0);
		result = 31 * result + (getTarget() != null ? getTarget().hashCode() : 0);
		return result;
	}
}
