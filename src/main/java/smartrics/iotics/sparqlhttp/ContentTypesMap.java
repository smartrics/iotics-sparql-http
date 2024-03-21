package smartrics.iotics.sparqlhttp;

import com.iotics.api.SparqlResultType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.iotics.api.SparqlResultType.*;

public class ContentTypesMap {

    private static final Map<String, SparqlResultType> resultFormat = new ConcurrentHashMap<>(8);

    private static final List<SparqlResultType> SPARQL_RESULT_TYPES = Arrays.asList(SPARQL_XML, SPARQL_JSON, SPARQL_CSV);
    private static final List<SparqlResultType> RDF_RESULT_TYPES = Arrays.asList(RDF_XML, RDF_TURTLE, RDF_NTRIPLES);

    static {
        resultFormat.put("application/sparql-results+xml", SPARQL_XML);
        resultFormat.put("application/sparql-results+json", SparqlResultType.SPARQL_JSON);
        resultFormat.put("text/csv", SparqlResultType.SPARQL_CSV);
        resultFormat.put("text/tab-separated-values", SparqlResultType.UNRECOGNIZED);

        resultFormat.put("application/rdf+xml", SparqlResultType.RDF_XML);
        resultFormat.put("text/turtle", SparqlResultType.RDF_TURTLE);
        resultFormat.put("application/x-turtle", SparqlResultType.RDF_TURTLE); // Assuming the same enum value for Turtle
        resultFormat.put("application/n-triples", SparqlResultType.RDF_NTRIPLES);
        resultFormat.put("application/n-quads", SparqlResultType.UNRECOGNIZED);
        resultFormat.put("application/ld+json", SparqlResultType.UNRECOGNIZED);
        resultFormat.put("application/rdf+json", SparqlResultType.UNRECOGNIZED);
        resultFormat.put("application/x-binary-rdf", SparqlResultType.UNRECOGNIZED);

    }

    public static boolean isSPARQLResultType(SparqlResultType in) {
        return SPARQL_RESULT_TYPES.contains(in);
    }

    public static boolean isRDFResultType(SparqlResultType in) {
        return RDF_RESULT_TYPES.contains(in);
    }

    public static SparqlResultType get(String v, SparqlResultType def) {
        return get(v).orElse(def);
    }

    public static Optional<SparqlResultType> get(String v) {
        if(v == null) {
            return Optional.empty();
        }
        return Optional.of(resultFormat.getOrDefault(v, SparqlResultType.UNRECOGNIZED));
    }

}
