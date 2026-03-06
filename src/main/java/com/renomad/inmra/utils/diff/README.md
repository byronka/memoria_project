This library is available in multiple languages. Regardless of the
language used, the interface for using it is the same. This page
describes the API for the public functions.  For further examples, see
the relevant test harness.

### Initialization

The first step is to create a new `diff_match_patch` object. This
object contains various properties which set the behaviour of the
algorithms, as well as the following methods/functions:

### diff_main(text1, text2) → diffs

An array of differences is computed which describe the transformation
of text1 into text2. Each difference is an array (JavaScript, Lua) or
tuple (Python) or Diff object (C++, C#, Objective C, Java). The first
element specifies if it is an insertion (1), a deletion (-1) or an
equality (0). The second element specifies the affected text.

```diff_main("Good dog", "Bad dog") → [(-1, "Goo"), (1, "Ba"), (0, "d dog")]```

Despite the large number of optimisations used in this function, diff
can take a while to compute. The `diff_match_patch.Diff_Timeout`
property is available to set how many seconds any diff's exploration
phase may take. The default value is 1.0. A value of 0 disables the
timeout and lets diff run until completion. Should diff timeout, the
return value will still be a valid difference, though probably
non-optimal.

### diff_cleanupSemantic(diffs) → null

A diff of two unrelated texts can be filled with coincidental matches.
For example, the diff of "mouse" and "sofas" is `[(-1, "m"), (1, "s"),
(0, "o"), (-1, "u"), (1, "fa"), (0, "s"), (-1, "e")]`. While this is
the optimum diff, it is difficult for humans to understand. Semantic
cleanup rewrites the diff, expanding it into a more intelligible
format. The above example would become: `[(-1, "mouse"), (1,
"sofas")]`. If a diff is to be human-readable, it should be passed to
`diff_cleanupSemantic`.

### diff_cleanupEfficiency(diffs) → null

This function is similar to `diff_cleanupSemantic`, except that
instead of optimising a diff to be human-readable, it optimises the
diff to be efficient for machine processing. The results of both
cleanup types are often the same.

The efficiency cleanup is based on the observation that a diff made up
of large numbers of small diffs edits may take longer to process (in
downstream applications) or take more capacity to store or transmit
than a smaller number of larger diffs. The
`diff_match_patch.Diff_EditCost` property sets what the cost of
handling a new edit is in terms of handling extra characters in an
existing edit. The default value is 4, which means if expanding the
length of a diff by three characters can eliminate one edit, then that
optimisation will reduce the total costs.

### diff_levenshtein(diffs) → int

Given a diff, measure its Levenshtein distance in terms of the number
of inserted, deleted or substituted characters. The minimum distance
is 0 which means equality, the maximum distance is the length of the
longer string.

### diff_prettyHtml(diffs) → html

Takes a diff array and returns a pretty HTML sequence. This function
is mainly intended as an example from which to write ones own display
functions.

### match_main(text, pattern, loc) → location

Given a text to search, a pattern to search for and an expected
location in the text near which to find the pattern, return the
location which matches closest. The function will search for the best
match based on both the number of character errors between the pattern
and the potential match, as well as the distance between the expected
location and the potential match.

The following example is a classic dilemma. There are two potential
matches, one is close to the expected location but contains a one
character error, the other is far from the expected location but is
exactly the pattern sought after:
`match_main("abc12345678901234567890abbc", "abc", 26)` Which result is
returned (0 or 24) is determined by the
`diff_match_patch.Match_Distance` property. An exact letter match
which is 'distance' characters away from the fuzzy location would
score as a complete mismatch. For example, a distance of '0' requires
the match be at the exact location specified, whereas a threshold of
'1000' would require a perfect match to be within 800 characters of
the expected location to be found using a 0.8 threshold (see below).
The larger Match_Distance is, the slower match_main() may take to
compute. This variable defaults to 1000.

Another property is `diff_match_patch.Match_Threshold` which
determines the cut-off value for a valid match. If Match_Threshold is
closer to 0, the requirements for accuracy increase. If
Match_Threshold is closer to 1 then it is more likely that a match
will be found. The larger Match_Threshold is, the slower match_main()
may take to compute. This variable defaults to 0.5. If no match is
found, the function returns -1.

### patch_make(text1, text2) → patches

### patch_make(diffs) → patches

### patch_make(text1, diffs) → patches

Given two texts, or an already computed list of differences, return an
array of patch objects. The third form (text1, diffs) is preferred,
use it if you happen to have that data available, otherwise this
function will compute the missing pieces.

### patch_toText(patches) → text

Reduces an array of patch objects to a block of text which looks
extremely similar to the standard GNU diff/patch format. This text may
be stored or transmitted.

### patch_fromText(text) → patches

Parses a block of text (which was presumably created by the
patch_toText function) and returns an array of patch objects.

### patch_apply(patches, text1) → [text2, results]

Applies a list of patches to text1. The first element of the return
value is the newly patched text. The second element is an array of
true/false values indicating which of the patches were successfully
applied. [Note that this second element is not too useful since large
patches may get broken up internally, resulting in a longer results
list than the input with no way to figure out which patch succeeded or
failed. A more informative API is in development.]

The previously mentioned Match_Distance and Match_Threshold properties
are used to evaluate patch application on text which does not match
exactly. In addition, the `diff_match_patch.Patch_DeleteThreshold`
property determines how closely the text within a major (~64
character) delete needs to match the expected text. If
Patch_DeleteThreshold is closer to 0, then the deleted text must match
the expected text more closely. If Patch_DeleteThreshold is closer to
1, then the deleted text may contain anything. In most use cases
Patch_DeleteThreshold should just be set to the same value as
Match_Threshold.



Each language port of Diff Match Patch uses the [same API](API).
These are the language-specific notes regarding **Java**.

Before starting, go to the `java` directory, and create an empty
sub-directory called `classes`.

### Hello World

Here's a minimal example of a diff in Java:

```java
import java.util.LinkedList;
import name.fraser.neil.plaintext.diff_match_patch;

public class hello {
  public static void main(String args[]) {
    diff_match_patch dmp = new diff_match_patch();
    LinkedList<diff_match_patch.Diff> diff = dmp.diff_main("Hello World.", "Goodbye World.");
    // Result: [(-1, "Hell"), (1, "G"), (0, "o"), (1, "odbye"), (0, " World.")]
    dmp.diff_cleanupSemantic(diff);
    // Result: [(-1, "Hello"), (1, "Goodbye"), (0, " World.")]
    System.out.println(diff);
  }
}
```

Go to the `java/src` directory and save the above program as
`hello.java`.  Then go to the `java` directory and execute these two
commands:

```
javac -d classes src/name/fraser/neil/plaintext/diff_match_patch.java src/hello.java
java -classpath classes hello
```

### Tests

Unit tests can be performed from the `java` directory by executing two commands:
```
javac -d classes src/name/fraser/neil/plaintext/diff_match_patch.java tests/name/fraser/neil/plaintext/diff_match_patch_test.java
java -classpath classes name/fraser/neil/plaintext/diff_match_patch_test
```
All tests should pass.

Speed test for diff can be performed from the `java` directory by executing two commands:
```
javac -d classes src/name/fraser/neil/plaintext/diff_match_patch.java tests/name/fraser/neil/plaintext/Speedtest.java
java -classpath classes name/fraser/neil/plaintext/Speedtest
```


The difference algorithm in this library operates in character mode.
This produces the most detailed diff possible. However, for some
applications one may wish to take a coarser approach.

Note that the following applies to all language ports **except** Lua.
Lua currently has no capability to handle Unicode. The examples below
are in JavaScript, but the procedure is identical in other languages.

### Line Mode
Computing a line-mode diff is really easy.

```javascript
function diff_lineMode(text1, text2) {
  var dmp = new diff_match_patch();
  var a = dmp.diff_linesToChars_(text1, text2);
  var lineText1 = a.chars1;
  var lineText2 = a.chars2;
  var lineArray = a.lineArray;
  var diffs = dmp.diff_main(lineText1, lineText2, false);
  dmp.diff_charsToLines_(diffs, lineArray);
  return diffs;
}
```

What's happening here is that the texts are rebuilt by the
`diff_linesToChars` function so that each line is represented by a
single Unicode character. Then these Unicode characters are diffed.
Finally the diff is rebuilt by the `diff_charsToLines` function to
replace the Unicode characters with the original lines.

Adding `dmp.diff_cleanupSemantic(diffs);` before returning the diffs
is probably a good idea so that the diff is more human-readable.

### Word Mode

Computing a word-mode diff is exactly the same as the line-mode diff,
except you will have to make a copy of `diff_linesToChars` and call it
`diff_linesToWords`. Look for the line that identifies the next line
boundary:

`lineEnd = text.indexOf('\n', lineStart);`

Change this line to look for any runs of whitespace, not just end of lines.

Despite the name, the `diff_charsToLines` function will continue to
work fine in word-mode.


The patch algorithms generate (using `patch_toText()`) and parse
(using `patch_fromText()`) a textual diff format which looks a lot
like the [Unidiff
format](https://en.wikipedia.org/wiki/Diff_utility#Unified_format).
However the formats have three differences. These differences are only
important if one plans to parse this text.

### Example

Text 1: ``` There are two things that are more difficult than making an after-dinner speech: climbing a wall which is leaning toward you and kissing a girl who is leaning away from you.```

Text 2: ``` `Churchill` talked about climbing a wall which is leaning toward you and kissing a woman who is leaning away from you.```

This example contains two relatively well-separated edits. Using this library will result in the following two patches:

```@@ -1,84 +1,28 @@ -There are two things that are more difficult than making an after-dinner speech: +%60Churchill%60 talked about cli @@ -136,12 +80,13 @@ g a -girl +woman who```

### 1. Character Based

The Diff Match Patch library is character-based (unlike GNU Diff and
Patch which are line-based).

### 2. Encoded Characters

Special characters are encoded using %xx notation. The set of
characters which are encoded matches JavaScript's `encodeURI()`
function, with the exception of spaces which are not encoded.

### 3. Rolling context

GNU Diff and Patch assume that patches may be applied selectively or
in arbitrary order. The Diff Match Patch library assumes patches will
be applied serially from start to finish. The result is that each
patch contains context information which assume that the previous
patches have been applied.

This design decision stems from a fundamental limitation of the
matching algorithm. Generally, the Bitap match can only work with text
that is 32 characters long. Thus patches (context plus deletions,
excluding insertions) can only be 32 characters long. Long patches
(such as the one above) will be automatically split up into 32
character chunks with overlapping contexts during application:

`@@ -1,32 +1,4 @@ -There are two things that ar e mo @@ -29,32 +1,4 @@ -e more difficult than making an @@ -57,28 +1,28 @@ - an after-dinner speech: +%60Churchill%60 talked about cli @@ -136,12 +80,13 @@ g a -girl +woman who`

In these cases each fragmented patch overlaps neatly with the previous patch.


Matching operations (found as a stand-alone match or within a patch)
comes in two forms: exact match and fuzzy match.  This library handles
exact matches for any type of content, whether plain text or DOM tree
or binary content.  However, this library only supports fuzzy matches
for plain text.

Attempting to feed HTML, XML or some other structured content through
a fuzzy match or patch may result in problems. Consider the case where
a series of patches are applied to HTML content on a best-effort
basis. One could be left with a `<B>` tag that starts but doesn't end,
text falling between a `</TD>` and a `<TD>`, or a syntactically
invalid tag missing a bracket.

The correct solution is to use a tree-based diff, match and patch.
These employ totally different algorithms. I'm afraid I can't help you
there.

### Doing it anyway

However, depending on the task, there are sometimes some interesting
ways to use text-based algorithms on structured content.

One method is to strip the tags from the HTML using a simple regex or
node-walker. Then diff the HTML content against the text content.
Don't perform any diff cleanups. This diff enables one to map
character positions from one version to the other (see the
`diff_xIndex` function). After this, one can apply all the patches one
wants against the plain text, then safely map the changes back to the
HTML. The catch with this technique is that although text may be
freely edited, HTML tags are immutable.

Another method is to walk the HTML and replace every opening and
closing tag with a Unicode character. Check the Unicode spec for a
range that is not in use. During the process, create a hash table of
Unicode characters to the original tags. The result is a block of text
which can be patched without fear of inserting text inside a tag or
breaking the syntax of a tag. One just has to be careful when
reconverting the content back to HTML that no closing tags are lost.
