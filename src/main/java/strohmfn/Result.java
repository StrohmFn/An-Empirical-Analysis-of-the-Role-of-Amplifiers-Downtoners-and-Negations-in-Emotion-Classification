package strohmfn;

/**
 * 
 * @author strohmfn
 *
 */
public class Result {

	// Variables that store all results
	private double[] recall;
	private double[] precision;
	private double[] accuracy;
	private double[] f1score;
	private double avgRecall;
	private double avgPrecision;
	private double avgAccuracy;
	private double avgF1Score;

	private int[] tweetsPerEmoTraining;
	private int[] tweetsPerEmoTest;
	private double[][] misclassificationMatrix;

	public Result(double[] recall, double[] precision, double[] accuracy, double[] f1score, int[] tweetsPerEmoTraining, int[] tweetsPerEmoTest, double[][] misclassificationMatrix) {
		this.recall = recall;
		this.precision = precision;
		this.accuracy = accuracy;
		this.f1score = f1score;
		this.tweetsPerEmoTraining = tweetsPerEmoTraining;
		this.tweetsPerEmoTest = tweetsPerEmoTest;
		this.misclassificationMatrix = misclassificationMatrix;
		avgRecall = calcAvgRecall();
		avgPrecision = calcAvgPrecision();
		avgAccuracy = calcAvgAccuracy();
		avgF1Score = calcAvgF1Score();
	}

	public Result(double[] recall, double[] precision, double[] accuracy, double[] f1score) {
		this.recall = recall;
		this.precision = precision;
		this.accuracy = accuracy;
		this.f1score = f1score;
		avgRecall = calcAvgRecall();
		avgPrecision = calcAvgPrecision();
		avgAccuracy = calcAvgAccuracy();
		avgF1Score = calcAvgF1Score();
	}

	/**
	 * 
	 * @return Returns the average F1 score.
	 */
	private double calcAvgF1Score() {
		double result = 0;
		// iterate over all classes and add results
		for (int i = 0; i < f1score.length; i++) {
			result = result + f1score[i];
		}
		// divide by the number of classes
		result = result / f1score.length;
		// return average F1 score
		return result;
	}

	/**
	 * 
	 * @return Returns the average accuracy.
	 */
	private double calcAvgAccuracy() {
		double result = 0;
		// iterate over all classes and add results
		for (int i = 0; i < accuracy.length; i++) {
			result = result + accuracy[i];
		}
		// divide by the number of classes
		result = result / accuracy.length;
		// return average accuracy
		return result;
	}

	/**
	 * 
	 * @return Returns the average precision.
	 */
	private double calcAvgPrecision() {
		double result = 0;
		// iterate over all classes and add results
		for (int i = 0; i < precision.length; i++) {
			result = result + precision[i];
		}
		// divide by the number of classes
		result = result / precision.length;
		// return average precision
		return result;
	}

	/**
	 * 
	 * @return Returns the average recall.
	 */
	private double calcAvgRecall() {
		double result = 0;
		// iterate over all classes and add results
		for (int i = 0; i < recall.length; i++) {
			result = result + recall[i];
		}
		// divide by the number of classes
		result = result / recall.length;
		// return average recall
		return result;
	}

	/**
	 * 
	 * @return Returns the recall values.
	 */
	public double[] getRecall() {
		return recall;
	}

	/**
	 * 
	 * @return Returns the precision values.
	 */
	public double[] getPrecision() {
		return precision;
	}

	/**
	 * 
	 * @return Returns the accuracy values.
	 */
	public double[] getAccuracy() {
		return accuracy;
	}

	/**
	 * 
	 * @return Returns the F1 scores.
	 */
	public double[] getF1Score() {
		return f1score;
	}

	/**
	 * 
	 * @return Returns the average recall.
	 */
	public double getAvgRecall() {
		return avgRecall;
	}

	/**
	 * 
	 * @return Returns the average precision.
	 */
	public double getAvgPrecision() {
		return avgPrecision;
	}

	/**
	 * 
	 * @return Returns the average accuracy.
	 */
	public double getAvgAccuracy() {
		return avgAccuracy;
	}

	/**
	 * 
	 * @return Returns the average F1 score.
	 */
	
	public double getAvgF1Score() {
		return avgF1Score;
	}
	
	public int[] getTweetsPerEmoTraining() {
		return tweetsPerEmoTraining;
	}

	public int[] getTweetsPerEmoTest() {
		return tweetsPerEmoTest;
	}

	public double[][] getMisclassificationMatrix() {
		return misclassificationMatrix;
	}
}
