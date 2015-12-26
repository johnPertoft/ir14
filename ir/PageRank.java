package ir;

import java.util.*;
import java.io.*;

public class PageRank{
	final static int MAX_NUMBER_OF_DOCS = 2000000;
	Hashtable<String,Integer> docNumber = new Hashtable<String,Integer>();
	String[] docName = new String[MAX_NUMBER_OF_DOCS];
	HashMap<Integer,HashSet<Integer>> link = new HashMap<Integer,HashSet<Integer>>();
	
	int numDocs;
	String linksFile;
	String articleNamesFile;
	int[] out = new int[MAX_NUMBER_OF_DOCS];
	int numberOfSinks = 0;
	
	final static double BORED = 0.15;
	final static double EPSILON = 0.0001;
	final static int MAX_NUMBER_OF_ITERATIONS = 1000;

	public PageRank(String linksFile, String articleNamesFile) {
		this.linksFile = linksFile;
		this.articleNamesFile = articleNamesFile;

		numDocs = readDocs(linksFile);
	}

	public void pagerank(Map<String, Double> pagerankScores) {
		pagerankScores.clear();
		try {
			// first read the mapping from pagerank's document numbers to the filenames
			Map<Integer, String> articleNames = new HashMap<Integer, String>();
			BufferedReader br = new BufferedReader(new FileReader(articleNamesFile));
			String line;
			while ((line = br.readLine()) != null) {
				String[] s = line.split(";");
				articleNames.put(Integer.parseInt(s[0]), s[1]);
			}
			
			long start = System.currentTimeMillis();
			double[] scores = exactPagerank(numDocs);

			//double[] scores = approxPagerank2(numDocs);
			//double[] scores = mcEndPointRandomStart(numDocs, 0.02);
			//double[] scores = mcEndPointCyclicStart(numDocs, 50);
			// args for mcCompletePath(numDocs, 100, stopAtSink, randomStart)
			//double[] scores = mcCompletePath(numDocs, 100, false, false);
			//double[] scores = mcCompletePath(numDocs, 200, true, false);
			//double[] scores = mcCompletePath(numDocs, 200, true, true);
			long end = System.currentTimeMillis();
			System.out.println("Pagerank calculated in: " + (end - start) + " ms.");
			
			// compare with other approximation methods
			//double[] scores2 = mcCompletePath(numDocs, 100, false, false);
			//System.out.println("Sum of sq pos diffs: " + sumSquareDiffs(scores, scores2, false));
			
			// TODO: wrong scores are saved to documents
			/*
			for (int i = 0; i < scores.length; i++) {
				if (i == 121) {
					System.out.println(articleNames.get(i));
					System.out.println(scores[i-1]);
					System.out.println(scores[i]);
					System.out.println(scores[i+1]);
				}
				pagerankScores.put(articleNames.get(i), scores[i]);
			}
			*/

		} catch (Exception e ) {
			System.err.println("fail during pagerank");
		}
	}
	
	public double[] exactPagerank(int numberOfDocs) {
		final double c = 1 - BORED;	
		final double jumpProbability = BORED / numberOfDocs;
		final double universalProbability = c / (numberOfDocs - 1);
		
		// Power iteration
		double[] x = new double[numberOfDocs]; // (0,0,0,...)
		double[] xprim = new double[numberOfDocs];
		double avg = 1.0 / numberOfDocs;
		for (int i = 0; i < numberOfDocs; i++) xprim[i] = avg;

		int iterations = 0;
		boolean done = false;
		while (!done) {
			iterations++;
			System.out.println("iteration: " + iterations);
			x = xprim;
			xprim = matrixRMult(c, jumpProbability, universalProbability, x);

			if (Double.compare(euclideanDistance(x, xprim), EPSILON) < 0 || iterations > 10) 
				done = true;
		}
		
		return x;
	}

	private double[] matrixRMult(double C, double jumpProb, double universalProb, double[] x) {
		double[] xprim = new double[x.length];

		// first add the contribution from all the ones that have outgoing links to their respective columns
		for (Map.Entry<Integer, HashSet<Integer>> l : link.entrySet()) {
			int from = l.getKey();
			for (int to : l.getValue()) xprim[to] += (C / out[from]) * x[from]; 
		}

		// then add the universal jump probability to the ones without outgoing links
		// and bored jump probability to all
		for (int c = 0; c < x.length; c++) {
			for (int r = 0; r < x.length; r++) {
				if (out[r] == 0) xprim[c] += universalProb * x[r];
				xprim[c] += jumpProb * x[r];
			}
		}

		return xprim;
	}
	
	private double euclideanDistance(double[] x, double[] y) {
		double sum = 0.0;
		for (int i = 0; i < x.length; i++) sum += Math.pow(x[i] - y[i], 2.0);
		return Math.sqrt(sum);
	}

	public double[] approxPagerank(int numberOfDocs) {
		double c = 1 - BORED;
		double BORED_OVER_N = BORED / numberOfDocs;
		double NUM_SINKS_OVER_N_SQ = ((double) numberOfSinks) / (numberOfDocs * numberOfDocs);	

		double[] x = new double[numberOfDocs];
		double[] xprim = new double[numberOfDocs];
		double avg = 1.0 / numberOfDocs;
		for (int i = 0; i < numberOfDocs; i++) x[i] = avg;

		int iterations = 0;
		boolean done = false;
		while (!done) {
			iterations++;
			xprim = new double[numberOfDocs];
			for (int i = 0; i < numberOfDocs; i++) {
				if (link.containsKey(i))
					for (int j : link.get(i)) // adds probability to get to the neighbors
						xprim[j] += (x[i] * c) / ((double) link.get(i).size());

				xprim[i] += BORED_OVER_N; // bored jump probability 

				// with this the values differ more, did they assume it was 0?
				//xprim[i] += NUM_SINKS_OVER_N_SQ; // 0 for full wiki?
			}

			double dist = euclideanDistance(x, xprim);
			if (Double.compare(euclideanDistance(x, xprim), EPSILON) < 0 || iterations > 1000)
				done = true;
			else 
				x = xprim.clone();
		}

		return x;
	}
	
	// this computes the same as the exact but faster, anything wrong?
	public double[] approxPagerank2(int numberOfDocs) {
		double c = 1 - BORED;
		double BORED_OVER_N = BORED / numberOfDocs;
		double NUM_SINKS_OVER_N_SQ = ((double) numberOfSinks) / (numberOfDocs * numberOfDocs);	

		double[] x = new double[numberOfDocs];
		double[] xprim = new double[numberOfDocs];
		double avg = 1.0 / numberOfDocs;
		for (int i = 0; i < numberOfDocs; i++) x[i] = avg;

		int iterations = 0;
		boolean done = false;
		while (!done) {
			iterations++;
			xprim = new double[numberOfDocs];

			for (int i = 0; i < numberOfDocs; i++) {
				if (link.containsKey(i))
					for (int j : link.get(i)) // add probability to neighbors
						xprim[j] += (x[i] * c) / ((double) link.get(i).size());
				else
					for (int j = 0; j < numberOfDocs; j++)
						if (i != j) 
							xprim[j] += (x[i] * c) / ((double) numberOfDocs - 1);

				xprim[i] += BORED_OVER_N; // bored jump probability
			}

			if (Double.compare(euclideanDistance(x, xprim), EPSILON) < 0 || iterations > 1000)
				done = true;
			else 
				x = xprim.clone();
		}

		return x;
	}

	public double[] mcEndPointRandomStart(int numberOfDocs, double scalar) {
		Random rand = new Random(System.currentTimeMillis());
		final int N = (int) (numberOfDocs * numberOfDocs * scalar);
		int[] ends = new int[numberOfDocs];
		for (int i = 0; i < N; i++) {
			KeepLast kl = new KeepLast();
			randomWalkGeneral(rand, numberOfDocs, kl);
			ends[kl.doc]++;
		}
		
		double[] x = new double[numberOfDocs];
		for (int i = 0; i < ends.length; i++) x[i] = ((double) ends[i]) / N;

		return x;
	}

	public double[] mcEndPointCyclicStart(int numberOfDocs, int numStarts) {
		double[] x = new double[numberOfDocs];
		int[] ends = new int[numberOfDocs];
		final int N = numberOfDocs * numStarts;
		Random rand = new Random(System.currentTimeMillis());
		HashMap<String, Object> options = new HashMap<String, Object>();
		
		for (int i = 0; i < numberOfDocs; i++) {
			options.put("start", i);
			for (int j = 0; j < numStarts; j++) {
				KeepLast kl = new KeepLast();
				randomWalkGeneral(options, rand, numberOfDocs, kl);
				ends[kl.doc]++;
			}
		}

		for (int i = 0; i < ends.length; i++) x[i] = ((double) ends[i]) / N;
		
		return x;
	}
	
	public double[] mcCompletePath(int numberOfDocs, int numStarts, boolean stopAtSink, boolean randomStart) {
		double[] x = new double[numberOfDocs];
		RecordVisits rv = new RecordVisits(new int[numberOfDocs]);
		HashMap<String, Object> options = new HashMap<String, Object>();
		if (stopAtSink) options.put("stopAtSink", true);

		final int N = numberOfDocs * numStarts;
		Random rand = new Random(System.currentTimeMillis());

		for (int i = 0; i < numberOfDocs; i++) {
			if (!randomStart) options.put("start", i);
			for (int j = 0; j < numStarts; j++) 
				randomWalkGeneral(options, rand, numberOfDocs, rv);
		}
		
		int totalNumVisits = 0;
		for (int i = 0; i < rv.visits.length; i++) totalNumVisits += rv.visits[i];

		for (int i = 0; i < rv.visits.length; i++) {
			//x[i] = ((double) rv.visits[i]) / N; // according to lecture
			x[i] = ((double) rv.visits[i]) / totalNumVisits; // not according to lecture, but scoring results similar to others
		}		
		
		return x;
	}

	private void randomWalkGeneral(Random rand, int numDocs, CallBack cb) {
		randomWalkGeneral(new HashMap<String, Object>(), rand, numDocs, cb);
	}

	private void randomWalkGeneral(HashMap<String, Object> options, Random rand, int numDocs, CallBack cb) {
		int start = rand.nextInt(numDocs);
		if (options.containsKey("start")) start = ((Integer) options.get("start")).intValue();
		boolean stopAtSink = false;
		if (options.containsKey("stopAtSink")) stopAtSink = ((Boolean) options.get("stopAtSink")).booleanValue();
		
		int doc = start;
		cb.act(doc);
		
		double chance = rand.nextDouble();
		while (Double.compare(chance, BORED) > 0) { // TODO: change to do while?
			if (link.containsKey(doc)) {
				int index = rand.nextInt(link.get(doc).size());
				int i = 0;
				for (int next : link.get(doc)) { // can't pick a random element from set in constant time
					if (i++ == index) {
						doc = next;
						break;
					}
				}

			} else {
				if (stopAtSink) return;
				boolean valid = false;
				int next = rand.nextInt(numDocs);
				if (next != doc) valid = true;

				while (!valid) {
					next = rand.nextInt(numDocs);
					if (next != doc) valid = true;
				}

				doc = next;
			}

			cb.act(doc);
			chance = rand.nextDouble();
		}
	}

	private interface CallBack {
		public void act(int doc);
	}

	private class RecordVisits implements CallBack {
		int[] visits;
		RecordVisits(int[] visits) {
			this.visits = visits;
		}
		public void act(int doc) {
			visits[doc]++;
		}
	}

	private class KeepLast implements CallBack {
		int doc;
		public void act(int doc) {
			this.doc = doc;
		}
	}
	
	private int readDocs( String filename ) {
		int fileIndex = 0;
		try {
			System.err.print( "Reading file... " );
			BufferedReader in = new BufferedReader( new FileReader( filename ));
			String line;
			while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
				int index = line.indexOf( ";" );
				String title = line.substring( 0, index );
				Integer fromdoc = docNumber.get( title );
				//  Have we seen this document before?
				if ( fromdoc == null ) {	
					// This is a previously unseen doc, so add it to the table.
					fromdoc = fileIndex++;
					docNumber.put( title, fromdoc );
					docName[fromdoc] = title;
				}
				// Check all outlinks.
				StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
				while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
					String otherTitle = tok.nextToken();
					Integer otherDoc = docNumber.get( otherTitle );
					if ( otherDoc == null ) {
						// This is a previousy unseen doc, so add it to the table.
						otherDoc = fileIndex++;
						docNumber.put( otherTitle, otherDoc );
						docName[otherDoc] = otherTitle;
					}
					// Set the probability to 0 for now, to indicate that there is
					// a link from fromdoc to otherDoc.
					if (link.get(fromdoc) == null) {
						link.put(fromdoc, new HashSet<Integer>());
					}
					if (!link.get(fromdoc).contains(otherDoc)) {
						link.get(fromdoc).add(otherDoc);
						out[fromdoc]++;
					}
				}
			}
			if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
				System.err.print( "stopped reading since documents table is full. " );
			}
			else {
				System.err.print( "done. " );
			}
			// Compute the number of sinks.
			for ( int i=0; i<fileIndex; i++ ) {
				if ( out[i] == 0 )
					numberOfSinks++;
			}
		}
		catch ( FileNotFoundException e ) {
			System.err.println( "File " + filename + " not found!" );
		}
		catch ( IOException e ) {
			System.err.println( "Error reading file " + filename );
		}
		System.err.println( "Read " + fileIndex + " number of documents" );
		return fileIndex;
	}
	
	private void print(double[] x) {
		List<Doc> pageRankList = new ArrayList<Doc>();
		for (int i = 0; i < x.length; i++) pageRankList.add(new Doc(docName[i], x[i]));
		Collections.sort(pageRankList);
		for (int i = 0; i < 50; i++) System.out.println((i+1) + "\t" + pageRankList.get(i));
	}

	private void setPagerankScores(double[] x, Map<String, Double> pagerankScores) {
		for (int i = 0; i < x.length; i++) pagerankScores.put(docName[i], x[i]);
	}
	
	// should be either 50 highest exact or 50 lowest exact
	private int sumSquareDiffs(double[] exact, double[] comp, boolean f50) {
		int sumSqDiff = 0;
		double sumSqScoreDiff = 0.0;
		
		ArrayList<Doc> exactList = new ArrayList<Doc>(exact.length);
		ArrayList<Doc> compList = new ArrayList<Doc>(exact.length);
		for (int i = 0; i < exact.length; i++) {
			exactList.add(new Doc(docName[i], exact[i]));
			compList.add(new Doc(docName[i], comp[i]));
		}
		Collections.sort(exactList);
		Collections.sort(compList);
		
		if (f50) {
			for (int i = 0; i < 50; i++) {
				String doc = exactList.get(i).docName;
				int j = compList.indexOf(new Doc(doc, 0.0));
				int diff = i - j;
				double scoreDiff = exactList.get(i).score - compList.get(j).score; 
				sumSqDiff += diff * diff;
				sumSqScoreDiff += scoreDiff * scoreDiff;
			}
		} else {
			for (int i = exact.length - 1; i >= exact.length - 50; i--) {
				String doc = exactList.get(i).docName;
				int j = compList.indexOf(new Doc(doc, 0.0));
				int diff = i - j;
				double scoreDiff = exactList.get(i).score - compList.get(j).score; 
				sumSqDiff += diff * diff;
				sumSqScoreDiff += scoreDiff * scoreDiff;
			}
		}
		
		System.out.println("sum sq score diff: " + sumSqScoreDiff);
		return sumSqDiff;
	}
	
	// only used for printing
	private class Doc implements Comparable<Doc> {
		String docName;
		double score; 

		Doc(String docName, double score) {
			this.docName = docName;
			this.score = score;
		}

		@Override
		public int compareTo(Doc other) {
			return Double.compare(other.score, score);
		}	

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Doc)) return false;
			Doc otherDoc = (Doc) other;
			return docName.equals(otherDoc.docName);
		}

		@Override
		public String toString() {
			return docName + "\t" + score;
		}
	}
}
