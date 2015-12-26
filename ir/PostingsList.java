package ir;

import java.util.LinkedList;
import java.io.Serializable;
import java.util.*;

/**
 *   A list of postings for a given word.
 */
public class PostingsList implements Serializable {

	public LinkedList<PostingsEntry> list = new LinkedList<PostingsEntry>();

	public int size() {
		return list.size();
	}

	public PostingsEntry get( int i ) {
		return list.get( i );
	}

	public Iterator<PostingsEntry> iterator() {
		return list.iterator();
	}

	public void add(int docID, int offset) {
		if (list.isEmpty() || list.getLast().docID != docID) {
			PostingsEntry pe = new PostingsEntry(docID);
			pe.offsets.add(offset);
			list.add(pe);
		
		} else list.getLast().offsets.add(offset);
	}

	public void add(PostingsEntry pe) {
		list.add(pe);
	}
	
	// no leading and trailing spaces are returned
	public String getSaveFormatString() {
		StringBuilder sb = new StringBuilder();

		for (PostingsEntry pe : list) {
			sb.append(pe.docID + " ");
			for (int o : pe.offsets) sb.append(o + " ");
			sb.setCharAt(sb.length() - 1, ','); // replace last space with a comma
		}

		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public void sortOnDocID() {
		Collections.sort(list, new Comparator<PostingsEntry>() {
			@Override
			public int compare(PostingsEntry p1, PostingsEntry p2) {
				return p1.docID - p2.docID;
			}
		});
	}

	public void sortOnScore() {
		Collections.sort(list);
	}
}
