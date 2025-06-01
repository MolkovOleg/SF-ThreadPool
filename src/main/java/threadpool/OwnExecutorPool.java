package threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OwnExecutorPool implements CustomExecutor {

    private static final Logger logger = LoggerFactory.getLogger(OwnExecutorPool.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int minSpareThreads;

    private final List<QueueWorker> workers;
    private final List<BlockingQueue<Runnable>> taskQueues;

    private final AtomicInteger queueIndex = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

    public OwnExecutorPool(int corePoolSize, int maxPoolSize, int queueCapacity, long keepAliveTime,
                           TimeUnit timeUnit, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.minSpareThreads = minSpareThreads;
        this.workers = new ArrayList<>();
        this.taskQueues = new ArrayList<>();

        logger.info("Инициализация пула потоков: core={}, max={}, queueSize={}, keepAlive={} {}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveTime, timeUnit);

        for (int i = 0; i < corePoolSize; i++) {
            createWorker(i);
        }
    }

    private void createWorker(int index) {
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(queueCapacity);
        QueueWorker worker = new QueueWorker(taskQueue, index, keepAliveTime, timeUnit);
        taskQueues.add(taskQueue);
        workers.add(worker);
        new Thread(worker, "Worker-" + index).start();
    }

    @Override
    public void execute(Runnable command) {
        if (this.isShutdown) {
            throw new RejectedExecutionException("Пул потоков завершен, задача отклонена.");
        }

        int index = queueIndex.getAndIncrement() % taskQueues.size();
        BlockingQueue<Runnable> taskQueue = taskQueues.get(index);
        if (!taskQueue.offer(command)) {
            throw new RejectedExecutionException("Очередь переполненна, задача отклонена.");
        }

        logger.debug("Задача отправлена в очередь {}", index);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (this.isShutdown) {
            throw new RejectedExecutionException("Пул потоков завершен, задача отклонена.");
        }

        FutureTask<T> futureTask = new FutureTask<>(task);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        this.isShutdown = true;
        logger.info("Пул потоков переводится в режим завершения (shutdown)");

    }

    @Override
    public void shutdownNow() {
        this.isShutdown = true;
        logger.warn("Принудительное завершение потоков (shutdownNow");
        for (QueueWorker worker : workers) {
            worker.stop();
        }
    }

    public int getWorkerCount() {
        return workers.size();
    }
}
