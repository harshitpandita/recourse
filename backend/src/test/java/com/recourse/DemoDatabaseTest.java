package com.recourse;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the split between demo data and real data. */
class DemoDatabaseTest {

    private static String url(String file) throws IOException {
        Properties p = PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
        return p.getProperty("spring.datasource.url");
    }

    @Test
    void demoProfileUsesItsOwnDatabaseFile() throws IOException {
        String demo = url("application-demo.properties");
        String real = url("application.properties");
        assertTrue(demo.startsWith("jdbc:h2:file:./data/demo/"), demo);
        assertNotEquals(real, demo);
        assertFalse(real.contains("/demo/"), real);
    }
}
