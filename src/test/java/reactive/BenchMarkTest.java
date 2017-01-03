package reactive;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactive.excutor.EscapableExecutor;
import reactive.excutor.ReactiveExecutor;
import reactive.internal.ReactiveEntity;
import reactive.internal.ReactiveFutureTask;
import reactive.internal.TaskExecutorPair;

import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Created by GaoBinfang on 2016/11/25-14:39.
 */
public class BenchMarkTest {

    private ReactiveExecutor reactiveExecutor;

    private TaskExecutorPair callback;

    private Callable<Boolean> task;

    private ReactiveTask<Boolean> reactiveTask;

    @Before
    public void setup() {
        reactiveExecutor = new ReactiveExecutor();
        reactiveTask = new ReactiveTask<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                System.out.println("onSuccess " + result + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onException(Throwable e) {
                System.out.println("onException " + e + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onCancellation() {
                System.out.println("onCancellation callback run " + Thread.currentThread().getName());
            }
        };
        callback = new ReactiveEntity(reactiveTask);
    }

    @After
    public void tearDown() {
        reactiveExecutor.shutdown();
    }

    @Test
    public void testGet() throws Exception {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                System.out.println("task about to finish " + Thread.currentThread().getName());
                return true;
            }
        };
        ReactiveFutureTask<Boolean> futureTask = reactiveExecutor.submit(task);
        assertThat(futureTask.get(), is(true));
    }

    @Test
    public void testOnSuccess() throws Exception {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                System.out.println("task about to finish " + Thread.currentThread().getName());
                return true;
            }
        };
        ReactiveFutureTask<Boolean> futureTask = reactiveExecutor.submit(task);
        futureTask.addReactEvent(callback);
        assertThat(futureTask.get(), is(true));
    }

    @Test(expected = CancellationException.class)
    public void testOnCancellation() throws Exception {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                System.out.println("task about to finish " + Thread.currentThread().getName());
                return true;
            }
        };
        ReactiveFutureTask<Boolean> futureTask = reactiveExecutor.submit(task);
        futureTask.addReactEvent(callback);
        assertThat(futureTask.cancel(true), is(true));
        futureTask.get();
    }

    @Test(expected = ExecutionException.class)
    public void testOnException() throws Exception {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                System.out.println("task about to exception " + Thread.currentThread().getName());
                throw new Exception("exception");
            }
        };
        ReactiveFutureTask<Boolean> futureTask = reactiveExecutor.submit(task);
        futureTask.addReactEvent(callback);
        futureTask.get();
    }

    @Test
    public void testMultipleDividedOnSuccess() throws Exception {
        reactiveExecutor.shutdown();
        reactiveExecutor = new ReactiveExecutor(Executors.newFixedThreadPool(2));//make original executor become react executor
        ExecutorService react = Executors.newFixedThreadPool(5);//manually set react handler
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                System.out.println("task about to finish " + Thread.currentThread().getName());
                return true;
            }
        };
        ReactiveFutureTask<Boolean> futureTask1 = reactiveExecutor.submit(task);
        ReactiveFutureTask<Boolean> futureTask2 = reactiveExecutor.submit(task);
        for (int index = 13; index < 20; index++) {
            futureTask1.addReactEvent(getRandomTask(index, react));
            if (index == 19) {
                futureTask2.addReactEvent(getRandomTask(index, react));
            }
        }
        assertThat(futureTask1.get(), is(true));
        assertThat(futureTask2.get(), is(true));
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
    }

    private ReactiveEntity<Boolean> getRandomTask(final int count, ExecutorService react) {
        return new ReactiveEntity<>(new ReactiveTask<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                System.out.println(count + " onSuccess " + result + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onException(Throwable e) {
                System.out.println("onException " + e + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onCancellation() {
                System.out.println("onCancellation callback run " + Thread.currentThread().getName());
            }
        }, react);
    }

    @Test
    public void testOnSuccessRunnable() throws Exception {
        ReactiveFutureTask futureTask = reactiveExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                }
                System.out.println("task about to finish " + Thread.currentThread().getName());
            }
        });
        futureTask.addReactEvent(new ReactiveEntity(new ReactiveTask() {
            @Override
            public void onSuccess(Object result) {
                System.out.println("onSuccess " + result + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onException(Throwable e) {
            }

            @Override
            public void onCancellation() {
            }
        }));
        assertThat(futureTask.get(), is(nullValue()));
    }

    @Test
    public void testOnSuccessRunnableWithResultValue() throws Exception {
        ReactiveFutureTask futureTask = reactiveExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                }
                System.out.println("task about to finish " + Thread.currentThread().getName());
            }
        }, Boolean.TRUE);
        futureTask.addReactEvent(new ReactiveEntity(new ReactiveTask() {
            @Override
            public void onSuccess(Object result) {
                System.out.println("onSuccess " + result + " callback run " + Thread.currentThread().getName());
            }

            @Override
            public void onException(Throwable e) {
            }

            @Override
            public void onCancellation() {
            }
        }));
        assertThat((Boolean) futureTask.get(), is(true));
    }

    @Test(expected = CancellationException.class)
    public void testOnAutoCancel() throws Exception {
        EscapableExecutor test = new EscapableExecutor();
        Future futureTask = test.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(TimeUnit.MINUTES.toMillis(5));
                return true;
            }
        }, 10, TimeUnit.SECONDS);
        try {
            futureTask.get();
        } catch (Exception e) {
        }
        assertThat(futureTask.isCancelled(), is(true));
        futureTask.get();
    }
}
