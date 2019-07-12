package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

/**
 * 
 * @author strohmfn
 *
 */
public class EmoClassifier_SVM {

	// stores all feature string + number pairs
	private HashMap<String, Integer> featureMap = new HashMap<String, Integer>();
	// stores all words in the stop word list file
	private ArrayList<String> stopWordList = new ArrayList<String>();

	private Corpus corpus;
	private int featureCount = 0;
	private File stopWords;

	// stopping criteria
	private double eps = 0;
	private double C = 0;
	private int n_gram = 0;

	public EmoClassifier_SVM(Corpus corpus, File stopWords, double eps, double c, int n_gram) {
		this.corpus = corpus;
		this.stopWords = stopWords;
		this.eps = eps;
		this.C = c;
		this.n_gram =  n_gram;
	}

	/**
	 * Classifies the Tweets into the basic emotions.
	 * 
	 * @throws IOException
	 *             Throws exception if stopword list can not be read
	 * @return Classified corpus
	 */
	public double startClassification() throws IOException {
		// Start SVM training
		Model model = trainSVM();
		// create Tweet iterator
		Iterator<Tweet> iter = corpus.getTestSet().iterator();
		// needed to show percentage done
		int numberOfTweetsFinished = 0;
		int percentageDone = 0;
		int numberOfTweets = corpus.getTestSet().size();
		// iterate over Tweets
		System.out.print("Emotion classification: ");
		while (iter.hasNext()) {
			Tweet tweet = iter.next();
			// Creates a feature array for the token list of the current Tweet
			Feature[] instance = createFeatureNodes(tweet.getTokenList());
			// uses the trained model and ne feature array to predict the class if the current Tweet
			double prediction = Linear.predict(model, instance);
			// sets predicted emotion of the Tweet
			setPredictedEmotion(prediction, tweet);
			// calculates the percentage done and prints it to console
			numberOfTweetsFinished++;
			if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone+10) {
				percentageDone = (int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
		System.out.println("DONE!");
		return C;
	}
	
	/**
	 * Creates feature nodes for the corresponding token list
	 * 
	 * @param tokenList
	 *            Token list of which the feature nodes are supposed to be created
	 * @return Returns feature nodes array for corresponding token list
	 */
	private FeatureNode[] createFeatureNodes(ArrayList<Token> tokenList) {
		int featureCount = 0;
		String tokenString;
		String unigram = "";
		String bigram = "";
		String trigram = "";
		// Array that stores the features as a string temporarily
		ArrayList<String> features = new ArrayList<String>();
		// iterate over token list
		for (int i = 0; i < tokenList.size(); i++) {
			// if token string is not a stopword and does not start with # (ignores hashtagged words)
			if (!stopWordList.contains(tokenList.get(i).getNormalizedTokenString()) && !tokenList.get(i).getNormalizedTokenString().startsWith("#")) {
				tokenString = tokenList.get(i).getNormalizedTokenString();
				trigram = bigram + tokenString;
				bigram = unigram + tokenString;
				unigram = tokenString;
				// add feature string to array
				if((n_gram > 1) && trigram != bigram){
					features.add(trigram);
					featureCount++;
				}
				if((n_gram > 2) && bigram != unigram){
					features.add(bigram);
					featureCount++;
				}
				features.add(unigram);
				featureCount++;
			}
		}

		FeatureNode[] featureNodes = new FeatureNode[featureCount];
		// iterate over feature strings
		for (int i = 0; i < features.size(); i++) {
			// create a feature node for each feature string
			featureNodes[i] = new FeatureNode(getFeatureNo(features.get(i)), 1);
		}
		// return feture nodes
		return featureNodes;
	}
	
	/**
	 * 
	 * @param featureString
	 *            String of a feature that will be mapped to an integer
	 * @return Returns the feature number of a feature string
	 */
	private int getFeatureNo(String featureString) {
		// if the feature occured bevore, return the corresponding number
		if (featureMap.containsKey(featureString)) {
			return featureMap.get(featureString);
			// if the feature occures the first time, increase feature count and add feature to feature map
		} else {
			featureCount++;
			featureMap.put(featureString, featureCount);
			return featureCount;
		}
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
	 * Sets the predicted emotion of the Tweet
	 * 
	 * @param prediction
	 *            Predicted emotion as integer
	 * @param tweet
	 *            Tweet corresponding to the prediction
	 */
	private void setPredictedEmotion(double prediction, Tweet tweet) {
		switch ((int) prediction) {
		case 0:
			tweet.setPredictedEmotion("happy");
			break;
		case 1:
			tweet.setPredictedEmotion("anger");
			break;
		case 2:
			tweet.setPredictedEmotion("fear");
			break;
		case 3:
			tweet.setPredictedEmotion("sad");
			break;
		case 4:
			tweet.setPredictedEmotion("surprise");
			break;
		case 5:
			tweet.setPredictedEmotion("disgust");
			break;
		}
	}
	
	/**
	 * Trains the SVM with the training data.
	 * 
	 * @throws IOException
	 *             Throws exception if stopword list can not be read
	 *   @return Returns the Model for the SVM.
	 */
	private Model trainSVM() throws IOException {
		System.out.println("Starting SVM training...");
		// loads words that will be ignored by the SVM
		loadStopWords();
		// creates a new problem
		Problem problem = new Problem();
		// creates a feature node array of the size of the training data
		FeatureNode[][] x = new FeatureNode[corpus.getTrainingSet().size()][];
		// creates a new array of the size of the training data
		// this array stores the classes of the feature nodes
		double[] y = new double[corpus.getTrainingSet().size()];

		// create iterator for the training set
		Iterator<Tweet> iter = corpus.getTrainingSet().iterator();
		// create counter for array access
		int counter = 0;

		// needed to display percentage done
		int numberOfTweetsFinished = 0;
		int percentageDone = 0;
		int numberOfTweets = corpus.getTrainingSet().size();
		// iterate over training set
		System.out.print("Creating feature nodes: ");
		while (iter.hasNext()) {
			// retrieve next Tweet
			Tweet tweet = iter.next();
			// create feature nodes for current Tweet
			x[counter] = sortFeatureNodes(createFeatureNodes(tweet.getTokenList()));
			// store class of current Tweet as integer
			y[counter] = calculateY(tweet);
			counter++;
			// calculates the percentage done and prints it to console
			numberOfTweetsFinished++;
			if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone+10) {
				percentageDone = (int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
		System.out.println("DONE!");

		// number of training examples
		problem.l = corpus.getTrainingSet().size();
		// feature nodes
		problem.x = x;
		// classes of feature nodes
		problem.y = y;
		// number of features
		problem.n = featureCount;

		// create solver
		SolverType solver = SolverType.MCSVM_CS;
		
		// find best parameter C via cross validation and grid search
		if (C == 0) {
			System.out.println("Searching best parameter C... (can take up to several days depending on training size)");
			C = findBestC(problem, solver);
			System.out.println("Best C found: " + C);
		}
		// create new parameter
		Parameter parameter = new Parameter(solver, C, eps);
		// train a model
		System.out.println("Creating SVM model...");
		Model model = Linear.train(problem, parameter);
		System.out.println("SVM training done!");
		return model;
	}
	
	/**
	 * 
	 * @param tweet
	 *            The Tweet of which the gold emotion is needed as an integer
	 * @return Returns the gold emotion of the Tweet as an integer
	 */
	private double calculateY(Tweet tweet) {
		String goldEmotion = tweet.getGoldEmotion();
		switch (goldEmotion) {
		case "happy":
			return 0.0;
		case "anger":
			return 1.0;
		case "fear":
			return 2.0;
		case "sad":
			return 3.0;
		case "surprise":
			return 4.0;
		case "disgust":
			return 5.0;
		}
		return -1.0;
	}

	/**
	 * Performs a grid search and 10-fold-crossvalidation to find the best value for C.
	 * @param problem The to be solved liblinear problem.
	 * @param solver Liblinear solver.
	 * @return Returns the value for parameter C that results in the highest F1 score.
	 */
	private double findBestC(Problem problem, SolverType solver) {
		double bestCoarseExponent = -1;
		double bestF1 = 0;
		// This variable will store the predictions retrieved by the cross-validation.
		double[] target = new double[problem.l];
		ArrayList<Tweet> trainingSet = corpus.getTrainingSet();
		// Perform a coarse grid search from 2^-15 to 2^15 (stepsize = 1).
		for (int i = -15; i <= 15; i++) {
			double C = Math.pow(2, i);
			// Run LibLinear cross validation with 10 folds.
			Parameter parameter = new Parameter(solver, C, eps);
			Linear.crossValidation(problem, parameter, 10, target);
			// Annotate the training corpus according to the retrieved predictions.
			for (int j = 0; j < trainingSet.size(); j++) {
				setPredictedEmotion(target[j], trainingSet.get(j));
			}
			// Calculate the F1 score.
			double currentF1 = new Evaluation(trainingSet).evaluateF1Score().getAvgF1Score();
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
			for (int j = 0; j < trainingSet.size(); j++) {
				setPredictedEmotion(target[j], trainingSet.get(j));
			}
			double currentF1 = new Evaluation(trainingSet).evaluateF1Score().getAvgF1Score();
			if (currentF1 > bestF1) {
				bestF1 = currentF1;
				bestFineExponent = i;
			}
		}
		return Math.pow(2, bestCoarseExponent + bestFineExponent);
	}
	
	/**
	 * Loads the stopwords from file into list
	 * 
	 * @throws IOException
	 *             Throws exception if the stopwords list can not be found
	 */
	private void loadStopWords() throws IOException {
		BufferedReader input = new BufferedReader(new FileReader(stopWords));
		String inputString = input.readLine();
		while (inputString != null) {
			stopWordList.add(inputString);
			inputString = input.readLine();
		}
		input.close();
	}
}
