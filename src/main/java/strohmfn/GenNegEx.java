/***************************************************************************************
 * Author: Junebae Kye, Supervisor: Imre Solti 
 * Date: 06/30/2010
 * 
 * Wendy Chapman's NegEx algorithm in Java.
 *
 * Sentence boundaries  serve as WINDOW for negation (suggested by Wendy Chapman)
 *
 *
 * NOTES: 
 * If the negation scope exists in a sentence, it will print the negation scope such as  1 - 1, 0 - 24.
 * If the negation does not exist in a sentence, it will print -1
 * If a pre-UMLS phrase is used as a post-UMLS phrase, for example, pain and fever denied, it will print the negation scope of, in this case, 0 - 2, for an option of yes or print -2 for an option of no
 *
 ****************************************************************************************/

package strohmfn;

import java.util.*;

public class GenNegEx {
	private List<String> pseNegPhrases; // list of pseudo-negation phrases
	private List<String> negPhrases; // list of negation phrases
	private List<String> postNegPhrases; // list of post-negation pharses
	private List<String> conjunctions; // list of conjunctions
	private boolean value; // boolean for an option of yes or no

	// post: constructs a GenNegEx object
	// creates a list of negation phrases, pseudo-negation phrases, post-negation phrases, and conjunction
	public GenNegEx(boolean value) {
		pseNegPhrases = new LinkedList<String>();
		negPhrases = new LinkedList<String>();
		postNegPhrases = new LinkedList<String>();
		conjunctions = new LinkedList<String>();
		processPhrases(pseNegPhrases, negPhrases, postNegPhrases, conjunctions);
		sorts(pseNegPhrases);
		sorts(negPhrases);
		sorts(postNegPhrases);
		sorts(conjunctions);
		this.value = value;
	}

	// post: sorts a list in descending order
	private void sorts(List<String> list) {
		Collections.sort(list);
		Collections.reverse(list);
	}

	// post: returns a negation scope of an input sentence
	public String negScope(String line) {
		String[] s = line.split("\\s+");
		return helper(s, 0);
	}

	// post: processes data and returns negation scope
	// returns -1 if no negation phrase is found
	private String helper(String[] s, int index) {
		if (index < s.length)
			for (int i = index; i < s.length; i++) {
				int indexII = contains(s, pseNegPhrases, i, 0);
				if (indexII != -1)
					return helper(s, indexII);
				else {
					int indexIII = contains(s, negPhrases, i, 0);
					if (indexIII != -1) {
						int indexIV = -1;
						for (int j = indexIII; j < s.length; j++) {
							indexIV = contains(s, conjunctions, j, 1);
							if (indexIV != -1)
								break;
						}
						if (indexIV != -1)
							return indexIII + " - " + (indexIV - 1);
						else if (indexIII > s.length - 1)
							if (value)
								return "0 - " + (indexIII - 2);
							else
								return "-2";
						else
							return indexIII + " - " + (s.length - 1);
					} else {
						int indexV = contains(s, postNegPhrases, i, 1);
						if (indexV != -1)
							return "0 - " + indexV;
					}
				}
			}
		return "-1";
	}

	// post: returns index of negation phrase if any negation phrase is found in a sentence
	// returns -1 if no negation phrase is found
	private int contains(String[] s, List<String> list, int index, int type) {
		int counts = 0;
		for (String token : list) {
			String[] element = token.split("\\s+");
			if (element.length == 1) {
				if (s[index].equals(element[0]))
					return index + 1;
			} else if (s.length - index >= element.length) {
				String firstWord = s[index];
				if (firstWord.equals(element[0])) {
					counts++;
					for (int i = 1; i < element.length; i++) {
						if (s[index + i].equals(element[i]))
							counts++;
						else {
							counts = 0;
							break;
						}
						if (counts == element.length)
							if (type == 0)
								return index + i + 1;
							else
								return index;
					}
				}
			}
		}
		return -1;
	}

	// post: saves pseudo negation phrases, negation phrases, conjunctions into the database
	private void processPhrases(List<String> pseNegPhrases, List<String> negPhrases, List<String> postNegPhrases, List<String> conjunctions) {
		negPhrases.add("aint");
		negPhrases.add("cannot");
		negPhrases.add("cant");
		negPhrases.add("darent");
		negPhrases.add("denied");
		negPhrases.add("denies");
		negPhrases.add("didnt");
		negPhrases.add("doesnt");
		negPhrases.add("dont");
		negPhrases.add("hadnt");
		negPhrases.add("hasnt");
		negPhrases.add("havent");
		negPhrases.add("havnt");
		negPhrases.add("isnt");
		negPhrases.add("lack");
		negPhrases.add("lacking");
		negPhrases.add("lacks");
		negPhrases.add("mightnt");
		negPhrases.add("mustnt");
		negPhrases.add("neednt");
		negPhrases.add("neither");
		negPhrases.add("never");
		negPhrases.add("no");
		negPhrases.add("nobody");
		negPhrases.add("none");
		negPhrases.add("noone");
		negPhrases.add("nor");
		negPhrases.add("not");
		negPhrases.add("nothing");
		negPhrases.add("nowhere");
		negPhrases.add("n´t");
		negPhrases.add("n't");
		negPhrases.add("n`t");
		negPhrases.add("oughtnt");
		negPhrases.add("shant");
		negPhrases.add("shouldnt");
		negPhrases.add("wasnt");
		negPhrases.add("without");
		negPhrases.add("wouldnt");

		conjunctions.add(".");
		conjunctions.add("?");
		conjunctions.add("!");
		conjunctions.add("but");
		conjunctions.add("however");
		conjunctions.add("nevertheless");
		conjunctions.add("yet");
		conjunctions.add("though");
		conjunctions.add("although");
		conjunctions.add("still");
		conjunctions.add("aside from");
		conjunctions.add("except");
		conjunctions.add("apart from");
		conjunctions.add("secondary to");
		conjunctions.add("as the cause of");
		conjunctions.add("as the source of");
		conjunctions.add("as the reason of");
		conjunctions.add("as the etiology of");
		conjunctions.add("as the origin of");
		conjunctions.add("as the cause for");
		conjunctions.add("as the source for");
		conjunctions.add("as the reason for");
		conjunctions.add("as the etiology for");
		conjunctions.add("as the origin for");
		conjunctions.add("as the secondary cause of");
		conjunctions.add("as the secondary source of");
		conjunctions.add("as the secondary reason of");
		conjunctions.add("as the secondary etiology of");
		conjunctions.add("as the secondary origin of");
		conjunctions.add("as the secondary cause for");
		conjunctions.add("as the secondary source for");
		conjunctions.add("as the secondary reason for");
		conjunctions.add("as the secondary etiology for");
		conjunctions.add("as the secondary origin for");
		conjunctions.add("as a cause of");
		conjunctions.add("as a source of");
		conjunctions.add("as a reason of");
		conjunctions.add("as a etiology of");
		conjunctions.add("as a cause for");
		conjunctions.add("as a source for");
		conjunctions.add("as a reason for");
		conjunctions.add("as a etiology for");
		conjunctions.add("as a secondary cause of");
		conjunctions.add("as a secondary source of");
		conjunctions.add("as a secondary reason of");
		conjunctions.add("as a secondary etiology of");
		conjunctions.add("as a secondary origin of");
		conjunctions.add("as a secondary cause for");
		conjunctions.add("as a secondary source for");
		conjunctions.add("as a secondary reason for");
		conjunctions.add("as a secondary etiology for");
		conjunctions.add("as a secondary origin for");
		conjunctions.add("cause of");
		conjunctions.add("cause for");
		conjunctions.add("causes of");
		conjunctions.add("causes for");
		conjunctions.add("source of");
		conjunctions.add("source for");
		conjunctions.add("sources of");
		conjunctions.add("sources for");
		conjunctions.add("reason of");
		conjunctions.add("reason for");
		conjunctions.add("reasons of");
		conjunctions.add("reasons for");
		conjunctions.add("etiology of");
		conjunctions.add("etiology for");
		conjunctions.add("trigger event for");
		conjunctions.add("origin of");
		conjunctions.add("origin for");
		conjunctions.add("origins of");
		conjunctions.add("origins for");
		conjunctions.add("other possibilities of");
	}
}
