package usach.cl.laboratorio1.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Buscar el header "Authorization" en la peticion
        String authHeader = request.getHeader("Authorization");

        // 2. Si existe y empieza con "Bearer ", extraer el token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // quitar "Bearer "
            try {
                // 3. Decodificar el token usando la misma clave secreta
                //    con la que se firmo en JwtService.
                //    Si alguien modifico el token, la firma no coincide
                //    y lanza excepcion (cae al catch).
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                // 4. Extraer username y rol del token
                String username = claims.getSubject();
                String rol = claims.get("rol", String.class);

                if (username != null) {
                    // 5. Crear la "autenticacion" de Spring Security.
                    //    Le decimos a Spring: "este usuario esta autenticado
                    //    y tiene el rol ROLE_USER o ROLE_ADMIN".
                    //    Esto permite usar @PreAuthorize en los controllers.
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + (rol != null ? rol : "USER"))
                    );
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Token invalido, expirado o manipulado: limpiar contexto.
                // La peticion seguira pero sin autenticacion,
                // asi que Spring Security la rechazara con 401.
                SecurityContextHolder.clearContext();
            }
        }

        // 6. Pasar la peticion al siguiente filtro (o al controller).
        filterChain.doFilter(request, response);
    }
}