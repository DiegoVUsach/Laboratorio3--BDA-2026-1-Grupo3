package usach.cl.laboratorio1.tablas;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "usuarios")
public class Usuario {
    @Id
    private Integer idUsuario;
    private String nombreUsuario;
    private String password;
    private String rol;
}