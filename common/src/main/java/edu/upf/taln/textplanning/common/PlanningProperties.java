package edu.upf.taln.textplanning.common;

import com.beust.jcommander.ParameterException;
import com.google.common.base.Enums;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors.SentenceVectorType;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PlanningProperties
{
	private Path dictionary = null;
	private Path dictionaryCache = null;
	private VectorType senseVectorsType;
	private Path senseVectorsPath = null;
	private VectorType wordVectorsType;
	private Path wordVectorsPath = null;
	private SentenceVectorType sentenceVectorsType;
	private Path sentenceVectorsPath = null;
	private Path idfPath = null;
	private BiasFunction.Type biasFunction;
	private Path meaningsPath = null;

	private final static Logger log = LogManager.getLogger();

	public PlanningProperties()
	{
		Properties prop = new Properties();
		try (InputStream input = PlanningProperties.class.getClassLoader().getResourceAsStream("config.properties"))
		{
			if (input == null)
			{
				log.error("Sorry, unable to find config.properties");
				return;
			}
			prop.load(input);
		}
		catch (Exception ex)
		{
			log.error("Failed to load properties");
		}

		//get the property value and print it out
		dictionary = checkValidFolder(prop.getProperty("tp.dictionary.folder"));
		dictionaryCache = Paths.get(prop.getProperty("tp.dictionary.cache"));
		senseVectorsType = Enums.getIfPresent(VectorType.class, prop.getProperty("tp.vectors.sense.type")).orNull();
		senseVectorsPath = checkValidPath(prop.getProperty("tp.vectors.sense.path"));
		wordVectorsType = Enums.getIfPresent(VectorType.class, prop.getProperty("tp.vectors.word.type")).orNull();
		wordVectorsPath = checkValidPath(prop.getProperty("tp.vectors.word.path"));
		sentenceVectorsType = Enums.getIfPresent(SentenceVectorType.class, prop.getProperty("tp.vectors.sentence.type")).orNull();
		sentenceVectorsPath = checkValidPath(prop.getProperty("tp.vectors.sentence.path"));
		idfPath = checkValidFile(prop.getProperty("tp.vectors.sentence.idf"));
		biasFunction = BiasFunction.Type.valueOf(prop.getProperty("tp.bias.function"));
		meaningsPath = checkValidFile(prop.getProperty("tp.bias.meanings"));
	}

	public Path getDictionary()
	{
		return dictionary;
	}

	public Path getDictionaryCache()
	{
		return dictionaryCache;
	}

	public VectorType getSenseVectorsType()
	{
		return senseVectorsType;
	}

	public Path getSenseVectorsPath()
	{
		return senseVectorsPath;
	}

	public VectorType getWordVectorsType()
	{
		return wordVectorsType;
	}

	public Path getWordVectorsPath()
	{
		return wordVectorsPath;
	}

	public SentenceVectorType getSentenceVectorsType()
	{
		return sentenceVectorsType;
	}

	public Path getSentenceVectorsPath()
	{
		return sentenceVectorsPath;
	}

	public Path getIdfPath()
	{
		return idfPath;
	}

	public BiasFunction.Type getBiasFunction()
	{
		return biasFunction;
	}

	public Path getMeaningsPath()
	{
		return meaningsPath;
	}

	private Path checkValidFile(String value)
	{
		if (value == null || value.isEmpty())
			return null;

		Path path = Paths.get(value);
		if (!Files.exists(path) || !Files.isRegularFile(path))
		{
			throw new RuntimeException(value + " is not a valid path");
		}
		return path;
	}

	private Path checkValidFolder(String value) throws ParameterException
	{
		if (value == null || value.isEmpty())
			return null;

		Path path = Paths.get(value);
		if (!Files.exists(path) || !Files.isDirectory(path))
		{
			throw new RuntimeException(value + " is not a valid path");
		}

		return path;
	}

	private Path checkValidPath(String value) throws ParameterException
	{
		if (value == null || value.isEmpty())
			return null;

		Path path = Paths.get(value);
		if (!Files.exists(path))
		{
			throw new RuntimeException(value + " is not a valid path");
		}

		return path;
	}
}
