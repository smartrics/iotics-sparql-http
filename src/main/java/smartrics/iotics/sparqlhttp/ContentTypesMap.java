package smartrics.iotics.sparqlhttp;

import com.iotics.api.SparqlResultType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ContentTypesMap {

    private static final Map<String, SparqlResultType> resultFormat = new ConcurrentHashMap<>(8);

    static {
        resultFormat.put("application/sparql-results+xml", SparqlResultType.SPARQL_XML);
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

    public static Optional<SparqlResultType> get(String v) {
        if(v == null) {
            return Optional.empty();
        }
        return Optional.of(resultFormat.getOrDefault(v, SparqlResultType.UNRECOGNIZED));
    }

}
