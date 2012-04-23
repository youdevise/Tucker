package com.timgroup.status;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class ApplicationReport {
    
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_COMPONENT = "component";
    private static final String TAG_VALUE = "value";
    private static final String TAG_EXCEPTION = "exception";
    private static final String TAG_TIMESTAMP = "timestamp";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_ID = "id";
    
    private final String applicationId;
    private final Map<Component, Report> componentReports;
    private final long timestamp;
    
    public ApplicationReport(String applicationId, Map<Component, Report> componentReports) {
        this.applicationId = applicationId;
        this.componentReports = componentReports;
        timestamp = System.currentTimeMillis();
    }
    
    public void render(Writer writer) throws IOException {
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter out = xmlOutputFactory.createXMLStreamWriter(writer);
            out.writeStartDocument();
            out.writeDTD(constructDTD(TAG_APPLICATION, StatusPage.DTD_FILENAME));
            out.writeProcessingInstruction("xml-stylesheet", "type=\"text/css\" href=\"" + StatusPage.CSS_FILENAME + "\"");
            
            out.writeStartElement(TAG_APPLICATION);
            out.writeAttribute(ATTR_ID, applicationId);
            Status applicationStatus = findApplicationStatus(componentReports);
            out.writeAttribute(ATTR_CLASS, applicationStatus.name().toLowerCase());
            
            for (Entry<Component, Report> componentReport : componentReports.entrySet()) {
                Component component = componentReport.getKey();
                Report report = componentReport.getValue();
                out.writeStartElement(TAG_COMPONENT);
                out.writeAttribute(ATTR_ID, component.getId());
                out.writeAttribute(ATTR_CLASS, report.getStatus().name().toLowerCase());
                out.writeCharacters(component.getLabel());
                if (report.hasValue()) {
                    out.writeCharacters(": ");
                    if (report.isSuccessful()) {
                        out.writeStartElement(TAG_VALUE);
                        out.writeCharacters(String.valueOf(report.getValue()));
                        out.writeEndElement();
                    } else {
                        out.writeStartElement(TAG_EXCEPTION);
                        out.writeCharacters(report.getException().getMessage());
                        out.writeEndElement();
                    }
                }
                out.writeEndElement();
            }
            
            out.writeStartElement(TAG_TIMESTAMP);
            out.writeCharacters(formatTime(timestamp));
            out.writeEndElement();
            
            out.writeEndElement();
            out.writeEndDocument();
            out.close();
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }
    
    private Status findApplicationStatus(Map<Component, Report> componentReports) {
        return Report.worstStatus(componentReports.values());
    }
    
    private String constructDTD(String rootElement, String systemID) {
        return "<!DOCTYPE " + rootElement + " SYSTEM \"" + systemID + "\">";
    }
    
    private String formatTime(long time) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(UTC);
        return df.format(time);
    }
    
}
