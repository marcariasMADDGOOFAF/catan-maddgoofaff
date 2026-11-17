package MADDGOOFAFF.catan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import MADDGOOFAFF.catan.model.Hexagon;

public interface HexagonRepository extends JpaRepository<Hexagon, Integer> {

	List<Hexagon> taulerPerPartida(@Param("idPartida") Integer idPartida);

}
