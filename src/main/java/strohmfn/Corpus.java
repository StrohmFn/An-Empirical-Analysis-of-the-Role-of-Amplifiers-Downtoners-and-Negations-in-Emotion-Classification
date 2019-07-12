package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;

/**
 * @author strohmfn
 *
 */
public class Corpus {

	// Stores all Tweets in the training set.
	private ArrayList<Tweet> trainingSet = new ArrayList<Tweet>();
	// Stores all Tweets in the test set.
	private ArrayList<Tweet> testSet = new ArrayList<Tweet>();

	private TokenizerFactory<Word> tf = PTBTokenizer.factory();

	private boolean stemm;

	public Corpus(File trainingData, File testData, boolean stemm) throws IOException {
		this.stemm = stemm;
		System.out.print("Creating training set: ");
		trainingSet = createCorpus(trainingData);
		System.out.println("DONE!");
		System.out.print("Creating test set: ");
		testSet = createCorpus(testData);
		System.out.println("DONE!");
	}

	public Corpus(File handAnnotatedTweets) throws IOException {
		testSet = createCorpus(handAnnotatedTweets);
	}

	public Corpus(ArrayList<Tweet> trainingSet, ArrayList<Tweet> testSet) {
		this.trainingSet = trainingSet;
		this.testSet = testSet;
		System.out.println("Corpus loaded");
	}

	/**
	 * Creates a Tweet class for each Tweet in the data file and adds them to the test-/training set.
	 * 
	 * @param data The data used to create the corpus.
	 * @throws IOException
	 *             Throws IO exception if the data file can not be read.
	 * @return Returns the corpus.
	 */
	private ArrayList<Tweet> createCorpus(File data) throws IOException {
		// create empty list that will store the Tweets
		ArrayList<Tweet> tweetsList = new ArrayList<Tweet>();
		// tell the tokenizer factory to not delete untokenizable tokens
		tf.setOptions("untokenizable=noneDelete");
		// count number of lines (Tweets) in the file. Is needed to display progress
		int numberOfTweets = (int) Files.lines(data.toPath(), Charset.forName("ISO-8859-1")).count();
		int numberOfTweetsFinished = 0;
		byte percentageDone = 0;
		// read from file
		BufferedReader input = new BufferedReader(new FileReader(data));
		String inputTweet = input.readLine();
		while (inputTweet != null) {
			// split each line by tabs
			String[] dataArray = inputTweet.split("\t");
			// dataArray[0] = gold emotion; dataArray[3] = ID of Tweet;
			// dataArray[6] = language of the Tweet; dataArray[8] = text of the Tweet;
			// this if statement makes sure that only Tweets are considered whose gold emotion
			// matches one of the six emotions that are used in this thesis and that the language is in english
			if ((dataArray[0].equals("happy") || dataArray[0].equals("anger") || dataArray[0].equals("fear") || dataArray[0].equals("sad") || dataArray[0].equals("surprise") || dataArray[0].equals("disgust"))
					&& dataArray[6].equals("en")) {
				// creates a new token list for the Tweet text
				ArrayList<Token> tokenList = createTokenList(dataArray[8]);
				// creates a new Tweet
				Tweet tweet = new Tweet(dataArray[3], dataArray[0], dataArray[8], tokenList);
				// adds the Tweet to the list
				tweetsList.add(tweet);
			}
			numberOfTweetsFinished++;
			// calculates the percentage done and prints it to console
			if ((int) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100) >= percentageDone + 10) {
				percentageDone = (byte) (((double) numberOfTweetsFinished / (double) numberOfTweets) * 100);
				System.out.print(percentageDone + "% | ");
			}
			// reads next line from file
			inputTweet = input.readLine();
		}
		// closes the input stream
		input.close();
		// returns the list of created Tweets
		return tweetsList;
	}

	/**
	 * Creates a Token class for each token in the text.
	 * 
	 * @param tweetText
	 *            Contains the text of the Tweet.
	 * @return Returns an ArrayList containing all Token classes of the Tweet.
	 */
	private ArrayList<Token> createTokenList(String tweetText) {
		// use stanford nlp library to tokenize the Tweet's text
		List<Word> tokenListTemp = tf.getTokenizer(new StringReader(tweetText)).tokenize();
		// create an emty list that will store all tokens
		ArrayList<Token> tokenList = new ArrayList<Token>();
		// iterate over all tokens
		Iterator<Word> iter = tokenListTemp.iterator();
		while (iter.hasNext()) {
			// convert the token to String
			String tokenString = iter.next().toString();
			// create new token class for the token
			String normalizedString = tokenString.toLowerCase();
			if (normalizedString.startsWith("@")) {
				normalizedString = "XUSERX";
			} else if (normalizedString.startsWith("#")) {
				normalizedString = "XHASHTAGX";
			} else if (normalizedString.startsWith("http")) {
				normalizedString = "XURLX";
			}
			if (stemm) {
				Stemmer stemmer = new Stemmer();
				normalizedString = stemmer.stem(normalizedString);
			}
			Token token = new Token(tokenString, normalizedString);
			// add token to the list
			tokenList.add(token);
		}
		// return the list of created tokens
		return tokenList;
	}

	/**
	 * 
	 * @return Returns the training set.
	 */
	public ArrayList<Tweet> getTrainingSet() {
		return trainingSet;
	}

	/**
	 * 
	 * @return Returns the test set.
	 */
	public ArrayList<Tweet> getTestSet() {
		return testSet;
	}
}
