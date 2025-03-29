package com.reeco.bas.transport.utils.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Generate a unique request ID and add to MDC for distributed tracing
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        // Wrap request and response to cache content
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        // Log the request
        log.info("Request: {} {} ({})", request.getMethod(), request.getRequestURI(), requestId);

        try {
            // Execute the request
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Log the response
            log.info("Response: {} {} completed in {}ms with status {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    executionTime,
                    responseWrapper.getStatus());

            // Only log detailed request/response for API calls and in debug mode
            if (log.isDebugEnabled() && request.getRequestURI().startsWith("/api")) {
                logRequestDetails(requestWrapper);
                logResponseDetails(responseWrapper);
            }

            // Copy content to the original response
            responseWrapper.copyBodyToResponse();

            // Clear MDC context
            MDC.remove("requestId");
        }
    }

    private void logRequestDetails(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            String contentString = new String(content, StandardCharsets.UTF_8);
            log.debug("Request payload: {}", contentString);
        }
    }

    private void logResponseDetails(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            String contentString = new String(content, StandardCharsets.UTF_8);
            // Truncate very large responses
            if (contentString.length() > 1000) {
                contentString = contentString.substring(0, 1000) + "... [truncated]";
            }
            log.debug("Response payload: {}", contentString);
        }
    }
}