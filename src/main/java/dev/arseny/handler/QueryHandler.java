package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dev.arseny.RequestUtils;
import dev.arseny.model.QueryResponse;
import dev.arseny.service.IndexSearcherService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.QueryBuilder;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Named("query")
public class QueryHandler implements RequestHandler<Map<String, String>, QueryResponse> {
    private static final Logger LOG = Logger.getLogger(QueryHandler.class);

    @Inject
    protected IndexSearcherService indexSearcherService;

    @Override
    public QueryResponse handleRequest(Map<String, String> event, Context context) {
        QueryBuilder qp = new QueryBuilder(new StandardAnalyzer());

        QueryResponse queryResponse = new QueryResponse();

        try {
            Query query;
            if (event.size() == 1) {
                Entry<String, String> entry = event.entrySet().iterator().next();
                query = qp.createBooleanQuery(entry.getKey(), RequestUtils.escape(entry.getValue()));
            } else {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                event.entrySet().stream()
                        .forEach(e -> {
                            if (e.getKey() != null && e.getValue() != null && e.getValue() != "") {
                                if (e.getKey().startsWith("+"))
                                    builder.add(
                                            qp.createBooleanQuery(e.getKey().substring(1),
                                                    RequestUtils.escape(e.getValue())),
                                            Occur.MUST);
                                else
                                    builder.add(qp.createBooleanQuery(e.getKey(), RequestUtils.escape(e.getValue())),
                                            Occur.SHOULD);
                            }
                        });
                query = builder.build();
            }

            IndexSearcher searcher = indexSearcherService.getIndexSearcher(System.getenv("index"));

            TopDocs topDocs = searcher.search(query, 1000);

            for (ScoreDoc scoreDocs : topDocs.scoreDocs) {
                Document document = searcher.doc(scoreDocs.doc);

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
        } catch (IOException e) {
            LOG.error(e);

            QueryResponse response = new QueryResponse();
            response.setError(e.getMessage());
            return response;
        }
    }
}
