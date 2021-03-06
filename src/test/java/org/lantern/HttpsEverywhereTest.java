package org.lantern;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.lantern.httpseverywhere.HttpsEverywhere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpsEverywhereTest {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Test public void testHttpsEverywhereRegex() throws Exception {
        final String[] urls = new String[] {
            "http://www.gmail.com/test",
            "http://news.google.com/news",
            "http://www.google.com.testing/",
            "http://www.balatarin.com/test",
            "http://www.facebook.com/testing?query=test",
            "http://www.flickr.com/newPicture.jpg",
            "http://www.google.com/",
            "http://www.twitter.com/",
            "http://platform.linkedin.com/",
        };
        
        final String[] expecteds = new String[] {
            "https://mail.google.com/test",
            "https://www.google.com/news",
             // This should be the same -- it should match the *target* for 
             // http://www.google.com.* but there is no relevant rule.
            "http://www.google.com.testing/", 
            "https://balatarin.com/test",
            "https://www.facebook.com/testing?query=test",
            "https://secure.flickr.com/newPicture.jpg",
            "https://encrypted.google.com/",
            "https://twitter.com/",
            "https://platform.linkedin.com/",
        };
        for (int i = 0; i < urls.length; i++) {
            final String request = urls[i];
            final String expected = expecteds[i];
            final String converted = LanternHub.httpsEverywhere().toHttps(request);
            log.info("Got converted: "+converted);
            assertEquals(expected, converted);
        }
        
        final String[] excluded = new String[] {
            "http://www.google.com/search?tbm=isch",
            "http://test.forums.wordpress.com/"
        };
        
        for (int i = 0; i < excluded.length; i++) {
            final String request = excluded[i];
            final String converted = LanternHub.httpsEverywhere().toHttps(request);
            log.info("Got converted: "+converted);
            assertEquals(request, converted);
        }
    }
}
