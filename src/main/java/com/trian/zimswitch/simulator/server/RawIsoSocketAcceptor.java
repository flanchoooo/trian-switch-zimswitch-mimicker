package com.trian.zimswitch.simulator.server;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple ISO8583 server using plain ServerSocket with length-prefix framing
 * and optional 1-byte TPDU header strip/append, inspired by the user's example.
 */
@Component
public class RawIsoSocketAcceptor implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(RawIsoSocketAcceptor.class);

    private final ISOPackager packager;

    @Value("${acceptor.socket.enabled:true}")
    private boolean enabled;

    @Value("${acceptor.socket.port:6000}")
    private int port;

    // Hex string, e.g. "00" or "42". Appended to responses if not blank
    @Value("${acceptor.socket.header:00}")
    private String headerHex;

    // If true, strip the first byte after the 2-byte length (typical for NAC)
    @Value("${acceptor.socket.strip-first-byte:true}")
    private boolean stripFirstByte;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket server;

    public RawIsoSocketAcceptor(ISOPackager packager) {
        this.packager = packager;
    }

    @Override
    public void start() {
        if (!enabled || running.get()) return;
        try {
            server = new ServerSocket();
            // Bind on all interfaces
            server.bind(new InetSocketAddress("0.0.0.0", port));
            running.set(true);
            log.info("Raw ISO acceptor listening on {} (stripFirstByte={}, header={})", port, stripFirstByte, headerHex);
            pool.submit(this::acceptLoop);
        } catch (IOException e) {
            log.warn("Failed to start raw ISO acceptor on {}: {}", port, e.getMessage());
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = server.accept();
                pool.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    log.warn("Accept error: {}", e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        log.info("POS connected: {}", socket.getRemoteSocketAddress());
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            int length = in.readUnsignedShort();
            byte[] data = new byte[length];
            in.readFully(data);

            byte[] payload = data;
            if (stripFirstByte && data.length > 0) {
                payload = Arrays.copyOfRange(data, 1, data.length);
            }

            ISOMsg req = new ISOMsg();
            req.setPackager(packager);
            req.unpack(payload);

            // Build response
            ISOMsg resp = buildResponse(req);

            byte[] respPacked = resp.pack();
            byte[] header = (headerHex != null && !headerHex.isBlank()) ? ISOUtil.hex2byte(headerHex) : new byte[0];
            byte[] finalResp = new byte[header.length + respPacked.length];
            System.arraycopy(header, 0, finalResp, 0, header.length);
            System.arraycopy(respPacked, 0, finalResp, header.length, respPacked.length);

            out.writeShort(finalResp.length);
            out.write(finalResp);
            out.flush();
        } catch (Exception e) {
            log.warn("Client error: {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
            log.info("POS disconnected: {}", socket.getRemoteSocketAddress());
        }
    }

    private static ISOMsg buildResponse(ISOMsg req) throws Exception {
        String mti = req.getMTI();
        ISOMsg resp;
        if ("1420".equals(mti) || "0420".equals(mti)) {
            resp = (ISOMsg) req.clone();
            // Respond to reversal; keep MTI as-is or set response MTI depending on your switch
            // Here we keep MTI and set 39 indicating processed
            if (!resp.hasField(39)) resp.set(39, "00");
        } else if ("0800".equals(mti) || "1200".equals(mti)) {
            resp = (ISOMsg) req.clone();
            resp.setResponseMTI();
            resp.set(39, "00");
        } else {
            resp = new ISOMsg();
            resp.setPackager(req.getPackager());
            resp.setMTI("1814");
            resp.set(39, "96");
        }
        return resp;
    }

    @Override
    public void stop() {
        running.set(false);
        try { if (server != null) server.close(); } catch (IOException ignore) {}
        log.info("Raw ISO acceptor stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}

