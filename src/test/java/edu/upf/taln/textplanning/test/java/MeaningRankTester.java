package edu.upf.taln.textplanning.test.java;

import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.similarity.RandomAccessVectorsSimilarity;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.weighting.NoWeights;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

public class MeaningRankTester
{
	private Collection<Candidate> createCandidates()
	{
		Collection<Candidate> candidates = new ArrayList<>();
		{
			Mention mention = new Mention("s1", Pair.of(0, 1), "Havana", "Havana", "N", Candidate.Type.Other);
			candidates.add(new Candidate(Meaning.get("bn:00469973n", "WIKIRED:EN:Havana,_ND", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00015532n", "WN:EN:Havana,", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00191913n", "WIKIRED:EN:Havana,_AR", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:03339019n", "WIKI:EN:Havana_(film)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:01412273n", "WIKI:EN:Havana_(juggling)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:01033004n", "WIKI:EN:Havana_(song)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:01918763n", "WIKI:EN:Havana_(Edwardian_musical)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:17169069n", "WIKIRED:EN:Hibou_noir", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00562898n", "GEONM:EN:Havana,_Texas", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00490939n", "WIKIRED:EN:Havana_(2010_musical),", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:03295685n", "WIKI:EN:Havana_(novel)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00207677n", "WIKI:EN:Havana_(soundtrack)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:01520969n", "WIKIRED:EN:Havana_rabbit", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00239041n", "WIKIRED:EN:Havana,_IL", false), mention));
		}
		{
			Mention mention = new Mention("s1", Pair.of(1, 2), "Cuba", "Cuba", "N", Candidate.Type.Other);
			candidates.add(new Candidate(Meaning.get("bn:00024247n","OMWIKI:EN:Republic_of_Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00024248n","WN:EN:Cuba,", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:01950379n","WIKI:EN:Cuba_(village),_New_York", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:14854471n","WIKIDATA:EN:Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00951028n","WIKI:EN:Cuba_(film)", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00277486n","GEONM:EN:Cuba,_Kansas", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00024247n","OMWIKI:EN:Republic_of_Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:15216323n","WIKIDATA:EN:Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:03362508n","WIKIRED:EN:“pseudo”_republic", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:16249402n","WIKI:EN:Air_Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00170427n","WIKIRED:EN:Cuba,_AL", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:03580354n","WIKIRED:EN:SS_Coblenz", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:16352753n","WIKIDATA:EN:Cuba", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:14223823n","WIKI:EN:Cuba,_Portugal", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:00234020n","GEONM:EN:Cuba,_Illinois", false), mention));
			candidates.add(new Candidate(Meaning.get("bn:16902188n","WIKIRED:EN:Cuba_(town),_New_York", false), mention));

		}
		return candidates;
	}

	@Test
	public void rankMeaningsTest() throws IOException
	{
		WeightingFunction weighting = new NoWeights();
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/NASARIembed+UMBC_w2v_bin");
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/sensembed-vectors-merged_bin");
		Path randoma_access_vectors = Paths.get("/home/gerard/data/sew-embed.nasari_bin");
		//Path randoma_access_vectors = Paths.get("/home/gerard/data/sew-embed.w2v_bin");
		SimilarityFunction similarity = new RandomAccessVectorsSimilarity(randoma_access_vectors);

		final Collection<Candidate> candidates = createCandidates();
		TextPlanner.Options options = new TextPlanner.Options();
		TextPlanner.rankMeanings(candidates, weighting, similarity, options);
	}
}
