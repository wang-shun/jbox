package com.alibaba.jbox.logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import com.taobao.diamond.client.Diamond;
import com.taobao.diamond.manager.ManagerListenerAdapter;

/**
 * @author jifang.zjf
 * @since 2017/7/23 下午6:50.
 */
public abstract class AbstractLoggerConfigurator {

    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    public AbstractLoggerConfigurator() {
        this("config", "properties");
    }

    public AbstractLoggerConfigurator(String diamondGroup, String diamondDataId) {
        Diamond.addListener(diamondDataId, diamondGroup, new ManagerListenerAdapter() {

            @Override
            public void receiveConfigInfo(String configInfo) {
                if (!INIT.compareAndSet(false, true)) {
                    Map<String, String> loggerConfigs = JSON.parseObject(configInfo, new TypeReference<Map<String, String>>() {
                    });
                    handleLoggerConfigs(loggerConfigs);
                }
            }
        });

    }

    protected abstract void handleLoggerConfigs(Map<String, String> loggerConfigs);
}
