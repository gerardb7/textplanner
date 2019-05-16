# Text Planner #

This repo contains code for an in-progress tool, the **Text Planner**. Given one or more texts, the Text Planner produces two separate ranks, 1) a rank of disambiguated meanings and 2) a rank of fragments of the original text. These ranks can be applied to a variety of downstream tasks such as extractive and abstractive summarization, text simplification, information extraction, semantic indexing, etc.

The Text Planner implements a novel unsupervised approach that is largely independent from language and can be applied to texts of any lenght, genre and domain. It draws methods from research in WSD, Entity Linking and automatic summarization to address these tasks jointly using a global graph-based strategy to rank candidate meanings and their mentions in the text. It only requires shallow lingusitic analysis -sentence splitting, tokenization and POS tagging.

# Main features
* New languages can be supported by providing some common language-specific resources: a linguistic pre-processing pipeline (sentence splitting, tokenizer and POS tagging), a [list of stop words](https://github.com/stopwords-iso) and a set of [pre-trained word embeddings](https://fasttext.cc/docs/en/crawl-vectors.html). 
* Various lexicographic resources can be used as target repositories of meanings. Our default implementation uses [BabelNet](https://babelnet.org/), but the approach can be migrated with minimal effort to langauge-specific versions of [DBPedia](https://wiki.dbpedia.org/), Wikipedia, [WordNet](https://wordnet.princeton.edu/), etc.
* Dependency-based syntactic or semantic structures can be incorporated into the ranking calculations. This enables the Text Planner to produce lingusitic structures that, coupled with natural language generation methods, can be applied to produce paraphrased versions of the input text, e.g. abstractive summaries, simplified texts, etc. This repo contains code for incoporating [AMR](https://amr.isi.edu/) and [DSynt](https://www.cambridge.org/core/journals/natural-language-engineering/article/datadriven-deepsyntactic-dependency-parsing/BC72079B9AC388F47729C2E1664D19B1/core-reader) analyses.
* This tool can be integrated as a component of an [Apache UIMA](https://uima.apache.org/) pipeline.
* The Text Planner is fully implemented with Java 8+ and makes heavy use of Streams and lambdas to guarantee safe concurrent execution of computationally intensive tasks.
* [Trove collections](http://java-performance.info/primitive-types-collections-trove-library/) and random access files are used to reduce memory footprint

# Structure
Code is organized into multi-module maven project: 
* *core* contains the code of the text planning library. 
* *tools* has evaluation scripts to test the tool using the [SemEval 2015 Shared Task 13 dataset](http://alt.qcri.org/semeval2015/task13/) and the [DeepMind Q&A dataset](https://cs.nyu.edu/~kcho/DMQA/). 
* *amr* runs the ranker on the ouput of an AMR parser
* *dsynt-uima* contains two UIMA wrappers for the text planning, which can be ran as separate WSD and text ranking components.
* *optimization* contains an alternative approach to ranking based on multiobjective optimization with softmax distributions

# Future work
* A demo will be set up to show the capabilities of the tool and some of its applications
* Publications describing the scientific work behind the tool and evaluations will be posted as they appear in publications 

