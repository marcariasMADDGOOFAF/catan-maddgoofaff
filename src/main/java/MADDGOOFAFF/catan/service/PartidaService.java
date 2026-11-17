package MADDGOOFAFF.catan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import MADDGOOFAFF.catan.dao.Missatges;
import MADDGOOFAFF.catan.dto.HexagonTauler;
import MADDGOOFAFF.catan.model.Hexagon;
import MADDGOOFAFF.catan.model.Partida;
import MADDGOOFAFF.catan.repository.HexagonRepository;
import MADDGOOFAFF.catan.repository.PartidaRepository;


@Service
public class PartidaService {

	@Autowired
	private PartidaRepository repository;

	@Autowired
	private HexagonRepository hRepository;


	@Transactional(readOnly = true)
	public ResponseEntity<?> tauler(int idPartida) {
		Optional<Partida> p = repository.findById(idPartida);
		if (!p.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Missatges.PARTIDA_NO_EXISTEIX + ": " + idPartida);
		}
		Partida partida = p.get();
		List<HexagonTauler> tauler = new ArrayList<HexagonTauler>();

		for (Hexagon h : hRepository.taulerPerPartida(idPartida)) {
			tauler.add(HexagonTauler.de(h, partida.getLladre()));
		}

		return ResponseEntity.ok(tauler);
	}

}
