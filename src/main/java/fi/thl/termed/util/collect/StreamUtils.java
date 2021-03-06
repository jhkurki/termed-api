package fi.thl.termed.util.collect;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterators.partition;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StreamUtils {

  private static final Logger log = LoggerFactory.getLogger(StreamUtils.class);

  private StreamUtils() {
  }

  public static <T, K> Stream<T> distinctByKey(Stream<T> stream, Function<T, K> keyExtractor) {
    Map<K, Boolean> visited = new ConcurrentHashMap<>();
    return stream.filter(v -> visited.putIfAbsent(keyExtractor.apply(v), Boolean.TRUE) == null);
  }

  /**
   * Attach repeatedly called {@link Runnable} to Stream until the Stream is closed. Can be used
   * e.g. to log warnings of unclosed streams.
   */
  public static <T> Stream<T> toStreamWithScheduledRepeatingAction(Stream<T> stream,
      ScheduledExecutorService scheduledExecutorService, int delay, TimeUnit timeUnit,
      Runnable action) {

    ScheduledFuture<?> scheduledFuture = scheduledExecutorService
        .scheduleWithFixedDelay(action, delay, delay, timeUnit);

    // cancel scheduledFuture if stream is properly closed on time
    return stream.onClose(() -> scheduledFuture.cancel(false));
  }

  /**
   * Set timeout to Stream, i.e. Stream is automatically closed after given time.
   */
  public static <T> Stream<T> toStreamWithTimeout(Stream<T> stream,
      ScheduledExecutorService scheduledExecutorService, int delay, TimeUnit timeUnit,
      Supplier<String> timeoutMessageSupplier) {
    ScheduledFuture<Void> closeOnTimeout = scheduledExecutorService.schedule(() -> {
      stream.close();
      log.warn("Stream closed on timeout: {}", timeoutMessageSupplier.get());
      return null;
    }, delay, timeUnit);

    // cancel closeOnTimeout if stream is properly closed on time
    return stream.onClose(() -> closeOnTimeout.cancel(false));
  }

  public static <T> List<T> toListAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.collect(toList());
    }
  }

  public static <T> ImmutableList<T> toImmutableListAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.collect(toImmutableList());
    }
  }

  public static <T> Set<T> toSetAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.collect(toSet());
    }
  }

  public static <T> Set<T> toImmutableSetAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.collect(toImmutableSet());
    }
  }

  public static <T> Optional<T> findFirstAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.findFirst();
    }
  }

  public static <T> void forEachAndClose(Stream<T> stream, Consumer<T> consumer) {
    try (Stream<T> autoClosed = stream) {
      autoClosed.forEach(consumer);
    }
  }

  public static <T> long countAndClose(Stream<T> stream) {
    try (Stream<T> autoClosed = stream) {
      return autoClosed.count();
    }
  }

  public static <T> Stream<T> toStream(Optional<T> optional) {
    return optional.map(Stream::of).orElse(Stream.empty());
  }

  public static <L, R> Stream<Map.Entry<L, R>> zip(Stream<L> l, Stream<R> r) {
    return zip(l, r, SimpleImmutableEntry::new);
  }

  public static <L, R, Z> Stream<Z> zip(Stream<L> l, Stream<R> r, BiFunction<L, R, Z> zipper) {
    return stream(spliteratorUnknownSize(new Iterator<Z>() {
      Iterator<L> leftIterator = l.iterator();
      Iterator<R> rightIterator = r.iterator();

      @Override
      public boolean hasNext() {
        return leftIterator.hasNext() && rightIterator.hasNext();
      }

      @Override
      public Z next() {
        return zipper.apply(leftIterator.next(), rightIterator.next());
      }
    }, Spliterator.ORDERED), l.isParallel() || r.isParallel());
  }

  public static <L, Z> Stream<Z> zipIndex(Stream<L> l, BiFunction<L, Integer, Z> zipper) {
    return zipIndex(l, 0, zipper);
  }

  public static <L, Z> Stream<Z> zipIndex(Stream<L> l, int offset,
      BiFunction<L, Integer, Z> zipper) {
    return zip(l, IntStream.iterate(offset, i -> i + 1).boxed(), zipper);
  }

  public static <T> Stream<T> nullToEmpty(Stream<T> stream) {
    return stream != null ? stream : Stream.empty();
  }

  public static <T, R> Stream<R> partitionedMap(Stream<T> stream, int partitionSize,
      Function<List<T>, Stream<R>> mapper) {
    Stream<List<T>> partitionedNodeStream = stream(
        spliteratorUnknownSize(partition(stream.iterator(), partitionSize), 0), false)
        .onClose(stream::close);
    return partitionedNodeStream.flatMap(mapper);
  }

}
