package ir;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.*;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDDocument;

public class Indexer {
	public Index index;
	private int lastDocID = 0;
	private final boolean MEMORY_INDEX = false;
	private final boolean USE_BIWORD_INDEX = false;
	private static final String PATH_ROOT = "/tmp/pertoft/";
	private final String BIWORD_INDEX_ROOT = PATH_ROOT + "biwordindex/";
	private final String INDEX_FILENAME = "full-index";
	private final String TERM_INDEX_FILENAME = "term-index";
	private final String DOC_PATHS_FILENAME = "docpath-index";
	private final String DOC_LENGTHS_FILENAME = "doclength-index";
	private final String PAGERANK_FILENAME = "pagerank-scores";
	private final int IN_MEMORY_LIMIT = 500000; // #tokens
	private int partitionID = 0;
	private int memoryConsumption = 0;
	private HashMap<String,PostingsList> tempIndex = new HashMap<String,PostingsList>();
	private List<String> partitionFiles = new LinkedList<String>();
	public Map<String, Long> termIndexPositions = null;

	static {
		try {
			File rootDir = new File(PATH_ROOT);
			if (!rootDir.exists()) rootDir.mkdir();
		} catch (Exception e) {
			System.err.println("Failed to create index file directory");
			System.exit(1);
		}
	}

	private int generateDocID() {
		return lastDocID++;
	}

	private int generateDocID( String s ) {
		return s.hashCode();
	}

	public Indexer() {
		index = MEMORY_INDEX ? new HashedIndex() : new DiskIndex(this);
	}

	public void processFiles( File f ) {
		// do not try to index fs that cannot be read
		if ( f.canRead() ) {
			if ( f.isDirectory() ) {
				String[] fs = f.list();
				// an IO error could occur
				if ( fs != null ) {
					for ( int i=0; i<fs.length; i++ ) {
						processFiles( new File( f, fs[i] ));
					}
				}
			} else {
				int docID = generateDocID();
				index.docIDs.put( "" + docID, f.getPath() );
				try {
					//  Read the first few bytes of the file to see if it is 
					// likely to be a PDF 
					Reader reader = new FileReader( f );
					char[] buf = new char[4];
					reader.read( buf, 0, 4 );
					if ( buf[0] == '%' && buf[1]=='P' && buf[2]=='D' && buf[3]=='F' ) {
						// We assume this is a PDF file
						try {
							String contents = extractPDFContents( f );
							reader = new StringReader( contents );
						}
						catch ( IOException e ) {
							// Perhaps it wasn't a PDF file after all
							reader = new FileReader( f );
						}
					}
					else {
						// We hope this is ordinary text
						reader = new FileReader( f );
					}
					SimpleTokenizer tok = new SimpleTokenizer( reader );
					int offset = 0;
					while ( tok.hasMoreTokens() ) {
						String token = tok.nextToken();
						insertIntoIndex( docID, token, offset++ );
					}


					index.docLengths.put( "" + docID, offset );
					reader.close();
				}
				catch ( IOException e ) {
					e.printStackTrace();
				}
			}
		}
	}

	public Map<String, Integer> getTermsInDoc(int docID) {
		Map<String, Integer> terms = new HashMap<String, Integer>();
		File f = new File(index.docIDs.get("" + docID));
		
		// only read files, not directories
		if (f.canRead() && !f.isDirectory()) {
			try {
				Reader reader = new FileReader( f );
				char[] buf = new char[4];
				reader.read( buf, 0, 4 );
				reader.close();

				if ( buf[0] == '%' && buf[1]=='P' && buf[2]=='D' && buf[3]=='F' ) {
					// We assume this is a PDF file
					try {
						String contents = extractPDFContents( f );
						reader = new StringReader( contents );
					}
					catch ( IOException e ) {
						// Perhaps it wasn't a PDF file after all
						reader = new FileReader( f );
					}

				}	else {
					// We hope this is ordinary text
					reader = new FileReader( f );
				}

				SimpleTokenizer tok = new SimpleTokenizer( reader );

				while ( tok.hasMoreTokens() ) {
					String token = tok.nextToken();

					if (terms.containsKey(token)) {
						terms.put(token, terms.get(token) + 1);

					} else {
						// first occurrence of word
						terms.put(token, 1);
					}
				}

				reader.close();

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}

		return terms;
	}

	public void readExistingIndex() {
		if (!indexExists()) {
			System.err.println("Index does not exist or is not complete.");
			return;
		}

		System.out.println("Reading existing index.");
		try {
			// first read file positions for each term
			BufferedReader br = new BufferedReader(new FileReader(getTermIndexFilename()));
			Map<String, Long> tIndex = new HashMap<String, Long>();
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] s = line.split(" ");
				tIndex.put(s[0], Long.parseLong(s[1]));
			}

			br.close();
			termIndexPositions = tIndex;

			// then read the paths for each document
			br = new BufferedReader(new FileReader(getDocPathsFilename()));
			line = null;
			while ((line = br.readLine()) != null) {
				String[] s = line.split(" ");
				index.docIDs.put(s[0], s[1]);
			}

			br.close();

			// then read the lengths of the documents
			br = new BufferedReader(new FileReader(getDocLengthsFilename()));
			line = null;
			while ((line = br.readLine()) != null) {
				String[] s = line.split(" ");
				index.docLengths.put(s[0], Integer.parseInt(s[1]));
			}

			if (new File(getPagerankFilename()).exists()) {
				System.out.println("Reading existing pagerank scores");
				br = new BufferedReader(new FileReader(getPagerankFilename()));
				line = null;
				while ((line = br.readLine()) != null) {
					String[] s = line.split(" ");
					index.pagerankScores.put(s[0], Double.parseDouble(s[1]));
				}
			}

		} catch (Exception e) {
			System.err.println("Error during reading of index file");
			System.exit(1);
		}
	}

	public void finishIndex() {
		if (MEMORY_INDEX) return;

		// write the remaining contents of the temporary index to a partition
		if (!tempIndex.isEmpty()) partitionFiles.add(writePartition());

		termIndexPositions = mergePartitions();
	}

	public boolean indexExists() {
		return new File(getIndexFilename()).exists() && 
			new File(getTermIndexFilename()).exists() &&
			new File(getDocPathsFilename()).exists() &&
			new File(getDocLengthsFilename()).exists();
	}

	public String getIndexFilename() {
		return PATH_ROOT + INDEX_FILENAME;
	}

	public String getTermIndexFilename() {
		return PATH_ROOT + TERM_INDEX_FILENAME; 
	}

	public String getDocPathsFilename() {
		return PATH_ROOT + DOC_PATHS_FILENAME;
	}

	public String getDocLengthsFilename() {
		return PATH_ROOT + DOC_LENGTHS_FILENAME;
	}

	public String getPagerankFilename() {
		return PATH_ROOT + PAGERANK_FILENAME;
	}

	public String extractPDFContents( File f ) throws IOException {
		FileInputStream fi = new FileInputStream( f );
		PDFParser parser = new PDFParser( fi );   
		parser.parse();   
		fi.close();
		COSDocument cd = parser.getDocument();   
		PDFTextStripper stripper = new PDFTextStripper();   
		String result = stripper.getText( new PDDocument( cd ));  
		cd.close();
		return result;
	}

	public void insertIntoIndex( int docID, String token, int offset ) {
		if (MEMORY_INDEX) index.insert(token, docID, offset);
		else {
			// spimi
			if (memoryConsumption < IN_MEMORY_LIMIT) {
				if (!tempIndex.containsKey(token)) tempIndex.put(token, new PostingsList());
				tempIndex.get(token).add(docID, offset);
				memoryConsumption++;

			} else {
				String partitionFile = writePartition();
				memoryConsumption = 0;
				tempIndex = new HashMap<String, PostingsList>();
				partitionFiles.add(partitionFile);
			}
		}
	}

	// NOTE: file structure is as follows
	// <term> <#documents>
	// <docID1> <offset1> <offset2> ...
	// ...
	// <docIDN> ...
	private String writePartition() {
		List<String> dictionary = new ArrayList<String>(tempIndex.size());
		dictionary.addAll(tempIndex.keySet());
		Collections.sort(dictionary);

		String fileName = PATH_ROOT + "partition-" + partitionID++;

		try {
			File partitionFile = new File(fileName);	
			BufferedWriter bw = new BufferedWriter(new FileWriter(partitionFile));

			for (String term : dictionary) {
				PostingsList pl = tempIndex.get(term);
				bw.write(term + " " + pl.size() + "\n");			
				Iterator<PostingsEntry> it = pl.iterator();
				while (it.hasNext()) {
					PostingsEntry pe = it.next();
					bw.write(pe.docID + " ");

					for (int offset : pe.offsets) bw.write(offset + " ");

					bw.write("\n");
				}
			}

			bw.flush();
			bw.close();

		} catch (Exception e) {
			//e.printStackTrace();
			System.err.println("Failed during partition writing");
			System.exit(1);
		}

		return fileName;
	}

	private class MergeEntry implements Comparable<MergeEntry> {
		String term;
		PostingsList postingsList;

		MergeEntry(String term, PostingsList pl) {
			this.term = term;
			this.postingsList = pl;
		}

		public int compareTo(MergeEntry other) {
			if (this.term.equals(other.term)) {
				int thisLastDocID = this.postingsList.list.getLast().docID;
				int otherFirstDocID = other.postingsList.list.getFirst().docID;
				if (thisLastDocID == otherFirstDocID) {
					// TODO compare offsets somehow
					return -1;

				} else return thisLastDocID - otherFirstDocID; // maybe wrong here?

			} else return this.term.compareTo(other.term);
		}
	}

	// reads a mergeentry from a partition file
	private MergeEntry readMergeEntry(BufferedReader br) throws IOException {
		String line = br.readLine();
		if (line == null) return null;
		String[] s = line.split(" ");
		String term = s[0];
		int numDocs = Integer.parseInt(s[1]);
		PostingsList pl = new PostingsList();
		for (int i = 0; i < numDocs; i++) {
			s = br.readLine().split(" ");
			int docID = Integer.parseInt(s[0]);
			PostingsEntry pe = new PostingsEntry(docID);
			for (int j = 1; j < s.length; j++) pe.offsets.add(Integer.parseInt(s[j]));

			pl.add(pe);
		}

		return new MergeEntry(term, pl);
	}

	private Map<String, Long> mergePartitions() {
		System.out.println("Merging partition files.");
		String fileName = PATH_ROOT + INDEX_FILENAME;
		Map<String, Long> termIndexPositions = new HashMap<String, Long>();

		try {
			File indexFile = new File(fileName);
			indexFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(indexFile));

			List<BufferedReader> partitionReaders = new ArrayList<BufferedReader>(partitionFiles.size());
			for (String fname : partitionFiles) partitionReaders.add(new BufferedReader(new FileReader(fname)));

			PriorityQueue<MergeEntry> prioQueue = new PriorityQueue<MergeEntry>();
			MergeEntry lastMergeEntry = null;

			int totalReaders = partitionReaders.size();
			int finishedReaders = 0;
			while (finishedReaders < totalReaders) {
				Iterator<BufferedReader> it = partitionReaders.iterator();
				while (it.hasNext()) {
					BufferedReader br = it.next();
					MergeEntry me = readMergeEntry(br);
					if (me == null) {
						it.remove();
						finishedReaders++;

					} else prioQueue.add(me);
				}

				// only read one (the one with highest prio) here
				MergeEntry me = prioQueue.poll();
				writeMergeEntry(lastMergeEntry, me, termIndexPositions, bw, indexFile);
				lastMergeEntry = me;
			}

			while (!prioQueue.isEmpty()) {
				MergeEntry me = prioQueue.poll();
				writeMergeEntry(lastMergeEntry, me, termIndexPositions, bw, indexFile);
				lastMergeEntry = me;
			}

			bw.flush();
			bw.close();

			// remove all partition files
			for (String pf : partitionFiles) new File(pf).delete();

		} catch (Exception e) {
			//e.printStackTrace();
			System.err.println("Failed during merging of partitions");
			System.exit(1);
		}

		System.out.println("Index file created.");
		return termIndexPositions;
	}

	private void writeMergeEntry(MergeEntry lastMergeEntry, 
			MergeEntry me, 
			Map<String, Long> termIndexPositions, 
			BufferedWriter bw,
			File indexFile) throws IOException {

		if (lastMergeEntry == null) {
			// first term
			termIndexPositions.put(me.term, indexFile.length());
			bw.write(me.postingsList.getSaveFormatString());


		} else if (lastMergeEntry.term.equals(me.term)) {
			// existing term
			if (lastMergeEntry.postingsList.list.getLast().docID == me.postingsList.list.getFirst().docID) {
				Iterator<PostingsEntry> peIt = me.postingsList.iterator();
				// write the first one's offsets without comma first since they belong to the previous docID
				PostingsEntry pe = peIt.next();
				for (int o : pe.offsets) bw.write(" " + o);

				while (peIt.hasNext()) {
					pe = peIt.next();
					bw.write("," + pe.docID);
					for (int o : pe.offsets) bw.write(" " + o);
				}

			} else {
				bw.write(",");
				bw.write(me.postingsList.getSaveFormatString());
			}

		} else {
			// new term
			bw.write("\n");
			bw.flush(); // NOTE: need to flush here to get the correct file position to store in the map
			termIndexPositions.put(me.term, indexFile.length());
			bw.write(me.postingsList.getSaveFormatString());
		}
	}
}
