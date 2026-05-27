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
public class StoreContextFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader("X-Store-Id");

    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = ((HttpServletRequest) request).getHeader("X-Store-Id");

        if (header == null) {
            chain.doFilter(request, response);
            return;
        }

        int storeId;
        try {
            storeId = Integer.parseInt(header.strip());
        } catch (NumberFormatException _) {
            log.warn("Invalid X-Store-Id header: '{}'", header.replaceAll("[\r\n]", "_"));
            sendError(response, "INVALID_STORE_ID");
            return;
        }

        if (storeId <= 0) {
            log.warn("Non-positive X-Store-Id header: {}", storeId);
            sendError(response, "INVALID_STORE_ID");
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

    private void sendError(ServletResponse response, String errorCode) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(httpResponse.getWriter(), ErrorResponse.of(400, errorCode));
    }
}
