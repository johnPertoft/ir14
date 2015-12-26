package ir;

import java.util.*;
import java.io.*;

public class DiskIndex implements Index {
	private Indexer indexer;
	private SearchEngine searchEngine;

	public DiskIndex(Indexer indexer) {
		this.indexer = indexer;
		this.searchEngine = new SearchEngine(this);
	}

	public void insert(String token, int docID, int offset) {
		throw new UnsupportedOperationException("The index file is not created here");
	}

	public Iterator<String> getDictionary() {
		return indexer.termIndexPositions.keySet().iterator();
	}
	
	public double getPagerank(int docID) {
		String fullname = docIDs.get("" + docID);
		int idx = fullname.lastIndexOf("/");
		String shortname = fullname.substring(idx+1, fullname.length());
		idx = shortname.lastIndexOf(".");
		shortname = shortname.substring(0, idx);

		if (pagerankScores.containsKey(shortname)) return pagerankScores.get(shortname);
		else {
			System.err.println("Missing pagerank for: " + fullname + " (" + shortname + ")");
			return 0.0;
		}
	}

	public PostingsList getPostings( String token ) {
		if (indexer.termIndexPositions.containsKey(token)) {
			long filePos = indexer.termIndexPositions.get(token);

			try {
				RandomAccessFile indexFile = new RandomAccessFile("/tmp/pertoft/full-index", "r");
				indexFile.seek(filePos);
				String line = indexFile.readLine();
				indexFile.close();
				return parsePostingsList(line);

			} catch (Exception e) {
				System.err.println("Error reading index file");
				return null;
			}
		}
		return null;
	}

	private PostingsList parsePostingsList(String plString) {
		PostingsList pl = new PostingsList();
		String[] pEntries = plString.split(",");
		for (String pEntry : pEntries) {
			String[] info = pEntry.split(" ");
			int docID = Integer.parseInt(info[0]);
			for (int i = 1; i < info.length; i++) pl.add(docID, Integer.parseInt(info[i]));
		}

		return pl;
	}

	public PostingsList search( Query query, int queryType, int rankingType, int structureType ) {
		return searchEngine.search(query, queryType, rankingType, structureType);	
	}

	// saves the term index file
	public void cleanup() {
		if (indexer.indexExists()) return;

		System.out.println("Saving term index file");
		try {
			File termIndexFile = new File(indexer.getTermIndexFilename());
			termIndexFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(termIndexFile));

			for (Map.Entry<String, Long> ti : indexer.termIndexPositions.entrySet()) 
				bw.write(ti.getKey() +  " " + ti.getValue() + "\n");

			bw.flush();
			bw.close();
			
			File docPathsFile = new File(indexer.getDocPathsFilename());
			docPathsFile.createNewFile();
			bw = new BufferedWriter(new FileWriter(docPathsFile));
			for (Map.Entry<String, String> ti : docIDs.entrySet()) 
				bw.write(ti.getKey() + " " + ti.getValue() + "\n");
			
			bw.flush();
			bw.close();

			File docLengthsFile = new File(indexer.getDocLengthsFilename());
			docLengthsFile.createNewFile();
			bw = new BufferedWriter(new FileWriter(docLengthsFile));
			for (Map.Entry<String, Integer> e : docLengths.entrySet())
				bw.write(e.getKey() + " " + e.getValue() + "\n");

			bw.flush();
			bw.close();
			
			System.out.println("Writing pagerank file");
			File pagerankFile = new File(indexer.getPagerankFilename());
			pagerankFile.createNewFile();
			bw = new BufferedWriter(new FileWriter(pagerankFile));
			for (Map.Entry<String, Double> e : pagerankScores.entrySet()) 
				bw.write(e.getKey() + " " + e.getValue() + "\n");

			bw.flush();
			bw.close();

		} catch (Exception e) {
			System.err.println("Error during saving of index");
			// TODO: remove all index files
		}
	}
}
