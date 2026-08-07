package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.Notificacion;

import java.util.*;

@Repository
public class NotificacionRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<Map<String, Object>> misNotificaciones(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje))
                .with(Sort.by(Sort.Direction.DESC, "fecha"));
        List<Notificacion> list = mongoTemplate.find(query, Notificacion.class);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Notificacion n : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id_notificacion", n.getIdNotificacion());
            map.put("tipo", n.getTipo());
            map.put("mensaje", n.getMensaje());
            map.put("leida", n.getLeida());
            map.put("fecha", java.sql.Timestamp.valueOf(n.getFecha()));
            res.add(map);
        }
        return res;
    }

    public Integer contarNoLeidas(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje).and("leida").is(false));
        return (int) mongoTemplate.count(query, Notificacion.class);
    }

    public void marcarTodasLeidas(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje));
        Update update = new Update().set("leida", true);
        mongoTemplate.updateMulti(query, update, Notificacion.class);
    }

    public void marcarLeida(Integer idNotificacion) {
        Query query = new Query(Criteria.where("idNotificacion").is(idNotificacion));
        Update update = new Update().set("leida", true);
        mongoTemplate.updateFirst(query, update, Notificacion.class);
    }
}
