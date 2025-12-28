package com.trian.zimswitch.simulator.config;

import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.NACChannel;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jpos.util.SimpleLogListener;
import org.jpos.util.LogSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.InputStream;

@Configuration
public class IsoChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(IsoChannelConfig.class);

    @Value("${gateway.host:127.0.0.1}")
    private String host;

    @Value("${gateway.port:5000}")
    private int port;

    @Value("${gateway.timeout:5000}")
    private int timeoutMs;

    @Bean
    public ISOPackager isoPackager() throws Exception {
        // Always prefer the custom packager and normalize its DOCTYPE to the provided URL
        ClassLoader cl = getClass().getClassLoader();
        InputStream raw = cl.getResourceAsStream("packager/isoswitch.xml");
        if (raw == null) {
            throw new IllegalStateException("Packager resource not found on classpath: packager/isoswitch.xml");
        }
        String xml = new String(raw.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        // Force DOCTYPE to requested URL
        String dtd = "http://45.94.58.51/genericpackager.dtd";
        if (xml.contains("<!DOCTYPE isopackager")) {
            xml = xml.replaceAll("<!DOCTYPE\\s+isopackager\\s+SYSTEM\\s+\"[^\"]*\">", "<!DOCTYPE isopackager SYSTEM \"" + dtd + "\">");
        } else {
            // Insert DOCTYPE after XML declaration if missing
            int idx = xml.indexOf('\n');
            if (idx > -1 && xml.startsWith("<?xml")) {
                xml = xml.substring(0, idx + 1) + "<!DOCTYPE isopackager SYSTEM \"" + dtd + "\">\n" + xml.substring(idx + 1);
            } else {
                xml = "<!DOCTYPE isopackager SYSTEM \"" + dtd + "\">\n" + xml;
            }
        }
        log.info("Loading ISO packager from classpath resource 'packager/isoswitch.xml' with DTD {}", dtd);
        java.io.ByteArrayInputStream is = new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new GenericPackager(is);
    }

    @Bean
    public ISOChannel isoChannel(ISOPackager packager) throws Exception {
        // Use constructor available in this jPOS version (no TPDU header)
        NACChannel channel = new NACChannel(host, port, packager, org.jpos.iso.ISOUtil.hex2byte("42"));
        channel.setTimeout(timeoutMs);

        // Enable jPOS ISO send/receive logging
        org.jpos.util.Logger jposLogger = new org.jpos.util.Logger();
        jposLogger.addListener(new SimpleLogListener(System.out));
        channel.setLogger(jposLogger, "zimswitch-nac");
        if (packager instanceof LogSource) {
            ((LogSource) packager).setLogger(jposLogger, "zimswitch-packager");
        }
        return channel;
    }

    @Bean
    public com.trian.zimswitch.simulator.iso.IsoMessageBuilder isoMessageBuilder(ISOPackager packager) {
        return new com.trian.zimswitch.simulator.iso.IsoMessageBuilder(packager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent e) {
        // Try initial connect once application starts
        ISOChannel channel = e.getApplicationContext().getBean(ISOChannel.class);
        try {
            synchronized (channel) {
                if (!channel.isConnected()) {
                    log.info("Connecting ISOChannel to {}:{} ...", host, port);
                    channel.connect();
                    log.info("ISOChannel connected");
                }
            }
        } catch (Exception ex) {
            // Do not crash app on failure; service will reconnect on demand
            log.warn("Initial ISOChannel connect failed: {}", ex.getMessage());
        }
    }
}
