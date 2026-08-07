package usach.cl.laboratorio1.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

// Este servicio se encarga de CREAR tokens JWT.
// Un JWT tiene 3 partes: header.payload.firma
// - header: dice que algoritmo se uso (HS256)
// - payload: contiene los datos (username, rol, fecha expiracion)
// - firma: garantiza que nadie modifico el token
@Service
public class JwtService {

    // @Value lee el valor desde application.properties.
    // Esta es la clave secreta con la que se firma el token.
    // Si alguien no tiene esta clave, no puede falsificar tokens.
    @Value("${app.jwt.secret}")
    private String secretKey;

    // Crea un token para un usuario con su rol.
    // El token incluye:
    // - subject: el username (para saber QUIEN es)
    // - claim "rol": el rol del usuario (para saber QUE puede hacer)
    // - issuedAt: cuando se creo
    // - expiration: cuando expira (10 horas)
    public String createToken(String username, String rol) {
        return Jwts.builder()
                .setSubject(username)
                .claim("rol", rol)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }
}