package fi.thl.termed.util.dao;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.thl.termed.util.specification.Specification;

public interface SystemDao<K extends Serializable, V> {

  void insert(Map<K, V> map);

  void insert(K key, V value);

  void update(Map<K, V> map);

  void update(K key, V value);

  void delete(List<K> keys);

  void delete(K key);

  Map<K, V> getMap();

  Map<K, V> getMap(Specification<K, V> specification);

  Map<K, V> getMap(List<K> keys);

  List<K> getKeys();

  List<K> getKeys(Specification<K, V> specification);

  List<V> getValues();

  List<V> getValues(Specification<K, V> specification);

  List<V> getValues(List<K> keys);

  boolean exists(K key);

  Optional<V> get(K key);

}