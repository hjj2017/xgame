package org.xgame.bizserver.base;

import com.alibaba.fastjson.JSONObject;
import org.xgame.comm.db.IQuerySystem;
import org.xgame.dbfarmer.base.DBFarmerLeader;

import java.util.function.Function;

/**
 * 数据库农民直接调用实现
 */
public class DBFarmerDirectCallImpl implements IQuerySystem {
    @Override
    public void init(JSONObject joConfig) {
        if (null != joConfig) {
            DBFarmerLeader.getInstance().init(joConfig);
        }
    }

    @Override
    public void execQueryAsync(
        Class<?> dbFarmerClazz, long bindId, String queryId, JSONObject joParam, Function<Boolean, Void> callback) {
        if (null == dbFarmerClazz ||
            null == queryId) {
            return;
        }

        DBFarmerLeader.getInstance().execQuery(dbFarmerClazz, queryId, joParam);

        if (null != callback) {
            callback.apply(true);
        }
    }
}
