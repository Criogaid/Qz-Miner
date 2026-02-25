package club.heiqi.qz_miner.thread;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 并行tick类
 */
public class ParallelTick {
    public Logger LOG = LogManager.getLogger();
    private static final String SEARCHER_AUDIT_TAG = "[SearcherAudit]";
    public AtomicBoolean preTick = new AtomicBoolean(false);
    public AtomicBoolean postTick = new AtomicBoolean(false);

    public ArrayList<Pauseable> preTickTasks = new ArrayList<>();
    public ArrayList<Pauseable> postTickTasks = new ArrayList<>();
    public ArrayList<Pauseable> normalTasks = new ArrayList<>();

    public ParallelTick() {}

    public void processPreTickTasks(boolean shouldRun) {
        // 清理preTickTasks
        if (!shouldRun) {
            removeStoppedTasks(preTickTasks);
        }
        processTasks(shouldRun, true);
    }

    public void processPostTickTasks(boolean shouldRun) {
        // 清理postTickTasks
        if (!shouldRun) {
            removeStoppedTasks(postTickTasks);
        }
        processTasks(shouldRun, false);
    }

    /**
     * @param shouldRun 通过Minecraft的tick事件进入或退出传入true或false
     * @param preTick 通过布尔值选择preTickTasks或postTickTasks;<br>1.true为preTickTasks;<br>2.false为postTickTasks
     */
    private void processTasks(boolean shouldRun, boolean preTick) {
        ArrayList<Pauseable> tasks = preTick ? preTickTasks : postTickTasks;
        String queueType = preTick ? "pre" : "post";
        for (Pauseable task : tasks) {
            if (!task.started.get()) {
                if (shouldRun) {
                    logTaskStart(task, queueType);
                    task.start();
                    // LOG.info("启动线程：{}", task.getClass().getSimpleName());
                }
            } else {
                if (shouldRun) {
                    task.unPause();
                    // LOG.info("恢复线程：{}", task.getClass().getSimpleName());
                } else {
                    task.pause();
                    // LOG.info("暂停线程：{}", task.getClass().getSimpleName());
                }
            }
        }
        processNormalTasks();
    }

    /**
     * 普通任务不执行暂停和恢复操作
     */
    public ReentrantLock normalTaskLock = new ReentrantLock();
    public void processNormalTasks() {
        if (normalTaskLock.isLocked()) {
            LOG.warn("通用并行同步线程被阻塞! [General-purpose parallel synchronous threads are blocked!]");
            return;
        };
        normalTaskLock.lock();
        try {
            Iterator<Pauseable> iterator = normalTasks.iterator();
            while (iterator.hasNext()) {
                Pauseable task = iterator.next();
                if (!task.started.get()) {
                    logTaskStart(task, "normal");
                    task.start();
                }
                if (task.stopped.get()) {
                    iterator.remove();
                }
            }
        } finally {
            normalTaskLock.unlock();
        }
    }


    public void addPreServerTickTask(Pauseable task) {
        task.setDaemon(true);
        preTickTasks.add(task);
        logTaskQueued(task, "pre");
    }

    public void addPostServerTickTask(Pauseable task) {
        task.setDaemon(true);
        postTickTasks.add(task);
        logTaskQueued(task, "post");
    }

    public void addNormalTask(Pauseable task) {
        normalTaskLock.lock();
        try {
            task.setDaemon(true);
            normalTasks.add(task);
            logTaskQueued(task, "normal");
        } finally {
            normalTaskLock.unlock();
        }
    }

    private void logTaskQueued(Pauseable task, String queueType) {
        LOG.info(
                "{} queued queue={} task={} threadName={} requesterThread={}",
                SEARCHER_AUDIT_TAG,
                queueType,
                task.getClass().getSimpleName(),
                task.getName(),
                Thread.currentThread().getName()
        );
    }

    private void logTaskStart(Pauseable task, String queueType) {
        LOG.info(
                "{} start queue={} task={} threadName={} requesterThread={}",
                SEARCHER_AUDIT_TAG,
                queueType,
                task.getClass().getSimpleName(),
                task.getName(),
                Thread.currentThread().getName()
        );
    }

    private static void removeStoppedTasks(ArrayList<Pauseable> tasks) {
        Iterator<Pauseable> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().stopped.get()) {
                iterator.remove();
            }
        }
    }
}
