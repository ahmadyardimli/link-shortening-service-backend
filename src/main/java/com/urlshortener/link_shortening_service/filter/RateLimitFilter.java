package com.urlshortener.link_shortening_service.filter;

import com.urlshortener.link_shortening_service.exception.RateLimitExceededException;
import com.urlshortener.link_shortening_service.util.AuthUtil;
import com.urlshortener.link_shortening_service.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// this class is for putting limits for link shortens. User with no Auth will be limited but client IP
// and  authenticated users will be limited by userId. This is only for /shorten endpoint.
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    @Value("${ratelimit.window.seconds:3600}")
    private long windowSeconds;

    // per IP per window
    @Value("${ratelimit.anon.max:30}")
    private int anonymousUserMax;

    // per user per window
    @Value("${ratelimit.auth.max:500}")
    private int authUserMax;

    private static final class WindowCounter {
        volatile long windowStartEpochSec;
        volatile int count;
        WindowCounter(long start, int count) {
            this.windowStartEpochSec = start;
            this.count = count;
        }
    }

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // using servletPath to ignore any context path prefix
        return !(request.getMethod().equalsIgnoreCase("POST") && "/shorten".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        Long userId = AuthUtil.currentUserIdOrNull();
        String key = (userId != null) ? ("u:" + userId) : ("ip:" + ClientIpUtil.extractClientIp(request));
        int limit = (userId != null) ? authUserMax : anonymousUserMax;

        long currentSeconds = System.currentTimeMillis() / 1000;
        WindowCounter windowCounter = counters.compute(key, (k, old) -> {
            if (old == null) return new WindowCounter(currentSeconds, 1);
            if (currentSeconds - old.windowStartEpochSec >= windowSeconds) {
                return new WindowCounter(currentSeconds, 1); // reset window
            } else {
                if (old.count < Integer.MAX_VALUE) old.count++;
                return old;
            }
        });

        if (windowCounter.count > limit) {
            throw new RateLimitExceededException("Rate limit exceeded. Try again later.");
        }

        chain.doFilter(request, response);
    }
}