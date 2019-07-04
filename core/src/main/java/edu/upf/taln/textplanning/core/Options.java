package edu.upf.taln.textplanning.core;

import edu.upf.taln.textplanning.core.extraction.Policy;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class Options
{
	public Set<POS.Tag> ranking_POS_Tags = new HashSet<>(); // only rank meanings of words with these Tag - other meanings are excluded from the ranking but disambiguated anyway
	public int min_context_freq = 3; // Minimum frequency of document tokens used to calculate nominal context vectors
	public int window_size = 5; // Size of window in number of tokens used to calculate non-nominal context vectors
	public double min_bias_threshold = 0.7; // minimum bias value below which candidate meanings are ignored.  Values in range [0..1].
	public int num_first_meanings = 1; // Number of top dictionary meanings to be included in ranking
	public double sim_threshold = 0.0; // Pairs of meanings with sim below this value have their score set to 0. Values in range [0..1].
	public double damping_meanings = 0.2; // controls balance between bias and similarity. Values in range [0..1]. 0 -> no bias. 1 -> only bias
	public double disambiguation_lambda = 0.2; // penalizes shorter mentions. Value in range [0..1]. 0 -> always choose longest span. 0.5 strictly prefer span with highest weight. 1 -> always choose shortest span.
	public double damping_variables = 0.2; // controls bias towards meanings rank when ranking variables. Values in range [0..1]. 0 -> no bias. 1 -> only bias
	public int num_subgraphs_extract = 100; // Number of sampled subgraphs during extraction
	public double extraction_lambda = 0.8; // Controls size of extracted graphs by balancing value and cost. Values in range [0..1]. higher value -> smaller graphs
	public Policy.Type start_policy = Policy.Type.Softmax; // policy to select a start node from which sample a subgraph
	public Policy.Type expand_policy = Policy.Type.ArgMax; // policy to select additional nodes to add to a subgraph
	public double softmax_temperature = 0.5; // Controls randomness of softmax policy. Values in range [0..1]. 1 is default softamx. 0 is argmax.
	public int num_subgraphs = 10; // Number of subgraphs to include in the plan after redundancy removal
	public double tree_edit_lambda = 0.0; // Controls impact of roles in similarity between semantic trees. Values in range [0..1]. 0 ignores roles.

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
		this.disambiguation_lambda = o.disambiguation_lambda;
		this.damping_variables = o.damping_variables;
		this.num_subgraphs_extract = o.num_subgraphs_extract;
		this.extraction_lambda = o.extraction_lambda;
		this.start_policy = o.start_policy;
		this.expand_policy = o.expand_policy;
		this.softmax_temperature = o.softmax_temperature;
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
				"\n\tdisambiguation_lambda = " + f.format(disambiguation_lambda) +
				"\n\tdamping_variables = " + f.format(damping_variables) +
				"\n\tnum_subgraphs_extract = " + num_subgraphs_extract +
				"\n\textraction_lambda = " + f.format(extraction_lambda) +
				"\n\tstart_policy = " + start_policy +
				"\n\texpand_policy = " + expand_policy +
				"\n\tsoftmax_temperature = " + softmax_temperature +
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
				".dl" + DebugUtils.printDouble(disambiguation_lambda) +
				".dv" + DebugUtils.printDouble(damping_variables) +
				".ns" + num_subgraphs_extract +
				".el" + DebugUtils.printDouble(extraction_lambda) +
				".sp" + start_policy +
				".ep" + expand_policy +
				".st" + softmax_temperature +
				".ns" + num_subgraphs +
				".rl" + DebugUtils.printDouble(tree_edit_lambda);
	}
}
