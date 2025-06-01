package threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(QueueWorker.class);

    private final BlockingQueue<Runnable> taskQueue;
    private final int workerId;

    private final long keepAliveTime;
    private final TimeUnit timeUnit;

    private volatile boolean running = true;

    public QueueWorker(BlockingQueue<Runnable> taskQueue, int workerId, long keepAliveTime, TimeUnit timeUnit) {
        this.taskQueue = taskQueue;
        this.workerId = workerId;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
    }

    @Override
    public void run() {
        logger.info("Worker-{} запущен", workerId);
        try {
            while (running) {
                Runnable task = taskQueue.poll(keepAliveTime, timeUnit);
                if (task != null) {
                    logger.debug("Worker-{} получил задачу", workerId);
                    try {
                        task.run();
                    } catch (Exception e) {
                        logger.error("Ошибка при выполнении задачи Worker-{}: {}", workerId, e.getMessage());
                    }
                } else {
                    logger.info("Worker-{} завершает работу после простоя", workerId);
                    running = false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Worker-{} был прерван", workerId);
        }
    }

    public void stop() {
        this.running = false;
    }
}
