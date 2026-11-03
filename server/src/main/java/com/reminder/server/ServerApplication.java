package com.reminder.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.io.File;
import java.util.Arrays;

@SpringBootApplication
@Slf4j
public class ServerApplication {

    private final Environment environment;

    @Value("${server.port:8088}")
    private String serverPort;

    @Value("${spring.datasource.url:unknown}")
    private String datasourceUrl;

    public ServerApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        configureLoggingDirectory();
        SpringApplication.run(ServerApplication.class, args);
    }

    public static void configureLoggingDirectory() {
        String logDir = System.getenv("REMINDER_LOG_DIR");
        if (logDir == null || logDir.trim().isEmpty()) {
            logDir = System.getProperty("REMINDER_LOG_DIR");
        }
        if (logDir == null || logDir.trim().isEmpty()) {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                logDir = "M:\\ReminderServer\\logs";
            } else {
                logDir = "/opt/reminder/logs";
            }
        }

        try {
            File dir = new File(logDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    System.err.println("Could not create log directory '" + logDir + "'. Falling back to './logs'.");
                    logDir = "./logs";
                    new File(logDir).mkdirs();
                }
            }
        } catch (Exception e) {
            System.err.println("Error resolving log directory '" + logDir + "': " + e.getMessage() + ". Falling back to './logs'.");
            logDir = "./logs";
            new File(logDir).mkdirs();
        }

        System.setProperty("REMINDER_LOG_DIR", logDir);
    }

    @PostConstruct
    public void onStartup() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profilesStr = activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "default";
        String sanitizedDbUrl = sanitizeJdbcUrl(datasourceUrl);
        String logDir = System.getProperty("REMINDER_LOG_DIR", "logs");

        log.info("==================================================================");
        log.info("ReminderServer Application Started");
        log.info("Active Profiles     : {}", profilesStr);
        log.info("Port                : {}", serverPort);
        log.info("Database URL        : {}", sanitizedDbUrl);
        log.info("Log Directory       : {}", logDir);
        log.info("Primary Log File    : {}/reminder-server.log", logDir);
        log.info("JVM Version         : {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"));
        log.info("OS                  : {} {} ({})", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        log.info("==================================================================");
    }

    @PreDestroy
    public void onShutdown() {
        log.info("ReminderServer Application is shutting down gracefully.");
    }

    private String sanitizeJdbcUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("(?i)(password=)[^&;]*", "$1*****");
    }
}
