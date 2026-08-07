package usach.cl.laboratorio1.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain; // Nueva importación
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Nueva importación
import org.springframework.web.cors.CorsConfiguration; // Nueva importación
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String secretKey;

    // BCryptPasswordEncoder hashea las passwords con el algoritmo BCrypt,
    // que incluye un salt aleatorio y es resistente a ataques de fuerza bruta.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CAMBIO: Se vincula el bean corsConfigurationSource definido abajo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtFilter(secretKey),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // NUEVO: Definición de reglas de CORS para permitir al frontend
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Permite el origen de tu frontend (ajusta el puerto si usas otro como 5173 o 4200)
        // Agregamos los puertos comunes de desarrollo (5173 para Vite, 8080 para otros)
    configuration.setAllowedOrigins(List.of(
    "http://localhost:5173", 
    "http://127.0.0.1:5173", 
    "http://localhost:8080",
    "http://localhost"
    ));
        
        // Permite los métodos HTTP estándar
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Permite cabeceras necesarias para JWT y contenido JSON
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        
        // Permite que el navegador envíe credenciales (cookies/auth headers) si fuera necesario
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Aplica esta configuración a todas las rutas del API
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}