package fi.thl.termed.spesification;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import java.io.Serializable;

public class FalseSpecification<K extends Serializable, V> extends AbstractSpecification<K, V>
    implements SqlSpecification<K, V>, LuceneSpecification<K, V> {

  @Override
  public boolean accept(K key, V value) {
    return false;
  }

  @Override
  public Query luceneQuery() {
    return new BooleanQuery();
  }

  @Override
  public String sqlQueryTemplate() {
    return "1 = 0";
  }

  @Override
  public Object[] sqlQueryParameters() {
    return new Object[0];
  }

}