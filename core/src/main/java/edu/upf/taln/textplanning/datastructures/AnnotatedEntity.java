package edu.upf.taln.textplanning.datastructures;

/**
 * Annotations of entities in natural language text
 */
public class AnnotatedEntity extends Entity
{
	private final Annotation ann;

	public AnnotatedEntity(Annotation inAnn)
	{
		ann = inAnn;
	}

	public Annotation getAnnotation() { return ann; }

	/**
	 * @return the sense label or, if no sense is annotated, the word lemma
	 */
	@Override
	public String getEntityLabel()
	{
		return ann.getSense() != null ? ann.getSense() : ann.getForm();
	}

	@Override
	public String toString()
	{
		if (getAnnotation().getSense() == null)
			return getAnnotation().getForm();
		else
			return getAnnotation().getSense() + "-" + getAnnotation().getForm();
	}
}
