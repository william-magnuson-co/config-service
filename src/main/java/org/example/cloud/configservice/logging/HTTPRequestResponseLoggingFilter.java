package org.example.cloud.configservice.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@ManagedResource
public class HTTPRequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final List<MediaType> VISIBLE_TYPES = Arrays.asList(
            MediaType.valueOf("text/*"),
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            MediaType.valueOf("application/*+json"),
            MediaType.valueOf("application/*+xml"),
            MediaType.MULTIPART_FORM_DATA
    );

    /**
     * List of HTTP headers whose values should not be logged.
     * "authorization",
     * "proxy-authorization"
     */
    private static final List<String> SENSITIVE_HEADERS = Arrays.asList(
            "authorization",
            "proxy-authorization"
    );

    private boolean enabled = true;

    private static void logRequestHeader(ContentCachingRequestWrapper request, String prefix, StringBuilder msg) {
        String queryString = request.getQueryString();
        if (queryString == null) {
            msg.append(String.format("%s %s %s", prefix, request.getMethod(), request.getRequestURI())).append("\n");
        } else {
            msg.append(String.format("%s %s %s?%s", prefix, request.getMethod(), request.getRequestURI(), queryString)).append("\n");
        }
        if (log.isTraceEnabled()) {
            Collections.list(request.getHeaderNames())
                    .forEach(headerName ->
                            Collections.list(request.getHeaders(headerName))
                                    .forEach(headerValue -> {
                                        if (isSensitiveHeader(headerName)) {
                                            msg.append(String.format("%s %s: %s", prefix, headerName, "*******")).append("\n");
                                        } else {
                                            msg.append(String.format("%s %s: %s", prefix, headerName, headerValue)).append("\n");
                                        }
                                    }));
        }
        msg.append(prefix).append("\n");
    }

    private static void logRequestBody(ContentCachingRequestWrapper request, String prefix, StringBuilder msg) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            logContent(content, request.getContentType(), request.getCharacterEncoding(), prefix, msg);
        }
    }

    private static void logResponse(ContentCachingResponseWrapper response, String prefix, StringBuilder msg) {
        int status = response.getStatus();
        msg.append(String.format("%s %s %s", prefix, status, HttpStatus.valueOf(status).getReasonPhrase())).append("\n");
        if (log.isTraceEnabled()) {
            response.getHeaderNames()
                    .forEach(headerName ->
                            response.getHeaders(headerName)
                                    .forEach(headerValue ->
                                    {
                                        if (isSensitiveHeader(headerName)) {
                                            msg.append(String.format("%s %s: %s", prefix, headerName, "*******")).append("\n");
                                        } else {
                                            msg.append(String.format("%s %s: %s", prefix, headerName, headerValue)).append("\n");
                                        }
                                    }));
            msg.append(prefix).append("\n");
        }
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            logContent(content, response.getContentType(), response.getCharacterEncoding(), prefix, msg);
        }
    }

    private static void logContent(byte[] content, String contentType, String contentEncoding, String prefix, StringBuilder msg) {
        MediaType mediaType = MediaType.valueOf(contentType);
        boolean visible = VISIBLE_TYPES.stream().anyMatch(visibleType -> visibleType.includes(mediaType));
        if (visible) {
            try {
                String contentString = new String(content, contentEncoding);
                Stream.of(contentString.split("\r\n|\r|\n")).forEach(line -> msg.append(prefix).append(" ").append(line).append("\n"));
            } catch (UnsupportedEncodingException e) {
                msg.append(String.format("%s [%d bytes content]", prefix, content.length)).append("\n");
            }
        } else {
            msg.append(String.format("%s [%d bytes content]", prefix, content.length)).append("\n");
        }
    }

    /**
     * Determine if a given header name should have its value logged.
     *
     * @param headerName HTTP header name.
     * @return True if the header is sensitive (i.e. its value should <b>not</b> be logged).
     */
    private static boolean isSensitiveHeader(String headerName) {
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase());
    }

    private static ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            return (ContentCachingRequestWrapper) request;
        } else {
            return new ContentCachingRequestWrapper(request);
        }
    }

    private static ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper) {
            return (ContentCachingResponseWrapper) response;
        } else {
            return new ContentCachingResponseWrapper(response);
        }
    }

    @ManagedOperation(description = "Enable logging of HTTP requests and responses")
    public void enable() {
        this.enabled = true;
    }

    @ManagedOperation(description = "Disable logging of HTTP requests and responses")
    public void disable() {
        this.enabled = false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response);
        } else {
            doFilterWrapped(wrapRequest(request), wrapResponse(response), filterChain);
        }
    }

    protected void doFilterWrapped(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, FilterChain filterChain) throws ServletException, IOException {

        StringBuilder msg = new StringBuilder();

        try {
            beforeRequest(request, response, msg);
            filterChain.doFilter(request, response);
        } finally {
            afterRequest(request, response, msg);
            if (log.isDebugEnabled()) {
                log.info(msg.toString());
            }
            response.copyBodyToResponse();
        }
    }

    protected void beforeRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, StringBuilder msg) {
        if (log.isDebugEnabled()) {
            msg.append("HTTP Request received.\n-- REQUEST --\n");
            logRequestHeader(request, request.getRemoteAddr() + "|>", msg);
        }
    }

    protected void afterRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, StringBuilder msg) {
        if (log.isDebugEnabled()) {
            logRequestBody(request, request.getRemoteAddr() + "|>", msg);
            msg.append("\n-- RESPONSE --\n");
            logResponse(response, request.getRemoteAddr() + "|<", msg);
        }
    }
}
