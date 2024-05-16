package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dev.arseny.RequestUtils;
import dev.arseny.model.QueryResponse;
import dev.arseny.service.IndexSearcherService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.QueryBuilder;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Named("query")
public class QueryHandler implements RequestHandler<Map<String, Object>, QueryResponse> {
    private static final Logger LOG = Logger.getLogger(QueryHandler.class);

    @Inject
    protected IndexSearcherService indexSearcherService;

    @Override
    public QueryResponse handleRequest(Map<String, Object> event, Context context) {
        QueryBuilder qp = new QueryBuilder(new StandardAnalyzer());

        QueryResponse queryResponse = new QueryResponse();

        try {
            Query query;
            if (event.size() == 1) {
                Entry<String, Object> entry = event.entrySet().iterator().next();
                query = qp.createBooleanQuery(entry.getKey(), RequestUtils.escape(entry.getValue().toString()));
            } else {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                event.entrySet().stream()
                        .forEach(e -> {
                            if (e.getKey() != null && e.getValue() != null && e.getValue() != "") {
                                if (e.getKey().startsWith("+")) {
                                    if (e.getKey().startsWith("vector"))
                                        builder.add(
                                                new KnnFloatVectorQuery(e.getKey(),
                                                        (float[]) e.getValue(), 1000),
                                                Occur.MUST);
                                    else if (e.getKey().startsWith("maploc"))
                                        builder.add(
                                                LatLonPoint.newDistanceQuery(e.getKey(),
                                                        ((float[]) e.getValue())[0], ((float[]) e.getValue())[1],
                                                        ((float[]) e.getValue())[2]),
                                                Occur.MUST);
                                    else
                                        builder.add(
                                                qp.createBooleanQuery(e.getKey().substring(1),
                                                        RequestUtils.escape(e.getValue().toString())),
                                                Occur.MUST);
                                } else {

                                    if (e.getKey().startsWith("vector"))
                                        builder.add(
                                                new KnnFloatVectorQuery(e.getKey(),
                                                        (float[]) e.getValue(), 1000),
                                                Occur.SHOULD);
                                    else if (e.getKey().startsWith("maploc"))
                                        builder.add(
                                                LatLonPoint.newDistanceQuery(e.getKey(),
                                                        ((float[]) e.getValue())[0], ((float[]) e.getValue())[1],
                                                        ((float[]) e.getValue())[2]),
                                                Occur.SHOULD);
                                    else
                                        builder.add(
                                                qp.createBooleanQuery(e.getKey(),
                                                        RequestUtils.escape(e.getValue().toString())),
                                                Occur.SHOULD);
                                }
                            }
                        });
                query = builder.build();
            }

            IndexSearcher searcher = indexSearcherService.getIndexSearcher(System.getenv("index"));
            StoredFields storedFields = searcher.storedFields();

            TopDocs topDocs = searcher.search(query, 1000);

            for (ScoreDoc scoreDocs : topDocs.scoreDocs) {
                Document document = storedFields.document(scoreDocs.doc);

                Map<String, String> result = new HashMap<>();

                for (IndexableField field : document.getFields()) {
                    result.put(field.name(), field.stringValue());

                }

                result.put("__confidence", Float.toString(scoreDocs.score));

                queryResponse.getDocuments().add(result);
            }

            queryResponse.setTotalDocuments(
                    (topDocs.totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO ? "≥" : "")
                            + topDocs.totalHits.value);

            return queryResponse;
        } catch (

        IOException e) {
            LOG.error(e);

            QueryResponse response = new QueryResponse();
            response.setError(e.getMessage());
            return response;
        }
    }
}
