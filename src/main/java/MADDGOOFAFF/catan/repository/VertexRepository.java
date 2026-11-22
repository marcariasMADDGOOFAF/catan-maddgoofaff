package MADDGOOFAFF.catan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import MADDGOOFAFF.catan.model.Jugador;
import MADDGOOFAFF.catan.model.Vertex;

public interface VertexRepository extends JpaRepository<Vertex, Integer> {

}
