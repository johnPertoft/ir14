package ir;

import java.util.*;

public class HashedIndex implements Index {

	private SearchEngine searchEngine;
	private HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();

	public HashedIndex() {
		this.searchEngine = new SearchEngine(this);
	}

	public void insert( String token, int docID, int offset ) {
		if (!index.containsKey(token)) index.put(token, new PostingsList());
		index.get(token).add(docID, offset);
	}

	public Iterator<String> getDictionary() {
		return index.keySet().iterator();
	}

	public PostingsList getPostings( String token ) {
		return index.get(token);
	}
	
	public double getPagerank(int docID) {
		String fullname = docIDs.get("" + docID);
		int idx = fullname.lastIndexOf("/");
		String shortname = fullname.substring(idx+1, fullname.length());
		idx = shortname.indexOf(".");
		shortname = shortname.substring(0, idx);

		if (pagerankScores.containsKey(shortname)) return pagerankScores.get(shortname);
		else {
			System.err.println("No pagerank score for that document");
			return 0.0;
		}
	}

	public PostingsList search( Query query, int queryType, int rankingType, int structureType ) {
		return searchEngine.search(query, queryType, rankingType, structureType);
	}
	
	public void cleanup() {
	}
}
