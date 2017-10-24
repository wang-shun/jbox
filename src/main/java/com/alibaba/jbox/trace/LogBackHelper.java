package com.alibaba.jbox.trace;

import java.nio.charset.Charset;
import java.util.List;

import com.alibaba.jbox.script.ScriptExecutor;
import com.alibaba.jbox.trace.AbstractTLogConfig.TLogFilter;
import com.alibaba.jbox.trace.TLogManager.LogEventParser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.FileSize;
import com.ali.com.google.common.base.Strings;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.jbox.trace.TraceConstants.LOGGER_FILE_PATTERN;
import static com.alibaba.jbox.trace.TraceConstants.tracer;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 08:15:00.
 */
class LogBackHelper {

    static Logger initTLogger(String loggerName, String filePath, String charset, int maxHistory, long totalSizeCapKb,
                              List<TLogFilter> filters) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(loggerName), "log name can't be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(filePath), "log file can't be empty!");

        Logger logger = LoggerFactory.getLogger(loggerName);
        try {
            Class.forName("ch.qos.logback.classic.Logger");
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger tLogger = (ch.qos.logback.classic.Logger)logger;

                // init appender
                RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
                appender.setName(tLogger.getName());
                appender.setContext(tLogger.getLoggerContext());
                appender.setFile(filePath);
                appender.setAppend(true);

                // init policy
                TimeBasedRollingPolicy policy = new TimeBasedRollingPolicy();
                policy.setParent(appender);
                policy.setFileNamePattern(filePath + LOGGER_FILE_PATTERN);
                policy.setMaxHistory(maxHistory);
                if (totalSizeCapKb != 0) {
                    policy.setTotalSizeCap(new FileSize(totalSizeCapKb * FileSize.KB_COEFFICIENT));
                }
                policy.setContext(tLogger.getLoggerContext());
                policy.start();

                // init encoder
                PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                encoder.setPattern("%m%n%n");
                encoder.setCharset(Charset.forName(charset));
                encoder.setContext(tLogger.getLoggerContext());
                encoder.start();

                // start appender
                appender.setRollingPolicy(policy);
                appender.setEncoder(encoder);
                registerBuildInFilters(appender);
                registerCustomFilters(appender, filters);
                appender.start();

                // start logger
                tLogger.detachAndStopAllAppenders();
                tLogger.setAdditive(false);
                tLogger.setLevel(Level.ALL);
                tLogger.addAppender(appender);
                ScriptExecutor.register(tLogger.getName(), appender);
            } else {
                tracer.warn(
                    "app [{}] not used Logback impl for Log, please set '{}' logger in your logger context manual.",
                    System.getProperty("project.name", "unknown"),
                    loggerName);
            }
        } catch (ClassNotFoundException e) {
            tracer.warn("app [{}] not used Logback impl for Log, please set '{}' logger in your logger context manual.",
                System.getProperty("project.name", "unknown"),
                loggerName, e);
        }

        return logger;
    }

    private static void registerBuildInFilters(RollingFileAppender<ILoggingEvent> appender) {
        appender.addFilter(new Filter<ILoggingEvent>() {
            @Override
            public FilterReply decide(ILoggingEvent event) {
                StackTraceElement[] callerData = event.getCallerData();
                if (callerData != null && callerData.length >= 1) {
                    if (LogEventParser.class.getName().equals(callerData[0].getClassName())) {
                        return FilterReply.NEUTRAL;
                    } else {
                        return FilterReply.DENY;
                    }
                }

                return FilterReply.NEUTRAL;
            }
        });
    }

    private static void registerCustomFilters(RollingFileAppender<ILoggingEvent> appender, List<TLogFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (TLogFilter filter : filters) {
            // 将com.alibaba.jbox.trace.TLogManager.TLogFilter转换为ch.qos.logback.core.filter.Filter, 注册到Appender上
            appender.addFilter(new Filter<ILoggingEvent>() {
                @Override
                public FilterReply decide(ILoggingEvent event) {
                    try {
                        TLogFilter.FilterReply reply = filter.decide(event.getFormattedMessage());
                        return FilterReply.valueOf(reply.name());
                    } catch (Exception e) {
                        tracer.error("filter '{}' invoke error, formatted message: {}",
                            filter, event.getFormattedMessage(), e);
                        return FilterReply.DENY;
                    }
                }
            });
        }
    }
}
