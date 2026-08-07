package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.Usuario;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.util.List;

@Repository
public class UsuarioRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    public List<Usuario> findAll() {
        return mongoTemplate.findAll(Usuario.class);
    }

    public Usuario findById(Integer id) {
        return mongoTemplate.findById(id, Usuario.class);
    }

    public Usuario findByUsername(String username) {
        Query query = new Query(Criteria.where("nombreUsuario").is(username));
        return mongoTemplate.findOne(query, Usuario.class);
    }

    public int save(Usuario entity) {
        if (entity.getIdUsuario() == null) {
            entity.setIdUsuario(sequenceGeneratorService.generateSequence("usuarioId"));
        }
        if (entity.getRol() == null) {
            entity.setRol("USER");
        }
        mongoTemplate.save(entity);
        return 1;
    }

    public int update(Usuario entity) {
        mongoTemplate.save(entity);
        return 1;
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("idUsuario").is(id));
        mongoTemplate.remove(query, Usuario.class);
        return 1;
    }
}