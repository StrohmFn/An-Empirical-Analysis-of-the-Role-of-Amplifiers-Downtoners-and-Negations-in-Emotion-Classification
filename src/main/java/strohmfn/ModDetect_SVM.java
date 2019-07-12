package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
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
public class ModDetect_SVM {

	// Stores all words in the negation lexicon.
	private ArrayList<String> negationLexicon = new ArrayList<String>();
	// Stores all words in the intensifier lexicon.
	private ArrayList<String> intensifierLexicon = new ArrayList<String>();
	// Stores all words in the diminisher lexicon.
	private ArrayList<String> diminisherLexicon = new ArrayList<String>();

	// Stores settings for the modifier detection.
	private boolean[] modifierDetectionSettings;

	private Model[] svmModels = null;
	private File[] trainingFiles;
	private Corpus handAnnotatedTweetsCorpus;
	private double eps;
	private double C;

	/**
	 * Loads modifier lexicons.
	 * 
	 * @param modifierLexicons
	 *            Array that contains the negation, intensifier and diminisher lexicon.
	 * @param modifierDetectionSettings
	 *            Says which modifier types are supposed to be considered.
	 * @param trainingFiles
	 *            Files containing the training data for the three SVM.
	 * @param handAnnotatedTweetsCorpus
	 *            Corpus that contains all Tweets for which hand annotations exist.
	 * @param eps
	 *            Stopping criteria.
	 * @param C
	 *            Costs of constraint violation.
	 * @throws Exception
	 *             Throws exception if the lexicons can not be read.
	 */
	public ModDetect_SVM(File[] modifierLexicons, boolean[] modifierDetectionSettings, File[] trainingFiles, Corpus handAnnotatedTweetsCorpus, double eps, double C) throws Exception {
		this.modifierDetectionSettings = modifierDetectionSettings;
		this.trainingFiles = trainingFiles;
		this.handAnnotatedTweetsCorpus = handAnnotatedTweetsCorpus;
		this.eps = eps;
		this.C = C;

		// loads lexica
		loadNegationLexicon(modifierLexicons[0]);
		loadIntensifierLexicon(modifierLexicons[1]);
		loadDiminisherLexicon(modifierLexicons[2]);
	}

	/**
	 * Annotates negation scopes using three binary SVM, one for each modifier type.
	 * 
	 * @param corpus
	 *            The to be annotated corpus.
	 * @throws IOException
	 *             Throws exception if the training data is corrupt.
	 */
	public void annotateSVM(ArrayList<Tweet> corpus) throws IOException {
		// Start SVM training if no models are present.
		if (svmModels == null) {
			System.out.println("No SVM models existing! Start SVM training...");
			svmModels = trainSVM();
			System.out.println("Training done. Start modifier detection...");
			System.out.print("Annotating training set: ");
		}
		// Needed to display percentage done.
		int numberOfSentences = corpus.size();
		int numberOfSentencesFinished = 0;
		byte percentageDone = 0;

		// Create CoreNLP annotation pipeline.
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, parse, depparse");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		// Iterate over the to be annotated corpus.
		Iterator<Tweet> tweetIter = corpus.iterator();
		while (tweetIter.hasNext()) {
			// Retrieve next Tweet.
			Tweet currentTweet = tweetIter.next();
			// Retrieve token list of the current Tweet.
			ArrayList<Token> tokenList = currentTweet.getTokenList();
			// Annotate modifier cues in the current Tweet.
			ArrayList<ArrayList<Integer>> modifierTokensIndices = annotateModifierCues(tokenList);
			// Create an empty Annotation just with the given text.
			Annotation document = new Annotation(currentTweet.getOriginalText());
			// Run all Annotators on this text.
			pipeline.annotate(document);
			// These are all the sentences in this document.
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			// Iterate over current token list.
			for (int i = 0; i < tokenList.size(); i++) {
				// Annotate modifier scope.
				predict(tokenList, i, modifierTokensIndices, sentences);
			}

			// Calculate percentage done and print it to console.
			numberOfSentencesFinished++;
			if ((int) (((double) numberOfSentencesFinished / (double) numberOfSentences) * 100) >= percentageDone + 10) {
				percentageDone = (byte) (((double) numberOfSentencesFinished / (double) numberOfSentences) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
	}

	/**
	 * Annotates the modifier cues of the present token list.
	 * 
	 * @param tokenList
	 *            The to be annotated token list
	 * @return Returns Returns an array list for each modifier type containing modifier cue indices.
	 */
	private ArrayList<ArrayList<Integer>> annotateModifierCues(ArrayList<Token> tokenList) {
		// Create an array list for each modifier type which will store the modifier cue indices.
		ArrayList<Integer> negCues = new ArrayList<Integer>();
		ArrayList<Integer> intCues = new ArrayList<Integer>();
		ArrayList<Integer> dimCues = new ArrayList<Integer>();
		// Iterate over the token list.
		Iterator<Token> tokenIter = tokenList.iterator();
		int counter = 0;
		while (tokenIter.hasNext()) {
			// Annotated modifier cues and store their indices.
			Token currentToken = tokenIter.next();
			if (negationLexicon.contains(currentToken.getNormalizedTokenString())) {
				currentToken.setNegator(true);
				negCues.add(counter);
			} else if (intensifierLexicon.contains(currentToken.getNormalizedTokenString())) {
				currentToken.setIntensifier(true);
				intCues.add(counter);
			} else if (diminisherLexicon.contains(currentToken.getNormalizedTokenString())) {
				currentToken.setDiminisher(true);
				dimCues.add(counter);
			}
			counter++;
		}
		// Store the three lists containing the modifier cue indices in one array and return it.
		ArrayList<ArrayList<Integer>> modifierTokensIndices = new ArrayList<ArrayList<Integer>>();
		modifierTokensIndices.add(negCues);
		modifierTokensIndices.add(intCues);
		modifierTokensIndices.add(dimCues);
		return modifierTokensIndices;
	}

	/**
	 * Predicts the class of the presented token. Uses three binary SVM to determine if the token is either negated, intesified or diminished.
	 * 
	 * @param tokenList
	 *            Token list that contains the to be annotated token.
	 * @param currentTokenIndex
	 *            Index in the token list of the to be annotated token.
	 * @param modifierTokensIndices
	 *            ArrayList that contains the indices of all modifier cues.
	 * @param sentences
	 *            The sentences created and annotated by CoreNLP. Contains the dependency graph for each sentence of the Tweet containing the current to be
	 *            annotated token.
	 */
	private void predict(ArrayList<Token> tokenList, int currentTokenIndex, ArrayList<ArrayList<Integer>> modifierTokensIndices, List<CoreMap> sentences) {
		// Retrieve the to be annotated token.
		Token currentToken = tokenList.get(currentTokenIndex);
		Feature[] instance;
		double prediction = 0;
		// Retrieve the indices of the negation cues.
		ArrayList<Integer> negTokens = modifierTokensIndices.get(0);
		// Create and sorts the feature nodes relating to the to be annotated token.
		instance = sortFeatureNodes(createFeatureNodes(negTokens, currentTokenIndex, tokenList, sentences));
		// Predicts if the current token is either negated or not.
		prediction = Linear.predict(svmModels[0], instance);
		// If the prediction is 1, the token is negated.
		if (prediction == 1) {
			// Negate the token if modifierDetectionSettings[0] = true.
			if (modifierDetectionSettings[0]) {
				currentToken.setNormalizedTokenString("NEG_" + currentToken.getNormalizedTokenString());
				currentToken.setNegated(true);
			}
		}
		// If the prediction was 0 (token not negated), check if it is intensified.
		else {
			// Retrieve the indices of the intensifier cues.
			ArrayList<Integer> intTokens = modifierTokensIndices.get(1);
			// Create and sorts the feature nodes relating to the to be annotated token.
			instance = sortFeatureNodes(createFeatureNodes(intTokens, currentTokenIndex, tokenList, sentences));
			// Predicts if the current token is either intensified or not.
			prediction = Linear.predict(svmModels[1], instance);
			// If the prediction is 1, the token is intensified.
			if (prediction == 1) {
				// Intensify the token if modifierDetectionSettings[1] = true.
				if (modifierDetectionSettings[1]) {
					currentToken.setNormalizedTokenString("INT_" + currentToken.getNormalizedTokenString());
					currentToken.setIntensified(true);
				}
			}
			// If the prediction was again 0 (token not negated or intensified), check if it is diminished.
			else {
				// Retrieve the indices of the diminisher cues.
				ArrayList<Integer> dimTokens = modifierTokensIndices.get(2);
				// Create and sorts the feature nodes relating to the to be annotated token.
				instance = sortFeatureNodes(createFeatureNodes(dimTokens, currentTokenIndex, tokenList, sentences));
				// Predicts if the current token is either diminished or not.
				prediction = Linear.predict(svmModels[2], instance);
				// If the prediction is 1, the token is diminished.
				if (prediction == 1) {
					// Diminish the token if modifierDetectionSettings[2] = true.
					if (modifierDetectionSettings[2]) {
						currentToken.setNormalizedTokenString("DIM_" + currentToken.getNormalizedTokenString());
						currentToken.setDiminished(true);
					}
				}
			}
		}
	}

	// Keeps track of the amount of features.
	private int featureCount = 0;
	// The following HasMaps store the feature numbers for each feature.
	private HashMap<String, Integer> tokenStringFeatures = new HashMap<String, Integer>();
	private HashMap<String, Integer> POSFeatures = new HashMap<String, Integer>();
	private HashMap<Integer, Integer> rDistFeatures = new HashMap<Integer, Integer>();
	private HashMap<Integer, Integer> lDistFeatures = new HashMap<Integer, Integer>();
	private HashMap<Integer, Integer> depDistFeatures = new HashMap<Integer, Integer>();
	private HashMap<String, Integer> dep1POSFeatures = new HashMap<String, Integer>();
	private HashMap<Integer, Integer> dep1DistFeatures = new HashMap<Integer, Integer>();
	private HashMap<String, Integer> dep2POSFeatures = new HashMap<String, Integer>();
	private HashMap<Integer, Integer> dep2DistFeatures = new HashMap<Integer, Integer>();

	/**
	 * Creates the features for the presented token. Features: normalized token string, POS of the token, POS of the first and second order parent, distance to
	 * the next modifier cue from the token itself and from the first and second order parent in the dependency graph, the left and right distance from the
	 * token to the next modifier cue in the sentence.
	 * 
	 * @param modTokens
	 *            List of the modifier cue indices.
	 * @param currentTokenIndex
	 *            Index of the token for which the features will be created.
	 * @param tokenList
	 *            Token list containing the token in concern.
	 * @param sentences
	 *            The sentences created and annotated by CoreNLP. Contains the dependency graph for each sentence of the Tweet containing the token in concern.
	 * @return Returns an array containing all feature nodes for the present token.
	 */
	private FeatureNode[] createFeatureNodes(ArrayList<Integer> modTokens, int currentTokenIndex, ArrayList<Token> tokenList, List<CoreMap> sentences) {
		// Create empty array that will store all the features.
		ArrayList<Integer> features = new ArrayList<Integer>();
		// Retrieve the normalized token string.
		String tokenString = tokenList.get(currentTokenIndex).getNormalizedTokenString();
		// Retrieve feature number and add to the features list.
		// At first, check if the corresponding HashMap already contains the particular feature.
		if (tokenStringFeatures.containsKey(tokenString)) {
			// Retrieve feature number and add it to the feature list.
			features.add(tokenStringFeatures.get(tokenString));
		}
		// If the feature is not present in the corresponding HashMap, create new feature number.
		else {
			// Increase feature count.
			featureCount++;
			// Add new feature to the corresponding HashMap.
			tokenStringFeatures.put(tokenString, featureCount);
			// Add new feature number to the feature array.
			features.add(featureCount);
		}
		// Define variables which will hold the features.
		String POS = null;
		String dep1POS = null;
		String dep2POS = null;
		int depDist = 1000;
		int dep1Dist = 1000;
		int dep2Dist = 1000;
		// Map the index from the token list to the sentence.
		int tokenSentenceIndex = currentTokenIndex + 1;
		int sentenceOffset = 0;
		// Iterate over the sentences.
		for (CoreMap sentence : sentences) {
			// At first, search the sentence in which the current token is present.
			int sentenceSize = sentence.get(TokensAnnotation.class).size();
			if (sentenceSize < tokenSentenceIndex) {
				tokenSentenceIndex -= sentenceSize;
				sentenceOffset += sentenceSize;
			} else {
				// Retrieve the token list annotated by CoreNLP.
				List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
				// Retrieve POS of the token.
				POS = tokens.get(tokenSentenceIndex - 1).get(PartOfSpeechAnnotation.class);
				// Retrieve the dependency graph of the sentence containing our token.
				SemanticGraph dependencies = sentence.get(EnhancedDependenciesAnnotation.class);
				// Retrieve the node in the graph of our token.
				IndexedWord tokenNode = dependencies.getNodeByIndex(tokenSentenceIndex);
				// Try to retrieve the first order parent node.
				IndexedWord firstOrderParentNode = dependencies.getParent(tokenNode);
				// If first order parent is existent, retrieve the POS if the corresponding token.
				if (firstOrderParentNode != null) {
					int fistOrderParentIndex = firstOrderParentNode.index();
					dep1POS = tokens.get(fistOrderParentIndex - 1).get(PartOfSpeechAnnotation.class);
				}
				IndexedWord secondOrderParentNode = null;
				// If first order parent is existent, try to retrieve second order parent node.
				if (firstOrderParentNode != null) {
					secondOrderParentNode = dependencies.getParent(firstOrderParentNode);
					// If second order parent is existent, retrieve the POS if the corresponding token.
					if (secondOrderParentNode != null) {
						int secondOrderParentIndex = secondOrderParentNode.index();
						dep2POS = tokens.get(secondOrderParentIndex - 1).get(PartOfSpeechAnnotation.class);
					}
				}

				// Iterate over the modifier cue indices.
				for (int i = 0; i < modTokens.size(); i++) {
					// Map index of the modifier cue to the sentence index.
					int modTokenSentenceIndex = modTokens.get(i) - sentenceOffset + 1;
					// Check if the modifier cue index is inside our sentence.
					if (modTokenSentenceIndex > 0 && modTokenSentenceIndex < sentenceSize) {
						// Retrieve the modifier cue node in the dependency graph.
						IndexedWord modToken = dependencies.getNodeByIndex(modTokenSentenceIndex);
						// Try to retrieve the shortest path from the modifier cue to our token in concern.
						List<SemanticGraphEdge> edgesShortestPath = dependencies.getShortestDirectedPathEdges(tokenNode, modToken);
						// Check if the shortest path exists.
						if (edgesShortestPath != null) {
							// Retrieve the length of the shortest path.
							int depDistTmp = edgesShortestPath.size();
							// If the length is shorter that that from the previous modifier cure, store the new shorter length.
							if (depDistTmp < depDist) {
								depDist = depDistTmp;
							}
						}

						// Same as above but this time from the first order parent to the modifier token.
						if (firstOrderParentNode != null) {
							edgesShortestPath = dependencies.getShortestDirectedPathEdges(firstOrderParentNode, modToken);
							if (edgesShortestPath != null) {
								int dep1DistTmp = edgesShortestPath.size();
								if (dep1DistTmp < dep1Dist) {
									dep1Dist = dep1DistTmp;
								}
							}
						}

						// Same as above but this time from the second order parent to the modifier token.
						if (secondOrderParentNode != null) {
							edgesShortestPath = dependencies.getShortestDirectedPathEdges(secondOrderParentNode, modToken);
							if (edgesShortestPath != null) {
								int dep2DistTmp = edgesShortestPath.size();
								if (dep2DistTmp < dep2Dist) {
									dep2Dist = dep2DistTmp;
								}
							}
						}

					}
				}
				break;
			}
		}

		// The following if blocks add the feature numbers for each feature to the feature list.
		if (POS != null) {
			if (POSFeatures.containsKey(POS)) {
				features.add(POSFeatures.get(POS));
			} else {
				featureCount++;
				POSFeatures.put(POS, featureCount);
				features.add(featureCount);
			}
		}
		if (dep1POS != null) {
			if (dep1POSFeatures.containsKey(dep1POS)) {
				features.add(dep1POSFeatures.get(dep1POS));
			} else {
				featureCount++;
				dep1POSFeatures.put(dep1POS, featureCount);
				features.add(featureCount);
			}
		}
		if (dep2POS != null) {
			if (dep2POSFeatures.containsKey(dep2POS)) {
				features.add(dep2POSFeatures.get(dep2POS));
			} else {
				featureCount++;
				dep2POSFeatures.put(dep2POS, featureCount);
				features.add(featureCount);
			}
		}

		if (depDistFeatures.containsKey(depDist)) {
			features.add(depDistFeatures.get(depDist));
		} else {
			featureCount++;
			depDistFeatures.put(depDist, featureCount);
			features.add(featureCount);
		}

		if (dep1DistFeatures.containsKey(dep1Dist)) {
			features.add(dep1DistFeatures.get(dep1Dist));
		} else {
			featureCount++;
			dep1DistFeatures.put(dep1Dist, featureCount);
			features.add(featureCount);
		}

		if (dep2DistFeatures.containsKey(dep2Dist)) {
			features.add(dep2DistFeatures.get(dep2Dist));
		} else {
			featureCount++;
			dep2DistFeatures.put(dep2Dist, featureCount);
			features.add(featureCount);
		}

		// Define variables which will hold the features.
		int lDist = 1000;
		int rDist = 1000;
		// Iterate over the modifier tokens.
		for (int i = 0; i < modTokens.size(); i++) {
			// Calculate the left and right distance from the modifier cure to our token in concern and stores the shortest distance.
			int modifierTokenIndex = modTokens.get(i);
			int dist = currentTokenIndex - modifierTokenIndex;
			if (dist > 0 && dist < rDist) {
				rDist = dist;
			} else if (dist < 0 && Math.abs(dist) < lDist) {
				lDist = Math.abs(dist);
			}
		}

		// Add feature numbers to the feature list.
		if (rDistFeatures.containsKey(rDist)) {
			features.add(rDistFeatures.get(rDist));
		} else {
			featureCount++;
			rDistFeatures.put(rDist, featureCount);
			features.add(featureCount);
		}

		if (lDistFeatures.containsKey(lDist)) {
			features.add(lDistFeatures.get(lDist));
		} else {
			featureCount++;
			lDistFeatures.put(lDist, featureCount);
			features.add(featureCount);
		}

		// Create feature nodes array and add our features.
		FeatureNode[] featureNodes = new FeatureNode[features.size()];
		for (int i = 0; i < features.size(); i++) {
			featureNodes[i] = new FeatureNode(features.get(i), 1);
		}
		// Return final feature nodes.
		return featureNodes;
	}

	/**
	 * Creates a model for each of our three SVM using the training data.
	 * 
	 * @return Returns the models for our three SVM.
	 * @throws IOException
	 *             Throws exception of the training files are corrupt.
	 */
	private Model[] trainSVM() throws IOException {
		// Read the training data to an array list for each SVM.
		ArrayList<String> negTrainData = readFileToList(trainingFiles[0]);
		ArrayList<String> intTrainData = readFileToList(trainingFiles[1]);
		ArrayList<String> dimTrainData = readFileToList(trainingFiles[2]);
		// Add the training data to one array.
		ArrayList<ArrayList<String>> trainData = new ArrayList<ArrayList<String>>();
		trainData.add(negTrainData);
		trainData.add(intTrainData);
		trainData.add(dimTrainData);
		// Create three empty models.
		Model[] models = new Model[3];
		// Retrieve a HashMap that contains all Tweets as values with their corresponding ID as the key.
		// This allows us to retrieve the Tweet object with the Tweet ID extracted from the hand annotations.
		HashMap<String, Tweet> tweets = createHashMap();
		// Create CoreNLP annotation pipeline.
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, parse, depparse");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		for (int i = 0; i < 3; i++) {
			// Creates a new problem.
			Problem problem = new Problem();
			// Creates a feature node array of the size of the training data.
			FeatureNode[][] x = new FeatureNode[trainData.get(i).size()][];
			// Creates a new array of the size of the training data.
			// This array stores the classes of the feature nodes.
			double[] y = new double[trainData.get(i).size()];

			// Create counter for array access.
			int counter = 0;

			// Stores the Tweet ID and token index of each annotation. Needed to find best C.
			ArrayList<String[]> trainDataTweetIDs = new ArrayList<String[]>();

			// Needed to display percentage done.
			int numberOfTweetsFinished = 0;
			int percentageDone = 0;
			int numberOfTweets = trainData.get(i).size();
			System.out.print("Creating feature nodes for SVM " + (i + 1) + ": ");

			// Iterate over training set
			Iterator<String> iter = trainData.get(i).iterator();
			while (iter.hasNext()) {
				// Retrieve next annotation line and split it.
				String emoModPair = iter.next();
				String[] annotationData = emoModPair.split("\t");
				String[] data = { annotationData[0], annotationData[1] };
				trainDataTweetIDs.add(data);
				// Retrieve Tweet object.
				Tweet currentTweet = tweets.get(annotationData[0]);
				// Create empty annotation just with the given text.
				Annotation document = new Annotation(currentTweet.getOriginalText());
				// Run all annotators on this text.
				pipeline.annotate(document);
				// These are all the sentences in this document.
				List<CoreMap> sentences = document.get(SentencesAnnotation.class);
				// Annotate the modifier cues in the current Tweet.
				ArrayList<ArrayList<Integer>> modifierTokens = annotateModifierCues(currentTweet.getTokenList());
				// Create feature nodes for current Tweet.
				x[counter] = sortFeatureNodes(createFeatureNodes(modifierTokens.get(i), Integer.parseInt(annotationData[2]) - 1, currentTweet.getTokenList(), sentences));
				// Store class of current Tweet as integer.
				y[counter] = Double.parseDouble(annotationData[6]);
				counter++;

				// Calculates the percentage done and prints it to console.
				numberOfTweetsFinished++;
				if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone + 10) {
					percentageDone = (int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
					System.out.print(percentageDone + "% | ");
				}
			}
			System.out.println("DONE!");

			// Number of training examples.
			problem.l = trainData.get(i).size();
			// Feature nodes.
			problem.x = x;
			// Classes of feature nodes.
			problem.y = y;
			// Number of features.
			problem.n = featureCount;

			// Create solver.
			SolverType solver = SolverType.MCSVM_CS;

			// Check if any valid value of C is present. If not, execute grid search and cross-validation to find the best C.
			if (C == 0) {
				try {
					System.out.println("Paramter C=0. Start finding best C.");
					C = findBestC(problem, solver, i, trainingFiles[i], tweets, trainDataTweetIDs);
					System.out.println("Found best C = " + C);
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Failed to find best parameter C. Continue with default value C = 1");
					C = 1;
				}
			}
			System.out.println("Creating model " + (i + 1) + "/3.");
			// Create new parameter.
			Parameter parameter = new Parameter(solver, C, eps);
			// Train a model.
			models[i] = Linear.train(problem, parameter);
			System.out.println("Model " + (i + 1) + "/3 created.");
		}
		return models;
	}

	/**
	 * 
	 * @param problem
	 *            Problem for which the best C shall be found.
	 * @param solver
	 *            Selected solver to solve this problem.
	 * @param modifierType
	 *            Indicates the modifier type. (1 = negator, 2 = intensifier, 3 = diminisher)
	 * @param trainingFile
	 *            File containing the training data.
	 * @param tweets
	 *            HashMap containing all Tweets with their corresponding Tweet ID as the key.
	 * @param trainDataTweetIDs
	 *            Stores the Tweet ID and token index of each annotation.
	 * @return Returns the best value for the parameter C.
	 * @throws Exception
	 *             Throws exception if the training data is corrupt.
	 */
	private double findBestC(Problem problem, SolverType solver, int modifierType, File trainingFile, HashMap<String, Tweet> tweets, ArrayList<String[]> trainDataTweetIDs) throws Exception {
		double bestCoarseExponent = 0;
		double bestF1 = 0;
		// This variable will store the predictions retrieved by the cross-validation.
		double[] target = new double[problem.l];
		// Perform a coarse grid search from 2^-15 to 2^15 (stepsize = 1).
		for (int i = -15; i <= 15; i++) {
			double C = Math.pow(2, i);
			// Run LibLinear cross validation with 10 folds.
			Parameter parameter = new Parameter(solver, C, eps);
			Linear.crossValidation(problem, parameter, 10, target);
			// Annotate the training corpus according to the retrieved predictions.
			for (int j = 0; j < problem.l; j++) {
				String TweetID = trainDataTweetIDs.get(j)[0];
				int tokenIndex = Integer.parseInt(trainDataTweetIDs.get(j)[1]);
				Tweet tweet = tweets.get(TweetID);
				Token token = tweet.getTokenList().get(tokenIndex);
				if (target[j] == 1) {
					if (modifierType == 0) {
						token.setNegated(true);
					} else if (modifierType == 1) {
						token.setIntensified(true);
					} else {
						token.setDiminished(true);
					}
				}
			}
			// Calculate the F1 score.
			double currentF1 = new Evaluation(handAnnotatedTweetsCorpus.getTestSet()).evaluateModifierSimple(trainingFile).getAvgF1Score();
			// If the new F1 score is the new highest, store it and the corresponding exponent.
			if (currentF1 > bestF1) {
				bestF1 = currentF1;
				bestCoarseExponent = i;
			}
		}
		// Perform finer grid search from X-0.75 to X+0.75 (stepsize = 0.25).
		double bestFineExponent = 0;
		for (double i = -0.75; i <= 0.75; i += 0.25) {
			double C = Math.pow(2, bestCoarseExponent + i);
			Parameter parameter = new Parameter(solver, C, eps);
			Linear.crossValidation(problem, parameter, 10, target);
			for (int j = 0; j < problem.l; j++) {
				String TweetID = trainDataTweetIDs.get(j)[0];
				int tokenIndex = Integer.parseInt(trainDataTweetIDs.get(j)[1]);
				Tweet tweet = tweets.get(TweetID);
				Token token = tweet.getTokenList().get(tokenIndex);
				if (target[j] == 1) {
					if (modifierType == 0) {
						token.setNegated(true);
					} else if (modifierType == 1) {
						token.setIntensified(true);
					} else {
						token.setDiminished(true);
					}
				}
			}
			double currentF1 = new Evaluation(handAnnotatedTweetsCorpus.getTestSet()).evaluateModifierSimple(trainingFile).getAvgF1Score();
			if (currentF1 > bestF1) {
				bestF1 = currentF1;
				bestFineExponent = i;
			}
		}
		return Math.pow(2, bestCoarseExponent + bestFineExponent);
	}

	/**
	 * Sorts a feature nodes array ascending by feature index (required by liblinear)
	 * 
	 * @param featureNodes
	 *            To be sorted feature nodes array
	 * @return Returns sorted feature nodes array
	 */
	private FeatureNode[] sortFeatureNodes(FeatureNode[] featureNodes) {
		// Check if there are feature nodes
		if (featureNodes.length == 0) {
			return featureNodes;
		}

		// Create temporary array with indices of features and position in featureNode array for sorting
		double[][] indices = new double[featureNodes.length][2];
		for (int i = 0; i < featureNodes.length; i++) {
			indices[i][0] = featureNodes[i].getIndex();
			indices[i][1] = i;
		}

		// Sort ascending by feature indices
		Arrays.sort(indices, new java.util.Comparator<double[]>() {
			public int compare(double[] a, double[] b) {
				return Double.compare(a[0], b[0]);
			}
		});

		// Count number of distinct features
		int featureCount = 1;
		int actualFeature = (int) indices[0][0];
		for (int i = 1; i < featureNodes.length; i++) {
			if (actualFeature != (int) indices[i][0]) {
				featureCount++;
				actualFeature = (int) indices[i][0];
			}
		}

		// Create new array with sorted distinct features
		FeatureNode[] featureNodesSorted = new FeatureNode[featureCount];
		featureNodesSorted[0] = featureNodes[(int) indices[0][1]];
		int index = 1;
		for (int i = 1; i < featureNodes.length; i++) {
			if (featureNodesSorted[index - 1].getIndex() != featureNodes[(int) indices[i][1]].getIndex()) {
				featureNodesSorted[index] = featureNodes[(int) indices[i][1]];
				index++;
			} else {
				featureNodesSorted[index - 1].setValue(featureNodesSorted[index - 1].getValue() + 1);
			}
		}
		return featureNodesSorted;
	}

	/**
	 * Creates a HashMap containing all Tweets from the 'handAnnotatedTweetsCorpus' with their corresponding TweetID as the key. This allows to easily retrieve
	 * a Tweet object with its ID.
	 * 
	 * @return Returns a HashMap containing all Tweets from the 'handAnnotatedTweetsCorpus' with their corresponding TweetID as the key.
	 */
	private HashMap<String, Tweet> createHashMap() {
		ArrayList<Tweet> handAnnotatedTweets = handAnnotatedTweetsCorpus.getTestSet();
		HashMap<String, Tweet> tweets = new HashMap<String, Tweet>();
		for (int i = 0; i < handAnnotatedTweets.size(); i++) {
			Tweet tweet = handAnnotatedTweets.get(i);
			tweets.put(tweet.getID(), tweet);
		}
		return tweets;
	}

	/**
	 * Reads the data from a file into an ArrayList.
	 * 
	 * @param inputFile
	 *            To be read file.
	 * @return Returns a list containing the contents of the file.
	 * @throws IOException
	 *             Throws exception if the file is corrupt.
	 */
	private ArrayList<String> readFileToList(File inputFile) throws IOException {
		ArrayList<String> readData = new ArrayList<String>();
		BufferedReader input = new BufferedReader(new FileReader(inputFile));
		String inputString = input.readLine();
		while (inputString != null) {
			readData.add(inputString);
			inputString = input.readLine();
		}
		input.close();
		return readData;
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
	 *             Throws exception if the intensifier lexicon can not be found
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
	 *             Throws exception if the diminisher lexicon can not be found
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

	/**
	 * 
	 * @return Returns the value of the paramert C.
	 */
	public double getC_MOD() {
		return C;
	}

}
