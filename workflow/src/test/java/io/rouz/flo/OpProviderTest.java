package io.rouz.flo;

import static io.rouz.flo.TestUtils.evalAndGet;
import static io.rouz.flo.TestUtils.evalAndGetException;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Test;
import org.mockito.InOrder;

public class OpProviderTest {

  private String setFromInjected;

  @Test
  public void injectsOps() throws Exception {
    Task<String> task = Task.named("inject").ofType(String.class)
        .op(new TestProvider())
        .process(injected -> injected.doSomething("foo"));

    String result = evalAndGet(task);

    assertThat(result, is("ok"));
    assertThat(setFromInjected, is("something foo"));
  }

  @Test
  public void shouldOverrideTaskResult() throws Exception {
    Task<String> task = Task.named("inject").ofType(String.class)
        .op(new OverridingProvider())
        .process(injected -> injected.doSomething("foo"));

    String result = evalAndGet(task);

    assertThat(result, is("overridden result"));
    assertNull(setFromInjected);
  }

  @Test
  public void shouldExecuteOnSuccess() throws Exception {
    StrictBasicProvider strictBasicProvider = spy(new StrictBasicProvider("foo"));
    Task<String> task = Task.named("inject").ofType(String.class)
        .op(strictBasicProvider)
        .process(injected -> {
          assertThat(injected, is("foo"));
          return "ok";
        }
    );

    String result = evalAndGet(task);
    assertThat(result, is("ok"));
    verify(strictBasicProvider).onSuccess(task, "ok");
  }

  @Test
  public void shouldOverrideTaskResultWithUpstream() throws Exception {
    OverridingProvider op1 = spy(new OverridingProvider());
    TestProvider op2 = spy(new TestProvider());
    Task<String> task1 = Task.named("inject1").ofType(String.class)
        .op(op1)
        .process(injected -> injected.doSomething("foo"));

    Task<String> task2 = Task.named("inject2").ofType(String.class)
        .op(op2)
        .in(() -> task1)
        .process(Injected::doSomething);

    String result = evalAndGet(task2);

    assertThat(result, is("ok"));
    assertThat(setFromInjected, is("something overridden result"));

    verify(op1, never()).provide(any());
    verify(op2, times(1)).provide(any());
  }

  @Test
  public void injectsOpsSecond() throws Exception {
    Task<String> task = Task.named("inject").ofType(String.class)
        .in(() -> Task.named("foo").ofType(String.class).process(() -> "hej"))
        .op(new TestProvider())
        .process((a, b) -> b.doSomething("bar"));

    String result = evalAndGet(task);

    assertThat(result, is("ok"));
    assertThat(setFromInjected, is("something bar"));
  }

  @Test
  public void lifecycleMethodsCalledInOrder() throws Exception {
    BasicProvider op1 = spy(new BasicProvider("foo"));
    BasicProvider op2 = spy(new BasicProvider("bar"));
    Task<String> task = Task.named("inject").ofType(String.class)
        .op(op1)
        .op(op2)
        .process((i1, i2) -> {
          assertThat(i1, is("foo"));
          assertThat(i2, is("bar"));
          op1.mark();
          return i1 + i2;
        });

    evalAndGet(task);
    InOrder inOrder = inOrder(op1, op2);
    inOrder.verify(op1).preRun(task);
    inOrder.verify(op2).preRun(task);
    inOrder.verify(op1).mark();
    inOrder.verify(op2).onSuccess(task);
    inOrder.verify(op1).onSuccess(task);
  }

  @Test
  public void lifecycleMethodsCalledInOrderOnFail() throws Exception {
    BasicProvider op1 = spy(new BasicProvider("foo"));
    BasicProvider op2 = spy(new BasicProvider("bar"));
    Task<String> task = Task.named("inject").ofType(String.class)
        .op(op1)
        .op(op2)
        .process((i1, i2) -> {
          assertThat(i1, is("foo"));
          assertThat(i2, is("bar"));
          op1.mark();
          throw new RuntimeException("force fail");
        });

    Throwable throwable = evalAndGetException(task);
    InOrder inOrder = inOrder(op1, op2);
    inOrder.verify(op1).preRun(task);
    inOrder.verify(op2).preRun(task);
    inOrder.verify(op1).mark();
    inOrder.verify(op2).onFail(task, throwable);
    inOrder.verify(op1).onFail(task, throwable);
    assertThat(throwable.getMessage(), is("force fail"));
  }

  @Test
  public void lifecycleMethodsCalledAfterInputsHaveEvaluated() throws Exception {
    //noinspection unchecked
    TaskBuilder.F0<String> t1Fn = mock(TaskBuilder.F0.class);
    //noinspection unchecked
    TaskBuilder.F0<String> t2Fn = mock(TaskBuilder.F0.class);
    when(t1Fn.get()).thenReturn("hej");
    when(t2Fn.get()).thenReturn("hej");
    BasicProvider op1 = spy(new BasicProvider("foo"));

    Task<String> task = Task.named("inject").ofType(String.class)
        .in(() -> Task.named("foo").ofType(String.class).process(t1Fn))
        .op(op1)
        .in(() -> Task.named("bar").ofType(String.class).process(t2Fn))
        .process((t1, i1, t2) -> {
          op1.mark();
          return t1 + i1 + t2;
        });

    evalAndGet(task);
    InOrder inOrder = inOrder(t1Fn, t2Fn, op1);
    inOrder.verify(t2Fn).get();
    inOrder.verify(op1).provide(any());
    inOrder.verify(t1Fn).get();
    inOrder.verify(op1).preRun(task);
    inOrder.verify(op1).mark();
    inOrder.verify(op1).onSuccess(task);
  }

  @Test
  public void lifecycleMethodsNotCalledIfInputsFail() throws Exception {
    //noinspection unchecked
    TaskBuilder.F0<String> t1Fn = mock(TaskBuilder.F0.class);
    when(t1Fn.get()).thenThrow(new RuntimeException("Fail"));
    BasicProvider op1 = spy(new BasicProvider("foo"));

    Task<String> task = Task.named("inject").ofType(String.class)
        .op(op1)
        .in(() -> Task.named("foo").ofType(String.class).process(t1Fn))
        .process((i1, t1) -> {
          op1.mark();
          return t1 + i1;
        });

    Throwable throwable = evalAndGetException(task);
    assertThat(throwable.getMessage(), is("Fail"));
    InOrder inOrder = inOrder(t1Fn, op1);
    inOrder.verify(t1Fn).get();
    inOrder.verify(op1).provide(any());
    inOrder.verifyNoMoreInteractions();
  }

  private class Injected {
    String doSomething(String some) {
      setFromInjected = "something " + some;
      return "ok";
    }
  }

  private class TestProvider implements OpProvider<Injected> {

    @Override
    public Injected provide(TaskContext taskContext) {
      return new Injected();
    }
  }

  private class OverridingProvider implements OpProviderStrict<Injected, String> {

    @Override
    public Injected provide(TaskContext taskContext) {
      return new Injected();
    }

    @Override
    public Optional<String> overrideResult(TaskContext taskContext) {
      return Optional.of("overridden result");
    }
  }

  private class StrictBasicProvider implements OpProviderStrict<String, String> {

    private final String inject;

    private StrictBasicProvider(String inject) {
      this.inject = inject;
    }

    @Override
    public String provide(TaskContext taskContext) {
      return inject;
    }

    @Override
    public void onSuccess(Task<?> task, String value) {
    }
  }

  private class BasicProvider implements OpProvider<String> {

    private final String inject;

    private BasicProvider(String inject) {
      this.inject = inject;
    }

    @Override
    public String provide(TaskContext taskContext) {
      return inject;
    }

    public void mark() {
      // noop, used to verify call order
    }
  }
}
