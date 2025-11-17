package profect.group1.goormdotcom.common.log;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CachedHttpRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedHttpRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = readBody(request);
    }

    //원문 요청을 읽고 캐싱
    private byte[] readBody(HttpServletRequest request) throws IOException {
        try (InputStream inputStream = request.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] chunk = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    //캐싱된 본문을 InputStream으로 변환
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);

        return new ServletInputStream() {
            @Override
            public int read() {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Spring MVC 기반의 Blocking I/O 환경 -> 비동기 구현
            }
        };
    }

    public byte[] getCachedBody() {
        return this.cachedBody;
    }
}