package id.my.mfikriproject.widuri.erp.core;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.dto.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class StoreContextFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String header = httpRequest.getHeader("X-Store-Id");
        boolean isApiPath = httpRequest.getRequestURI().startsWith("/api/");

        if (header == null) {
            if (isApiPath) {
                log.warn("Missing X-Store-Id header on API path: {}", httpRequest.getRequestURI());
                sendError(response, "MISSING_STORE_ID", "X-Store-Id header is required for API endpoints");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        int storeId;
        try {
            storeId = Integer.parseInt(header);
        } catch (NumberFormatException _) {
            log.warn("Invalid X-Store-Id header: '{}'", header);
            sendError(response, "INVALID_STORE_ID", "X-Store-Id must be a valid integer");
            return;
        }

        if (storeId <= 0) {
            log.warn("Non-positive X-Store-Id header: {}", storeId);
            sendError(response, "INVALID_STORE_ID", "X-Store-Id must be a positive integer");
            return;
        }

        try {
            ScopedValue.where(StoreContext.STORE_ID, storeId).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Store context propagation failed", e);
        }
    }

    private void sendError(ServletResponse response, String code, String message) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(httpResponse.getWriter(), ErrorResponse.of(code, message));
    }
}
