package com.game.part.lazySaving;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据更新器
 * 
 * @author haijiang.jin
 * @since 2015/3/30
 * 
 */
public final class LazySavingHelper {
    /** 单例对象 */
    public static final LazySavingHelper OBJ = new LazySavingHelper();

    /**
     * 变化的数据字典 0, 主字典!
     * 当处于更新过程中的时候 ( 执行 {@link #execUpdateWithInterval()} 时 ),
     * 主字典会与外界隔绝...
     *
     */
    private final Map<String, UpdateEntry> _changeObjMap0 = new ConcurrentHashMap<>();

    /**
     * 变化的数据字典 1, 辅助字典!
     * 当处于更新过程中的时候 ( 执行 {@link #execUpdateWithInterval()} 时 ),
     * 新数据会塞到辅助字典里...
     *
     */
    private final Map<String, UpdateEntry> _changeObjMap1 = new ConcurrentHashMap<>();

    /** 更新中...? */
    private AtomicBoolean _updatingFlag = new AtomicBoolean(false);

    /** 当数据对象空闲超过指定时间后才真正执行更新操作 */
    public long _idelToUpdate = 8L * 60L * 1000L;
    /** 间隔制定时间之后, 才执行一次更新操作 */
    public long _interval = 2L * 60L * 1000L;
    /** 上一次执行更新操作的时间 */
    private AtomicLong _lastUpdateTime = new AtomicLong(-1L);
    /** 每次更新时写出多少条数据 */
    public int _writeCount = 256;

    /** 根据内置时间更新 */
    private static final Pred_UpdateWithInterval PRED_DEFAULT = new Pred_UpdateWithInterval();

    /**
     * 类默认构造器
     *
     */
    private LazySavingHelper() {
    }

    /**
     * 增加要被更新的对象, 注意 : 必须是同一个实例
     *
     * @param lso 延迟保存对象接口
     *
     */
    public void addUpdate(ILazySavingObj<?> lso) {
        // 字典变量
        Map<String, UpdateEntry> mapX = this._updatingFlag.get() ? this._changeObjMap1 : this._changeObjMap0;
        // 添加更新操作
        addUpdate(lso, this.nowTime(), mapX);
    }

    /**
     * 增加要被更新的对象, 注意 : 必须是同一个实例
     *
     * @param lso 延迟保存对象接口
     * @param nowTime 当前时间
     * @param saveMap 将 lso 对象保存到该字典中
     *
     */
    private static void addUpdate(ILazySavingObj<?> lso, long nowTime, Map<String, UpdateEntry> saveMap) {
        if (lso == null ||
            saveMap == null) {
            // 如果参数对象为空,
            // 则直接退出!
            return;
        }

        // 获取业务对象存储键
        final String storeKey = lso.getStoreKey();
        // 获取旧的进入点
        UpdateEntry oldEntry = saveMap.get(storeKey);

        if (oldEntry != null) {
            if (oldEntry._operTypeInt == UpdateEntry.OPT_del) {
                // 如果已有的入口是删除操作,
                // 放弃本次更新操作!
                LazySavingLog.LOG.error(MessageFormat.format(
                    "准备将对象标记为更新操作, 但是已存在一个 storeKey ( = {0} ) 相同的删除操作, 所以放弃本次更新操作",
                    storeKey
                ));
                return;
            }

            if (oldEntry._lso != lso) {
                // 如果 LifeCycle 不是同一个对象,
                // 则直接退出!
                LazySavingLog.LOG.error("更新对象 ( 内存地址 ) 不相同, 这是不允许的");
                return;
            } else {
                // 如果是同一对象,
                // 则直接退出!
                // 但是在退出前修改一下时间
                oldEntry._lastModifiedTime = nowTime;
                return;
            }
        }

        // 创建新的进入点
        UpdateEntry newEntry = new UpdateEntry(lso, UpdateEntry.OPT_saveOrUpdate);
        // 设置最后修改时间
        newEntry._lastModifiedTime = nowTime;
        // 添加到字典中
        saveMap.put(storeKey, newEntry);
    }

    /**
     * 增加要被删除的对象
     *
     * @param lso 延迟保存对象接口
     *
     */
    public void addDel(ILazySavingObj<?> lso) {
        if (lso == null) {
            // 如果参数对象为空,
            // 则直接退出!
            return;
        }

        // 字典变量
        Map<String, UpdateEntry> mapX = this._updatingFlag.get() ? this._changeObjMap1 : this._changeObjMap0;
        // 添加删除操作
        addDel(lso, this.nowTime(), mapX);
    }

    /**
     * 增加要被删除的对象
     *
     * @param lso 延迟保存对象接口
     * @param nowTime 当前时间
     * @param saveMap 将 lso 对象保存到该字典中
     *
     */
    private static void addDel(ILazySavingObj<?> lso, long nowTime, Map<String, UpdateEntry> saveMap) {
        if (lso == null ||
            saveMap == null) {
            // 如果参数对象为空,
            // 则直接退出!
            return;
        }

        // 获取业务对象存储键
        final String storeKey = lso.getStoreKey();
        // 获取旧的进入点
        UpdateEntry oldEntry = saveMap.get(storeKey);

        if (oldEntry != null) {
            do {
                if (oldEntry._operTypeInt == UpdateEntry.OPT_saveOrUpdate) {
                    // 如果已有的入口是更新操作,
                    // 我擦, 那到底是删除还是更新啊...
                    LazySavingLog.LOG.error(MessageFormat.format(
                        "准备将对象标记为删除操作, 但是已存在一个 storeKey ( = {0} ) 相同的更新操作, 所以替换更新操作为删除操作!",
                        storeKey
                    ));
                    break;
                    // 注意, 这里的 break 打破了 do ... while 循环,
                    // 这种用法其实 goto 语句的一个变种!
                    // 因为这时候需要跳转到 ADD_DEL
                    // 也就是说:
                    // 1、在 oldEntry 为空的情况下, 需要新建对象并添加到字典;
                    // 2、在 oldEntry 不为空, 但操作类型为更新时, 也需要新建对象并添加到字典;
                }

                if (oldEntry._lso != lso) {
                    // 如果 LifeCycle 不是同一个对象,
                    // 则直接退出!
                    LazySavingLog.LOG.error("更新对象 ( 内存地址 ) 不相同, 这是不允许的");
                    return;
                } else {
                    // 如果是同一对象,
                    // 则直接退出!
                    // 但是在退出前修改一下时间
                    oldEntry._lastModifiedTime = nowTime;
                    return;
                }
            } while (false);
        }

// ADD_DEL:
        // 创建新的进入点
        UpdateEntry newEntry = new UpdateEntry(lso, UpdateEntry.OPT_del);
        // 设置最后修改时间
        newEntry._lastModifiedTime = nowTime;
        // 添加到字典
        saveMap.put(storeKey, newEntry);
    }

    /**
     * 获取当前时间
     *
     * @return 当前时间的时间戳
     *
     */
    private long nowTime() {
        // 获取系统时间
        return System.currentTimeMillis();
    }

    /**
     * 根据间隔时间执行更新操作,
     * 该函数的调用者应该是一个定时器!
     *
     * @see #_interval
     *
     */
    public final void execUpdateWithInterval() {
        // 获取当前时间
        final long nowTime = this.nowTime();
        // 获取上一次执行更新操作的时间
        final long lastUpdateTime = this._lastUpdateTime.get();

        if ((nowTime - lastUpdateTime) < this._interval) {
            // 如果还没有到间隔时间,
            // 则直接退出!
            return;
        }

        // 设置当前时间并执行更新操作
        PRED_DEFAULT._nowTime = nowTime;
        this.execUpdateWithPredicate(PRED_DEFAULT, this._writeCount);
        // 设置最后更新时间
        this._lastUpdateTime.set(nowTime);
    }

    /**
     * 按照断言条件来更新
     *
     * @param pred 断言条件
     * @throws IllegalArgumentException if pred == null
     * @see #execUpdateWithPredicate(ILazySavingPredicate, int)
     *
     */
    public final void execUpdateWithPredicate(
        ILazySavingPredicate pred) {
        this.execUpdateWithPredicate(pred, Integer.MAX_VALUE);
    }

    /**
     * 按照断言条件来更新
     *
     * @param pred 断言条件
     * @param updateCount 更新数量
     * @throws IllegalArgumentException if pred == null
     *
     */
    public final void execUpdateWithPredicate(ILazySavingPredicate pred, int updateCount) {
        if (updateCount <= 0) {
            // 如果更新数量 <= 0,
            // 则直接退出!
            return;
        }

        if (pred == null) {
            // 如果更新条件为空,
            // 则抛出异常!
            throw new IllegalArgumentException("pred is null");
        }

        if (!this._updatingFlag.compareAndSet(false, true)) {
            // 事先检查是否未在更新过程中,
            // 如果没在更新, 则把标志位设置为 true...
            // 但如果正在更新中,
            // 则直接退出!
            LazySavingLog.LOG.error("正在更新中...");
            return;
        }

        // 获取开始时间
        final long startTime = this.nowTime();

        // 将字典 1 中的数据移到字典 0
        mv(this._changeObjMap1, this._changeObjMap0);
        // 获取迭代器
        Iterator<UpdateEntry> it = this._changeObjMap0.values().iterator();

        while (it.hasNext() && updateCount > 0) {
            // 获取入口
            UpdateEntry entry = it.next();

            if (entry == null ||
                entry._lso == null) {
                // 如果入口对象为空,
                // 则直接跳过!
                it.remove();
                continue;
            }

            // 获取延迟保存对象
            final ILazySavingObj<?> lso = entry._lso;

            try {
                if (!pred.predicate(lso)) {
                    // 如果有断言对象,
                    // 并且当前 LSO 不满足条件,
                    // 则直接退出!
                    continue;
                }

                if (entry._operTypeInt == UpdateEntry.OPT_saveOrUpdate) {
                    // 执行保存或更新操作
                    CommUpdater.OBJ.saveOrUpdate(lso);
                } else {
                    // 执行删除操作
                    CommUpdater.OBJ.del(lso);
                }
            } catch (Exception ex) {
                // 记录异常日志
                LazySavingLog.LOG.error(ex.getMessage(), ex);
            } finally {
                // 从字典中移除对象
                it.remove();
            }

            // 数量减 1
            updateCount--;
        }

        // 再次将字典 1 中的数据移到字典 0,
        // 因为在更新过程中,
        // map1 中可能又有数据了...
        mv(this._changeObjMap1, this._changeObjMap0);

        // 获取结束时间
        final long endTime = this.nowTime();
        // 获取花费时间
        final long costTime = endTime - startTime;

        // 记录调试信息
        LazySavingLog.LOG.debug(MessageFormat.format(
            "更新消耗时间 = {0}(ms)",
            String.valueOf(costTime)
        ));

        // 修改更新标识
        this._updatingFlag.set(false);
    }

    /**
     * 将 "来源字典" 中的键值移到 "目标字典"
     *
     * @param fromMap 来源字典
     * @param toMap 目标字典
     *
     */
    private static void mv(Map<String, UpdateEntry> fromMap, Map<String, UpdateEntry> toMap) {
        if (fromMap == null ||
            fromMap.isEmpty() ||
            toMap == null) {
            // 如果参数对象为空,
            // 则直接退出!
            return;
        }

        // 获取键值迭代器
        Iterator<Entry<String, UpdateEntry>> it = fromMap.entrySet().iterator();

        while (it.hasNext()) {
            // 获取字典入口
            Entry<String, UpdateEntry> mapEntry = it.next();
            // 删除当前键值
            it.remove();

            if (mapEntry == null ||
                mapEntry.getValue() == null) {
                // 如果字典入口为空,
                // 则直接跳过!
                continue;
            }

            // 获取更新入口
            UpdateEntry upEntry = mapEntry.getValue();

            if (upEntry._operTypeInt == UpdateEntry.OPT_saveOrUpdate) {
                // 添加 LC 到目标字典 ( 保存或者更新 )
                addUpdate(
                    upEntry._lso,
                    upEntry._lastModifiedTime,
                    toMap
                );
            } else {
                // 添加 LC 到目标字典 ( 删除 )
                addDel(
                    upEntry._lso,
                    upEntry._lastModifiedTime,
                    toMap
                );
            }
        }
    }

    /**
     * 根据空闲时间更新,
     * 空闲时间参数由 {@link #_idelToUpdate} 指定
     *
     * @author hjj2017
     * @since 2015/11/26
     *
     */
    static class Pred_UpdateWithInterval implements ILazySavingPredicate {
        /** 当前时间 */
        public long _nowTime = 0L;

        @Override
        public boolean predicate(ILazySavingObj<?> lso) {
            if (lso == null) {
                // 如果参数对象为空,
                // 则直接退出!
                return false;
            }

            // 获取更新入口
            UpdateEntry entry = LazySavingHelper.OBJ._changeObjMap0.get(lso.getStoreKey());

            if (entry == null) {
                // 如果入口对象为空,
                // 则直接退出!
                return false;
            }

            // 获取空闲时间
            final long idelTime = this._nowTime - entry._lastModifiedTime;
            // 判断空闲时间是否已到?
            return (idelTime >= LazySavingHelper.OBJ._idelToUpdate);
        }
    }
}
