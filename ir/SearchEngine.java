package ir;

import java.util.*;

public class SearchEngine {
	private Index index;

	public SearchEngine(Index index) {
		this.index = index;
	}

	public PostingsList search(Query query, int queryType, int rankingType, int structureType) {
		ArrayList<PostingsList> postingsLists = new ArrayList<PostingsList>(query.terms.size());
		for (String term : query.terms) {
			PostingsList pl = index.getPostings(term);
			if (pl != null) postingsLists.add(pl);
		}
		
		// TODO: if query is coming from relevance feedback, handle it differently

		if (postingsLists.isEmpty()) return null;

		switch (queryType) {
			case Index.INTERSECTION_QUERY: return intersectionQuery(postingsLists, query.terms);
			case Index.PHRASE_QUERY: return phraseQuery(postingsLists, query.terms);
			case Index.RANKED_QUERY:
				switch (rankingType) {
					case Index.TF_IDF: return cosineSimilarity(query, postingsLists);
					case Index.PAGERANK: return pagerank(postingsLists);
					case Index.COMBINATION: return rankedCombination(query, postingsLists);
					default: return null;
				}

			default:
				System.err.println("Invalid search type");
				return null;
		}
	}

	private PostingsList intersectionQuery(List<PostingsList> postingsLists, List<String> terms) {
		if (postingsLists.size() != terms.size()) return null;
		return postingsLists.size() == 1 ? postingsLists.get(0) : intersection(postingsLists);
	}

	private PostingsList intersection(List<PostingsList> postingsLists) {
		Collections.sort(postingsLists, new Comparator<PostingsList>() {
			@Override
			public int compare(PostingsList p1, PostingsList p2) {
				if (p1.size() < p2.size()) return -1;
				else if (p1.size() == p2.size()) return 0;
				else return 1;
			}
		});

		Iterator<PostingsList> it = postingsLists.iterator();
		PostingsList intersection = it.next();

		while (it.hasNext())
			intersection = intersection(intersection, it.next());

		return intersection;
	}

	private PostingsList intersection(PostingsList p1, PostingsList p2) {
		PostingsList intersection = new PostingsList();

		Iterator<PostingsEntry> it1 = p1.iterator();
		Iterator<PostingsEntry> it2 = p2.iterator();
		PostingsEntry pe1 = next(it1);
		PostingsEntry pe2 = next(it2);

		while (pe1 != null && pe2 != null) {
			if (pe1.docID == pe2.docID) {
				intersection.add(pe1);
				pe1 = next(it1);
				pe2 = next(it2);

			} else if (pe1.docID < pe2.docID) 
				pe1 = next(it1);
			else 
				pe2 = next(it2);
		}

		return intersection;
	}

	private PostingsList union(List<PostingsList> postingsLists) {
		PostingsList result = new PostingsList();

		Iterator<PostingsList> it = postingsLists.iterator();
		while (it.hasNext()) result = union(result, it.next());

		return result;
	}

	private PostingsList union(PostingsList p1, PostingsList p2) {
		PostingsList result = new PostingsList();
		Iterator<PostingsEntry> it1 = p1.iterator();
		Iterator<PostingsEntry> it2 = p2.iterator();
		PostingsEntry pe1 = next(it1);
		PostingsEntry pe2 = next(it2);

		while (pe1 != null && pe2 != null) {
			if (pe1.docID < pe2.docID) {
				result.add(new PostingsEntry(pe1.docID, 0.0));
				pe1 = next(it1);
			} else if (pe1.docID == pe2.docID) {
				result.add(new PostingsEntry(pe1.docID, 0.0));
				pe1 = next(it1);
				pe2 = next(it2);
			} else {
				result.add(new PostingsEntry(pe2.docID, 0.0));
				pe2 = next(it2);
			}
		}
		
		// add remainder
		if (pe1 == null) {
			while (pe2 != null) {
				result.add(new PostingsEntry(pe2.docID, 0.0));
				pe2 = next(it2);
			}
		} else {
			while (pe1 != null) {
				result.add(new PostingsEntry(pe1.docID, 0.0));
				pe1 = next(it1);
			}
		}

		return result;
	}

	private PostingsList phraseQuery(List<PostingsList> postingsLists, List<String> terms) {
		if (postingsLists.size() == 1) return postingsLists.get(0);
		if (postingsLists.size() < terms.size()) return null;

		Iterator<PostingsList> it = postingsLists.iterator();
		PostingsList p1 = it.next();
		PostingsList p2 = it.next();
		PostingsList phraseIntersection = phraseIntersection(p1, p2);

		while (it.hasNext()) 
			phraseIntersection = phraseIntersection(phraseIntersection, it.next());

		return phraseIntersection;
	}

	private PostingsList phraseIntersection(PostingsList p1, PostingsList p2) {
		PostingsList phraseIntersection = new PostingsList();
		Iterator<PostingsEntry> it1 = p1.iterator();
		Iterator<PostingsEntry> it2 = p2.iterator();
		PostingsEntry pe1 = next(it1);
		PostingsEntry pe2 = next(it2);

		while (pe1 != null && pe2 != null) {
			if (pe1.docID == pe2.docID) {
				for (int o1 : pe1.offsets) {
					for (int o2 : pe2.offsets) {
						if (o2 - o1 == 1) phraseIntersection.add(pe1.docID, o2);
						else if (o2 > o1) break;
					}
				}

				pe1 = next(it1);
				pe2 = next(it2);

			} else if (pe1.docID < pe2.docID) pe1 = next(it1);
			else pe2 = next(it2);
		}

		return phraseIntersection;
	}
	
	private PostingsList cosineSimilarity(Query query, List<PostingsList> postingsLists) {
		HashMap<Integer, Double> docScores = new HashMap<Integer, Double>();
		final int N = index.docIDs.size();
		
		for (String term : query.terms) {
			PostingsList pl = index.getPostings(term);
			if (pl == null) continue;

			final int dft = pl.size();
			final double idft = Math.log10(((double) N) / dft);
			double wtq = 1 * idft * query.weights.get(term);

			Iterator<PostingsEntry> peIt = pl.iterator();
			while (peIt.hasNext()) {
				PostingsEntry pe = peIt.next();
				final int tf = pe.offsets.size();
				final double wtd = tf * idft;

				if (!docScores.containsKey(pe.docID)) docScores.put(pe.docID, 0.0);
				double oldScore = docScores.get(pe.docID);
				double newScore = wtq * wtd + oldScore;
				docScores.put(pe.docID, newScore);
			}
		}
		
		PostingsList result = new PostingsList();
		for (int docID : docScores.keySet()) {
			// NOTE: approximation for euclidean length here
			// should be dividing by the product of the 2-norms of the query and document
			// TODO: maybe pre-calculate that somewhere?
			double cosSim = docScores.get(docID) / index.docLengths.get("" + docID); 
			PostingsEntry pe = new PostingsEntry(docID, cosSim);
			result.add(pe);
		}

		result.sortOnScore();
		return result;
	}

	private PostingsList pagerank(List<PostingsList> postingsLists) {
		PostingsList unionPL = union(postingsLists);
		Iterator<PostingsEntry> it = unionPL.iterator();
		while (it.hasNext()) {
			PostingsEntry pe = it.next();
			pe.score = index.getPagerank(pe.docID);
		}
		
		unionPL.sortOnScore();
		return unionPL;
	}

	private PostingsList rankedCombination(Query query, List<PostingsList> postingsLists) {
		PostingsList pl = cosineSimilarity(query, postingsLists);
		Iterator<PostingsEntry> it = pl.iterator();
		while (it.hasNext()) {
			PostingsEntry pe = it.next();
			double cosSim = pe.score;
			double pagerank = index.getPagerank(pe.docID);

			// TODO: fancy function here
			// TODO: maybe try 
			pe.score = 1.0 * cosSim + 100.0 * pagerank; 
		}

		pl.sortOnScore();
		return pl;
	}

	private PostingsEntry next(Iterator<PostingsEntry> it) {
		return it.hasNext() ? it.next() : null;
	}
}
