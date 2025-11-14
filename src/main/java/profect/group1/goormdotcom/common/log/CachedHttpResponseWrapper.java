package profect.group1.goormdotcom.common.log;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class CachedHttpResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream cachedContent = new ByteArrayOutputStream();
    private PrintWriter cachedWriter;
    private ServletOutputStream cachedOutputStream;

    public CachedHttpResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (cachedWriter != null) {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }

        if (cachedOutputStream == null) {
            cachedOutputStream = new CachedServletOutputStream(cachedContent);
        }
        return cachedOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (cachedOutputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called on this response.");
        }

        if (cachedWriter == null) {
            cachedWriter = new PrintWriter(new OutputStreamWriter(cachedContent, StandardCharsets.UTF_8));
        }
        return cachedWriter;
    }

    public byte[] getCachedBody() {
        try {
            if (cachedWriter != null) {
                cachedWriter.flush();
            } else if (cachedOutputStream != null) {
                cachedOutputStream.flush();
            }
        } catch (IOException ignored) {
        }
        return cachedContent.toByteArray();
    }

    private static class CachedServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream buffer;

        private CachedServletOutputStream(ByteArrayOutputStream buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            // I/O의 비동기 처리X
        }
    }
}