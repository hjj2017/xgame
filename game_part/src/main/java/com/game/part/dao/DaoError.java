package com.game.part.dao;

import com.game.part.GameError;

/**
 * DAO 错误
 *
 * @author Haijiang
 * @since 2012/6/3
 */
public class DaoError extends GameError {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = -1L;

    /**
     * 类默认构造器
     */
    public DaoError() {
        super();
    }

    /**
     * 类参数构造器
     *
     * @param msg 异常信息
     */
    public DaoError(String msg) {
        super(msg);
    }

    /**
     * 类参数构造器
     *
     * @param err 引发异常的异常
     */
    public DaoError(Throwable err) {
        super(err);
    }

    /**
     * 类参数构造器
     *
     * @param msg 异常信息
     * @param err 引发异常的异常
     */
    public DaoError(String msg, Throwable err) {
        super(msg, err);
    }
}
