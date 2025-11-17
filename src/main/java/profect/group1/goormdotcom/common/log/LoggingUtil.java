package profect.group1.goormdotcom.common.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class LoggingUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern SENSITIVE_PATTERN =
            Pattern.compile("(?i)\"(password|email|phone|ssn|accessToken|refreshToken|authorization)\"\\s*:\\s*\"(.*?)\"");

    private static final Pattern JWT_PATTERN =
            Pattern.compile("([A-Za-z0-9-_]{10,})\\.([A-Za-z0-9-_]{10,})\\.([A-Za-z0-9-_]{10,})");

    public static String toPrettyJson(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            Object json = objectMapper.readValue(body, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    public static Map<String, String> extractRequestParams(HttpServletRequest request) {
        Map<String, String> paramMap = new LinkedHashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String param = parameterNames.nextElement();
            paramMap.put(param, request.getParameter(param));
        }
        return paramMap;
    }

    public static String maskSensitiveFields(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        Matcher matcher = SENSITIVE_PATTERN.matcher(json);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "\"" + matcher.group(1) + "\": \"******\"");
        }
        matcher.appendTail(sb);
        String masked = sb.toString();

        masked = JWT_PATTERN.matcher(masked).replaceAll("******");

        return masked;
    }
}