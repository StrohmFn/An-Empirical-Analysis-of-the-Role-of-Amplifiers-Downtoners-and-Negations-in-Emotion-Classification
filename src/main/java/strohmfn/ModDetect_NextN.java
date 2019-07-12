package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * 
 * @author strohmfn
 *
 */
public class ModDetect_NextN {

	// stores all words in the negation lexicon
	private ArrayList<String> negationLexicon = new ArrayList<String>();
	// stores all words in the intensifier lexicon
	private ArrayList<String> intensifierLexicon = new ArrayList<String>();
	// stores all words in the diminisher lexicon
	private ArrayList<String> diminisherLexicon = new ArrayList<String>();

	// stores settings for the modifier detection
	private boolean[] modifierDetectionSettings;

	/**
	 * Loads the modifier lexicons.
	 * 
	 * @param modifierLexicons
	 *            Array that contains the negation, intensifier and diminisher lexicon.
	 * @param modifierDetectionSettings
	 *            Says which modifier types are supposed to be considered.
	 * @throws Exception
	 *             Throws exception if the lexicons can not be read or if an invalid modifier detector method is selected.
	 */
	public ModDetect_NextN(File[] modifierLexicons, boolean[] modifierDetectionSettings) throws Exception {
		this.modifierDetectionSettings = modifierDetectionSettings;
		// loads lexica
		loadNegationLexicon(modifierLexicons[0]);
		loadIntensifierLexicon(modifierLexicons[1]);
		loadDiminisherLexicon(modifierLexicons[2]);
	}

	/**
	 * Modifies the next n tokens after a modifier cue.
	 * 
	 * @param corpus
	 *            The to be annotated corpus.
	 * @param n
	 *            Modifies next n tokens after a modifier has occured.
	 */
	public void annotateNextN(ArrayList<Tweet> corpus, int n) {
		// Create iterator to iterate over corpus.
		Iterator<Tweet> tweetIter = corpus.iterator();
		// Needed to show percentage done.
		int numberOfTweets = corpus.size();
		int numberOfTweetsFinished = 0;
		byte percentageDone = 0;
		// Loads a list that contains all adversative conjunction words and end of line characters.
		ArrayList<String> conjunctions = loadConjunctions();
		// Iterate over the to be annotated corpus.
		while (tweetIter.hasNext()) {
			// Defines how many next words have to be modified.
			int modifyCounter = 0;
			// Says if next words are negated(1), intensified(2), diminished(3).
			int modifierType = 0;
			// Retrieve next Tweet form corpus.
			Tweet tweet = tweetIter.next();
			// Iterate over the token list of the current Tweet.
			Iterator<Token> tokenIter = tweet.getTokenList().iterator();
			// Stores the current modifier token.
			Token currentModifierToken = null;
			while (tokenIter.hasNext()) {
				Token token = tokenIter.next();
				// Check if token is a conjunction or end of line marker. If so, stop modifying by current modifier.
				if (conjunctions.contains(token.getNormalizedTokenString())) {
					modifyCounter = 0;
				}
				// Check if current token has to be modified.
				if (modifyCounter > 0) {
					if (!token.getNormalizedTokenString().startsWith("#") && !token.getNormalizedTokenString().startsWith("NEG_") && !token.getNormalizedTokenString().startsWith("INT_")
							&& !token.getNormalizedTokenString().startsWith("DIM_")) {
						// Add token to the "modifies list" of the current modifier token.
						currentModifierToken.getModifies().add(token);
						// Check if token is negated.
						if (modifierType == 1) {
							// Mark token as negated.
							token.setNegated(true);
							token.setNormalizedTokenString("NEG_" + token.getNormalizedTokenString());

						}
						// Check if token is intensified.
						else if (modifierType == 2) {
							token.setIntensified(true);
							token.setNormalizedTokenString("INT_" + token.getNormalizedTokenString());

						}
						// Check if token is diminished.
						else if (modifierType == 3) {
							token.setDiminished(true);
							token.setNormalizedTokenString("DIM_" + token.getNormalizedTokenString());
						}
					}
					modifyCounter--;
				}
				// Check in modifier detection settings if negations are supposed to be considered.
				// If so, check if negation lexicon contains current token string.
				if (modifierDetectionSettings[0] && negationLexicon.contains(token.getNormalizedTokenString())) {
					// Mark token as a negator.
					token.setNegator(true);
					// Store current negator token.
					currentModifierToken = token;
					// Set modifyCounter to n.
					modifyCounter = n;
					// Set modifierType to 1 -> negate next tokens.
					modifierType = 1;
				} else if (modifierDetectionSettings[1] && intensifierLexicon.contains(token.getNormalizedTokenString())) {
					token.setIntensifier(true);
					currentModifierToken = token;
					modifyCounter = n;
					modifierType = 2;
				} else if (modifierDetectionSettings[2] && diminisherLexicon.contains(token.getNormalizedTokenString())) {
					token.setDiminisher(true);
					currentModifierToken = token;
					modifyCounter = n;
					modifierType = 3;
				}
			}
			// Calculates the percentage done and prints it to console.
			numberOfTweetsFinished++;
			if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone + 10) {
				percentageDone = (byte) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
	}

	/**
	 * Creates a list that contains all adversative conjunction words and end of line characters.
	 * 
	 * @return Returns a list that contains all adversative conjunction words and end of line characters.
	 */
	private ArrayList<String> loadConjunctions() {
		ArrayList<String> conjunctions = new ArrayList<String>();
		conjunctions.add(".");
		conjunctions.add("?");
		conjunctions.add("!");
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
	 *             Throws exception if the negation lexicon can not be found.
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
