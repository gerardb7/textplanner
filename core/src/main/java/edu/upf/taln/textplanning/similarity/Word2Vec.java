package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Entity;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Computes similarity between word forms according to Word2Vec distributional vectors
 */
public class Word2Vec implements EntitySimilarity
{
	private final WordVectors vectors;
	private final static Logger log = LoggerFactory.getLogger(Word2Vec.class);

	public Word2Vec(Path inEmbeddingsPath) throws IOException
	{
		log.info("Loading Word2Vec vectors");
		Stopwatch timer = Stopwatch.createStarted();
		vectors = WordVectorSerializer.readWord2VecModel(inEmbeddingsPath.toAbsolutePath().toString());
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(Entity inItem)
	{
		return vectors.hasWord(((AnnotatedEntity)inItem).getAnnotation().getForm());
	}

	@Override
	public boolean isDefinedFor(Entity inItem1, Entity inItem2)
	{
		return vectors.hasWord(((AnnotatedEntity)inItem1).getAnnotation().getForm()) &&
				vectors.hasWord(((AnnotatedEntity)inItem2).getAnnotation().getForm());
	}

	@Override
	public double computeSimilarity(Entity inItem1, Entity inItem2)
	{
		String form1 = ((AnnotatedEntity) inItem1).getAnnotation().getForm();
		String form2 = ((AnnotatedEntity) inItem2).getAnnotation().getForm();

		if (form1.equals(form2))
			return 1.0;
		if (!isDefinedFor(inItem1, inItem2))
			return 0.0;

		return vectors.similarity(form1, form2);
	}
}
