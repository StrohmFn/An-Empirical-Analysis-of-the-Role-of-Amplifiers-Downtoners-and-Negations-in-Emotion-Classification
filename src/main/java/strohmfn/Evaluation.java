package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * 
 * @author strohmfn
 *
 */
public class Evaluation {

	private ArrayList<Tweet> trainingSet;
	private ArrayList<Tweet> testSet;

	// stores true positives, true negatives, false positives and false
	// negatives for each basic emotion
	// [0] = happy, [1] = anger,
	// [0] = fear, [0] = sad,
	// [0] = surprise, [0] = disgust
	private double[] TP_Emotions = { 0, 0, 0, 0, 0, 0 };
	private double[] TN_Emotions = { 0, 0, 0, 0, 0, 0 };
	private double[] FP_Emotions = { 0, 0, 0, 0, 0, 0 };
	private double[] FN_Emotions = { 0, 0, 0, 0, 0, 0 };

	// stores true positives, true negatives, false positives and false
	// negatives for each modifier type
	// [0] = negation, [1] = intensifier, [2] = diminisher
	private double[] TP_Modifier = { 0, 0, 0 };
	private double[] TN_Modifier = { 0, 0, 0 };
	private double[] FP_Modifier = { 0, 0, 0 };
	private double[] FN_Modifier = { 0, 0, 0 };


	public Evaluation(ArrayList<Tweet> trainingSet, ArrayList<Tweet> testSet) {
		this.trainingSet = trainingSet;
		this.testSet = testSet;
	}

	public Evaluation(ArrayList<Tweet> testSet) {
		this.testSet = testSet;
	}

	/**
	 * Manages the evaluation procedure for the classification task
	 * 
	 * @return Returns the results of the evaluation of the classification
	 */
	public Result evaluateClassification() {
		// starts the calculation of TP/TN/FP/FN
		calcPosNegEmo();
		// retrieve the results and store them into the array
		double[] recall = calculateRecall(TP_Emotions, FN_Emotions);
		double[] precision = calculatePrecision(TP_Emotions, FP_Emotions);
		double[] accuracy = calculateAccuracy(TP_Emotions, TN_Emotions, FP_Emotions, FN_Emotions);
		double[] f1score = calculateF1Score(recall, precision);
		int[] tweetsPerEmoTraining = countTweetsPerEmo(trainingSet);
		int[] tweetsPerEmoTest = countTweetsPerEmo(testSet);
		double[][] misclassificationMatrix = calculateMissclassificationMatrix(tweetsPerEmoTest);
		// create new result and return it
		return new Result(recall, precision, accuracy, f1score, tweetsPerEmoTraining, tweetsPerEmoTest,
				misclassificationMatrix);
	}

	/**
	 * This method is a compact version of the normal 'evaluateClassification()' method. It is only
	 * used by the EmoClassifier_SVM class when performing parameter search. This method only
	 * calculates the relevant results needed for the parameter search.
	 * 
	 * @return Returns the results of the emotion classification.
	 */
	public Result evaluateF1Score() {
		// starts the calculation of TP/TN/FP/FN
		calcPosNegEmo();
		// retrieve the results and store them into the array
		double[] recall = calculateRecall(TP_Emotions, FN_Emotions);
		double[] precision = calculatePrecision(TP_Emotions, FP_Emotions);
		double[] accuracy = calculateAccuracy(TP_Emotions, TN_Emotions, FP_Emotions, FN_Emotions);
		double[] f1score = calculateF1Score(recall, precision);
		// create new result and return it
		return new Result(recall, precision, accuracy, f1score);
	}

	/**
	 * This method counts the number of Tweets for each basic emotion.
	 * 
	 * @param corpus
	 *            The to be evaluated corpus.
	 * @return Returns an array containing the amount of Tweets per basic emotion.
	 */
	private int[] countTweetsPerEmo(ArrayList<Tweet> corpus) {
		int[] tweetsPerEmo = { 0, 0, 0, 0, 0, 0 };
		for (int i = 0; i < corpus.size(); i++) {
			int goldEmotion = emotionToInt(corpus.get(i).getGoldEmotion());
			if (goldEmotion != 6) {
				tweetsPerEmo[goldEmotion] += 1;
			}
		}
		return tweetsPerEmo;
	}

	/**
	 * This method generates the missclassification matrix for the given results. For each basic
	 * emotion it shows the percentage distibution of the classification. For example the row
	 * corresponding to the emotion 'sadness' shows how many Tweets with the gold standard emotion
	 * 'sadness' has been classified as enjoyment, anger, fear, sadness, surprise and disgust.
	 * 
	 * @param tweetsPerEmoTest
	 *            Contains information about the amount of Tweets for each basic emotion in the test
	 *            set.
	 * @return Returns the missclassification matrix.
	 */
	private double[][] calculateMissclassificationMatrix(int[] tweetsPerEmoTest) {
		double[][] misclassificationMatrix = new double[6][7];
		for (int i = 0; i < testSet.size(); i++) {
			int goldEmotion = emotionToInt(testSet.get(i).getGoldEmotion());
			int predictedEmotion = emotionToInt(testSet.get(i).getPredictedEmotion());
			misclassificationMatrix[goldEmotion][predictedEmotion] += 1;
		}
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 7; j++) {
				misclassificationMatrix[i][j] = (misclassificationMatrix[i][j] / tweetsPerEmoTest[i]) * 100;
			}
		}
		return misclassificationMatrix;
	}

	/**
	 * Calculates the true positives, true negatives, false positives and false negatives for each
	 * basic emotion
	 */
	private void calcPosNegEmo() {
		// create iterator for the test set
		Iterator<Tweet> iter = testSet.iterator();
		// needed to show percentage done0;
		// iterate over test set
		while (iter.hasNext()) {
			// retrieve next Tweet in test set
			Tweet tweet = iter.next();
			// convert emotions into integer for easy array access
			int goldEmotion = emotionToInt(tweet.getGoldEmotion());
			int predictedEmotion = emotionToInt(tweet.getPredictedEmotion());

			// if prediction was correct
			if (goldEmotion == predictedEmotion) {
				// increment TP for the corresponding emotion
				TP_Emotions[predictedEmotion] += 1;
				// increment TN for every other emotion
				for (int z = 0; z < 6; z++) {
					if (z != predictedEmotion) {
						TN_Emotions[z] += 1;
					}
				}
			} else {
				// if prediction was incorrect increment FN for the
				// corresponding gold emotion
				FN_Emotions[goldEmotion] += 1;
				// if the emotion is not "unknown"
				if (predictedEmotion != 6) {
					// increment FP for the corresponding predicted emotion
					FP_Emotions[predictedEmotion] += 1;
				}
				// increment TN for every other emotion
				for (int z = 0; z < 6; z++) {
					if (z != predictedEmotion && z != goldEmotion) {
						TN_Emotions[z] += 1;
					}
				}
			}
		}
	}

	/**
	 * Converts an emotion string into an integer representation
	 * 
	 * @param emotion
	 *            Emotion string to be converted into an integer
	 * @return Returns an integer for the corresponding emotion string
	 */
	private int emotionToInt(String emotion) {
		// converts an emotion string to an integer
		int emotionAsInt;
		switch (emotion) {
		case "happy":
			emotionAsInt = 0;
			break;
		case "anger":
			emotionAsInt = 1;
			break;
		case "fear":
			emotionAsInt = 2;
			break;
		case "sad":
			emotionAsInt = 3;
			break;
		case "surprise":
			emotionAsInt = 4;
			break;
		case "disgust":
			emotionAsInt = 5;
			break;
		case "unknown":
			// this is the case if the word list classification could not make a
			// decision.
			emotionAsInt = 6;
			break;
		default:
			// throw exception for invalid emotions
			throw new IllegalArgumentException("Invalid emotion: " + emotion);
		}
		// return emotion as integer
		return emotionAsInt;
	}

	/**
	 * Manages the evaluation procedure for the modifier detection task
	 * 
	 * @param handAnnotatedTweetsFile
	 *            File that contains the annotations made by hand
	 * @return Returns the results of the evaluation of the modifier detection
	 * @throws Exception
	 *             Throws exception if a Tweet in the hand annotated Tweets file is not present in
	 *             the corpus.
	 * 
	 */
	public Result evaluateModifier(File handAnnotatedTweetsFile) throws Exception {
		LinkedList<String[]> handAnnotatedTweets = loadHandAnnotatedTweets(handAnnotatedTweetsFile);
		HashMap<String, Tweet> tweets = createHashMap();
		calcPosNegModifier(handAnnotatedTweets, tweets);
		double[] recall = calculateRecall(TP_Modifier, FN_Modifier);
		double[] precision = calculatePrecision(TP_Modifier, FP_Modifier);
		double[] accuracy = calculateAccuracy(TP_Modifier, TN_Modifier, FP_Modifier, FN_Modifier);
		double[] f1score = calculateF1Score(recall, precision);
		return new Result(recall, precision, accuracy, f1score);
	}

	/**
	 * Creates a hashmap containing all Tweet IDs as keys with their corresponding Tweet object as
	 * the value. The hashmap is used to retrieve a Tweet object using its ID from a hand
	 * annotation.
	 * 
	 * @return Returns a hashmap containing all Tweet IDs as keys with their corresponding Tweet
	 *         object as the value.
	 */
	private HashMap<String, Tweet> createHashMap() {
		HashMap<String, Tweet> tweets = new HashMap<String, Tweet>();
		for (int i = 0; i < testSet.size(); i++) {
			Tweet tweet = testSet.get(i);
			tweets.put(tweet.getID(), tweet);
		}
		return tweets;
	}

	/**
	 * Loads the annotations made by hand into a list
	 * 
	 * @param handAnnotatedTweetsFile
	 *            File that contains the annotations made by hand
	 * @return Returns a list containing all annotations made by hand
	 * @throws IOException
	 *             Throws exception if handAnnotatedTweetsFile can not be read
	 */
	private LinkedList<String[]> loadHandAnnotatedTweets(File handAnnotatedTweetsFile) throws IOException {
		// create empty list which will store all data from the hand annotated
		// Tweets file.
		LinkedList<String[]> handAnnotatedTweets = new LinkedList<String[]>();
		// read from file
		BufferedReader input = new BufferedReader(new FileReader(handAnnotatedTweetsFile));
		String inputString = input.readLine();
		// iterate trough file
		while (inputString != null) {
			handAnnotatedTweets.add(inputString.split("\t"));
			inputString = input.readLine();
		}
		// close input
		input.close();
		// return list that contains all the data of the hand annotated Tweets
		// file.
		return handAnnotatedTweets;
	}

	/**
	 * Calculates the true positives, true negatives, false positives and false negatives for each
	 * modifier This evaluation method not only considers if a token is modified correctly, but also
	 * if it is modified by the correct modifier token
	 * 
	 * @param handAnnotations
	 *            List containing all annotations made by hand
	 * @param handAnnotatedTweets
	 *            Hashmap containing all Tweet IDs as keys with their corresponding Tweet object as
	 *            the value.
	 * @throws Exception
	 *             Throws exception if a Tweet in the hand annotated Tweets file is not present in
	 *             the corpus
	 */
	private void calcPosNegModifier(LinkedList<String[]> handAnnotations, HashMap<String, Tweet> handAnnotatedTweets)
			throws Exception {
		int numberOfAnnotations = handAnnotations.size();
		// iterate over all Tweets that were annotated by hand
		for (int i = 0; i < numberOfAnnotations; i++) {
			// indicates if the current modifier modifies the corresponding
			// emotion
			boolean modifies = false;

			// [0] = TweetID; [1] = modiferIndex; [2] = emotionIndex; [3] =
			// modifierString; [4] = emotionString; [5] = modifierType; [6] =
			// correlating?
			String[] currentAnnotation = handAnnotations.get(i);
			// retrieve the token list of the corresponding Tweet
			ArrayList<Token> tokenList;
			try {
				tokenList = handAnnotatedTweets.get(currentAnnotation[0]).getTokenList();
			} catch (Exception e) {
				throw new Exception("Tweet with ID " + currentAnnotation[0] + " is not present in the corpus." + "\n"
						+ "To evaluate the modifer detection all Tweets in the hand annotated Tweets file must be present in the corpus.");
			}
			// retrieve the "modifies list" of the corresponding token
			ArrayList<Token> modifiesList = tokenList.get(Integer.parseInt(currentAnnotation[1]) - 1).getModifies();
			// iterate over the modifies list and search if it modifies the
			// corresponding emotion
			for (int j = 0; j < modifiesList.size(); j++) {
				Token token = modifiesList.get(j);
				if (token.getTokenString().equals(currentAnnotation[4])) {
					modifies = true;
				}
			}
			// set TP/TN
			// case: annotation correct for correlating modifier/emotion pair
			if ((modifies && currentAnnotation[6].equals("1"))) {
				TP_Modifier[Integer.parseInt(currentAnnotation[5]) - 1] += 1;
			}
			// set TN
			// case: annotation correct for not correlating modifier/emotion
			// pair
			else if (!modifies && currentAnnotation[6].equals("0")) {
				TN_Modifier[Integer.parseInt(currentAnnotation[5]) - 1] += 1;
			}
			// set TN/FN
			// case: annotation incorrect, token is actually modified
			else if (!modifies && currentAnnotation[6].equals("1")) {
				FN_Modifier[Integer.parseInt(currentAnnotation[5]) - 1] += 1;
			}
			// set TN/FP
			// case: annotation incorrect, token is actually not modified
			else if (modifies && currentAnnotation[6].equals("0")) {
				FP_Modifier[Integer.parseInt(currentAnnotation[5]) - 1] += 1;
			}
		}
	}

	/**
	 * Manages the simple evaluation procedure for the modifier detection task
	 * 
	 * @param handAnnotatedTweetsFile
	 *            File that contains the annotations made by hand
	 * @return Returns the results of the evaluation of the modifier detection
	 * @throws Exception
	 *             Throws exception if a Tweet in the hand annotated Tweets file is not present in
	 *             the corpus.
	 * 
	 */
	public Result evaluateModifierSimple(File handAnnotatedTweetsFile) throws Exception {
		LinkedList<String[]> handAnnotatedTweets = loadHandAnnotatedTweets(handAnnotatedTweetsFile);
		HashMap<String, Tweet> tweets = createHashMap();
		calcPosNegModifierSimple(handAnnotatedTweets, tweets);
		double[] recall = calculateRecall(TP_Modifier, FN_Modifier);
		double[] precision = calculatePrecision(TP_Modifier, FP_Modifier);
		double[] accuracy = calculateAccuracy(TP_Modifier, TN_Modifier, FP_Modifier, FN_Modifier);
		double[] f1score = calculateF1Score(recall, precision);
		return new Result(recall, precision, accuracy, f1score);
	}
	
	/**
	 * Calculates the true positives, true negatives, false positives and false negatives for each
	 * modifier The 'simple' evaluation method only considers if a token is modified correctly, but
	 * not if it is modified by the correct modifier token.
	 * 
	 * @param handAnnotations
	 *            List containing all annotations made by hand
	 * @param handAnnotatedTweets
	 *            Hashmap containing all Tweet IDs as keys with their corresponding Tweet object as
	 *            the value.
	 * @throws Exception
	 *             Throws exception if a Tweet in the hand annotated Tweets file is not present in
	 *             the corpus
	 */
	private void calcPosNegModifierSimple(LinkedList<String[]> handAnnotations,
			HashMap<String, Tweet> handAnnotatedTweets) throws Exception {
		handAnnotations = createSimpleAnnotations(handAnnotations);
		int numberOfAnnotations = handAnnotations.size();
		// iterate over all Tweets that were annotated by hand
		for (int i = 0; i < numberOfAnnotations; i++) {
			// indicates if the current modifier modifies the corresponding
			// emotion
			int modified = 0;
			// [0] = TweetID; [1] = emotionIndex; [2] = modifierType; [3] =
			// correlating?
			String[] currentAnnotation = handAnnotations.get(i);

			// retrieve the token list of the corresponding Tweet
			ArrayList<Token> tokenList;
			try {
				tokenList = handAnnotatedTweets.get(currentAnnotation[0]).getTokenList();
			} catch (Exception e) {
				throw new Exception("Tweet with ID " + currentAnnotation[0] + " is not present in the corpus." + "\n"
						+ "To evaluate the modifer detection all Tweets in the hand annotated Tweets file must be present in the corpus.");
			}
			Token currentToken = tokenList.get(Integer.parseInt(currentAnnotation[1]) - 1);
			if (currentToken.isNegated()) {
				modified = 1;
			} else if (currentToken.isIntensified()) {
				modified = 2;
			} else if (currentToken.isDiminished()) {
				modified = 3;
			}

			// set TP/TN
			// case: annotation correct for correlating modifier/emotion pair
			if ((modified == Integer.parseInt(currentAnnotation[2])) && currentAnnotation[3].equals("1")) {
				TP_Modifier[Integer.parseInt(currentAnnotation[2]) - 1] += 1;
			}
			// set TN
			// case: annotation correct for not correlating modifier/emotion
			// pair
			else if (modified != Integer.parseInt(currentAnnotation[2]) && currentAnnotation[3].equals("0")) {
				TN_Modifier[Integer.parseInt(currentAnnotation[2]) - 1] += 1;
			}
			// set TN/FN
			// case: annotation incorrect, token is actually modified
			else if ((modified != Integer.parseInt(currentAnnotation[2])) && currentAnnotation[3].equals("1")) {
				FN_Modifier[Integer.parseInt(currentAnnotation[2]) - 1] += 1;
			}
			// set TN/FP
			// case: annotation incorrect, token is actually not modified
			else if (modified == Integer.parseInt(currentAnnotation[2]) && currentAnnotation[3].equals("0")) {
				FP_Modifier[Integer.parseInt(currentAnnotation[2]) - 1] += 1;

			}
		}
	}

	/**
	 * This method takes a list of hand annotations as input and transforms them to annotations used
	 * by the simple annotation algorithm. Three annotation lines will be created for each unique
	 * emotion word appearing in the hand annotations, one for each modifier type. Thus, the simple
	 * annotations tell us for each unique emotion word if it is negated, intensified or diminished.
	 * 
	 * @param handAnnotations
	 *            List containing hand annotations.
	 * @return Returns a list of transformed annotations used for the simple evaluation algorithm.
	 */
	private LinkedList<String[]> createSimpleAnnotations(LinkedList<String[]> handAnnotations) {
		/*
		 * In the beginning we need to group the hand annotations by Tweet ID and emotion word
		 * index. For this, we use the HashMap 'allAnnotationsMap'. The outer HashMap contains an
		 * inner HashMap for each unique TweetID. The inner HashMap contains a list of all
		 * annotations corresponding to an unique emotion word index.
		 */
		HashMap<String, HashMap<String, LinkedList<String[]>>> allAnnotationsMap = new HashMap<String, HashMap<String, LinkedList<String[]>>>();
		for (int i = 0; i < handAnnotations.size(); i++) {
			String[] annotation = handAnnotations.get(i);
			if (allAnnotationsMap.containsKey(annotation[0])) {
				if (allAnnotationsMap.get(annotation[0]).containsKey(annotation[2])) {
					allAnnotationsMap.get(annotation[0]).get(annotation[2]).add(annotation);
				} else {
					LinkedList<String[]> annotationList = new LinkedList<String[]>();
					annotationList.add(annotation);
					allAnnotationsMap.get(annotation[0]).put(annotation[2], annotationList);
				}
			} else {
				LinkedList<String[]> annotationList = new LinkedList<String[]>();
				annotationList.add(annotation);
				HashMap<String, LinkedList<String[]>> specificAnnotationMap = new HashMap<String, LinkedList<String[]>>();
				specificAnnotationMap.put(annotation[2], annotationList);
				allAnnotationsMap.put(annotation[0], specificAnnotationMap);
			}
		}

		// Now we iterate through the 'allAnnotationsMap' and store them back into a linked list
		// but this time they are grouped by TweetID and emotion word index.
		LinkedList<String[]> groupedAnnotations = new LinkedList<String[]>();
		for (HashMap<String, LinkedList<String[]>> value : allAnnotationsMap.values()) {
			for (LinkedList<String[]> value2 : value.values()) {
				for (String[] value3 : value2) {
					groupedAnnotations.add(value3);
				}
			}
		}

		// Now we convert the annotations into simple annotations.
		LinkedList<String[]> simpleAnnotations = new LinkedList<String[]>();
		// Iterate over grouped annotations.
		for (int i = 0; i < groupedAnnotations.size(); i++) {
			// [0] = TweetID; [1] = modifierIndex; [2] = emotionIndex; [3] = modifierString; [4] =
			// emotionString; [5] = modifierType; [6] = correlating?
			String negated = "0";
			String intensified = "0";
			String diminished = "0";
			// Check if current emotion word is negated/intensified/diminished.
			String[] currentAnnotation = groupedAnnotations.get(i);
			if (Integer.parseInt(currentAnnotation[6]) == 1) {
				if (Integer.parseInt(currentAnnotation[5]) == 1) {
					negated = "1";
				} else if (Integer.parseInt(currentAnnotation[5]) == 2) {
					intensified = "1";
				} else {
					diminished = "1";
				}
			}
			
			/*
			 * Since we grouped the annotations, we now can iterate further over the list as long as
			 * the TweetID and the emotion word index are equal to the current token. This way we
			 * retrieve all informations about an unique emotion word.
			 */
			String TweetID = currentAnnotation[0];
			String emotionIndex = currentAnnotation[2];
			boolean sameEmotionIndex = true;
			while (sameEmotionIndex && i < groupedAnnotations.size() - 1) {
				String[] nextAnnotation = groupedAnnotations.get(i + 1);
				if (nextAnnotation[0].equals(TweetID) && nextAnnotation[2].equals(emotionIndex)) {
					i++;
					if (Integer.parseInt(nextAnnotation[6]) == 1) {
						if (Integer.parseInt(nextAnnotation[5]) == 1) {
							negated = "1";
						} else if (Integer.parseInt(nextAnnotation[5]) == 2) {
							intensified = "1";
						} else {
							diminished = "1";
						}
					}
				} else {
					sameEmotionIndex = false;
				}
			}

			/*
			 * After we gathered all information about one unique emotion word we create the simple
			 * annotations. If we would run the simple evaluations on the 'normal' annotations, the
			 * results would be incorrect. Consider a Tweet containing two negation words and one
			 * emotion word. One negation word modifies the emotion, the other does not. We will
			 * have two annotations reflecting this constellation. With the simple evaluation we
			 * then only would consider the emotion word, modifier type and if emotion word is
			 * modified. Since the emotion word in our example is negated, we would count one of the
			 * annotations as a false positive and the other as a true positive. But we only want to
			 * count a true positive since the emotion word is modified correctly.
			 * 
			 */
			String[] simpleAnnotationNeg = { currentAnnotation[0], currentAnnotation[2], "1", negated };
			String[] simpleAnnotationInt = { currentAnnotation[0], currentAnnotation[2], "2", intensified };
			String[] simpleAnnotationDim = { currentAnnotation[0], currentAnnotation[2], "3", diminished };
			simpleAnnotations.add(simpleAnnotationNeg);
			simpleAnnotations.add(simpleAnnotationInt);
			simpleAnnotations.add(simpleAnnotationDim);
		}
		return simpleAnnotations;
	}
	
	/**
	 * Calculates the F1 scores for each class
	 * 
	 * @param recall
	 *            Recall values
	 * @param precision
	 *            Precision values
	 * @return Returns the F1 scores.
	 */
	private double[] calculateF1Score(double[] recall, double[] precision) {
		// create empty array that will store all f1 scores
		double[] result = new double[recall.length];
		// iterate over all classes
		for (int i = 0; i < result.length; i++) {
			// calculate f1 score
			if ((precision[i] + recall[i]) != 0) {
				result[i] = ((2 * precision[i] * recall[i]) / (precision[i] + recall[i]));
			} else {
				result[i] = 0;
			}
		}
		// return array that contains all f1 scores
		return result;
	}

	/**
	 * Calculates the accuracies for each class
	 * 
	 * @param TP
	 *            Number of true Positives for each class
	 * @param TN
	 *            Number of true negatives for each class
	 * @param FP
	 *            Number of false Positives for each class
	 * @param FN
	 *            Number of false negatives for each class
	 * @return Returns the accuracy values.
	 */
	private double[] calculateAccuracy(double[] TP, double[] TN, double[] FP, double[] FN) {
		// create empty array that will store all accuracies
		double[] result = new double[TP.length];
		// iterate over all classes
		for (int i = 0; i < result.length; i++) {
			// calculate accuracy
			result[i] = ((TP[i] + TN[i]) / (TP[i] + TN[i] + FP[i] + FN[i])) * 100;
		}
		// return array that contains all accuracies
		return result;
	}

	/**
	 * Calculates the precision values for each class
	 * 
	 * @param TP
	 *            Number of true Positives for each class
	 * @param FP
	 *            Number of false Positives for each class
	 * @return Returns the precision values.
	 */
	private double[] calculatePrecision(double[] TP, double[] FP) {
		// create empty array that will store all precision values
		double[] result = new double[TP.length];
		// iterate over all classes
		for (int i = 0; i < result.length; i++) {
			// calculate precision
			if ((TP[i] + FP[i]) != 0) {
				result[i] = (TP[i] / (TP[i] + FP[i])) * 100;
			} else {
				result[i] = 0;
			}
		}
		// return array that contains all precision values
		return result;
	}

	/**
	 * Calculates the recall values for each class
	 * 
	 * @param TP
	 *            Number of true Positives for each class
	 * @param FN
	 *            Number of false negatives for each class
	 * @return Returns the recall values.
	 */
	private double[] calculateRecall(double[] TP, double[] FN) {
		// create empty array that will store all recall values
		double[] result = new double[TP.length];
		// iterate over all classes
		for (int i = 0; i < result.length; i++) {
			// calculate recall
			if ((TP[i] + FN[i]) != 0) {
				result[i] = (TP[i] / (TP[i] + FN[i])) * 100;
			} else {
				result[i] = 0;
			}
		}
		// return array that contains all recall values
		return result;
	}
}
