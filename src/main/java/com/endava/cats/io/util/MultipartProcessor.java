package com.endava.cats.io.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.nio.charset.Charset;

/**
 * HTTP Multipart processor.
 */
public class MultipartProcessor {
    private final String boundary;
    private static final String LINE_BREAK = "\r\n";
    private final OutputStream outputStream;
    private final PrintWriter writer;

    /**
     * Constructs a new multipart body builder.
     */
    public MultipartProcessor(OutputStream outputStream, String boundary, Charset charset) {
        this.boundary = boundary;

        this.outputStream = outputStream;
        this.writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);
    }

    /**
     * Adds a form field to the multipart message.
     *
     * @param name  field name
     * @param value field value
     */
    public void addFormField(String name, String value) {
        writer.append("--").append(boundary).append(LINE_BREAK);
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(LINE_BREAK);
        writer.append(LINE_BREAK);
        writer.append(value).append(LINE_BREAK);
        writer.flush();
    }

    /**
     * Adds a file field to the multipart message, but takes in an InputStream instead of just a file
     * to read bytes from.
     *
     * @param name        Field name
     * @param fileName    Name of the "file" being uploaded.
     * @param inputStream Stream of bytes to use in place of a file.
     * @throws IOException Thrown when writing / reading from streams fails.
     */
    public void addFileField(String name, String fileName, InputStream inputStream)
            throws IOException {
        writer.append("--").append(boundary).append(LINE_BREAK);
        writer
                .append("Content-Disposition: form-data; name=\"")
                .append(name)
                .append("\"; filename=\"")
                .append(fileName)
                .append("\"")
                .append(LINE_BREAK);

        String probableContentType = URLConnection.guessContentTypeFromName(fileName);
        writer.append("Content-Type: ").append(probableContentType).append(LINE_BREAK);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_BREAK);
        writer.append(LINE_BREAK);
        writer.flush();

        streamToOutput(inputStream);

        writer.append(LINE_BREAK);
        writer.flush();
    }

    /**
     * Utility method to read all the bytes from an InputStream into the outputStream.
     *
     * @param inputStream Stream of bytes to read from.
     * @throws IOException Thrown on errors reading / writing.
     */
    private void streamToOutput(InputStream inputStream) throws IOException {
        try (inputStream) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    /**
     * Adds the final boundary to the multipart message and closes streams.
     */
    public void finish() throws IOException {
        writer.append("--").append(boundary).append("--").append(LINE_BREAK);
        writer.flush();
        writer.close();
        outputStream.flush();
        outputStream.close();
    }
}
