package strohmfn;

import java.util.ArrayList;

/**
 * 
 * @author strohmfn
 *
 */
public class Tweet implements java.io.Serializable{

	private static final long serialVersionUID = 1L;

	private ArrayList<Token> tokenList = null;

	private String predictedEmotion;
	private String goldEmotion;
	private String originalText;
	private String ID;

	public Tweet(String ID, String goldEmotion, String originalText, ArrayList<Token> tokenList) {
		this.ID = ID;
		this.goldEmotion = goldEmotion;
		this.originalText = originalText;
		this.tokenList = tokenList;
	}

	/**
	 * 
	 * @return Returns an ArrayList that contains all tokens of this Tweet.
	 */
	public ArrayList<Token> getTokenList() {
		return tokenList;
	}

	/**
	 * 
	 * @return Returns the gold emotion of this Tweet.
	 */
	public String getGoldEmotion() {
		return goldEmotion;
	}

	/**
	 * 
	 * @return Returns the predicted emotions of this Tweet.
	 */
	public String getPredictedEmotion() {
		return predictedEmotion;
	}

	/**
	 * Sets the predicted emotion class of this Tweet.
	 * 
	 * @param predictedEmotion
	 *            emotion class that this Tweets gets classified in.
	 */
	public void setPredictedEmotion(String predictedEmotion) {
		this.predictedEmotion = predictedEmotion;
	}

	/**
	 * 
	 * @return Returns the original string of the Tweet.
	 */
	public String getOriginalText() {
		return originalText;
	}

	/**
	 * @return Returns the Tweet ID.
	 */
	public String getID() {
		return ID;
	}

}
