package strohmfn;

import java.util.ArrayList;

/**
 * 
 * @author strohmfn
 *
 */
public class Token implements java.io.Serializable{

	private static final long serialVersionUID = 1L;
	
	private String tokenString;
	private String normalizedTokenString;
	private boolean isNegator;
	private boolean isIntensifier;
	private boolean isDiminisher;
	private boolean isNegated;
	private boolean isIntensified;
	private boolean isDiminished;
	private ArrayList<Token> modifies = new ArrayList<Token>();

	public Token(String originalString, String normalizedString) {
		this.tokenString = originalString;
		this.normalizedTokenString = normalizedString;
	}

	/**
	 * 
	 * @return Returns if the token is a negator.
	 */
	public boolean isNegator() {
		return isNegator;
	}

	/**
	 * 
	 * @param isNegator
	 *            Sets the variable isNegator to true or false.
	 */
	public void setNegator(boolean isNegator) {
		this.isNegator = isNegator;
	}

	/**
	 * 
	 * @return Returns if the token is an intensifier.
	 */
	public boolean isIntensifier() {
		return isIntensifier;
	}

	/**
	 * 
	 * @param isIntensifier
	 *            Sets the variable isIntensifier to true or false.
	 */
	public void setIntensifier(boolean isIntensifier) {
		this.isIntensifier = isIntensifier;
	}

	/**
	 * 
	 * @return Returns if the token is a diminisher.
	 */
	public boolean isDiminisher() {
		return isDiminisher;
	}

	/**
	 * 
	 * @param isDiminisher
	 *            Sets the variable isDiminisher to true or false.
	 */
	public void setDiminisher(boolean isDiminisher) {
		this.isDiminisher = isDiminisher;
	}

	/**
	 * 
	 * @return Returns if the token is a negator.
	 */
	public boolean isNegated() {
		return isNegated;
	}

	/**
	 * 
	 * @param isNegated
	 *            Sets the variable isNegator to true or false.
	 */
	public void setNegated(boolean isNegated) {
		this.isNegated = isNegated;
	}

	/**
	 * 
	 * @return Returns if the token is an intensifier.
	 */
	public boolean isIntensified() {
		return isIntensified;
	}

	/**
	 * 
	 * @param isIntensified
	 *            Sets the variable isIntensifier to true or false.
	 */
	public void setIntensified(boolean isIntensified) {
		this.isIntensified = isIntensified;
	}

	/**
	 * 
	 * @return Returns if the token is a diminisher.
	 */
	public boolean isDiminished() {
		return isDiminished;
	}

	/**
	 * 
	 * @param isDiminished
	 *            Sets the variable isDiminisher to true or false.
	 */
	public void setDiminished(boolean isDiminished) {
		this.isDiminished = isDiminished;
	}

	/**
	 * 
	 * @return Returns an ArrayList containing all tokens that this token is modifying.
	 */
	public ArrayList<Token> getModifies() {
		return modifies;
	}

	/**
	 * 
	 * @return Returns the string of the token.
	 */
	public String getTokenString() {
		return tokenString;
	}

	/**
	 * 
	 * @return Returns the normalized string of the token.
	 */
	public String getNormalizedTokenString() {
		return normalizedTokenString;
	}

	/**
	 * 
	 * @param normalizedTokenString
	 *            Returns the normalized token string
	 */
	public void setNormalizedTokenString(String normalizedTokenString) {
		this.normalizedTokenString = normalizedTokenString;
	}
}
