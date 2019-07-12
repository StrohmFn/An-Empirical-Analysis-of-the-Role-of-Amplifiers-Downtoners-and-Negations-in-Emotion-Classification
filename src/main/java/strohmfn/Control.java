package strohmfn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;

/**
 * 
 * @author strohmfn
 *
 */
public class Control {

	public static void main(String[] args) {
		try {
			new Control();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	// The values for these variables will be read from the config.properties file.
	private boolean loadCorpus = false;
	private boolean saveCreatedCorpus = false;
	private int classifierType;
	private String corpusPath;
	private String trainingDataPath;
	private String testDataPath;
	private String handAnnotationsPath;
	private String handAnnotationsEvalCorpusPath;
	private String emotionLexiconPath;
	private String negationLexiconPath;
	private String intensifierLexiconPath;
	private String diminisherLexiconPath;
	private String stopWordsPath;
	private boolean[] modifierDetectionSettings = { false, false, false };
	private int modifierDetectionMethod;
	private int n;
	private boolean evaluateModifierDetection = false;
	private boolean stemming = false;
	private String resultPath;
	private double epsSVM_EMO;
	private double c_EMO;
	private int n_gram;
	private int tries;
	private int searches;
	private double epsWL;
	private boolean loadWeightMatrices;
	private String weightMatricesPath;
	private double epsSVM_MOD;
	private double c_MOD;
	private String negTrainDataPath;
	private String intTrainDataPath;
	private String dimTrainDataPath;

	private Corpus corpus;
	private Object modDetect;
	private EmoClassifier_Wordlist wordListClassifier;

	DecimalFormat df1;
	DecimalFormat df2;

	/**
	 * 
	 * @throws Exception
	 *             Throws exception if any file is missing or corrupt.
	 */
	private Control() throws Exception {
		// Create decimal formats. These are used to limit the digit after the decimal point.
		DecimalFormatSymbols symbol = new DecimalFormatSymbols();
		symbol.setDecimalSeparator('.');
		df1 = new DecimalFormat("00.0", symbol);
		df2 = new DecimalFormat("00.00", symbol);
		df1.setRoundingMode(RoundingMode.CEILING);
		df2.setRoundingMode(RoundingMode.CEILING);
		// Start execution of the main program.
		startExecution();
	}

	/**
	 * Manages the classification and evaluation process.
	 * 
	 * @throws Exception
	 *             Throws exception if any file is missing or corrupt.
	 */
	private void startExecution() throws Exception {
		// Save current time+date for export naming.
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		// Loads all parameters from config file.
		System.out.print("Loading config...");
		loadConfig();
		System.out.println(" DONE!");
		// Create a directory; all non-existent ancestor directories are automatically created.
		boolean success = (new File(resultPath + "Evaluation_results_" + timeStamp + "/")).mkdirs();
		// If directory could not be created throw an exception.
		// This is done in the beginning to prevent export failure after the experiment is done.
		if (!success) {
			throw new IOException("Could not create directory" + "'" + resultPath + "Evaluation_results/" + "'. Please check directory path." + "\n" + "Execution stopped!");
		}
		// Creates or loads the corpus.
		if (loadCorpus) {
			System.out.println("Loading corpus...");
			loadCorpus();
			System.out.println("Corpus loaded!");
		} else if (!loadCorpus) {
			createCorpora();
		} else {
			throw new Exception("Invalid parameter: loadOrCreateCorpus =" + loadCorpus + "\n" + "Only 'true' and 'false' are allowed.");
		}
		// Starts modifier detection if any method is selected and a new corpus was created.
		if ((modifierDetectionMethod != 0) && !loadCorpus) {
			System.out.println("Starting modifier detection...");
			detectModifier();
		}
		// Starts classification procedure.
		System.out.println("Starting emotion classification...");
		classify();
		// Starts emotion classification evaluation procedure.
		System.out.println("Starting evaluation...");
		System.out.print("Evaluating emotion classification... ");
		Result resultClassification = new Evaluation(corpus.getTrainingSet(), corpus.getTestSet()).evaluateClassification();
		System.out.println("DONE!");
		// Starts modifier detection evaluation if modifier detection was used and evaluateModifierDetection is true.
		Result resultModifier = null;
		Result resultModifierSimple = null;
		if (evaluateModifierDetection) {
			// If a modifier detection was used, modifierDetectionMethod must be in the range 1-4.
			if ((modifierDetectionMethod > 0) && (modifierDetectionMethod < 5)) {
				// Create the file containing all hand annotations used for modifier detection evaluation and check for existence.
				File handAnnotations = new File(handAnnotationsPath);
				if (!handAnnotations.exists()) {
					throw new FileNotFoundException("Hand annotations file not found");
				}
				// Create the file containing all Tweets which were annotated by hand and check for existence.
				File handAnnotationsCorpus = new File(handAnnotationsEvalCorpusPath);
				if (!handAnnotationsCorpus.exists()) {
					throw new FileNotFoundException("Hand annotated Tweets file not found");
				}
				// Create the modifier evaluation corpus.
				System.out.print("Creating modifier evaluation corpus: ");
				Corpus modifierEvalCorpus = new Corpus(handAnnotationsCorpus);
				System.out.println("DONE!");
				System.out.print("Annotating modifier evaluation corpus: ");
				// Apply the selcted modifer detection method to the modifier evaluation corpus.
				if (modifierDetectionMethod == 1) {
					((ModDetect_NextN) modDetect).annotateNextN(modifierEvalCorpus.getTestSet(), n);
				} else if (modifierDetectionMethod == 2) {
					((ModDetect_NegEx) modDetect).annotateNegEx(modifierEvalCorpus.getTestSet());
				} else if (modifierDetectionMethod == 3) {
					((ModDetect_DepTree) modDetect).annotateDependencyTree(modifierEvalCorpus.getTestSet());
				} else if (modifierDetectionMethod == 4) {
					((ModDetect_SVM) modDetect).annotateSVM(modifierEvalCorpus.getTestSet());
				}
				System.out.println("DONE!");
				System.out.print("Evaluating modifier detection... ");
				// Run both modifier evaluation methods if modifierDetectionMethod = 1 (next-n) or modifierDetectionMethod = 3 (DepTree).
				if (modifierDetectionMethod == 1 || modifierDetectionMethod == 3) {
					resultModifier = new Evaluation(modifierEvalCorpus.getTestSet()).evaluateModifier(handAnnotations);
					resultModifierSimple = new Evaluation(modifierEvalCorpus.getTestSet()).evaluateModifierSimple(handAnnotations);
				}
				// Only use the simple modifier evaluation method for other cases (NegEx or SVM).
				// The reason for this is that with NegEx or SVM we don't know which modifier modified which token.
				else {
					resultModifierSimple = new Evaluation(modifierEvalCorpus.getTestSet()).evaluateModifierSimple(handAnnotations);
				}
				System.out.println("DONE!");
			} else {
				System.out.println("No modifier detection method selected. Skipping modifier detection evaluation!");
			}
		}
		// Start export procedure.
		String exportPath = resultPath + "Evaluation_results_" + timeStamp + "/";
		System.out.println("Exporting results...");
		exportResults(exportPath, resultClassification, resultModifier, resultModifierSimple);
		System.out.println("Results successfully exported to '" + exportPath + "'");
	}

	/**
	 * Loads all data from the config.properties file. The file must be inside the resources folder inside the project's root folder.
	 * 
	 * @throws IOException
	 *             throws IOException if the config.properties file is missing or corrupt.
	 */
	private void loadConfig() throws IOException {
		// Creates the config file and checks existence.
		File config = new File("resources/config.properties");
		if (!config.exists()) {
			throw new FileNotFoundException("config.properties not found");
		}
		// Opens and reads from config file.
		Properties prop = new Properties();
		InputStream input = new FileInputStream("resources/config.properties");
		prop.load(input);

		// Stores contents of the properties file into variables.
		if (prop.getProperty("loadCorpus").equals("true")) {
			loadCorpus = true;
		}
		if (prop.getProperty("saveCreatedCorpus").equals("true")) {
			saveCreatedCorpus = true;
		}
		corpusPath = prop.getProperty("corpusPath");
		trainingDataPath = prop.getProperty("trainingDataPath");
		testDataPath = prop.getProperty("testDataPath");
		handAnnotationsPath = prop.getProperty("handAnnotationsPath");
		handAnnotationsEvalCorpusPath = prop.getProperty("handAnnotationsEvalCorpusPath");
		classifierType = Integer.parseInt(prop.getProperty("classifierType"));
		negationLexiconPath = prop.getProperty("negationLexiconPath");
		intensifierLexiconPath = prop.getProperty("intensifierLexiconPath");
		diminisherLexiconPath = prop.getProperty("diminisherLexiconPath");
		emotionLexiconPath = prop.getProperty("emotionLexiconPath");
		stopWordsPath = prop.getProperty("stopWordsPath");
		resultPath = prop.getProperty("resultPath");
		if (prop.getProperty("detectNegations").equals("true")) {
			modifierDetectionSettings[0] = true;
		}
		if (prop.getProperty("detectIntenisifer").equals("true")) {
			modifierDetectionSettings[1] = true;
		}
		if (prop.getProperty("detectDiminsiher").equals("true")) {
			modifierDetectionSettings[2] = true;
		}
		modifierDetectionMethod = Integer.parseInt(prop.getProperty("modifierDetectionMethod"));
		if (prop.getProperty("evaluateModifierDetection").equals("true")) {
			evaluateModifierDetection = true;
		}
		n = Integer.parseInt(prop.getProperty("n"));
		if (prop.getProperty("stemming").equals("true")) {
			stemming = true;
		}
		epsSVM_EMO = Double.parseDouble(prop.getProperty("epsilonSVM_EMO"));
		c_EMO = Double.parseDouble(prop.getProperty("c_EMO"));
		n_gram = Integer.parseInt(prop.getProperty("n_gram"));
		tries = Integer.parseInt(prop.getProperty("tries"));
		searches = Integer.parseInt(prop.getProperty("searches"));
		epsWL = Double.parseDouble(prop.getProperty("epsilonWL"));
		if (prop.getProperty("loadWeightMatrices").equals("true")) {
			loadWeightMatrices = true;
		}
		weightMatricesPath = prop.getProperty("weightMatricesPath");
		epsSVM_MOD = Double.parseDouble(prop.getProperty("epsilonSVM_MOD"));
		c_MOD = Double.parseDouble(prop.getProperty("c_MOD"));
		negTrainDataPath = prop.getProperty("negTrainDataPath");
		intTrainDataPath = prop.getProperty("intTrainDataPath");
		dimTrainDataPath = prop.getProperty("dimTrainDataPath");
		// Closes input stream.
		input.close();
	}

	/**
	 * 
	 * @throws IOException
	 *             Throws IOException if file is missing or corrupt.
	 * @throws ClassNotFoundException
	 *             Throws ClassNotFoundException if file does not contain a Corpus object.
	 */
	@SuppressWarnings("unchecked")
	private void loadCorpus() throws IOException, ClassNotFoundException {
		// Loads an existing corpus from file and stores it in the 'corpus' parameter.
		FileInputStream streamIn = new FileInputStream(corpusPath);
		ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
		Object data[] = (Object[]) objectinputstream.readObject();
		corpus = new Corpus((ArrayList<Tweet>) data[0], (ArrayList<Tweet>) data[1]);
		objectinputstream.close();
		streamIn.close();
	}
	
	/**
	 * Creates training and test corpora
	 * 
	 * @throws IOException
	 *             throws IOException if the testData or trainingData file is missing or corrupt.
	 */
	private void createCorpora() throws IOException {
		// Checks if test data file can be found.
		File testData = new File(testDataPath);
		if (!testData.exists()) {
			throw new FileNotFoundException("Tweets file not found");
		}
		// Checks if training data file can be found.
		File trainingData = new File(trainingDataPath);
		if (!trainingData.exists()) {
			throw new FileNotFoundException("Tweets file not found");
		}
		// Starts corpus creation.
		this.corpus = new Corpus(trainingData, testData, stemming);
	}
	
	/**
	 * Starts the modifier detection process.
	 * 
	 * @throws IOException
	 *             throws IOException if the negation-/intensifier-/diminisher lexicon is missing or corrupt.
	 */
	private void detectModifier() throws IOException {
		File[] modifierLexica = new File[3];
		modifierLexica[0] = new File(negationLexiconPath);
		modifierLexica[1] = new File(intensifierLexiconPath);
		modifierLexica[2] = new File(diminisherLexiconPath);
		if (!modifierLexica[0].exists() || !modifierLexica[1].exists() || !modifierLexica[2].exists()) {
			throw new FileNotFoundException("negation-/intensifier-/diminisher lexicon is missing");
		}
		try {
			if (modifierDetectionMethod == 1) {
				modDetect = new ModDetect_NextN(modifierLexica, modifierDetectionSettings);
				System.out.print("Annotating training set: ");
				((ModDetect_NextN) modDetect).annotateNextN(corpus.getTrainingSet(), n);
				System.out.println("DONE!");
				System.out.print("Annotating test set: ");
				((ModDetect_NextN) modDetect).annotateNextN(corpus.getTestSet(), n);
				System.out.println("DONE!");
			} else if (modifierDetectionMethod == 2) {
				modDetect = new ModDetect_NegEx();
				System.out.print("Annotating training set: ");
				((ModDetect_NegEx) modDetect).annotateNegEx(corpus.getTrainingSet());
				System.out.println("DONE!");
				System.out.print("Annotating test set: ");
				((ModDetect_NegEx) modDetect).annotateNegEx(corpus.getTestSet());
				System.out.println("DONE!");
			} else if (modifierDetectionMethod == 3) {
				modDetect = new ModDetect_DepTree(modifierLexica, modifierDetectionSettings);
				System.out.print("Annotating training set: ");
				((ModDetect_DepTree) modDetect).annotateDependencyTree(corpus.getTrainingSet());
				System.out.println("DONE!");
				System.out.print("Annotating test set: ");
				((ModDetect_DepTree) modDetect).annotateDependencyTree(corpus.getTestSet());
				System.out.println("DONE!");
			} else if (modifierDetectionMethod == 4) {
				File[] trainingFiles = new File[3];
				trainingFiles[0] = new File(negTrainDataPath);
				trainingFiles[1] = new File(intTrainDataPath);
				trainingFiles[2] = new File(dimTrainDataPath);
				if (!trainingFiles[0].exists() || !trainingFiles[1].exists() || !trainingFiles[2].exists()) {
					throw new FileNotFoundException("negation-/intensifier-/diminisher SVM training data is missing");
				}
				File handAnnotationsCorpusFile = new File(handAnnotationsEvalCorpusPath);
				if (!handAnnotationsCorpusFile.exists()) {
					throw new FileNotFoundException("hand annotated Tweets file not found");
				}
				System.out.print("Create hand annotated corpus: ");
				Corpus handAnnotatedTweetsCorpus = new Corpus(handAnnotationsCorpusFile);
				System.out.println("DONE!");
				modDetect = new ModDetect_SVM(modifierLexica, modifierDetectionSettings, trainingFiles, handAnnotatedTweetsCorpus, epsSVM_MOD, c_MOD);
				System.out.print("Annotating training set: ");
				((ModDetect_SVM) modDetect).annotateSVM(corpus.getTrainingSet());
				System.out.println("DONE!");
				System.out.print("Annotating test set: ");
				((ModDetect_SVM) modDetect).annotateSVM(corpus.getTestSet());
				System.out.println("DONE!");
			} else {
				System.out.println("Invalid argument for 'modifierDetectionMethod': " + modifierDetectionMethod + "\n" + "Only values in the range between 0-4 are allowed!");
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("An error occured during the modifier detection. Skipping modifier detection");
		}
	}

	/**
	 * Transforms the weight matrices string in the file into an array containing all values.
	 * 
	 * @param weightMatricesFile
	 *            File that contains the weight matrices as a string.
	 * @return Returns an array that contains all values of the weight matrices
	 * @throws IOException
	 *             Throws exception if the weight matrices file is corrupt.
	 */
	private double[][][] loadWeightMatrices(File weightMatricesFile) throws IOException {
		// Create empty array to store weight matrices from file.
		double[][][] weightMatrices = new double[4][6][6];
		// Read from weight matrices file.
		BufferedReader input = new BufferedReader(new FileReader(weightMatricesFile));
		// Skip first four lines since they contain no relevant information.
		input.readLine();
		input.readLine();
		input.readLine();
		input.readLine();
		// Read negation weight matrix into array;
		for (int i = 0; i < 6; i++) {
			String inputString = input.readLine();
			String[] dataArray = inputString.split("\t");
			for (int j = 0; j < 6; j++) {
				weightMatrices[0][i][j] = Double.parseDouble(dataArray[j]);
			}
		}
		input.readLine();
		input.readLine();
		// Read intensifier weight matrix into array;
		for (int i = 0; i < 6; i++) {
			String inputString = input.readLine();
			String[] dataArray = inputString.split("\t");
			for (int j = 0; j < 6; j++) {
				weightMatrices[1][i][j] = Double.parseDouble(dataArray[j]);
			}
		}
		input.readLine();
		input.readLine();
		// Read diminisher weight matrix into array;
		for (int i = 0; i < 6; i++) {
			String inputString = input.readLine();
			String[] dataArray = inputString.split("\t");
			for (int j = 0; j < 6; j++) {
				weightMatrices[2][i][j] = Double.parseDouble(dataArray[j]);
			}
		}
		input.readLine();
		input.readLine();
		// Read neutral weight matrix into array;
		for (int i = 0; i < 6; i++) {
			String inputString = input.readLine();
			String[] dataArray = inputString.split("\t");
			for (int j = 0; j < 6; j++) {
				weightMatrices[3][i][j] = Double.parseDouble(dataArray[j]);
			}
		}
		input.close();
		return weightMatrices;
	}
	
	/**
	 * Starts the classification with a specific classification method.
	 * 
	 * @throws IOException
	 *             throws IOException if the emotion lexicon file is missing/corrupt but the word list classification approach is used.
	 */
	private void classify() throws IOException {
		if (classifierType == 1) {
			File stopWordsFile = new File(stopWordsPath);
			if (!stopWordsFile.exists()) {
				throw new FileNotFoundException("Stop words file not found!");
			}
			if(n_gram < 1 || n_gram > 3){
				throw new IllegalArgumentException("The value n-gram = " + n_gram + " is invalid. Only values 0 < n < 4 are allowed.");
			}
			c_EMO = new EmoClassifier_SVM(corpus, stopWordsFile, epsSVM_EMO, c_EMO, n_gram).startClassification();
		} else if (classifierType == 2) {
			File emotionLexiconFile = new File(emotionLexiconPath);
			if (!emotionLexiconFile.exists()) {
				throw new FileNotFoundException("Emotion lexicon file not found!");
			}
			if (loadWeightMatrices) {
				File weightMatricesFile = new File(weightMatricesPath);
				if (!weightMatricesFile.exists()) {
					throw new FileNotFoundException("Weight matrices file not found!");
				}
				double[][][] weightMatrices = loadWeightMatrices(weightMatricesFile);
				wordListClassifier = new EmoClassifier_Wordlist(corpus, emotionLexiconFile, epsWL, searches, tries, weightMatrices);
			} else {
				wordListClassifier = new EmoClassifier_Wordlist(corpus, emotionLexiconFile, epsWL, searches, tries, null);
			}
			wordListClassifier.startClassification();
		}
	}

	/**
	 * Exports the annotated test corpus and the results of the classification and modifier detection.
	 * 
	 * @param exportPath
	 *            Path where the results will be saved.
	 * @param resultClassification
	 *            Results of the classification.
	 * @param resultModifier
	 *            Results of the modifier detection.
	 * @param resultModifierSimple
	 *            Results of the modifier detection using the simple evaluation method.
	 * @throws IOException
	 *             Throws exception if export files can not be created.
	 */
	private void exportResults(String exportPath, Result resultClassification, Result resultModifier, Result resultModifierSimple) throws IOException {
		try {
			// Export annotated test corpus.
			PrintWriter writer = new PrintWriter(exportPath + "Annotated_test_corpus.txt", "UTF-8");
			String outString = "1.Column=Tweet_ID" + "\t" + "2.Column=Gold_emotion" + "\t" + "3.Column=Predicted_emotion" + "\t" + "4.Column=Original_text" + "\t" + "5.Column=Normalized_tokens+'modifies'_list";
			writer.println(outString);
			writer.println();
			for (int i = 0; i < corpus.getTestSet().size(); i++) {
				outString = generateCorporaString(i);
				writer.println(outString);
			}
			writer.close();

			// Export emotion classification results and settings.
			writer = new PrintWriter(exportPath + "Classification_results.txt", "UTF-8");
			writer.println(generateResultStringClassification(resultClassification));
			writer.println(generateExperimentSetupString(resultClassification));
			writer.close();

			// Export weight weight matrices if word list classifier was used and new weight matrices were created.
			if (classifierType == 2 && !loadWeightMatrices) {
				writer = new PrintWriter(exportPath + "weightMatricesBEST.txt", "UTF-8");
				outString = generateWeightMatricesString(wordListClassifier.getBestWeightMatrices());
				writer.println(outString);
				writer.close();

				writer = new PrintWriter(exportPath + "weightMatricesAVG.txt", "UTF-8");
				outString = generateWeightMatricesString(wordListClassifier.getWeightMatricesAVG());
				writer.println(outString);
				writer.close();

				writer = new PrintWriter(exportPath + "weightMatricesSTDEV.txt", "UTF-8");
				outString = generateWeightMatricesString(wordListClassifier.getWeightMatricesSTDEV());
				writer.println(outString);
				writer.close();
			}

			// Export simple modifier evaluation results.
			if (evaluateModifierDetection && (modifierDetectionMethod > 0 && modifierDetectionMethod < 5)) {
				writer = new PrintWriter(exportPath + "Modifier_evaluation_simple_results.txt", "UTF-8");
				outString = generateResultStringModifier(resultModifierSimple);
				writer.println(outString);
				outString = generateExperimentSetupString(resultClassification);
				writer.println(outString);
				writer.close();
				// Export modifier evaluation results.
				if (resultModifier != null) {
					writer = new PrintWriter(exportPath + "Modifier_evaluation_results.txt", "UTF-8");
					outString = generateResultStringModifier(resultModifier);
					writer.println(outString);
					outString = generateExperimentSetupString(resultClassification);
					writer.println(outString);
					writer.close();
				}
			}

			// Write corpus to file if a new one was created and 'saveCreatedCorpus' is true.
			if (!loadCorpus && saveCreatedCorpus) {
				exportCorpus(exportPath);
			}
		} catch (IOException e) {
			throw new IOException("Could not write to file. Please check permissions or change path." + "\n" + "Execution stopped! No results have been saved!");
		}
	}

	/**
	 * Exports the used corpus object containing the training and test data. Also contains the annotations made by the modifier detection.
	 * 
	 * @param exportPath
	 *            Path where the corpus should be saved.
	 */
	private void exportCorpus(String exportPath) {
		ObjectOutputStream oos = null;
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(exportPath + "corpus.ser", true);
			oos = new ObjectOutputStream(fout);
			Object data[] = { corpus.getTrainingSet(), corpus.getTestSet() };
			oos.writeObject(data);
			oos.close();
			fout.close();
		} catch (Exception e) {
			System.out.println("Failed to export created corpus");
			e.printStackTrace();
		}
	}
	
	/**
	 * Transforms weight matrices into a string.
	 * 
	 * @param weightMatrices
	 *            The to be exported weight matrices.
	 * @return Returns the weight matrices in a string format.
	 */
	private String generateWeightMatricesString(double weightMatrices[][][]) {
		String outString = "";
		outString += "From left to right and from top to bottom:" + "\n";
		outString += "Enjoyment, Anger, Fear, Sadness, Surprise, Disgust." + "\n";
		outString += "\n";
		outString += "Negation weight matrix:" + "\n";
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				outString += df2.format(weightMatrices[0][i][j]) + "\t";
			}
			outString += "\n";
		}
		outString += "\n";
		outString += "Intensifier weight matrix:" + "\n";
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				outString += df2.format(weightMatrices[1][i][j]) + "\t";
			}
			outString += "\n";
		}
		outString += "\n";
		outString += "Diminisher weight matrix:" + "\n";
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				outString += df2.format(weightMatrices[2][i][j]) + "\t";
			}
			outString += "\n";
		}
		outString += "\n";
		outString += "Neutral weight matrix:" + "\n";
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				outString += df2.format(weightMatrices[3][i][j]) + "\t";
			}
			outString += "\n";
		}
		return outString;
	}

	/**
	 * Generates a string for a Tweet that contains all essential informations like gold Emotion, predicted Emotion, original text, tokens, ... .
	 * 
	 * @param i
	 *            Index of the Tweet in the test set.
	 * @return Returns a string containing all information/annotations of this specific Tweet.
	 */
	private String generateCorporaString(int i) {
		String outString = "";
		Tweet currentTweet = corpus.getTestSet().get(i);
		outString += currentTweet.getID() + "\t";
		outString += currentTweet.getGoldEmotion() + "\t";
		outString += currentTweet.getPredictedEmotion() + "\t";
		outString += currentTweet.getOriginalText() + "\t";
		// Create a string for each token containing its normalized string.
		// If the token is a modifier, the string also contains the normalized token string of all tokens that are modified by this token.
		for (int j = 0; j < currentTweet.getTokenList().size(); j++) {
			Token currentToken = currentTweet.getTokenList().get(j);
			if (currentToken.isNegator()) {
				outString += currentToken.getNormalizedTokenString() + "{";
				for (int k = 0; k < currentToken.getModifies().size(); k++) {
					outString += currentToken.getModifies().get(k).getNormalizedTokenString();
					if (k < currentToken.getModifies().size() - 1) {
						outString += "|";
					}
				}
				outString += "}";
				if (j < currentTweet.getTokenList().size() - 1) {
					outString += "|";
				}
			} else if (currentToken.isIntensifier()) {
				outString += currentToken.getNormalizedTokenString() + "{";
				for (int k = 0; k < currentToken.getModifies().size(); k++) {
					outString += currentToken.getModifies().get(k).getNormalizedTokenString();
					if (k < currentToken.getModifies().size() - 1) {
						outString += "|";
					}
				}
				outString += "}";
				if (j < currentTweet.getTokenList().size() - 1) {
					outString += "|";
				}
			} else if (currentToken.isDiminisher()) {
				outString += currentToken.getNormalizedTokenString() + "{";
				for (int k = 0; k < currentToken.getModifies().size(); k++) {
					outString += currentToken.getModifies().get(k).getNormalizedTokenString();
					if (k < currentToken.getModifies().size() - 1) {
						outString += "|";
					}
				}
				outString += "}";
				if (j < currentTweet.getTokenList().size() - 1) {
					outString += "|";
				}
			} else {
				outString += currentToken.getNormalizedTokenString();
				if (j < currentTweet.getTokenList().size() - 1) {
					outString += "|";
				}
			}
		}
		return outString;
	}

	/**
	 * Creates a string containing all results of the emotion classification.
	 * 
	 * @param resultClassification
	 *            Containing all results of the emotion classification.
	 * @return Returns a string that contains all results of the emotion classification.
	 */
	private String generateResultStringClassification(Result resultClassification) {
		String outString = "";
		outString += "Recall enjoyment = " + df1.format(resultClassification.getRecall()[0]) + "\n";
		outString += "Recall anger = " + df1.format(resultClassification.getRecall()[1]) + "\n";
		outString += "Recall fear = " + df1.format(resultClassification.getRecall()[2]) + "\n";
		outString += "Recall sad = " + df1.format(resultClassification.getRecall()[3]) + "\n";
		outString += "Recall surprise = " + df1.format(resultClassification.getRecall()[4]) + "\n";
		outString += "Recall disgust = " + df1.format(resultClassification.getRecall()[5]) + "\n";
		outString += "\n";
		outString += "Precision enjoyment = " + df1.format(resultClassification.getPrecision()[0]) + "\n";
		outString += "Precision anger = " + df1.format(resultClassification.getPrecision()[1]) + "\n";
		outString += "Precision fear = " + df1.format(resultClassification.getPrecision()[2]) + "\n";
		outString += "Precision sad = " + df1.format(resultClassification.getPrecision()[3]) + "\n";
		outString += "Precision surprise = " + df1.format(resultClassification.getPrecision()[4]) + "\n";
		outString += "Precision disgust = " + df1.format(resultClassification.getPrecision()[5]) + "\n";
		outString += "\n";
		outString += "Accuracy enjoyment = " + df1.format(resultClassification.getAccuracy()[0]) + "\n";
		outString += "Accuracy anger = " + df1.format(resultClassification.getAccuracy()[1]) + "\n";
		outString += "Accuracy fear = " + df1.format(resultClassification.getAccuracy()[2]) + "\n";
		outString += "Accuracy sad = " + df1.format(resultClassification.getAccuracy()[3]) + "\n";
		outString += "Accuracy surprise = " + df1.format(resultClassification.getAccuracy()[4]) + "\n";
		outString += "Accuracy disgust = " + df1.format(resultClassification.getAccuracy()[5]) + "\n";
		outString += "\n";
		outString += "F1 score enjoyment = " + df1.format(resultClassification.getF1Score()[0]) + "\n";
		outString += "F1 score anger = " + df1.format(resultClassification.getF1Score()[1]) + "\n";
		outString += "F1 score fear = " + df1.format(resultClassification.getF1Score()[2]) + "\n";
		outString += "F1 score sad = " + df1.format(resultClassification.getF1Score()[3]) + "\n";
		outString += "F1 score surprise = " + df1.format(resultClassification.getF1Score()[4]) + "\n";
		outString += "F1 score disgust = " + df1.format(resultClassification.getF1Score()[5]) + "\n";
		outString += "\n";
		outString += "AVG recall = " + df1.format(resultClassification.getAvgRecall()) + "\n";
		outString += "AVG precision = " + df1.format(resultClassification.getAvgPrecision()) + "\n";
		outString += "AVG accuracy = " + df1.format(resultClassification.getAvgAccuracy()) + "\n";
		outString += "AVG F1 score = " + df1.format(resultClassification.getAvgF1Score()) + "\n";
		outString += "\n";
		outString += "Classification Matrix" + "\n";
		outString += "Entry [i][j] indicates how many Tweets of the row-emotion has been classified as the column-emotion." + "\n";
		outString += "From left to right and from top to bottom:" + "\n";
		outString += "Enjoyment, Anger, Fear, Sadness, Surprise, Disgust, Unknown(only column)" + "\n";
		// Creates the string for the missclassification matrix.
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 7; j++) {
				outString += df1.format(resultClassification.getMisclassificationMatrix()[i][j]) + "\t";
			}
			outString += "\n";
		}
		return outString;
	}

	/**
	 * Creates a string containing all results of the modifier detection evaluation.
	 * 
	 * @param resultModifier
	 *            Containing all results of the modifier detection evaluation.
	 * @return Returns a string that contains all results of the modifier detection evaluation.
	 */
	private String generateResultStringModifier(Result resultModifier) {
		String outString = "";
		outString += "Recall negator = " + df1.format(resultModifier.getRecall()[0]) + "\n";
		outString += "Recall intensifier = " + df1.format(resultModifier.getRecall()[1]) + "\n";
		outString += "Recall diminisher = " + df1.format(resultModifier.getRecall()[2]) + "\n";
		outString += "\n";
		outString += "Precision negator = " + df1.format(resultModifier.getPrecision()[0]) + "\n";
		outString += "Precision intensifier = " + df1.format(resultModifier.getPrecision()[1]) + "\n";
		outString += "Precision diminisher = " + df1.format(resultModifier.getPrecision()[2]) + "\n";
		outString += "\n";
		outString += "Accuracy negator = " + df1.format(resultModifier.getAccuracy()[0]) + "\n";
		outString += "Accuracy intensifier = " + df1.format(resultModifier.getAccuracy()[1]) + "\n";
		outString += "Accuracy diminisher = " + df1.format(resultModifier.getAccuracy()[2]) + "\n";
		outString += "\n";
		outString += "F1 score negator = " + df1.format(resultModifier.getF1Score()[0]) + "\n";
		outString += "F1 score intensifier = " + df1.format(resultModifier.getF1Score()[1]) + "\n";
		outString += "F1 score diminisher = " + df1.format(resultModifier.getF1Score()[2]) + "\n";
		outString += "\n";
		outString += "AVG recall = " + df1.format(resultModifier.getAvgRecall()) + "\n";
		outString += "AVG precision = " + df1.format(resultModifier.getAvgPrecision()) + "\n";
		outString += "AVG accuracy = " + df1.format(resultModifier.getAvgAccuracy()) + "\n";
		outString += "AVG F1 score = " + df1.format(resultModifier.getAvgF1Score()) + "\n";
		outString += "\n";
		return outString;
	}

	/**
	 * Creates a string containing all settings of this experiment.
	 * 
	 * @param resultClassification
	 *            Containing all results of the emotion classification.
	 * @return Returns a string that contains all all settings of this experiment.
	 */
	private String generateExperimentSetupString(Result resultClassification) {
		String outString = "";
		outString += "--------------------------------------------------------------------------------------------------------------------------------";
		outString += "\n";
		outString += "EXPERIMENT SETTINGS:" + "\n";
		outString += "\n";
		outString += "Training data = " + trainingDataPath + "\n";
		outString += "Training data size = " + corpus.getTrainingSet().size() + " Tweets" + "\n";
		outString += "Number of Tweets per Emotion in training data =";
		outString += " Enjoyment:" + resultClassification.getTweetsPerEmoTraining()[0];
		outString += " Anger:" + resultClassification.getTweetsPerEmoTraining()[1];
		outString += " Fear:" + resultClassification.getTweetsPerEmoTraining()[2];
		outString += " Sad:" + resultClassification.getTweetsPerEmoTraining()[3];
		outString += " Surprise:" + resultClassification.getTweetsPerEmoTraining()[4];
		outString += " Disgust:" + resultClassification.getTweetsPerEmoTraining()[5] + "\n";
		outString += "\n";
		outString += "Test data = " + testDataPath + "\n";
		outString += "Test data size = " + corpus.getTestSet().size() + " Tweets" + "\n";
		outString += "Number of Tweets per Emotion in test data =";
		outString += " Enjoyment:" + resultClassification.getTweetsPerEmoTest()[0];
		outString += " Anger:" + resultClassification.getTweetsPerEmoTest()[1];
		outString += " Fear:" + resultClassification.getTweetsPerEmoTest()[2];
		outString += " Sad:" + resultClassification.getTweetsPerEmoTest()[3];
		outString += " Surprise:" + resultClassification.getTweetsPerEmoTest()[4];
		outString += " Disgust:" + resultClassification.getTweetsPerEmoTest()[5] + "\n";
		outString += "\n";
		if (classifierType == 1) {
			outString += "Classifier = support vector machine" + "\n";
			outString += "Epsilon = " + epsSVM_EMO + "\n";
			outString += "C = " + c_EMO + "\n";
			outString += "n-gram = " + n_gram + "\n";
		} else if (classifierType == 2) {
			outString += "Classifier = word list" + "\n";
			outString += "Epsilon = " + epsWL + "\n";
			outString += "Searches = " + searches + "\n";
			outString += "Tries = " + tries + "\n";
		} else {
			outString += "Classifier = ???" + "\n";
		}
		outString += "\n";
		if (modifierDetectionMethod == 0) {
			outString += "Modifier detection method = no detection" + "\n";
		} else if (modifierDetectionMethod == 1) {
			outString += "Modifier detection method = 'modify next n words' heuristic" + "\n";
			outString += "n = " + n + "\n";
		} else if (modifierDetectionMethod == 2) {
			outString += "Modifier detection method = NegEx" + "\n";
		} else if (modifierDetectionMethod == 3) {
			outString += "Modifier detection method = traversing dependency tree" + "\n";
		} else if (modifierDetectionMethod == 4) {
			outString += "Modifier detection method = support vector machine" + "\n";
			outString += "Epsilon = " + epsSVM_MOD + "\n";
			outString += "C = " + ((ModDetect_SVM) modDetect).getC_MOD() + "\n";
		} else {
			outString += "Modifier detection method = ???" + "\n";
		}
		if (modifierDetectionMethod != 0) {
			if (modifierDetectionSettings[0]) {
				outString += "negation detection = true" + "\n";
			} else {
				outString += "negation detection = false" + "\n";
			}
			if (modifierDetectionSettings[1]) {
				outString += "intensifier detection = true" + "\n";
			} else {
				outString += "intensifier detection = false" + "\n";
			}
			if (modifierDetectionSettings[2]) {
				outString += "diminisher detection = true" + "\n";
			} else {
				outString += "diminisher detection = false" + "\n";
			}
			outString += "\n";
			if (evaluateModifierDetection) {
				outString += "modifier evaluated = true" + "\n";
			} else {
				outString += "modifier evaluated = false" + "\n";
			}
		}
		outString += "\n";
		if (stemming) {
			outString += "stemming = true" + "\n";
		} else {
			outString += "stemming = false" + "\n";
		}
		return outString;
	}
}