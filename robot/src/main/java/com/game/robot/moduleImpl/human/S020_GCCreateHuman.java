package com.game.robot.moduleImpl.human;

import java.text.MessageFormat;

import com.game.bizModule.human.msg.CGQueryHumanEntryList;
import com.game.bizModule.human.msg.GCCreateHuman;
import com.game.part.msg.type.MsgStr;
import com.game.robot.RobotLog;
import com.game.robot.kernal.AbstractGCMsgHandler;
import com.game.robot.kernal.Robot;

/**
 * 接收 GC 消息
 * 
 * @author hjj2019
 * @since 2015/5/15
 * 
 */
public class S020_GCCreateHuman extends AbstractGCMsgHandler<GCCreateHuman> {
    @Override
    public void handleGCMsg(
        Robot robotObj, GCCreateHuman msgObj) {
        if (msgObj._success.getBoolVal()) {
            // 记录日志信息
            RobotLog.LOG.info(MessageFormat.format(
                "{0}.{1} 建角成功",
                robotObj._gameServerName,
                robotObj._userName
            ));

            // 重新查询角色入口列表
            // 创建并发送 CG 消息
            CGQueryHumanEntryList cgMSG = new CGQueryHumanEntryList();
            cgMSG._serverName = new MsgStr(robotObj._gameServerName);
            robotObj.sendCGMsg(cgMSG);
        } else {
            // 记录日志信息
            RobotLog.LOG.info(MessageFormat.format(
                "{0}.{1} 建角失败",
                robotObj._gameServerName,
                robotObj._userName
            ));
        }
    }

    @Override
    protected Class<GCCreateHuman> getGCMsgClazzDef() {
        return GCCreateHuman.class;
    }
}
