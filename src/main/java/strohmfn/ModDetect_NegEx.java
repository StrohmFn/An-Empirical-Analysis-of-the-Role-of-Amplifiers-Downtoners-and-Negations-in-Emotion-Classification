package strohmfn;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author strohmfn
 *
 */
public class ModDetect_NegEx {

	public ModDetect_NegEx() throws Exception {
	}

	/**
	 * Annotate negation scopes using the NegEx algorithm.
	 * 
	 * @param corpus
	 *            The to be annotated corpus.
	 */
	public void annotateNegEx(ArrayList<Tweet> corpus) {
		// Needed to display the percentage done.
		int numberOfSentences = corpus.size();
		int numberOfSentencesFinished = 0;
		byte percentageDone = 0;

		/*
		 * The NegEx algorithm returns the negation scope of a sentence in the form X-Y. 
		 * We use a pattern and a matcher to extract X and Y. Then we iterate over
		 * the token list within this scope and negate the tokens.
		 */

		// Create pattern to extract the scope.
		Pattern p = Pattern.compile("([0-9]*) - ([0-9]*)");
		// Instantiate NegEx.
		GenNegEx negEx = new GenNegEx(true);
		// Iterate over the to be annotated corpus.
		for (int i = 0; i < corpus.size(); i++) {
			// Retrieve token list of the current Tweet.
			ArrayList<Token> tokenList = corpus.get(i).getTokenList();
			// Create the Tweet string using the normalized token strings.
			String tweetString = "";
			for (Token token : tokenList) {
				tweetString += token.getNormalizedTokenString() + " ";
			}
			// Run NegEx.
			String result = negEx.negScope(tweetString);
			// Extract X and Y
			Matcher m = p.matcher(result);
			if (m.find()) {
				// Iterate over the token list from X to Y.
				for (int j = Integer.parseInt(m.group(1)); j <= Integer.parseInt(m.group(2)); j++) {
					// Negate current token.
					Token modifiedToken = tokenList.get(j);
					modifiedToken.setNegated(true);
					modifiedToken.setNormalizedTokenString("NEG_" + modifiedToken.getNormalizedTokenString());
				}
			}

			// Calculates the percentage done and prints it to console.
			numberOfSentencesFinished++;
			if ((int) (((double) numberOfSentencesFinished / (double) numberOfSentences) * 100) >= percentageDone + 10) {
				percentageDone = (byte) (((double) numberOfSentencesFinished / (double) numberOfSentences) * 100);
				System.out.print(percentageDone + "% | ");
			}
		}
	}
}
