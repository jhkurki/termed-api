package fi.thl.termed.util.query;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;

public final class Queries {

  private Queries() {
  }

  public static <K extends Serializable, V> Query<K, V> query(
      Specification<K, V> specification) {
    return new Query<>(specification);
  }

  public static <K extends Serializable, V> Query<K, V> query(
      Specification<K, V> specification, Sort sort, int max) {
    return new Query<>(specification, ImmutableList.of(sort), max);
  }

  public static <K extends Serializable, V> Query<K, V> query(
      Iterable<Select> selects, Specification<K, V> specification, List<Sort> sort, int max) {
    return new Query<>(selects, specification, sort, max);
  }

  public static <K extends Serializable, V> Query<K, V> sqlQuery(
      SqlSpecification<K, V> specification) {
    return query(Specifications.asSql(specification));
  }

  public static <K extends Serializable, V> Query<K, V> matchAll() {
    return query(Specifications.matchAll());
  }

  public static <K extends Serializable, V> Query<K, V> matchNone() {
    return query(Specifications.matchNone());
  }

}
