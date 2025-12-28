package com.trian.zimswitch.simulator.config;

import com.trian.zimswitch.simulator.server.PosIsoRequestListener;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOServer;
import org.jpos.iso.ServerChannel;
import org.jpos.iso.channel.NACChannel;
import org.jpos.iso.ISOUtil;
import org.jpos.util.Logger;
import org.jpos.util.SimpleLogListener;
import org.jpos.util.ThreadPool;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "acceptor.jpos.enabled", havingValue = "true", matchIfMissing = false)
public class IsoServerConfig {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(IsoServerConfig.class);

    @Value("${acceptor.port:6000}")
    private int listenPort;

    @Value("${acceptor.header:42}")
    private String headerHex;

    @Bean
    public ISOServer isoServer(ISOPackager packager, PosIsoRequestListener listener) throws Exception {
        // Build server-side NAC channel with TPDU header
        byte[] header = ISOUtil.hex2byte(headerHex);
        ServerChannel srvChannel = new NACChannel(packager, header);

        // jPOS logger to stdout for ISO traffic
        Logger jposLogger = new Logger();
        jposLogger.addListener(new SimpleLogListener(System.out));
       // srvChannel.setLogger(jposLogger, "zimswitch-acceptor-nac");

        ISOServer server = new ISOServer(listenPort, srvChannel, new ThreadPool(5, 50));
        server.setLogger(jposLogger, "zimswitch-acceptor");
        server.addISORequestListener(listener);

        log.info("Configured ISO8583 acceptor on port {} with header {}", listenPort, headerHex);
        return server;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startServer(ApplicationReadyEvent e) {
        ISOServer server = e.getApplicationContext().getBean(ISOServer.class);
        Thread t = new Thread(server, "iso-acceptor-" + listenPort);
        t.setDaemon(true);
        t.start();
        log.info("ISO8583 acceptor started on port {}", listenPort);
    }
}
