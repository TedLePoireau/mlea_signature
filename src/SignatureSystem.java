import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.linear.RealVector;

import plot.PlotChart;
import preprocessing.Preprocessor;
import train.Classifier;

import common.LabeledSignature;
import common.Signature;
import common.SignatureException;

import distance.Comparator;
import distance.CompareResult;
import features.FeatureExtractor;
import features.GlobalFeatureVector;
import features.PCA;


public class SignatureSystem
{
	private static final boolean FEATURE_TEST = false;
	final int numberOfUsers = 5;
	final int trainIteration = 4;

	double forgeryThreshold;
	double identityThreshold;

	boolean plotMode;

	private ArrayList<ArrayList<LabeledSignature>> userGenuineTrainSignatures;
	private ArrayList<ArrayList<LabeledSignature>> userForgeryTrainSignatures;
	private ArrayList<LabeledSignature> testSignatures;

	public SignatureSystem() {
		plotMode = false;
	}

	public ArrayList<RealVector> applyPCA(String database)
	{
		ArrayList<LabeledSignature> signatures = parseDatabaseFile(database);
		ArrayList<ArrayList<Double>> featureVectors = new ArrayList<ArrayList<Double>>();

		for (LabeledSignature s : signatures) {
			GlobalFeatureVector v = FeatureExtractor.extractGlobalFeature(s);
			featureVectors.add(v);
		}

		return PCA.compute(featureVectors);
	}

	/**
	 * Train the program and measure performances
	 * @param database File containing all the signatures path of all users
	 */
	public void measurePerformances(String database)
	{
		boolean bestSuccess[] = null;
		boolean bestForgery[] = null;
		boolean bestIdentity[] = null;
		boolean bestIntra[] = null;
		boolean bestTotal[] = null;
		double featureGlobal = 0.;
		double featureForgery = 0.;
		double featureIdentity = 0.;
		double featureIntra = 0.;
		double featureTotal = 0.;

		for (int step = (int) Math.pow(2, 0); step < Math.pow(2, 11); step++) {

			int binary = step;
			boolean selector[] = new boolean[15];
			for (int i = 0; i < 15; i++) {
				selector[i] = binary % 2 == 1;
				binary /= 2;
			}
			FeatureExtractor.selector = selector;

			try {
				File output = new File("log/perfs.log");
				FileWriter writer = new FileWriter(output);

				double globalSuccess = 0.0;
				double globalForgerySuccess = 0.0;
				double globalIdentitySuccess = 0.0;
				double globalIntraSuccess = 0.0;
				double thresholdMean = 0.0;

				for (int i = 0; i < this.trainIteration; i++)
				{
					if (!FEATURE_TEST)
						writer.write("=== Iteration " + i + " ===" + System.getProperty("line.separator"));

					// Train
					if (!FEATURE_TEST)
						System.out.println("================ Train ================");
					chooseTrainAndTestSignatures(database);
					this.forgeryThreshold = trainUnversalForgeryThreshold();
					thresholdMean += this.forgeryThreshold;

					if (!FEATURE_TEST) {
						writer.write("Chosen LocalThreshold : " + this.forgeryThreshold + System.getProperty("line.separator"));
						System.out.println("Chosen LocalThreshold : " + this.forgeryThreshold);

						this.identityThreshold = trainUnversalIdentityThreshold();
						writer.write("GlobalThreshold : " + this.identityThreshold + System.getProperty("line.separator"));
						System.out.println("GlobalThreshold : " + this.identityThreshold);
					}

					// Test
					if (!FEATURE_TEST)
						System.out.println("================ Test ================");

					double success = 0.0;
					double forgerySuccess = 0.0;
					double identitySuccess = 0.0;
					double intraSuccess = 0.0;
					int numberOfForgeryTests = 0;
					int numberOfIdentityTests = 0;
					int numberOfIntraTests = 0;

					for (int j = 0; j < this.testSignatures.size(); j++) {
						for (int k = j + 1; k < this.testSignatures.size(); k++) {
							if (j != k)
							{
								// Don't compare forgery with forgery
								if (!this.testSignatures.get(j).isGenuine() && !this.testSignatures.get(k).isGenuine())
									continue;

								// Same user = same ID + genuine
								boolean realDecision = this.testSignatures.get(j).getUserID() == this.testSignatures.get(k).getUserID() &&
										this.testSignatures.get(j).isGenuine() == this.testSignatures.get(k).isGenuine();
								// Compare
								CompareResult res = Comparator.compareSignatures(this.testSignatures.get(j), this.testSignatures.get(k),
										this.forgeryThreshold, this.identityThreshold);

								// Write log
								//								writer.write(this.testSignatures.get(j).getName() + (this.testSignatures.get(j).isGenuine() ? " (genuine)" : " (forgery)"));
								//								writer.write(" - " + this.testSignatures.get(k).getName() + (this.testSignatures.get(k).isGenuine() ? " (genuine)" : " (forgery)"));
								//								writer.write(" : dist = " + res.distance + ", decision = " + res.decision + ", reality = " + realDecision + System.getProperty("line.separator"));

								// Count success
								if (res.decision == realDecision) {
									success += 1.0;
								}

								if (this.testSignatures.get(j).getUserID() == this.testSignatures.get(k).getUserID() &&
										this.testSignatures.get(j).isGenuine() != this.testSignatures.get(k).isGenuine())
								{
									if (res.decision == realDecision)
										forgerySuccess += 1.0;
									numberOfForgeryTests++;
								}

								if (this.testSignatures.get(j).getUserID() == this.testSignatures.get(k).getUserID() &&
										this.testSignatures.get(j).isGenuine() == this.testSignatures.get(k).isGenuine()) {
									if (res.decision == realDecision)
										intraSuccess += 1.0;
									numberOfIntraTests++;
								}

								if (this.testSignatures.get(j).getUserID() != this.testSignatures.get(k).getUserID() ||
										this.testSignatures.get(j).isGenuine() == this.testSignatures.get(k).isGenuine())
								{
									if (res.decision == realDecision)
										identitySuccess += 1.0;
									numberOfIdentityTests++;
								}

							}
						}
					}

					success = 100.0 * success / (numberOfForgeryTests + numberOfIdentityTests);
					forgerySuccess = 100.0 * forgerySuccess / numberOfForgeryTests;
					identitySuccess = 100.0 * identitySuccess / numberOfIdentityTests;
					intraSuccess = 100.0 * intraSuccess / numberOfIntraTests;

					globalSuccess += success;
					globalForgerySuccess += forgerySuccess;
					globalIdentitySuccess += identitySuccess;
					globalIntraSuccess += intraSuccess;

					if (!FEATURE_TEST) {
						System.out.println("[" + i + "]: " + success + "% success over " +
								numberOfForgeryTests + " forgery tests and " + numberOfIdentityTests + " identity tests.");
						System.out.println("     \t" + forgerySuccess + "% forgery success");
						System.out.println("     \t" + identitySuccess + "% identity success");
						System.out.println("     \t\t" + intraSuccess + "% intra success");
					}

					// Writer log result
					if (!FEATURE_TEST) {
						writer.write("=== Result ===" + System.getProperty("line.separator"));
						writer.write("=== " + success + "% success ===" + System.getProperty("line.separator"));
						writer.write("=== " + forgerySuccess + "% forgery success ===" + System.getProperty("line.separator"));
						writer.write("=== " + identitySuccess + "% identity success ===" + System.getProperty("line.separator"));
						writer.write("=== " + intraSuccess + "% intra success ===" + System.getProperty("line.separator"));
					}
				}

				thresholdMean /= trainIteration;
				globalSuccess /= trainIteration;
				globalForgerySuccess /= trainIteration;
				globalIdentitySuccess /= trainIteration;
				globalIntraSuccess /= trainIteration;

				if (!FEATURE_TEST) {
					System.out.println("=================================================");
					System.out.println("[Threshold]: " + thresholdMean);
					System.out.println("[Performances]: " + globalSuccess + "% success");
					System.out.println("                \t" + globalForgerySuccess + "% forgery success");
					System.out.println("                \t" + globalIdentitySuccess + "% identity success");
					System.out.println("                \t\t" + globalIntraSuccess + "% intra success");
				}

				writer.close();

				if (globalSuccess > featureGlobal) {
					featureGlobal = globalSuccess;
					bestSuccess = FeatureExtractor.selector;
				}
				if (globalForgerySuccess > featureForgery) {
					featureForgery = globalForgerySuccess;
					bestForgery = FeatureExtractor.selector;
				}
				if (globalIdentitySuccess > featureIdentity) {
					featureIdentity = globalIdentitySuccess;
					bestIdentity = FeatureExtractor.selector;
				}
				if (globalIntraSuccess > featureIntra) {
					featureIntra = globalIntraSuccess;
					bestIntra = FeatureExtractor.selector;
				}
				if (globalForgerySuccess + globalIdentitySuccess + globalIntraSuccess > featureTotal) {
					featureTotal = globalForgerySuccess + globalIdentitySuccess + globalIntraSuccess;
					bestTotal = FeatureExtractor.selector;
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

			if (!FEATURE_TEST)
				break;
		}

		if (FEATURE_TEST) {
			System.out.println("[Performances]: " + featureGlobal + "% success " + selectorToString(bestSuccess));
			System.out.println("                \t" + featureForgery + "% forgery success " + selectorToString(bestForgery));
			System.out.println("                \t" + featureIdentity + "% identity success " + selectorToString(bestIdentity));
			System.out.println("                \t\t" + featureIntra + "% intra success " + selectorToString(bestIntra));
			System.out.println(featureTotal + "% Sum success " + selectorToString(bestTotal));
		}
	}

	private String selectorToString(boolean selector[]) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		for (boolean b : selector) {
			builder.append(b ? 1 : 0);
		}
		builder.append("]");
		return builder.toString();
	}

	/**
	 * Randomly select train and test signatures
	 * @param database File containing all the signatures path of all users
	 */
	public void chooseTrainAndTestSignatures(String database)
	{
		ArrayList<LabeledSignature> signatures = parseDatabaseFile(database);
		if (database == null) return;

		ArrayList<ArrayList<LabeledSignature>> userGenuineSignatures = new ArrayList<ArrayList<LabeledSignature>>(numberOfUsers);
		ArrayList<ArrayList<LabeledSignature>> userForgerySignatures = new ArrayList<ArrayList<LabeledSignature>>(numberOfUsers);
		this.userGenuineTrainSignatures = new ArrayList<ArrayList<LabeledSignature>>(numberOfUsers);
		this.userForgeryTrainSignatures = new ArrayList<ArrayList<LabeledSignature>>(numberOfUsers);
		this.testSignatures = new ArrayList<LabeledSignature>();

		// Initialize lists
		for (int i = 0; i < numberOfUsers; i++) {
			userGenuineSignatures.add(new ArrayList<LabeledSignature>());
			userForgerySignatures.add(new ArrayList<LabeledSignature>());
		}

		// Class signatures according to their userID
		for (LabeledSignature s : signatures) {
			if (s.isGenuine())
				userGenuineSignatures.get(s.getUserID() - 1).add(s);
			else
				userForgerySignatures.get(s.getUserID() - 1).add(s);
		}

		// Select random train signatures for each user
		int genuineTrainSize = (int)(6.0 * userGenuineSignatures.get(0).size() / 10.0);
		int forgeryTrainSize = (int)(6.0 * userGenuineSignatures.get(0).size() / 10.0);

		for (int i = 0; i < numberOfUsers; i++)
		{
			boolean[] checkGenuine = new boolean[userGenuineSignatures.get(i).size()];
			this.userGenuineTrainSignatures.add(chooseRandomSignature(userGenuineSignatures.get(i), genuineTrainSize, checkGenuine));
			// Store test signatures
			for (int k = 0; k < checkGenuine.length; k++) {
				if (!checkGenuine[k]) {
					this.testSignatures.add(userGenuineSignatures.get(i).get(k));
				}
			}

			boolean[] checkForgery = new boolean[userForgerySignatures.get(i).size()];
			this.userForgeryTrainSignatures.add(chooseRandomSignature(userForgerySignatures.get(i), forgeryTrainSize, checkForgery));
			// Store test signatures
			for (int k = 0; k < checkForgery.length; k++) {
				if (!checkForgery[k]) {
					this.testSignatures.add(userForgerySignatures.get(i).get(k));
				}
			}
		}
	}

	/**
	 * Choose random signatures within the database
	 * @param database The list of signatures
	 * @param k The number of random signatures to choose
	 * @param check A boolean array that indicates the signatures that were choosen (referenced by their index)
	 * @return A list of k randomly choosen signatures among database
	 */
	private ArrayList<LabeledSignature> chooseRandomSignature(ArrayList<LabeledSignature> database, int k, boolean[] check)
	{
		ArrayList<LabeledSignature> choosenSignatures = new ArrayList<LabeledSignature>();
		Random rand = new Random();

		Arrays.fill(check, false);

		if (database.size() < k) {
			return null;
		}

		for (int i = 0; i < k; i++)
		{
			int index = rand.nextInt(check.length);

			if (check[index]) // try again
				i--;
			else {
				check[index] = true;
				choosenSignatures.add(database.get(i));
			}
		}

		return choosenSignatures;
	}

	/**
	 * Compute the identity threshold, by comparing each user to other users
	 * Need to chooseTrainAndTest Signatures first
	 */
	public double trainUnversalIdentityThreshold()
	{
		double thresholdMean = 0;

		for (int i = 0; i < numberOfUsers; i++)
		{
			// Combine all user's signatures different from user i
			ArrayList<LabeledSignature> trainExtra = new ArrayList<LabeledSignature>();
			for (int j = 0; j < numberOfUsers; j++) {
				if (j != i) {
					trainExtra.addAll(this.userGenuineTrainSignatures.get(j));
				}
			}

			thresholdMean += trainPersonalThreshold(this.userGenuineTrainSignatures.get(i), trainExtra);
		}

		thresholdMean /= numberOfUsers;

		return thresholdMean;
	}

	/**
	 * Compute the forgery threshold, by comparing each user to his forgery signatures
	 * Need to chooseTrainAndTest Signatures first
	 */
	public double trainUnversalForgeryThreshold()
	{
		double thresholdMean = 0;

		//		System.out.println("[Threshold]: Compute forgery threshold for each user");
		for (int i = 0; i < numberOfUsers; i++) {
			thresholdMean += trainPersonalThreshold(this.userGenuineTrainSignatures.get(i), this.userForgeryTrainSignatures.get(i));
		}

		thresholdMean /= numberOfUsers;

		return thresholdMean;
	}

	/**
	 * Classify user's signatures with an other train signatures
	 * @param userTrain Genuine signatures of a user (intra)
	 * @param signatureTrain Train of signatures to compare to the user (extra)
	 * @return The threshold that best separates user intra class with the user extra class
	 */
	private double trainPersonalThreshold(ArrayList<LabeledSignature> userTrain, ArrayList<LabeledSignature> signatureTrain)
	{
		ArrayList<Double> intraDistances = new ArrayList<Double>();
		ArrayList<Double> extraDistances = new ArrayList<Double>();

		// Compute intra distances by comparing the user to himself
		for (Signature gs : userTrain) {
			for (Signature fs : signatureTrain) {
				double d = Comparator.compareSignatures(gs, fs);
				extraDistances.add(d);
			}
		}

		// Compute extra distances by comparaing the user to other signatures
		for (int i = 0; i < userTrain.size(); i++) {
			for (int j = i; j < userTrain.size(); j++) {
				double d = Comparator.compareSignatures(userTrain.get(i), userTrain.get(j));
				intraDistances.add(d);
			}
		}

		double threshold = Classifier.computeThreshold(intraDistances, extraDistances);

		if (plotMode) {
			PlotChart.Plot("User " + userTrain.get(0).getUserID(), intraDistances, extraDistances, threshold);
		}

		return threshold;
	}



	/**
	 * Compare a list of signature and store the results
	 * @param inputfile A file containing on each line two signature paths to compare.
	 * @param outputfile The file in which we store the comparison results
	 */
	public void compareSignaturesFromFile(String inputfile, String outputfile)
	{
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			File output = new File(outputfile);
			FileWriter writer = new FileWriter(output);

			String line;
			while ((line = br.readLine()) != null) {
				String[] tokens = line.split(" ");
				if (tokens.length != 2) {
					System.err.println("Malformed input file.");
					return;
				}

				// Create signatures
				Signature s1 = new Signature(tokens[0]);
				Signature s2 = new Signature(tokens[1]);

				// Preprocess signatures
				Preprocessor.normalizeAndReduce(s1);
				Preprocessor.normalizeAndReduce(s2);

				// Compare signatures
				CompareResult res = Comparator.compareSignatures(s1, s2, this.forgeryThreshold, this.identityThreshold);

				// Write result
				writer.write(line + " " + res.distance + " " + res.getDecision() + System.getProperty("line.separator"));
			}

			br.close();
			writer.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		}

	}

	private ArrayList<LabeledSignature> parseDatabaseFile(String filename)
	{
		ArrayList<LabeledSignature> database = new ArrayList<LabeledSignature>();

		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;

			while ((line = br.readLine()) != null) {
				LabeledSignature s = new LabeledSignature(line);
				Preprocessor.normalizeAndReduce(s);
				database.add(s);
			}

			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (SignatureException e) {
			e.printStackTrace();
			return null;
		}

		return database;
	}
}
