package MADDGOOFAFF.catan.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import MADDGOOFAFF.catan.dao.Missatges;
import MADDGOOFAFF.catan.dto.JugadorInventari;
import MADDGOOFAFF.catan.repository.JugadorRepository;

@Service
public class JugadorService {

	private final JugadorRepository repository;

	public JugadorService(JugadorRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public ResponseEntity<?> recursos(int idJugador) {
		var jugador = repository.findById(idJugador);
		if (jugador.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.JUGADOR_NO_EXISTEIX + ": " + idJugador);
		}
		return ResponseEntity.ok(JugadorInventari.de(jugador.get()));
	}
}
