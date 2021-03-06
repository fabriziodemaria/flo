package io.rouz.flo.context;

import io.rouz.flo.Fn;
import io.rouz.flo.Task;
import io.rouz.flo.TaskContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link TaskContext} that evaluates tasks immediately and memoizes results in memory.
 *
 * <p>Memoized results are tied to the instance the evaluated the values.
 *
 * <p>This context is not thread safe.
 */
public class InMemImmediateContext implements TaskContext {

  private InMemImmediateContext() {
  }

  public static TaskContext create() {
    return new InMemImmediateContext();
  }

  @Override
  public <T> Value<T> evaluateInternal(Task<T> task, TaskContext context) {
    return TaskContext.super.evaluateInternal(task, context);
  }

  @Override
  public <T> Value<T> value(Fn<T> value) {
    final Promise<T> promise = promise();

    try {
      promise.set(value.get());
    } catch (Throwable t) {
      promise.fail(t);
    }

    return promise.value();
  }

  @Override
  public <T> Promise<T> promise() {
    return new ValuePromise<>();
  }

  private final class DirectValue<T> implements Value<T> {

    private final Semaphore setLatch;
    private final List<Consumer<T>> valueConsumers = new ArrayList<>();
    private final List<Consumer<Throwable>> failureConsumers = new ArrayList<>();
    private final AtomicReference<Consumer<Consumer<T>>> valueReceiver;
    private final AtomicReference<Consumer<Consumer<Throwable>>> failureReceiver;

    private DirectValue() {
      valueReceiver = new AtomicReference<>(valueConsumers::add);
      failureReceiver = new AtomicReference<>(failureConsumers::add);
      this.setLatch = new Semaphore(1);
    }

    @Override
    public TaskContext context() {
      return InMemImmediateContext.this;
    }

    @Override
    public <U> Value<U> flatMap(Function<? super T, ? extends Value<? extends U>> fn) {
      Promise<U> promise = promise();
      consume(t -> {
        final Value<? extends U> apply = fn.apply(t);
        apply.consume(promise::set);
        apply.onFail(promise::fail);
      });
      onFail(promise::fail);
      return promise.value();
    }

    @Override
    public void consume(Consumer<T> consumer) {
      valueReceiver.get().accept(consumer);
    }

    @Override
    public void onFail(Consumer<Throwable> errorConsumer) {
      failureReceiver.get().accept(errorConsumer);
    }
  }

  private final class ValuePromise<T> implements Promise<T> {

    private final DirectValue<T> value = new DirectValue<>();

    @Override
    public Value<T> value() {
      return value;
    }

    @Override
    public void set(T t) {
      final boolean completed = value.setLatch.tryAcquire();
      if (!completed) {
        throw new IllegalStateException("Promise was already completed");
      } else {
        value.valueReceiver.set(c -> c.accept(t));
        value.valueConsumers.forEach(c -> c.accept(t));
      }
    }

    @Override
    public void fail(Throwable throwable) {
      final boolean completed = value.setLatch.tryAcquire();
      if (!completed) {
        throw new IllegalStateException("Promise was already completed");
      } else {
        value.failureReceiver.set(c -> c.accept(throwable));
        value.failureConsumers.forEach(c -> c.accept(throwable));
      }
    }
  }
}
