package fi.thl.termed.spesification.common;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import fi.thl.termed.spesification.LuceneSpecification;
import fi.thl.termed.spesification.Specification;
import fi.thl.termed.spesification.SqlSpecification;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.collect.Iterables.all;

public class DisjunctionSpecification<K extends Serializable, V> extends AbstractSpecification<K, V>
    implements SqlSpecification<K, V>, LuceneSpecification<K, V> {

  private List<Specification<K, V>> specifications;

  public DisjunctionSpecification(List<Specification<K, V>> specifications) {
    this.specifications = checkNotNull(specifications);
  }

  public List<Specification<K, V>> getSpecifications() {
    return specifications;
  }

  @Override
  public boolean accept(K key, V value) {
    for (Specification<K, V> specification : specifications) {
      if (specification.accept(key, value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Query luceneQuery() {
    checkState(all(specifications, instanceOf(LuceneSpecification.class)));

    BooleanQuery query = new BooleanQuery();

    for (Specification<K, V> spec : specifications) {
      query.add(((LuceneSpecification<K, V>) spec).luceneQuery(), BooleanClause.Occur.SHOULD);
    }

    return query;
  }

  @Override
  public String sqlQueryTemplate() {
    checkState(all(specifications, instanceOf(SqlSpecification.class)));

    List<String> query = Lists.newArrayList();

    for (Specification<K, V> spec : specifications) {
      query.add(((SqlSpecification<K, V>) spec).sqlQueryTemplate());
    }

    return "(" + Joiner.on(" OR ").join(query) + ")";
  }

  @Override
  public Object[] sqlQueryParameters() {
    checkState(all(specifications, instanceOf(SqlSpecification.class)));

    List<Object> queryParameters = Lists.newArrayList();

    for (Specification<K, V> spec : specifications) {
      queryParameters.addAll(Arrays.asList(((SqlSpecification<K, V>) spec).sqlQueryParameters()));
    }

    return queryParameters.toArray(new Object[queryParameters.size()]);
  }

}