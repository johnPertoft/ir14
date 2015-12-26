package ir;

import java.io.*;
import java.util.*;

public class Query {

	public LinkedList<String> terms = new LinkedList<String>();
	public HashMap<String, Double> weights = new HashMap<String, Double>();

	public Query() {
	}

	public Query(String queryString) {
		StringTokenizer tok = new StringTokenizer( queryString );
		while ( tok.hasMoreTokens() ) {
			String term = tok.nextToken();
			terms.add(term);
			weights.put(term, 1.0);
		}    
	}

	public int size() {
		return terms.size();
	}

	public Query copy() {
		Query queryCopy = new Query();
		queryCopy.terms = (LinkedList<String>) terms.clone();
		queryCopy.weights = (HashMap<String, Double>) weights.clone();
		return queryCopy;
	}
	
	// TODO: either move this to SearchEngine or call functions in SearchEngine to compute 
	// tfidf weights
	public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Indexer indexer ) {
		final double ALPHA = 1.0;
		final double BETA = 0.75;

		// weights (without idf) for each term in the original query
		for (String term : terms) {
			int dft = indexer.index.getPostings(term).size();
			weights.put(term, ALPHA / terms.size());
		}

		int numRelevant = 0;
		for (boolean b : docIsRelevant) if (b) numRelevant++;

		// normalised weights (without idf) for each term in each relevant document
		Iterator<PostingsEntry> peIt = results.iterator();
		for (boolean isRelevant : docIsRelevant) {
			PostingsEntry pe = peIt.next();
			
			// only use the relevant documents (GAMMA = 0)
			if (isRelevant) {
				Map<String, Integer> documentTerms = indexer.getTermsInDoc(pe.docID);
				int docLength = indexer.index.docLengths.get("" + pe.docID);
				
				for (Map.Entry<String, Integer> termEntry : documentTerms.entrySet()) {
					String term = termEntry.getKey();
					if (!weights.containsKey(term)) {
						weights.put(term, 0.0);
						terms.add(term);
					}
					int tf = termEntry.getValue();
					double oldW = weights.get(term);
					weights.put(term, oldW + (BETA / numRelevant) * (((double) tf) / docLength));
				}
			}
		}
	}
}
