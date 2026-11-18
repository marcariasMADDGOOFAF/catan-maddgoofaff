package MADDGOOFAFF.catan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import MADDGOOFAFF.catan.model.Partida;

public interface PartidaRepository extends JpaRepository<Partida, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Partida p where p.id = :id")
	Optional<Partida> findByIdForUpdate(@Param("id") Integer id);
}
