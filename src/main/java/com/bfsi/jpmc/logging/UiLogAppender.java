package com.bfsi.jpmc.logging;

import com.bfsi.jpmc.service.ProcessingLogService;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

@Plugin(name = "UiLogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class UiLogAppender extends AbstractAppender {

    protected UiLogAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        byte[] bytes = getLayout().toByteArray(event);
        String message = new String(bytes, StandardCharsets.UTF_8);
        ProcessingLogService.appendFromLogger(message);
    }

    @PluginFactory
    public static UiLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter
    ) {
        Layout<? extends Serializable> resolvedLayout = layout != null
                ? layout
                : PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n").build();

        return new UiLogAppender(name == null ? "UiLogAppender" : name, filter, resolvedLayout);
    }
}
