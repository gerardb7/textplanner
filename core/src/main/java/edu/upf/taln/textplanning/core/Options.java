package edu.upf.taln.textplanning.core;

import edu.upf.taln.textplanning.core.utils.DebugUtils;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class Options
{
	public Set<String> ranking_POS_Tags = new HashSet<>(); // only rank meanings of words with these POS - other meanings are excluded from the ranking but disambiguated anyway
	public int min_context_freq = 3; // Minimum frequency of document tokens used to calculate nominal context vectors
	public int window_size = 5; // Size of window in number of tokens used to calculate non-nominal context vectors
	public double min_bias_threshold = 0.7; // minimum bias value below which candidate meanings are ignored
	public int num_first_meanings = 1;
	public double sim_threshold = 0.0; // Pairs of meanings with sim below this value have their score set to 0
	public double damping_meanings = 0.2; // controls balance between bias and similarity: higher value -> more bias
	public double damping_variables = 0.2; // controls bias towards meanings rank when ranking variables
	public int num_subgraphs_extract = 1000; // Number of subgraphs to extract
	public double extraction_lambda = 1.0; // Controls balance between weight of nodes and cost of edges during subgraph extraction
	public int num_subgraphs = 10; // Number of subgraphs to include in the plan
	public double tree_edit_lambda = 0.1; // Controls impact of roles when calculating similarity between semantic trees

	public Options() {}

	public Options(Options o)
	{
		this.ranking_POS_Tags.addAll(o.ranking_POS_Tags);
		this.min_context_freq = o.min_context_freq;
		this.window_size = o.window_size;
		this.min_bias_threshold = o.min_bias_threshold;
		this.num_first_meanings = o.num_first_meanings;
		this.sim_threshold = o.sim_threshold;
		this.damping_meanings = o.damping_meanings;
		this.damping_variables = o.damping_variables;
		this.num_subgraphs_extract = o.num_subgraphs_extract;
		this.extraction_lambda = o.extraction_lambda;
		this.num_subgraphs = o.num_subgraphs;
		this.tree_edit_lambda = o.tree_edit_lambda;
	}

	@Override
	public String toString()
	{
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		//f.setMaximumFractionDigits(10);
		f.setMinimumFractionDigits(3);
		return  "Options:" +
				"\n\tranking_POS_Tags = " + ranking_POS_Tags +
				"\n\tmin_context_freq = " + min_context_freq +
				"\n\twindow_size = " + window_size +
				"\n\tmin_bias_threshold = " + min_bias_threshold +
				"\n\tnum_first_meanings = " + num_first_meanings +
				"\n\tsim_threshold = " + f.format(sim_threshold) +
				"\n\tdamping_meanings = " + f.format(damping_meanings) +
				"\n\tdamping_variables = " + f.format(damping_variables) +
				"\n\tnum_subgraphs_extract = " + num_subgraphs_extract +
				"\n\textraction_lambda = " + f.format(extraction_lambda) +
				"\n\tnum_subgraphs = " + num_subgraphs +
				"\n\tredundancy lambda = " + f.format(tree_edit_lambda);
	}

	public String toShortString()
	{
		return  "test.cf" + min_context_freq +
				".ws" + window_size +
				".ct" + DebugUtils.printDouble(min_bias_threshold) +
				".nf" + num_first_meanings +
				".st" + DebugUtils.printDouble(sim_threshold) +
				".dm" + DebugUtils.printDouble(damping_meanings) +
				".dv" + DebugUtils.printDouble(damping_variables) +
				".ns" + num_subgraphs_extract +
				".el" + DebugUtils.printDouble(extraction_lambda) +
				".ns" + num_subgraphs +
				".rl" + DebugUtils.printDouble(tree_edit_lambda);
	}
}
