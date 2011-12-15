package org.lantern;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultWhitelist implements Whitelist {

    private final Logger LOG = LoggerFactory.getLogger(DefaultWhitelist.class);
    
    private final String WHITELIST_NAME = "whitelist.txt";
    private final String REPORTED_WHITELIST_NAME = 
        "reportedWhitelist.txt";
    
    private final File WHITELIST_FILE = 
        new File(LanternUtils.configDir(), WHITELIST_NAME);
    private final File REPORTED_WHITELIST_FILE = 
        new File(LanternUtils.configDir(), REPORTED_WHITELIST_NAME);

    {
        buildWhitelists();
    }    
    
    private Collection<WhitelistEntry> whitelist;
    private Collection<WhitelistEntry> lastReportedWhitelist;
    

    public void reset() {
        WHITELIST_FILE.delete();
        REPORTED_WHITELIST_FILE.delete();
        buildWhitelists();
    }
    
    private void buildWhitelists() {
        final File original = new File(WHITELIST_NAME);
        if (!WHITELIST_FILE.isFile() || 
            FileUtils.isFileNewer(original, WHITELIST_FILE)) {
            try {
                FileUtils.copyFile(original, WHITELIST_FILE);
            } catch (final IOException e) {
                LOG.error("Could not copy original whitelist?", e);
            }
        }
        if (!REPORTED_WHITELIST_FILE.isFile()) {
            try {
                FileUtils.copyFile(original, REPORTED_WHITELIST_FILE);
            } catch (final IOException e) {
                LOG.error("Could not create reported whitelist file?", e);
            }
        }
        refreshFromFiles();
        whitelist.add(new WhitelistEntry("getlantern.org", true));
        whitelist.add(new WhitelistEntry("getexceptional.com", true));
    }

    private void refreshFromFiles() {
        whitelist = buildWhitelist(WHITELIST_FILE);
        lastReportedWhitelist = buildWhitelist(REPORTED_WHITELIST_FILE);
    }

    public boolean isWhitelisted(final String uri,
        final Collection<WhitelistEntry> wl) {
        final String toMatch = toBaseUri(uri);
        return wl.contains(new WhitelistEntry(toMatch));
    }
    
    public String toBaseUri(final String uri) {
        LOG.info("Parsing full URI: {}", uri);
        final String afterHttp;
        if (!uri.startsWith("http")) {
            afterHttp = uri;
        } else {
            afterHttp = StringUtils.substringAfter(uri, "://");
        }
        final String base;
        if (afterHttp.contains("/")) {
            base = StringUtils.substringBefore(afterHttp, "/");
        } else {
            base = afterHttp;
        }
        String domainExtension = StringUtils.substringAfterLast(base, ".");
        
        // Make sure we strip alternative ports, like 443.
        if (domainExtension.contains(":")) {
            domainExtension = StringUtils.substringBefore(domainExtension, ":");
        }
        final String domain = StringUtils.substringBeforeLast(base, ".");
        final String toMatchBase;
        if (domain.contains(".")) {
            toMatchBase = StringUtils.substringAfterLast(domain, ".");
        } else {
            toMatchBase = domain;
        }
        final String toMatch = toMatchBase + "." + domainExtension;
        LOG.info("Matching against: {}", toMatch);
        return toMatch;
    }
    
    /**
     * Decides whether or not the specified full URI matches domains for our
     * whitelist.
     * 
     * @return <code>true</code> if the specified domain matches domains for
     * our whitelist, otherwise false.
     */
    public boolean isWhitelisted(final String uri) {
        LOG.info("Parsing full URI: {}", uri);
        return isWhitelisted(uri, whitelist);
    }
    
    public boolean isWhitelisted(final HttpRequest request) {
        LOG.info("Checking whitelist for request");
        final String uri = request.getUri();
        LOG.info("URI is: {}", uri);

        final String referer = request.getHeader("referer");
        
        final String uriToCheck;
        LOG.info("Referer: "+referer);
        if (!StringUtils.isBlank(referer)) {
            uriToCheck = referer;
        } else {
            uriToCheck = uri;
        }

        return isWhitelisted(uriToCheck);
    }
    
    public void addEntry(final String entry) {
        whitelist.add(new WhitelistEntry(entry));
        write(whitelist, WHITELIST_FILE);
    }
    
    private void write(final Collection<WhitelistEntry> entries, 
        final File file) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file));
            for (final WhitelistEntry entry: entries) {
                bw.write(entry.getSite());
                bw.write("\n");
            }
        } catch (final IOException e) {
            LOG.error("Could not read file");
        } finally {
            IOUtils.closeQuietly(bw);
        }
    }

    public void removeEntry(final String entry) {
        whitelist.remove(new WhitelistEntry(entry));
        write(whitelist, WHITELIST_FILE);
    }
    
    public Collection<WhitelistEntry> getAdditions() {
        final Collection<WhitelistEntry> additions = 
            new LinkedHashSet<WhitelistEntry>();
        synchronized (whitelist) {
            synchronized (lastReportedWhitelist) {
                for (final WhitelistEntry entry : whitelist) {
                    if (!lastReportedWhitelist.contains(entry)) {
                        additions.add(entry);
                    }
                }
            }
        }
        return additions;
    }
    
    public Collection<WhitelistEntry> getRemovals() {
        final Collection<WhitelistEntry> removals = 
            new LinkedHashSet<WhitelistEntry>();
        synchronized (whitelist) {
            synchronized (lastReportedWhitelist) {
                for (final WhitelistEntry entry : lastReportedWhitelist) {
                    if (!whitelist.contains(entry)) {
                        removals.add(entry);
                    }
                }
            }
        }
        return removals;
    }
    
    public String getAdditionsAsJson() {
        return LanternUtils.jsonify(getAdditions());
    }

    public String getRemovalsAsJson() {
        return LanternUtils.jsonify(getRemovals());
    }
    
    private Collection<WhitelistEntry> buildWhitelist(final File file) {
        LOG.info("Processing whitelist file: {}", file);
        final Collection<WhitelistEntry> wl = new HashSet<WhitelistEntry>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String site = br.readLine();
            while (site != null) {
                site = site.trim();
                //LOG.info("Processing whitelist line: {}", site);
                if (StringUtils.isNotBlank(site)) {
                    // Ignore commented-out sites.
                    if (!site.startsWith("#")) {
                        wl.add(new WhitelistEntry(site));
                    }
                }
                site = br.readLine();
            }
        } catch (final FileNotFoundException e) {
            LOG.error("Could not find whitelist file!!", e);
        } catch (final IOException e) {
            LOG.error("Could not read whitelist file", e);
        } finally {
            IOUtils.closeQuietly(br);
        }
        return wl;
    }

    public void whitelistReported() {
        // We basically need to copy the current whitelist to be the last
        // reported whitelist.
        try {
            FileUtils.copyFile(WHITELIST_FILE, REPORTED_WHITELIST_FILE);
        } catch (final IOException e) {
            LOG.error("Could not copy whitelist file?");
        }
        refreshFromFiles();
    }
    
    public Collection<WhitelistEntry> getWhitelist() {
        synchronized (whitelist) {
            return new TreeSet<WhitelistEntry>(whitelist);
        }
    }
}