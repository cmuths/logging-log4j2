/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.taglib;

import java.util.WeakHashMap;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextKey;

/**
 * This bridge between the tag library and the Log4j API ensures that instances of {@link Log4jTaglibLogger} are
 * appropriately held in memory and not constantly recreated.
 *
 * @since 2.0
 */
final class Log4jTaglibLoggerContext implements LoggerContext {
    // These were change to WeakHashMaps to avoid ClassLoader (memory) leak, something that's particularly
    // important in Servlet containers.
    private static final WeakHashMap<ServletContext, Log4jTaglibLoggerContext> CONTEXTS =
            new WeakHashMap<>();

    private final WeakHashMap<String, Log4jTaglibLogger> loggers =
            new WeakHashMap<>();

    private final ServletContext servletContext;

    private Log4jTaglibLoggerContext(final ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public Object getExternalContext() {
        return this.servletContext;
    }

    @Override
    public Log4jTaglibLogger getLogger(final String name) {
        return this.getLogger(name, null);
    }

    @Override
    public Log4jTaglibLogger getLogger(final String name, final MessageFactory messageFactory) {
        String key = LoggerContextKey.create(name, messageFactory);
        Log4jTaglibLogger logger = this.loggers.get(key);
        if (logger != null) {
            AbstractLogger.checkMessageFactory(logger, messageFactory);
            return logger;
        }

        synchronized (this.loggers) {
            logger = this.loggers.get(key);
            if (logger == null) {
                final LoggerContext context = LogManager.getContext(false);
                final ExtendedLogger original = messageFactory == null ?
                        context.getLogger(name) : context.getLogger(name, messageFactory);
                // wrap a logger from an underlying implementation
                logger = new Log4jTaglibLogger(original, name, original.getMessageFactory());
                key = LoggerContextKey.create(name, original.getMessageFactory());
                this.loggers.put(key, logger);
            }
        }

        return logger;
    }

    @Override
    public boolean hasLogger(final String name) {
        return loggers.containsKey(LoggerContextKey.create(name));
    }

    @Override
    public boolean hasLogger(String name, MessageFactory messageFactory) {
        return loggers.containsKey(LoggerContextKey.create(name, messageFactory));
    }

    @Override
    public boolean hasLogger(String name, Class<? extends MessageFactory> messageFactoryClass) {
        return loggers.containsKey(LoggerContextKey.create(name, messageFactoryClass));
    }

    static synchronized Log4jTaglibLoggerContext getInstance(final ServletContext servletContext) {
        Log4jTaglibLoggerContext loggerContext = CONTEXTS.get(servletContext);
        if (loggerContext != null) {
            return loggerContext;
        }

        synchronized (CONTEXTS) {
            loggerContext = CONTEXTS.get(servletContext);
            if (loggerContext == null) {
                loggerContext = new Log4jTaglibLoggerContext(servletContext);
                CONTEXTS.put(servletContext, loggerContext);
            }
        }

        return loggerContext;
    }
}
