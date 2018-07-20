package fi.thl.termed.util.service;

import fi.thl.termed.domain.User;
import fi.thl.termed.util.collect.Identifiable;
import java.io.Serializable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteLoggingService2<K extends Serializable, V extends Identifiable<K>>
    extends ForwardingService2<K, V> {

  private Logger log;

  public WriteLoggingService2(Service2<K, V> delegate, String loggerName) {
    super(delegate);
    this.log = LoggerFactory.getLogger(loggerName);
  }

  public WriteLoggingService2(Service2<K, V> delegate, Class<?> loggerName) {
    super(delegate);
    this.log = LoggerFactory.getLogger(loggerName);
  }

  @Override
  public Stream<K> save(Stream<V> values, SaveMode mode, WriteOptions opts, User user) {
    Stream<V> valuesWithLogging = log.isInfoEnabled()
        ? values.peek(v -> log.info("save {} (user: {})", v.identifier(), user.getUsername()))
        : values;

    return super.save(valuesWithLogging, mode, opts, user);
  }

  @Override
  public K save(V value, SaveMode mode, WriteOptions opts, User user) {
    if (log.isInfoEnabled()) {
      log.info("save {} (user: {})", value.identifier(), user.getUsername());
    }

    return super.save(value, mode, opts, user);
  }

  @Override
  public void delete(Stream<K> ids, WriteOptions opts, User user) {
    Stream<K> idsWithLogging = log.isInfoEnabled()
        ? ids.peek(k -> log.info("delete {} (user: {})", k, user.getUsername()))
        : ids;

    super.delete(idsWithLogging, opts, user);
  }

  @Override
  public void delete(K key, WriteOptions opts, User user) {
    if (log.isInfoEnabled()) {
      log.info("delete {} (user: {})", key, user.getUsername());
    }

    super.delete(key, opts, user);
  }

}
