package com.atarashii.policyreport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@ConfigurationPropertiesScan
public class PolicyReportDemoApplication {
    @Value("${app.open-browser:false}")
    private boolean openBrowser;

    @Value("${server.port:8080}")
    private int serverPort;

    public static void main(String[] args) {
        SpringApplication.run(PolicyReportDemoApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserWhenReady() {
        if (!openBrowser || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + serverPort + "/"));
        } catch (Exception ignored) {
        }
    }
}