package io.rouz.flo;

import static java.util.Optional.empty;

import java.util.Optional;

/**
 * It extends the {@link OpProvider} interface to make the operator aware of the expected return
 * type of the task. This makes the operator less generic but it allows to override results and
 * skip the task evaluation as well as utilizing the returned value in case of successful task
 * execution.
 */
public interface OpProviderStrict<T, S> extends OpProvider<T> {

  /**
   * When a non empty value is returned, the {@link TaskContext} will not evaluate the task or its
   * upstreams but the returned value is used as task's result.
   *
   * @param taskContext The task context in which the current task is being evaluated
   * @return The optional result to be returned
   */
  default Optional<S> overrideResult(TaskContext taskContext) {
    return empty();
  }

  /**
   * Will be called just after a task that is using this operator has successfully evaluated.
   *
   * @param task The task that evaluated
   * @param z    The return value of the evaluated task
   */
  default void onSuccess(Task<?> task, S z) {
  }
}
