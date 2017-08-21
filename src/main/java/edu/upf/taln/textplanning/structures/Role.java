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
}
