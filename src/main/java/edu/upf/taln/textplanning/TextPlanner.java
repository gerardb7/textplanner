package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.disambiguation.BabelNetAnnotator;
import edu.upf.taln.textplanning.disambiguation.EntityDisambiguator;
import edu.upf.taln.textplanning.optimization.MultiObjectiveOptimizationRanking;
import edu.upf.taln.textplanning.pattern.PatternExtraction;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.ranking.RankingMatrices;
import edu.upf.taln.textplanning.similarity.CandidateSimilarity;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.structures.*;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text planPageRank
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final Corpus corpus;
	private final WeightingFunction weighting;
	private final EntitySimilarity esim;
	private final EntityDisambiguator disambiguator;
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		public int numPatterns = 25; // Number of patterns to return
		public double dampingRelevance = 0.2; // controls bias towards entity relevance
		public double dampingType = 0.1; // controls bias towards type matching in candidates
		public double rankingStopThreshold = 0.00000001; // stopping threshold for the main ranking algorithm
		public double minRelevance = 0.0001; // pseudocount α for additive smoothing of relevance values
		public double simLowerBound = 0.6; // Pairs of entities with similarity below this value have their score set to 0
		public double patternLambda = 1.0; // Controls balance between weight of nodes and cost of edges during pattern extraction
		public boolean generateStats = false;
		public String stats = "";

		@Override
		public String toString()
		{
			NumberFormat f = NumberFormat.getInstance();
			f.setRoundingMode(RoundingMode.UP);
			//f.setMaximumFractionDigits(10);
			f.setMinimumFractionDigits(3);
			return "numPatterns=" + numPatterns + " damping_rel=" + f.format(dampingRelevance) +
					" ranking_threshold=" + f.format(rankingStopThreshold) + " min_rel=" + f.format(minRelevance) +
					" min_sim=" + f.format(simLowerBound) + " pattern_lambda=" + f.format(patternLambda) +
					"\n\n" + stats;
		}
	}

	/**
	 * @param w functions used to weight contents
	 * @param s similarity function for entities
	 * @param d disambiguation for entities
	 */
	public TextPlanner(Corpus c, WeightingFunction w, EntitySimilarity s, EntityDisambiguator d)
	{
		corpus = c;
		weighting = w;
		esim = s;
		disambiguator = d;
	}

	/**
	 * Generates a text planPageRank from a list of structures, e.g. relations in a KB or extracted from a text
	 * @param structures initial set of structures
	 * @return list of patterns
	 */
	public List<ContentPattern> planPageRank(Set<LinguisticStructure> structures, Options o)
	{
		try
		{
			log.info("Planning started");
			// todo remove after re-processing test inputs
			BabelNetAnnotator.propagateCandidatesCoreference(structures);

			// 1- Create entity ranking matrix
			log.info("Creating ranking matrix");
			Stopwatch timer = Stopwatch.createStarted();
			timer.reset(); timer.start();
			List<Entity> entities = structures.stream()
					.flatMap(s -> s.vertexSet().stream()
							.map(AnnotatedWord::getBestCandidate))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(Candidate::getEntity)
					.collect(Collectors.toList());
			weighting.setContents(structures);
			Matrix rankingMatrix =
					RankingMatrices.createEntityRankingMatrix(entities, weighting, esim, o);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 4- Rank candidates using power iteration method
			log.info("Candidate ranking");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, o.rankingStopThreshold);
			log.info("Ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();
			IntStream.range(0, ranking.length)
					.forEach(i -> entities.get(i).setWeight(ranking[i])); // assign ranking values to candidates

			// 6- Create content graph
			log.info("Creating content graph");
			timer.reset(); timer.start();
			ContentGraph contentGraph = ContentGraphCreator.createContentGraph(structures);
			log.info("Graph creation took " + timer.stop());

			// 7- Extract patterns from content graph
			log.info("Extracting patterns");
			timer.reset(); timer.start();
			List<ContentPattern> patterns = PatternExtraction.extract(contentGraph, o.numPatterns, o.patternLambda);
			log.info("Pattern extraction took " + timer.stop());

			// 8- Sort the trees into a discourse-optimized list
			log.info("Structuring patterns");
			timer.reset(); timer.start();
			//patterns = DiscoursePlanner.structurePatterns(patterns, esim);
			log.info("Pattern structuring took " + timer.stop());

			// 9- Generate stats (optional)
			if (o.generateStats)
			{
				log.info("Generating stats");
				timer.reset();
				timer.start();
				//o.stats = StatsReporter.reportStats(structures, weighting, sim, contentGraph, o);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Performs EL/WSD, coreference on a set of documents, and creates a text planPageRank from the resulting disambiguated
	 * structures.
	 * @param structures initial set of structures
	 * @return list of patterns
	 */
	public List<ContentPattern> planAndDisambiguatePageRank(Set<LinguisticStructure> structures, Options inOptions)
	{
		try
		{
			log.info("Planning started");
			// todo remove after re-processing test inputs
			BabelNetAnnotator.propagateCandidatesCoreference(structures);

			// 1- Create candidate ranking matrix
			log.info("Creating ranking matrix");
			Stopwatch timer = Stopwatch.createStarted();
			List<Candidate> candidates = structures.stream()
					.flatMap(s -> s.vertexSet().stream()
							.flatMap(n -> n.getMentions().stream()
									.flatMap(m -> n.getCandidates(m).stream())))
					.collect(Collectors.toList());
			weighting.setContents(structures);
			CandidateSimilarity sim = new CandidateSimilarity(esim);
			Matrix rankingMatrix =
					RankingMatrices.createCandidateRankingMatrix(candidates, weighting, sim, inOptions);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 2- Rank candidates using power iteration method
			log.info("Candidate ranking");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();
			IntStream.range(0, ranking.length)
					.forEach(i -> candidates.get(i).setValue(ranking[i])); // assign ranking values to candidates

			// 3- Use ranking to disambiguate candidates in structures and weight nodes
			log.info("Candidate disambiguation");
			timer.reset(); timer.start();
			disambiguator.disambiguate(structures);
			log.info("Disambiguation took " + timer.stop());

			// 4- Create content graph
			log.info("Creating content graph");
			timer.reset(); timer.start();
			ContentGraph contentGraph = ContentGraphCreator.createContentGraph(structures);
			log.info("Graph creation took " + timer.stop());

			// 5- Extract patterns from content graph
			log.info("Extracting patterns");
			timer.reset(); timer.start();
			List<ContentPattern> patterns = PatternExtraction.extract(contentGraph, inOptions.numPatterns, inOptions.patternLambda);
			log.info("Pattern extraction took " + timer.stop());

			// 6- Sort the trees into a discourse-optimized list
			log.info("Structuring patterns");
			timer.reset(); timer.start();
			//patterns = DiscoursePlanner.structurePatterns(patterns, esim);
			log.info("Pattern structuring took " + timer.stop());

			// Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("Generating stats");
				timer.reset();
				timer.start();
				//inOptions.stats = StatsReporter.reportStats(structures, weighting, sim, contentGraph, inOptions);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}


	/**
	 * Performs EL/WSD, coreference on a set of documents, and creates a text planPageRank from the resulting disambiguated
	 * structures.
	 * @param structures initial set of structures
	 * @return list of patterns
	 */
	public List<ContentPattern> planAndDisambiguateOptimizer(Set<LinguisticStructure> structures, Options inOptions)
	{
		try
		{
			log.info("Planning started");

			// 1- Create candidate ranking matrix
			log.info("Ranking candidates");
			Stopwatch timer = Stopwatch.createStarted();
			weighting.setContents(structures);
			CandidateSimilarity sim = new CandidateSimilarity(esim);
			// todo sort out access to TFIDF or corpus class
			MultiObjectiveOptimizationRanking.optimize(structures, corpus, sim, weighting);
			log.info("Ranking took " + timer.stop());

			// 2- Use ranking to disambiguate candidates in structures and weight nodes
			log.info("Candidate disambiguation");
			timer.reset(); timer.start();
			disambiguator.disambiguate(structures);
			log.info("Disambiguation took " + timer.stop());

			// 3- Create content graph
			log.info("Creating content graph");
			timer.reset(); timer.start();
			ContentGraph contentGraph = ContentGraphCreator.createContentGraph(structures);
			log.info("Graph creation took " + timer.stop());

			// 4- Extract patterns from content graph
			log.info("Extracting patterns");
			timer.reset(); timer.start();
			List<ContentPattern> patterns = PatternExtraction.extract(contentGraph, inOptions.numPatterns, inOptions.patternLambda);
			log.info("Pattern extraction took " + timer.stop());

			// 5- Sort the trees into a discourse-optimized list
			log.info("Structuring patterns");
			timer.reset(); timer.start();
			//patterns = DiscoursePlanner.structurePatterns(patterns, esim);
			log.info("Pattern structuring took " + timer.stop());

			// Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("Generating stats");
				timer.reset();
				timer.start();
				//inOptions.stats = StatsReporter.reportStats(structures, weighting, sim, contentGraph, inOptions);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}


}
