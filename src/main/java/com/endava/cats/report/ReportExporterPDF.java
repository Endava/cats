package com.endava.cats.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReportExporterPDF {
    public void exportReportToPDF(String htmlFilePath, String outputPath) {
        try (FileInputStream htmlFileStream = new FileInputStream(new File(htmlFilePath));
             FileOutputStream pdfFileStream = new FileOutputStream(new File(outputPath))) {

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withFile(new File(htmlFilePath));
            builder.toStream(pdfFileStream);
            builder.run();

            System.out.println("Report exported to PDF successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
