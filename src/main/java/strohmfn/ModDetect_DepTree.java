
package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

/**
 * 
 * @author strohmfn
 *
 */
public class ModDetect_DepTree {

	// stores all words in the negation lexicon
	private ArrayList<String> negationLexicon = new ArrayList<String>();
	// stores all words in the intensifier lexicon
	private ArrayList<String> intensifierLexicon = new ArrayList<String>();
	// stores all words in the diminisher lexicon
	private ArrayList<String> diminisherLexicon = new ArrayList<String>();

	// stores settings for the modifier detection
	private boolean[] modifierDetectionSettings;

	/**
	 * 
	 * Loads modifier lexicons.
	 * 
	 * @param modifierLexicons
	 *            Array that contains the negation, intensifier and diminisher lexicon
	 * @param modifierDetectionSettings
	 *            Says which modifier types are supposed to be considered
	 * @throws Exception
	 *             Throws exception if the lexicons can not be read.
	 */
	public ModDetect_DepTree(File[] modifierLexicons, boolean[] modifierDetectionSettings) throws Exception {
		this.modifierDetectionSettings = modifierDetectionSettings;
		// loads lexica
		loadNegationLexicon(modifierLexicons[0]);
		loadIntensifierLexicon(modifierLexicons[1]);
		loadDiminisherLexicon(modifierLexicons[2]);
	}

	/**
	 * Annotates modifier cues and scope in the given corpus using dependency trees.
	 * 
	 * @param corpus
	 *            The to be annotated corpus.
	 */
	public void annotateDependencyTree(ArrayList<Tweet> corpus) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, parse, depparse");
		props.setProperty("depparse.extradependencies", "MAXIMAL");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		// Needed to display percentage done.
		int numberOfTweets = corpus.size();
		int numberOfTweetsFinished = 0;
		byte percentageDone = 0;
		// Loads all conjunctions into a list.
		ArrayList<String> conjunctions = loadConjunctions();
		// Iterate over corpus.
		Iterator<Tweet> tweetIter = corpus.iterator();
		while (tweetIter.hasNext()) {
			// Retrieve next Tweet.
			Tweet currentTweet = tweetIter.next();
			// Annotate modifier cues in current token list.
			for (int i = 0; i < currentTweet.getTokenList().size(); i++) {
				Token currentToken = currentTweet.getTokenList().get(i);
				if (negationLexicon.contains(currentToken.getNormalizedTokenString())) {
					currentToken.setNegator(true);
				} else if (intensifierLexicon.contains(currentToken.getNormalizedTokenString())) {
					currentToken.setIntensifier(true);
				} else if (diminisherLexicon.contains(currentToken.getNormalizedTokenString())) {
					currentToken.setDiminisher(true);
				}
			}

			// Create an empty Annotation just with the given text.
			Annotation document = new Annotation(currentTweet.getOriginalText());

			// Run all Annotators on this text.
			pipeline.annotate(document);
			// These are all the sentences in this document.
			// A CoreMap is essentially a Map that uses class objects as keys and has values with
			// custom types.
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			// This offset is used to map the token list indices to the sentence indices.
			int sentenceOffset = 0;
			// Iterate over all sentences of the current Tweet.
			for (CoreMap sentence : sentences) {
				// This is the Stanford dependency graph of the current sentence.
				SemanticGraph dependencies = sentence.get(EnhancedDependenciesAnnotation.class);
				// dependencies.prettyPrint();
				// Iterate over the dependency graph (= iterate over sentence).
				for (int i = 1; i <= dependencies.size(); i++) {
					IndexedWord node = dependencies.getNodeByIndex(i);
					List<IndexedWord> childList = dependencies.getChildList(node);
					Token modifierToken = null;
					int modifierType = 0;
					// Iterate over the child list of the current token.
					// If a child is a modifier, store to token of this child and its modifier type.
					for (int j = 0; j < childList.size(); j++) {
						IndexedWord child = childList.get(j);
						int childIndex = child.index() - 1;
						Token token = currentTweet.getTokenList().get(childIndex + sentenceOffset);
						if (token.isNegator()) {
							modifierToken = token;
							modifierType = 1;
							break;
						} else if (token.isIntensifier()) {
							modifierToken = token;
							modifierType = 2;
							break;
						} else if (token.isDiminisher()) {
							modifierToken = token;
							modifierType = 3;
							break;
						}
					}
					// Check if any child was a modifier.
					if (modifierToken != null) {
						// Retrieve the token of the current node 'i'.
						Token modifiedToken = currentTweet.getTokenList().get(node.index() - 1 + sentenceOffset);
						// Do not modify if the token starts with '#'.
						if (!modifiedToken.getNormalizedTokenString().startsWith("#")) {
							/*
							 * The following three if statements are essentially all the same, they
							 * only differ in the modifier type. So one block for negation, one for
							 * intensifier and one for diminisher.
							 */

							// Always check for the correct modifier type and if this type of
							// modifier shall be annotated at all according to the modifier
							// detection settings.
							if (modifierType == 1 && modifierDetectionSettings[0]) {
								// Mark the token as negated.
								modifiedToken.setNegated(true);
								modifiedToken.setNormalizedTokenString("NEG_" + modifiedToken.getNormalizedTokenString());
								// Add this token to its modifier 'modifies list'.
								modifierToken.getModifies().add(modifiedToken);

								/*
								 * In the following we also consider all outgoing edges of the
								 * modified node. If an outgoing edge is a conjunction relation, the
								 * node reachable through that edge will also get modified except if
								 * the conjunction token is one of the words in the 'conjunctions'
								 * list. The list contains words like 'but', 'however' and
								 * 'although' which do not act as a 'conjunction for the
								 * modification' (adversative conjunctions).
								 */

								// Retrieve all outgoing edges of the current node 'i'.
								List<SemanticGraphEdge> outEdge = dependencies.getOutEdgesSorted(node);
								// Iterate over them.
								for (int k = 0; k < outEdge.size(); k++) {
									// Get edge relation.
									String edgeRelation = outEdge.get(k).getRelation().toString();
									// The edge relation is of the kind conj:WORD, e.g. conj:but
									String[] edgeRelations = edgeRelation.split(":");
									// Check if it is a 'conj' relation and if the conjunction word
									// is not in the 'conjunctions' list.
									if (edgeRelations[0].equals("conj") && edgeRelations.length == 2 && !conjunctions.contains(edgeRelations[1])) {
										// Retrieve the corresponding token.
										modifiedToken = currentTweet.getTokenList().get(outEdge.get(k).getTarget().index() - 1 + sentenceOffset);
										// Modifie the token if it does not start with '#'.
										if (!modifiedToken.getNormalizedTokenString().startsWith("#")) {
											modifiedToken.setNegated(true);
											modifiedToken.setNormalizedTokenString("NEG_" + modifiedToken.getNormalizedTokenString());
											modifierToken.getModifies().add(modifiedToken);
										}
									}
								}

							} else if (modifierType == 2 && modifierDetectionSettings[1]) {
								modifiedToken.setIntensified(true);
								modifiedToken.setNormalizedTokenString("INT_" + modifiedToken.getNormalizedTokenString());
								modifierToken.getModifies().add(modifiedToken);
								List<SemanticGraphEdge> outEdge = dependencies.getOutEdgesSorted(node);
								for (int k = 0; k < outEdge.size(); k++) {
									String edgeRelation = outEdge.get(k).getRelation().toString();
									String[] edgeRelations = edgeRelation.split(":");
									if (edgeRelations[0].equals("conj") && edgeRelations.length == 2 && !conjunctions.contains(edgeRelations[1])) {
										modifiedToken = currentTweet.getTokenList().get(outEdge.get(k).getTarget().index() - 1 + sentenceOffset);
										if (!modifiedToken.getNormalizedTokenString().startsWith("#")) {
											modifiedToken.setIntensified(true);
											modifiedToken.setNormalizedTokenString("INT_" + modifiedToken.getNormalizedTokenString());
											modifierToken.getModifies().add(modifiedToken);
										}
									}
								}

							} else if (modifierType == 3 && modifierDetectionSettings[2]) {
								modifiedToken.setDiminished(true);
								modifiedToken.setNormalizedTokenString("DIM_" + modifiedToken.getNormalizedTokenString());
								modifierToken.getModifies().add(modifiedToken);
								List<SemanticGraphEdge> outEdge = dependencies.getOutEdgesSorted(node);
								for (int k = 0; k < outEdge.size(); k++) {
									String edgeRelation = outEdge.get(k).getRelation().toString();
									String[] edgeRelations = edgeRelation.split(":");
									if (edgeRelations[0].equals("conj") && edgeRelations.length == 2 && !conjunctions.contains(edgeRelations[1])) {
										modifiedToken = currentTweet.getTokenList().get(outEdge.get(k).getTarget().index() - 1 + sentenceOffset);
										if (!modifiedToken.getNormalizedTokenString().startsWith("#")) {
											modifiedToken.setDiminished(true);
											modifiedToken.setNormalizedTokenString("DIM_" + modifiedToken.getNormalizedTokenString());
											modifierToken.getModifies().add(modifiedToken);
										}
									}
								}
							}
						}
					}
				}
				// Update sentence offset.
				sentenceOffset += dependencies.size();
			}
			// Print percentage done to console.
			numberOfTweetsFinished++;
			if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone + 10) {
				percentageDone = (byte) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
	}

	/**
	 * Creates a list that contains all adversative conjunction words.
	 * @return Returns a list that contains all adversative conjunction words.
	 */
	private ArrayList<String> loadConjunctions() {
		ArrayList<String> conjunctions = new ArrayList<String>();
		conjunctions.add("but");
		conjunctions.add("however");
		conjunctions.add("nevertheless");
		conjunctions.add("yet");
		conjunctions.add("though");
		conjunctions.add("although");
		conjunctions.add("still");
		conjunctions.add("except");
		return conjunctions;
	}

	/**
	 * Loads the negation lexicon.
	 * 
	 * @param negationLexicon
	 *            File that contains all negating words.
	 * @throws IOException
	 *             Throws exception if the negation lexicon can not be found
	 */
	private void loadNegationLexicon(File negationLexicon) throws IOException {
		BufferedReader input = new BufferedReader(new FileReader(negationLexicon));
		String inputString = input.readLine();
		while (inputString != null) {
			this.negationLexicon.add(inputString);
			inputString = input.readLine();
		}
		input.close();
	}

	/**
	 * Loads the intensifier lexicon.
	 * 
	 * @param intensifierLexicon
	 *            File that contains all intensifying words.
	 * @throws IOException
	 *             Throws exception if the intensifier lexicon can not be found.
	 */
	private void loadIntensifierLexicon(File intensifierLexicon) throws IOException {
		BufferedReader input = new BufferedReader(new FileReader(intensifierLexicon));
		String inputString = input.readLine();
		while (inputString != null) {
			this.intensifierLexicon.add(inputString);
			inputString = input.readLine();
		}
		input.close();
	}

	/**
	 * Loads the diminisher lexicon.
	 * 
	 * @param diminisherLexicon
	 *            File that contains all diminishing words.
	 * @throws IOException
	 *             Throws exception if the diminisher lexicon can not be found.
	 */
	private void loadDiminisherLexicon(File diminisherLexicon) throws IOException {
		BufferedReader input = new BufferedReader(new FileReader(diminisherLexicon));
		String inputString = input.readLine();
		while (inputString != null) {
			this.diminisherLexicon.add(inputString);
			inputString = input.readLine();
		}
		input.close();
	}
}
