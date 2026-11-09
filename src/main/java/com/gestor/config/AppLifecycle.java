package com.gestor.config;

import com.gestor.service.ScheduledAlertService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class AppLifecycle implements ServletContextListener {
    private ScheduledExecutorService alertWorker;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ScheduledAlertService alerts = new ScheduledAlertService();
        alertWorker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "migestor-alertas");
            thread.setDaemon(true);
            return thread;
        });
        alertWorker.scheduleWithFixedDelay(() -> {
            try {
                alerts.scanDue();
            } catch (Exception error) {
                sce.getServletContext().log("No se pudieron revisar las alertas programadas", error);
            }
        }, 10, 60, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (alertWorker != null) {
            alertWorker.shutdownNow();
            try {
                alertWorker.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        Database.close();
    }
}
