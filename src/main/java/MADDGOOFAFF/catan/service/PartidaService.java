package MADDGOOFAFF.catan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;

import MADDGOOFAFF.catan.dao.Missatges;
import MADDGOOFAFF.catan.dto.HexagonTauler;
import MADDGOOFAFF.catan.model.Hexagon;
import MADDGOOFAFF.catan.model.Partida;
import MADDGOOFAFF.catan.model.*;
import MADDGOOFAFF.catan.repository.HexagonRepository;
import MADDGOOFAFF.catan.repository.PartidaRepository;
import jakarta.persistence.EntityManager;


@Service
public class PartidaService {

	@Autowired
	private PartidaRepository repository;

	@Autowired
	private HexagonRepository hRepository;

	@Autowired
	private EntityManager entityManager;


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

	public record RecursosJugador(int idJugador, String nom, Map<TipusRecurs, Integer> recursos, int descartats) {}
	public record ResultatDaus(int dau1, int dau2, int tirada, boolean lladrePendent, List<RecursosJugador> jugadors) {}

	@Transactional
	public ResponseEntity<?> daus(int idPartida) {
		Optional<Partida> resultat = repository.findByIdForUpdate(idPartida);
		if (resultat.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.PARTIDA_NO_EXISTEIX + ": " + idPartida);
		}
		Partida partida = resultat.get();
		if (partida.getEstat() == EstatPartida.FINALITZADA) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.PARTIDA_FINALITZADA);
		}
		if (partida.getEstat() != EstatPartida.EN_CURS) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.PARTIDA_NO_EN_CURS);
		}
		if (partida.getActiu() == null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.JUGADOR_NO_ACTIU);
		}
		if (partida.isDausTirats()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.DAUS_TIRATS);
		}
		if (partida.isLladrePendent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.LLADRE_PENDENT);
		}

		int dau1 = ThreadLocalRandom.current().nextInt(1, 7);
		int dau2 = ThreadLocalRandom.current().nextInt(1, 7);
		int tirada = dau1 + dau2;
		partida.setUltimaTirada(tirada);
		partida.setDausTirats(true);
		Map<Integer, Map<TipusRecurs, Integer>> rebuts = new LinkedHashMap<>();
		for (Jugador jugador : partida.getJugadors()) {
			rebuts.put(jugador.getId(), new EnumMap<>(TipusRecurs.class));
		}
		Map<Integer, Integer> descartats = new LinkedHashMap<>();
		if (tirada == 7) {
			partida.setLladrePendent(true);
			for (Jugador jugador : partida.getJugadors()) {
				List<RecursJugador> unitats = new ArrayList<>();
				for (RecursJugador recurs : jugador.getInventari()) {
					for (int i = 0; i < recurs.getQuantitat(); i++) unitats.add(recurs);
				}
				int quantitat = unitats.size() > 7 ? unitats.size() / 2 : 0;
				Collections.shuffle(unitats);
				for (int i = 0; i < quantitat; i++) {
					RecursJugador recurs = unitats.get(i);
					recurs.setQuantitat(recurs.getQuantitat() - 1);
				}
				descartats.put(jugador.getId(), quantitat);
			}
		} else {
			for (Hexagon hexagon : hRepository.taulerPerPartida(idPartida)) {
				if (!Integer.valueOf(tirada).equals(hexagon.getNumero()) ||
						(partida.getLladre() != null && partida.getLladre().getId().equals(hexagon.getId()))) continue;
				TipusRecurs tipus = switch (hexagon.getTerreny()) {
					case BOSC -> TipusRecurs.FUSTA;
					case TURONS -> TipusRecurs.MAO;
					case PASTURES -> TipusRecurs.LLANA;
					case CAMPS -> TipusRecurs.BLAT;
					case MUNTANYES -> TipusRecurs.MINERAL;
					case DESERT -> null;
				};
				if (tipus == null) continue;
				for (HexagonVertex vertex : hexagon.getHexagonVertices()) {
					Assentament assentament = vertex.getVertex().getAssentament();
					if (assentament == null) continue;
					Jugador jugador = assentament.getPropietari();
					Map<TipusRecurs, Integer> recursos = rebuts.get(jugador.getId());
					if (recursos == null) continue;
					int quantitat = Hibernate.getClass(assentament) == Ciutat.class ? 2 : 1;
					RecursJugador recurs = jugador.getInventari().stream()
							.filter(r -> r.getTipus() == tipus).findFirst().orElse(null);
					if (recurs == null) {
						recurs = new RecursJugador(tipus, 0, jugador);
						jugador.getInventari().add(recurs);
						entityManager.persist(recurs);
					}
					recurs.setQuantitat(recurs.getQuantitat() + quantitat);
					recursos.merge(tipus, quantitat, Integer::sum);
				}
			}
		}
		List<RecursosJugador> jugadors = partida.getJugadors().stream().map(j -> new RecursosJugador(j.getId(), j.getNom(), rebuts.get(j.getId()), descartats.getOrDefault(j.getId(), 0))).toList();
		return ResponseEntity.ok(new ResultatDaus(dau1, dau2, tirada, partida.isLladrePendent(), jugadors));
	}

}
