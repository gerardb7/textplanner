# Text Planner #

The Text Planner is an experimental tool that analyzes one or more texts and produces two separate ranks, 1) a rank of disambiguated meanings and 2) a rank of fragments of the original text. These ranks can be applied to a variety of downstream tasks such as extractive and abstractive summarization, text simplification, information extraction, semantic indexing, etc.

The planner implements a novel unsupervised approach that is largely independent from language and can be applied to texts of any lenght, genre and domain. It draws methods from research in WSD, Entity Linking and automatic summarization to address these tasks jointly using a global graph-based strategy to rank candidate meanings and their mentions in the text.

# Main features
* New languages can be supported by providing some common language-specific resources: a linguistic pre-processing pipeline (sentence splitting, tokenizer and POS tagging), a list of stop words and a set of pre-trained word embeddings. 
* Various lexicographic resources can be used as target repositories of meanings. Our default implementation uses BabelNet, but the approach can be migrated with minimal effort to langauge-specific versions of DBPedia, Wikipedia, WordNet, etc.
* Dependency-based syntactic or semantic structures can be incorporated into the ranking calculations. This enables the Text Planner to produce lingusitic structures that, coupled with natural language generation methods, can be applied to produce paraphrased versions of the input text, e.g. abstractive summaries, simplified texts, etc. Our repo contains code for incoporating AMR and DSynt analyses.
* The planner can be integrated as a component of a UIMA-based pipeline.
* The Text Planner is fully implemented with Java 8+ and makes heavy use of Streams and lambdas to guarantee safe concurrent execution of computationally intensive tasks.

# Usage

# Future work


