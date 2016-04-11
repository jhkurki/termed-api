package fi.thl.termed.repository.lucene;

import com.google.common.base.Converter;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import fi.thl.termed.domain.Query;
import fi.thl.termed.repository.Index;
import fi.thl.termed.util.ListUtils;
import fi.thl.termed.util.ProgressReporter;
import fi.thl.termed.util.ToStringFunction;

import static com.google.common.base.Functions.compose;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static fi.thl.termed.repository.lucene.LuceneConstants.DEFAULT_SEARCH_FIELD;
import static fi.thl.termed.repository.lucene.LuceneConstants.DOCUMENT_ID;
import static java.util.Arrays.asList;
import static org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
import static org.apache.lucene.util.Version.LUCENE_47;

public class LuceneIndex<V, K extends Serializable> implements Index<V, K> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Converter<V, Document> documentConverter;
  private Function<K, String> keyToString;

  private Directory directory;
  private Analyzer analyzer;

  private IndexWriter writer;
  private SearcherManager searcherManager;

  private TimerTask refreshTask;
  private TimerTask commitTask;

  private ExecutorService indexingExecutor;

  public LuceneIndex(String directoryPath,
                     Converter<V, Document> documentConverter) {

    this.documentConverter = documentConverter;
    this.keyToString = new ToStringFunction<K>();

    this.analyzer = new LowerCaseWhitespaceAnalyzer(LUCENE_47);

    try {
      this.directory = isNullOrEmpty(directoryPath) ? new RAMDirectory()
                                                    : FSDirectory.open(new File(directoryPath));
      this.writer = new IndexWriter(
          directory, new IndexWriterConfig(LUCENE_47, analyzer).setOpenMode(CREATE_OR_APPEND));
      this.searcherManager = new SearcherManager(writer, true, new SearcherFactory());
    } catch (IOException e) {
      throw new LuceneException(e);
    }

    this.refreshTask = new TimerTask() {
      public void run() {
        refresh();
      }
    };
    this.commitTask = new TimerTask() {
      public void run() {
        commit();
      }
    };

    new Timer().schedule(refreshTask, 0, 1000);
    new Timer().schedule(commitTask, 0, 10000);

    this.indexingExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void reindex(List<K> ids, Function<List<K>, List<V>> objectLoadingFunction) {
    indexingExecutor.submit(new IndexingTask(ids, objectLoadingFunction));
  }

  @Override
  public void reindex(K id, V object) {
    Term docIdTerm = new Term(DOCUMENT_ID, keyToString.apply(id));

    Document doc = documentConverter.convert(object);
    doc.add(new StringField(docIdTerm.field(), docIdTerm.text(), Field.Store.NO));

    try {
      writer.updateDocument(docIdTerm, doc);
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  @Override
  public int indexSize() {
    try {
      IndexSearcher searcher = searcherManager.acquire();
      try {
        TotalHitCountCollector hitCountCollector = new TotalHitCountCollector();
        searcher.search(new MatchAllDocsQuery(), hitCountCollector);
        return hitCountCollector.getTotalHits();
      } finally {
        searcherManager.release(searcher);
      }
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  @Override
  public List<V> query(Query query) {
    try {
      log.debug("query: {}", query);
      IndexSearcher searcher = searcherManager.acquire();
      try {
        return query(query, searcher);
      } finally {
        searcherManager.release(searcher);
      }
    } catch (ParseException e) {
      log.error("{}", e.getMessage());
      return Collections.emptyList();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  private List<V> query(Query query, IndexSearcher searcher) throws IOException, ParseException {
    String queryString = query.getQuery().isEmpty() ? "*:*" : query.getQuery();
    QueryParser parser = new QueryParser(LUCENE_47, DEFAULT_SEARCH_FIELD, analyzer);
    ScoreDoc[] hits = searcher
        .search(parser.parse(queryString), query.getMax(), sort(query.getOrderBy())).scoreDocs;
    // wrap into new array list to fully run transform before returning the list
    return newArrayList(transform(asList(hits), compose(documentConverter.reverse(),
                                                        new ScoreDocLoader(searcher))));
  }

  private Sort sort(List<String> orderBy) {
    if (ListUtils.isNullOrEmpty(orderBy)) {
      return new Sort(SortField.FIELD_SCORE);
    }

    List<SortField> sortFields = Lists.newArrayList();
    for (String field : orderBy) {
      sortFields.add(new SortField(field, SortField.Type.STRING));
    }

    return new Sort(sortFields.toArray(new SortField[sortFields.size()]));
  }

  @Override
  public void deleteFromIndex(K key) {
    try {
      writer.deleteDocuments(new Term(DOCUMENT_ID, keyToString.apply(key)));
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  protected void refresh() {
    try {
      searcherManager.maybeRefresh();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  protected void refreshBlocking() {
    try {
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  protected void commit() {
    try {
      writer.commit();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  @PreDestroy
  protected void close() {
    log.debug("Closing {}", getClass().getSimpleName());

    try {
      indexingExecutor.shutdown();
      refreshTask.cancel();
      searcherManager.close();
      commitTask.cancel();
      writer.close();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  private class IndexingTask implements Runnable {

    private List<K> ids;
    private Function<List<K>, List<V>> objectLoadingFunction;

    public IndexingTask(List<K> ids, Function<List<K>, List<V>> objectLoadingFunction) {
      this.ids = ids;
      this.objectLoadingFunction = objectLoadingFunction;
    }

    @Override
    public void run() {
      log.info("Indexing");

      ProgressReporter reporter = new ProgressReporter(log, "Indexed", 1000, ids.size());

      for (List<K> idBatch : Lists.partition(ids, 100)) {
        List<V> values = objectLoadingFunction.apply(idBatch);
        Preconditions.checkState(idBatch.size() == values.size());
        for (int i = 0; i < idBatch.size(); i++) {
          reindex(idBatch.get(i), values.get(i));
          reporter.tick();
        }
      }

      reporter.report();
    }
  }

  private class ScoreDocLoader implements Function<ScoreDoc, Document> {

    private IndexSearcher searcher;

    public ScoreDocLoader(IndexSearcher searcher) {
      this.searcher = searcher;
    }

    @Override
    public Document apply(ScoreDoc scoreDoc) {
      try {
        return searcher.doc(scoreDoc.doc);
      } catch (IOException e) {
        throw new LuceneException(e);
      }
    }
  }

}