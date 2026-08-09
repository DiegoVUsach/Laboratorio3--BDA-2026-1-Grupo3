package usach.cl.laboratorio1.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// OncePerRequestFilter garantiza que este filtro se ejecuta
// UNA sola vez por cada peticion HTTP (no se repite en redirects).
// Es el "guardia de seguridad" de la API: revisa el token
// ANTES de que la peticion llegue al controller.
public class JwtFilter extends OncePerRequestFilter {

    private final String secretKey;

    public JwtFilter(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        System.out.println("====================================");
        System.out.println("JWT FILTER");
        System.out.println("URL: " + request.getRequestURI());
        System.out.println("AUTH HEADER: " + authHeader);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7);

            try {

                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(
                                Keys.hmacShaKeyFor(
                                        secretKey.getBytes(StandardCharsets.UTF_8)
                                )
                        )
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                String rol = claims.get("rol", String.class);

                System.out.println("JWT VALIDO");
                System.out.println("USERNAME: " + username);
                System.out.println("ROL: " + rol);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + (rol != null ? rol : "USER")
                        )
                );

                UsernamePasswordAuthenticationToken auth
                        = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities
                        );

                auth.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);

                System.out.println(
                        "AUTHENTICATION ESTABLECIDA: "
                        + SecurityContextHolder
                                .getContext()
                                .getAuthentication()
                );

            } catch (Exception e) {

                System.out.println("========== ERROR JWT ==========");
                System.out.println(e.getClass().getName());
                System.out.println(e.getMessage());

                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
