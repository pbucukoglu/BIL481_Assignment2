import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SuggestionEngine extends Java8BaseListener {

	// A nested class to store each candidate method with its distance
	class Candidate implements Comparable<Candidate> {
		String methodName;
		double distance;

		public Candidate(String methodName, double distance) {
			this.methodName = methodName;
			this.distance = distance;
		}

		// Sort the candidates by their distances and method names
		public int compareTo(Candidate c) {
			int difference = (int) (this.distance - c.distance);
			if (difference == 0 && !methodName.equals(c.methodName)) {
				return 1;
			}
			return difference;
		}

		// Check if two candidates are equal based on their method names
		@Override
		public boolean equals(Object o) {
			if (o instanceof Candidate) {
				return ((Candidate) o).methodName.equals(this.methodName);
			}
			return super.equals(o);
		}

		// Compute the hash code of a candidate based on its method name
		@Override
		public int hashCode() {
			return methodName.hashCode();
		}
	}

	private final static Logger LOGGER = Logger.getLogger(SuggestionEngine.class.getName());

	public static void main(String[] args) throws IOException {
		// Set the logging level to INFO and log a message
		LOGGER.setLevel(Level.INFO);
		LOGGER.info("Info Log");

		// Create a new suggestion engine and read input from standard input
		SuggestionEngine se = new SuggestionEngine();
		InputStream code = System.in;

		// Parse the command line arguments
		String word = args[0];
		int topK = Integer.valueOf(args[1]);

		// Find the suggestions and store them in a sorted set
		TreeSet<Candidate> suggestions = se.suggest(code, word, topK);

		// Print the suggestions to standard output
		System.out.println("Suggestions are found:");
		while (!suggestions.isEmpty()) {
			Candidate c = suggestions.pollFirst();
			System.out.println(c.methodName + ": " + c.distance);
		}
	}

	// A list to store the names of all public methods in the code
	List<String> mMethods;

	// Find all public methods in the code and return the top K suggestions
	public TreeSet<Candidate> suggest(InputStream code, String word, int topK) throws IOException {
		// Initialize the ANTLR lexer, parser, and input stream
		ANTLRInputStream input = new ANTLRInputStream(code);
		Java8Lexer lexer = new Java8Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		Java8Parser parser = new Java8Parser(tokens);

		// Build the parse tree and measure the time
		LOGGER.info("Building the parse tree...");
		long start = System.nanoTime();
		ParseTree tree = parser.compilationUnit();
		long elapsedNano = System.nanoTime() - start;
		long elapsedSec = TimeUnit.SECONDS.convert(elapsedNano, TimeUnit.NANOSECONDS);
		LOGGER.info(String.format("Built the parse tree...(took %d seconds)", elapsedSec));

		// Traverse the parse tree and collect the names of all public methods
		ParseTreeWalker walker = new ParseTreeWalker();
		mMethods = new ArrayList<>();
		LOGGER.info("Collecting the public method names...");
		walker.walk(this, tree);
		LOGGER.info("Collected the public method names...");
		LOGGER.info(mMethods.toString());

		// Find the top K suggestions based on the Levenshtein distance
		LOGGER.info("Finding the suggestions...");
		return getTopKNeighbor(word, topK);
	}

	// This method is called by the ANTLR parser when a method declaration is encountered in the input Java code.
@Override
public void enterMethodDeclaration(Java8Parser.MethodDeclarationContext ctx) {

	// Get the list of method modifiers from the parsed method declaration context
	List<Java8Parser.MethodModifierContext> methodModifiers = ctx.methodModifier();

	// Loop through each modifier to check if the method is public
	for (Java8Parser.MethodModifierContext modifier : methodModifiers) {
		if (modifier.getText().equals("public")) {
			// Add the method name to the list of public methods if it is public
			mMethods.add(ctx.methodHeader().methodDeclarator().Identifier().getText());
			break;
		}
	}
}

// This method returns a sorted set of up to K method names that are the closest neighbors of the given word
private TreeSet<Candidate> getTopKNeighbor(String word, int K) {

	// Create an empty sorted set that will contain the closest K neighbors
	TreeSet<Candidate> minHeap = new TreeSet<>();

	// Loop through each method name in the list of public methods
	for (String methodName : mMethods) {
		// Compute the Levenshtein distance between the input word and the method name
		double distance = Levenshtein.distance(word, methodName);

		// Create a new candidate with the method name and its distance from the input word
		Candidate c = new Candidate(methodName, distance);

		// If the number of neighbors found so far is less than K, add the candidate to the set
		if (minHeap.size() < K) {
			minHeap.add(c);
		} else {
			// Otherwise, check if the candidate is closer to the input word than the farthest neighbor found so far
			Candidate max = minHeap.last();
			if (c.compareTo(max) < 0) {
				// If so, remove the farthest neighbor and add the candidate to the set
				minHeap.pollLast();
				minHeap.add(c);
			}
		}
	}

	// Return the sorted set of up to K closest neighbors
	return minHeap;
}

}





