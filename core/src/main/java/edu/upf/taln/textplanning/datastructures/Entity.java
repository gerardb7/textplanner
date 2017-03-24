package edu.upf.taln.textplanning.datastructures;

/**
 * Base class for semantic entities (word senses, individuals in SW datasets, database cells, etc.)
 * Please note that two entities are the same if they share the same label.
 */
public abstract class Entity
{
	public abstract String getEntityLabel();

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof Entity))
			return false;

		Entity other = (Entity) obj;
		return getEntityLabel().equals(other.getEntityLabel());
	}

	@Override
	public int hashCode()
	{
		return getEntityLabel().hashCode();
	}
}
