package com.timgroup.tucker.info.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.timgroup.tucker.info.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.timgroup.tucker.info.Component;
import com.timgroup.tucker.info.Report;
import com.timgroup.tucker.info.component.VersionComponent;

public class StatusPageGenerator {
    
    public static final String DTD_FILENAME = "status-page.dtd";
    public static final String CSS_FILENAME = "status-page.css";

    public static final String COMPONENT_STATUS_FORMAT = "{\"eventType\": \"ComponentStatus\", "
            +" \"event\": {\"id\": \"{}\", \"label\": \"{}\", \"status\": \"{}\", \"value\": \"{}\"}}";

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusPageGenerator.class);
    
    private final String applicationId;
    private final VersionComponent versionComponent;
    private final List<Component> components = new ArrayList<Component>();
    
    public StatusPageGenerator(String applicationId, VersionComponent versionComponent) {
        this.applicationId = applicationId;
        this.versionComponent = versionComponent;
        components.add(versionComponent);
    }
    
    public synchronized void addComponent(Component component) {
        components.add(component);
    }
    
    public StatusPage getApplicationReport() {
        List<Component> snapshot = componentsSnapshot();
        Map<Component, Report> componentReports = new LinkedHashMap<Component, Report>(snapshot.size());
        for (Component component : snapshot) {
            Report report;
            try {
                report = component.getReport();
            } catch (Throwable e) {
                LOGGER.error("exception getting report from component {}", component.getId(), e);
                report = new Report(e);
            }

            if (Status.CRITICAL.equals(report.getStatus()) || Status.WARNING.equals(report.getStatus())) {
                LOGGER.info(COMPONENT_STATUS_FORMAT, component.getId(), component.getLabel(), report.getStatus(), report.getValue());
            }

            componentReports.put(component, report);
        }
        return new StatusPage(applicationId, componentReports);
    }

    public Component getVersionComponent() {
        return versionComponent;
    }

    private synchronized List<Component> componentsSnapshot() {
        return new ArrayList<Component>(components);
    }
}
