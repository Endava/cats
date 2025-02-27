package com.endava.cats.report;

import java.io.FileNotFoundException;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

public class ReportExporterPDF {
    public void exportReportToPDF(String reportContent, String outputPath) {
        try {
            // Initialize PDF writer
            PdfWriter writer = new PdfWriter(outputPath);

            // Initialize PDF document
            PdfDocument pdf = new PdfDocument(writer);

            // Initialize document
            Document document = new Document(pdf);

            // Add content to the document
            document.add(new Paragraph(reportContent));

            // Close document
            document.close();

            System.out.println("Report exported to PDF successfully.");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
