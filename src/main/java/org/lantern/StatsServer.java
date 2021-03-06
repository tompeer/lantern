package org.lantern;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that serves JSON stats over REST.
 */
public class StatsServer {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final ExecutorService service = 
        Executors.newCachedThreadPool(new ThreadFactory() {
            
            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = new Thread(r, "Stats-Client-Thread");
                t.setDaemon(true);
                return t;
            }
        });
    
    public void serve() {
        service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final ServerSocket server = new ServerSocket();
                    server.bind(new InetSocketAddress("127.0.0.1", 7878));
                    while (true) {
                        final Socket sock = server.accept();
                        processSocket(sock);
                    }
                } catch (final IOException e) {
                    log.error("Could not run stats server?", e);
                }
            }
        });
    }

    private void processSocket(final Socket sock) {
        service.execute(new Runnable() {

            @Override
            public void run() {
                log.info("Got socket!!");
                try {
                    final InputStream is = sock.getInputStream();
                    final BufferedReader br = 
                        new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String cur = br.readLine();
                    final String requestLine = cur;
                    if (StringUtils.isBlank(cur)) {
                        log.info("Closing blank request socket");
                        IOUtils.closeQuietly(sock);
                        return;
                    } 
                    
                    
                    while (StringUtils.isNotBlank(cur)) {
                        log.info(cur);
                        cur = br.readLine();
                    }
                    log.info("Read all headers...");

                    final String json;
                    if (requestLine.startsWith("GET /stats")) {
                        json = LanternHub.statsTracker().toJson();
                    } else if (requestLine.startsWith("GET /oni")) {
                        json = LanternHub.statsTracker().oniJson();
                    } else if (requestLine.startsWith("GET /country/")) {
                        final String country;
                        if (requestLine.contains("?")) {
                            country = StringUtils.substringBetween(requestLine, "/country/", "?");
                        } else {
                            country = StringUtils.substringBetween(requestLine, "/country/", "HTTP");
                        }
                            
                        json = LanternHub.statsTracker().countryData(country);
                    } else if (requestLine.startsWith("GET /googleContentRemovalProductReason")) {
                        json = LanternHub.statsTracker().googleContentRemovalProductReason();
                    } else if (requestLine.startsWith("GET /googleContentRemovalRequests")) {
                        json = LanternHub.statsTracker().googleContentRemovalRequests();
                    } else if (requestLine.startsWith("GET /googleUserRequests")) {
                        json = LanternHub.statsTracker().googleUserRequests();
                    } else if (requestLine.startsWith("GET /googleRemovalByProductRequests")) {
                        json = LanternHub.statsTracker().googleRemovalByProductRequests();
                    } else {
                        json = "";
                    }
                    final String wrapped = wrapInCallback(requestLine, json);
                    final String ct;
                    if (json.equals(wrapped)) {
                        ct = "application/json";
                    } else {
                        ct = "text/javascript";
                    }
                    OutputStream os = sock.getOutputStream();
                    final String response = 
                        "HTTP/1.1 200 OK\r\n"+
                        "Content-Type: "+ct+"\r\n"+
                        "Connection: close\r\n"+
                        "Content-Length: "+wrapped.length()+"\r\n"+
                        "\r\n"+
                        wrapped;
                    os.write(response.getBytes("UTF-8"));
                } catch (final IOException e) {
                    log.info("Exception serving stats!", e);
                } finally {
                    IOUtils.closeQuietly(sock);
                }
            }
            
        });
    }

    
    protected String wrapInCallback(final String rl, final String json) {
        if (!rl.contains("callback=")) {
            return json;
        }
        final String requestLine = 
            StringUtils.substringBefore(rl, "HTTP").trim();
        final String callback;
        final String cb = 
            StringUtils.substringAfter(requestLine, "callback=");
        if (StringUtils.isBlank(cb)) {
            return json;
        }
        if (cb.contains("&")) {
            callback = StringUtils.substringBefore(cb, "&");
        } else {
            callback = cb;
        }
        return callback + "("+json+")";
    }
}
