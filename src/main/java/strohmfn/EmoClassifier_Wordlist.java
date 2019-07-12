package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * 
 * @author strohmfn
 *
 */
public class EmoClassifier_Wordlist {

	private ArrayList<String> wordlistHappiness = new ArrayList<String>();
	private ArrayList<String> wordlistAnger = new ArrayList<String>();
	private ArrayList<String> wordlistFear = new ArrayList<String>();
	private ArrayList<String> wordlistSadness = new ArrayList<String>();
	private ArrayList<String> wordlistSurprise = new ArrayList<String>();
	private ArrayList<String> wordlistDisgust = new ArrayList<String>();

	private File emotionLexicon;
	private Corpus corpus;
	private double eps;
	private int searches;
	private int tries;

	private double[][][][] allWeightMatrices;
	private double[][][] bestWeightMatrices = new double[4][6][6];
	private double[][][] weightMatricesAVG = new double[4][6][6];
	private double[][][] weightMatricesSTDEV = new double[4][6][6];

	private double[][][] weightMatrices;

	public EmoClassifier_Wordlist(Corpus corpus, File emotionLexicon, double eps, int searches, int tries, double[][][] weightMatrices) {
		this.corpus = corpus;
		this.emotionLexicon = emotionLexicon;
		this.eps = eps;
		this.searches = searches;
		this.tries = tries;
		this.weightMatrices = weightMatrices;
		allWeightMatrices = new double[searches][4][6][6];
	}

	/**
	 * Classifies Tweets into the basic emotions using emotion word lists.
	 * 
	 * @throws IOException
	 *             Throws exception if the word list file can not be read
	 */
	public void startClassification() throws IOException {
		// loads emotion lexicon
		loadWordlist();
		// searches the weight matrices which lead to the highest F1 scores in the training data.
		if (weightMatrices == null) {
			System.out.println("No weighting matrices loaded. Train new weight matrices (this can take several days depending on the number of searches and tries).");
			System.out.println("Start weight matices training: ");
			train();
			System.out.println("Weight matrices created!");
		}
		// create iterator to iterate over test set
		Iterator<Tweet> tweetIterator = corpus.getTestSet().iterator();
		// needed to display percentage done
		int numberOfTweets = corpus.getTestSet().size();
		int tweetsClassified = 0;
		int percentageDone = 0;
		// iterate over test set
		System.out.print("Emotion classification: ");
		while (tweetIterator.hasNext()) {
			// retrieve next Tweet
			Tweet tweet = tweetIterator.next();
			// classify current Tweet
			classifyTweet(tweet);
			// needed to display percentage done
			tweetsClassified++;
			if ((int) (((double) tweetsClassified / (double) numberOfTweets) * 100) >= percentageDone + 10) {
				percentageDone = (int) (((double) tweetsClassified / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
		System.out.println("DONE!");
	}

	/**
	 * Performs hill climbing to find a local optimum weight matrix. This is done several times and then the average weight matrices are calculated.
	 */
	private void train() {
		// The value of the variable 'tries' is the number of attempts the training method has in order to find a F1 score that is at least 'eps' better than
		// the current local best.
		double eps = this.eps;
		int numberOfTries = this.tries;
		// Create empty array that will store the matrices which lead to the highest F1 scores.
		// Do three iterations, one for each modifier matrix.
		for (int i = 0; i < 4; i++) {
			// Number of searches for the best matrix. The search starts each time with a different seed.
			// The more search attempts, the higher the chance of finding the global maximum F1 score.
			int numberOfSearches = this.searches;
			// Stores the current global best F1 score. If a better F1 score is achieved,
			// the corresponding matrix will be saved into the 'bestWeightMatrices' array and this variable will be updated.
			double globalF1 = 0;
			// Do 'numberOfSearches' searches.
			System.out.print("Train matrix " + (i + 1) + "/4. Searches left: ");
			while (numberOfSearches > 0) {
				System.out.print(numberOfSearches + " | ");
				numberOfSearches--;
				// This array stores the best weight matrix of the current search.
				double[][] tmpWeightMatrix = new double[6][6];
				// Create an empty matrix and fill it with start values;
				weightMatrices = new double[4][6][6];
				weightMatrices[i] = getSeed();
				// This variable stores the highest F1 score of the current search.
				double localF1 = 0;
				// This variable stores the difference between the best(of this search) and the current F1 score.
				double deltaEps = 1;
				// Stores the amount of tries left to find a higher F1 score.
				int remainingTries = numberOfTries;
				// Search for the best matrix while there are tries remaining.
				while (remainingTries > 0) {
					// Retrieve training set of corpus.
					ArrayList<Tweet> trainingSet = corpus.getTrainingSet();
					// Create iterator for this training set.
					Iterator<Tweet> tweetIterator = trainingSet.iterator();
					// Iterate over test set
					while (tweetIterator.hasNext()) {
						// Retrieve next Tweet.
						Tweet tweet = tweetIterator.next();
						// Classify current Tweet.
						classifyTweet(tweet);
					}
					// Evaluate the F1 score of the current classification.
					double currentF1 = new Evaluation(trainingSet).evaluateF1Score().getAvgF1Score();
					// Calculate the F1 score change.
					deltaEps = currentF1 - localF1;
					// If the F1 score increased, store the new maximum F1 score and its corresponding matrix.
					if (deltaEps >= 0) {
						localF1 = currentF1;
						tmpWeightMatrix = deepCopy(weightMatrices[i]);
						// If the F1 score decreased, backup to the last matrix.
					} else {
						weightMatrices[i] = deepCopy(tmpWeightMatrix);
					}
					// If the F1 score increased at least by the value of 'eps', reset the remaining tries.
					if (deltaEps >= eps) {
						remainingTries = numberOfTries;
						// If the F1 score increased less than the value of 'eps' decrement the number of tries left.
					} else {
						remainingTries--;
					}
					// Vary the values of the matrix corresponding to the current considered modifier type.
					varyMatrix(weightMatrices[i]);
				}
				// If the search attempt leads into a higher F1 score, update the global best F1 score and its corresponding matrix.
				if (localF1 >= globalF1) {
					globalF1 = localF1;
					bestWeightMatrices[i] = deepCopy(weightMatrices[i]);
				}
				// Store the weight matrix at the end of each search. All matrices will be averaged later.
				allWeightMatrices[numberOfSearches][i] = deepCopy(weightMatrices[i]);
			}
			System.out.println("DONE!");
			System.out.println("Matrix " + (i + 1) + " created!");
		}
		// Calculates the average weight matrices and the corresponding standard deviations.
		calculateSTDEV();
		weightMatrices = weightMatricesAVG;
	}

	/**
	 * Fills an array with random startvalues between -1 and 1.
	 * 
	 * @return Returns an array filled with random start values.
	 */
	private double[][] getSeed() {
		double[][] weightMatrix = new double[6][6];
		Random r = new Random();
		for (int j = 0; j < 6; j++) {
			for (int k = 0; k < 6; k++) {
				weightMatrix[j][k] = (r.nextDouble() * 2) - 1;
			}
		}
		return weightMatrix;
	}

	/**
	 * Creates an copy of the input matrix.
	 * 
	 * @param weightMatrix
	 *            To be copied matrix.
	 * @return Returns copy of the input matrix.
	 */
	private double[][] deepCopy(double[][] weightMatrix) {
		double[][] weightMatrixCopy = new double[6][6];
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				weightMatrixCopy[i][j] = weightMatrix[i][j];
			}
		}
		return weightMatrixCopy;
	}

	/**
	 * Adds a random number from the gaussian distribution to each cell of the input matrix.
	 * 
	 * @param matrix
	 *            To be varied matrix.
	 */
	private void varyMatrix(double[][] matrix) {
		Random rng = new Random();
		for (int j = 0; j < 6; j++) {
			for (int k = 0; k < 6; k++) {
				matrix[j][k] += rng.nextGaussian();
			}
		}
	}

	/**
	 * Calculates the average weighting matrices and their corresponding standard deviations.
	 */
	private void calculateSTDEV() {
		// calculate average weighting matrices.
		for (int h = 0; h < 4; h++) {
			for (int i = 0; i < 6; i++) {
				for (int j = 0; j < 6; j++) {
					double sum = 0;
					for (int k = 0; k < searches; k++) {
						sum += allWeightMatrices[k][h][i][j];
					}
					weightMatricesAVG[h][i][j] = sum / searches;
				}
			}
		}

		// calculate standard deviation.
		for (int h = 0; h < 4; h++) {
			for (int i = 0; i < 6; i++) {
				for (int j = 0; j < 6; j++) {
					double sum = 0;
					for (int k = 0; k < searches; k++) {
						double tmp = (allWeightMatrices[k][h][i][j] - weightMatricesAVG[h][i][j]);
						sum += tmp * tmp;
					}
					weightMatricesSTDEV[h][i][j] = Math.sqrt(sum / searches);
				}
			}
		}
	}

	/**
	 * Calculates the basic emotion for each Tweet.
	 * 
	 * @param tweet
	 *            To be classified Tweet.
	 */
	private void classifyTweet(Tweet tweet) {
		// set emotion values to zero for each basic emotion
		double[] emotionValues = { 0, 0, 0, 0, 0, 0 };
		// iterate over all tokens of the Tweet
		Iterator<Token> tokenIterator = tweet.getTokenList().iterator();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.next();
			String tokenString = token.getNormalizedTokenString();
			// delete modifier prefixes of tokens (only needed for SVM classification)
			// for word list classification token.isNegated()/token.isIntensified()/token.isDiminished() is used
			if (tokenString.startsWith("NEG_") || tokenString.startsWith("INT_") || tokenString.startsWith("DIM_")) {
				tokenString = tokenString.substring(4);
			}
			// calculate weighting for the token
			// add weighting to the emotion value of the corresponding basic emotion
			if (wordlistHappiness.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 0);
			}
			if (wordlistAnger.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 1);
			}
			if (wordlistFear.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 2);
			}
			if (wordlistSadness.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 3);
			}
			if (wordlistSurprise.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 4);
			}
			if (wordlistDisgust.contains(tokenString)) {
				emotionValues = calculateWeighting(token, emotionValues, 5);
			}
		}
		// set predicted basic emotion for the current Tweet
		tweet.setPredictedEmotion(calculatePredictedEmotion(emotionValues));
	}

	/**
	 * Calculates the predicted emotion by searching the highest emotion value
	 * 
	 * @param emotionValues
	 *            Values of each basic emotion
	 * @return Returns predicted basic emotion string
	 */
	private String calculatePredictedEmotion(double[] emotionValues) {
		// iterates trough the emotion values array and searches the index of the max value
		int maxIndex = 0;
		for (int i = 0; i < emotionValues.length; i++) {
			double weighting = emotionValues[i];
			if ((weighting > emotionValues[maxIndex])) {
				maxIndex = i;
			}
		}

		// if the max value is zero, no decision can be made and "unknown" will be returned
		if (emotionValues[maxIndex] == 0) {
			return "unknown";
		}
		// return the basic emotion string according the max index
		switch (maxIndex) {
		case 0:
			return "happy";
		case 1:
			return "anger";
		case 2:
			return "fear";
		case 3:
			return "sad";
		case 4:
			return "surprise";
		case 5:
			return "disgust";
		}
		return null;
	}

	/**
	 * Calculates the weighting for a token.
	 * 
	 * @param token
	 *            Token for which the weighting is calculated
	 * @param emotionValues
	 *            Array that contains a weighting for each emotion.
	 * @param emotion
	 *            Emotion of the current token.
	 * @return Returns weighting for the corresponding token
	 */
	private double[] calculateWeighting(Token token, double[] emotionValues, int emotion) {
		if (token.isNegated()) {
			// weighting if token is negated
			for (int i = 0; i < 6; i++) {
				emotionValues[i] += weightMatrices[0][emotion][i];
			}
		} else if (token.isIntensified()) {
			// weighting if token is intensified
			for (int i = 0; i < 6; i++) {
				emotionValues[i] += weightMatrices[1][emotion][i];
			}
		} else if (token.isDiminished()) {
			// weighting if token is diminished
			for (int i = 0; i < 6; i++) {
				emotionValues[i] += weightMatrices[2][emotion][i];
			}
		} else {
			// weighting if token is not modified
			for (int i = 0; i < 6; i++) {
				emotionValues[i] += weightMatrices[3][emotion][i];
			}
		}
		return emotionValues;
	}

	/**
	 * Loads the emotion word list that is used for classification.
	 * 
	 * @throws IOException
	 *             Throws exception if emotion lexicon file is missing/corrupt
	 */
	private void loadWordlist() throws IOException {
		// read from emotion lexicon file
		BufferedReader input = new BufferedReader(new FileReader(emotionLexicon));
		String inputString = input.readLine();
		// iterate trough file
		while (inputString != null) {
			// split data row by tab
			// dataArray[0] = emotion word; dataArray[1] = basic emotion; dataArray[2] = emotion word corresponds to basic emotion
			String[] dataArray = inputString.split("\t");
			// add emotion words to their corresponding basic emotion lexicon
			if (dataArray[1].equals("joy") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistHappiness.add(dataArray[0]);
			}
			if (dataArray[1].equals("anger") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistAnger.add(dataArray[0]);
			}
			if (dataArray[1].equals("fear") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistFear.add(dataArray[0]);
			}
			if (dataArray[1].equals("sadness") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistSadness.add(dataArray[0]);
			}
			if (dataArray[1].equals("surprise") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistSurprise.add(dataArray[0]);
			}
			if (dataArray[1].equals("disgust") && Integer.parseInt(dataArray[2]) == 1) {
				wordlistDisgust.add(dataArray[0]);
			}
			inputString = input.readLine();
		}
		// close input
		input.close();
	}

	/**
	 * 
	 * @return Returns the weight matrices which lead to the highest F1 score.
	 */
	public double[][][] getBestWeightMatrices() {
		return bestWeightMatrices;
	}

	/**
	 * 
	 * @return Returns the average weight matrices.
	 */
	public double[][][] getWeightMatricesAVG() {
		return weightMatricesAVG;
	}

	/**
	 * 
	 * @return Returns the standard deviation for each matrix.
	 */
	public double[][][] getWeightMatricesSTDEV() {
		return weightMatricesSTDEV;
	}
}