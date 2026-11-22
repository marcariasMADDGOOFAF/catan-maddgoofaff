package MADDGOOFAFF.catan.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import MADDGOOFAFF.catan.dao.Missatges;
import MADDGOOFAFF.catan.dto.JugadorInventari;
import MADDGOOFAFF.catan.model.*;
import MADDGOOFAFF.catan.repository.ArestaRepository;
import MADDGOOFAFF.catan.repository.JugadorRepository;
import MADDGOOFAFF.catan.repository.VertexRepository;
import jakarta.persistence.EntityManager;

@Service
public class JugadorService {

	private final JugadorRepository repository;
	private final ArestaRepository arestaRepository;
	private final VertexRepository vertexRepository;
	private final EntityManager entityManager;

	public JugadorService(JugadorRepository repository, ArestaRepository arestaRepository,
			VertexRepository vertexRepository, EntityManager entityManager) {
		this.repository = repository;
		this.arestaRepository = arestaRepository;
		this.vertexRepository = vertexRepository;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public ResponseEntity<?> recursos(int idJugador) {
		var jugador = repository.findById(idJugador);
		if (jugador.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.JUGADOR_NO_EXISTEIX + ": " + idJugador);
		}
		return ResponseEntity.ok(JugadorInventari.de(jugador.get()));
	}

	@Transactional
	public ResponseEntity<?> construir(int idJugador, String tipus, int posicio) {
		var resultat = repository.findById(idJugador);
		if (resultat.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.JUGADOR_NO_EXISTEIX + ": " + idJugador);
		}
		Jugador jugador = resultat.get();
		Partida partida = jugador.getPartida();
		if (partida.getEstat() != EstatPartida.EN_CURS) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.PARTIDA_NO_EN_CURS);
		}
		if (partida.getActiu() == null || !partida.getActiu().getId().equals(jugador.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.JUGADOR_NO_ACTIU);
		}
		if (!partida.isDausTirats()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.DAUS_NO_TIRATS);
		}

		if (tipus.equalsIgnoreCase("CARRETERA")) {
			return construirCarretera(jugador, partida, posicio);
		}
		if (tipus.equalsIgnoreCase("POBLAT")) {
			return construirPoblat(jugador, partida, posicio);
		}
		if (tipus.equalsIgnoreCase("CIUTAT")) {
			return construirCiutat(jugador, partida, posicio);
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Missatges.TIPUS_CONSTRUCCIO_INVALID);
	}

	private ResponseEntity<?> construirCarretera(Jugador jugador, Partida partida, int idAresta) {
		Aresta aresta = arestaRepository.findById(idAresta).orElse(null);
		if (aresta == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.ARESTA_NO_EXISTEIX + ": " + idAresta);
		}
		if (!aresta.getPartida().getId().equals(partida.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.ARESTA_ALTRA_PARTIDA);
		}
		if (aresta.getCarretera() != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.ARESTA_OCUPADA);
		}

		boolean connectada = false;
		List<Vertex> vertexs = new ArrayList<>();
		vertexs.add(aresta.getVertexA());
		vertexs.add(aresta.getVertexB());
		for (Vertex v : vertexs) {
			if (v.getAssentament() != null && v.getAssentament().getPropietari().getId().equals(jugador.getId())) {
				connectada = true;
			}
			List<Aresta> arestes = new ArrayList<>(v.getArestesA());
			arestes.addAll(v.getArestesB());
			for (Aresta a : arestes) {
				if (a.getCarretera() != null && a.getCarretera().getPropietari().getId().equals(jugador.getId())) {
					connectada = true;
				}
			}
		}
		if (!connectada) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.CARRETERA_NO_CONNECTADA);
		}
		int carreteres = 0;
		for (Construccio c : jugador.getConstruccions()) {
			if (c instanceof Carretera) {
				carreteres++;
			}
		}
		if (carreteres >= 15) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.LIMIT_CARRETERES);
		}

		RecursJugador fusta = null;
		RecursJugador mao = null;
		for (RecursJugador recurs : jugador.getInventari()) {
			if (recurs.getTipus() == TipusRecurs.FUSTA) {
				fusta = recurs;
			}
			if (recurs.getTipus() == TipusRecurs.MAO) {
				mao = recurs;
			}
		}
		if (fusta == null || fusta.getQuantitat() < 1 || mao == null || mao.getQuantitat() < 1) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.RECURSOS_INSUFICIENTS);
		}
		fusta.setQuantitat(fusta.getQuantitat() - 1);
		mao.setQuantitat(mao.getQuantitat() - 1);

		Carretera carretera = new Carretera();
		carretera.setPropietari(jugador);
		carretera.setAresta(aresta);
		jugador.getConstruccions().add(carretera);
		entityManager.persist(carretera);
		return ResponseEntity.ok(Missatges.CARRETERA_CONSTRUIDA);
	}

	private ResponseEntity<?> construirPoblat(Jugador jugador, Partida partida, int idVertex) {
		Vertex vertex = vertexRepository.findById(idVertex).orElse(null);
		if (vertex == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.VERTEX_NO_EXISTEIX + ": " + idVertex);
		}
		if (!vertex.getPartida().getId().equals(partida.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.VERTEX_ALTRA_PARTIDA);
		}
		if (vertex.getAssentament() != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.VERTEX_OCUPAT);
		}
		List<Aresta> arestes = new ArrayList<>(vertex.getArestesA());
		arestes.addAll(vertex.getArestesB());
		// Regla de la distancia: cap vertex veí pot tenir construccio
		for (Aresta a : arestes) {
			Vertex vei = a.getVertexA().getId() == vertex.getId() ? a.getVertexB() : a.getVertexA();
			if (vei.getAssentament() != null) {
				return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.DISTANCIA_NO_COMPLERTA);
			}
		}
		boolean connectat = false;
		for (Aresta a : arestes) {
			if (a.getCarretera() != null && a.getCarretera().getPropietari().getId().equals(jugador.getId())) {
				connectat = true;
			}
		}
		if (!connectat) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.POBLAT_NO_CONNECTAT);
		}
		int poblats = 0;
		for (Construccio c : jugador.getConstruccions()) {
			if (c instanceof Poblat) {
				poblats++;
			}
		}
		if (poblats >= 5) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.LIMIT_POBLATS);
		}

		RecursJugador fusta = null;
		RecursJugador mao = null;
		RecursJugador llana = null;
		RecursJugador blat = null;
		for (RecursJugador recurs : jugador.getInventari()) {
			if (recurs.getTipus() == TipusRecurs.FUSTA) {
				fusta = recurs;
			}
			if (recurs.getTipus() == TipusRecurs.MAO) {
				mao = recurs;
			}
			if (recurs.getTipus() == TipusRecurs.LLANA) {
				llana = recurs;
			}
			if (recurs.getTipus() == TipusRecurs.BLAT) {
				blat = recurs;
			}
		}
		if (fusta == null || fusta.getQuantitat() < 1 || mao == null || mao.getQuantitat() < 1
				|| llana == null || llana.getQuantitat() < 1 || blat == null || blat.getQuantitat() < 1) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.RECURSOS_INSUFICIENTS_POBLAT);
		}
		fusta.setQuantitat(fusta.getQuantitat() - 1);
		mao.setQuantitat(mao.getQuantitat() - 1);
		llana.setQuantitat(llana.getQuantitat() - 1);
		blat.setQuantitat(blat.getQuantitat() - 1);

		Poblat poblat = new Poblat();
		poblat.setPropietari(jugador);
		poblat.setVertex(vertex);
		jugador.getConstruccions().add(poblat);
		entityManager.persist(poblat);
		vertex.setAssentament(poblat);
		return ResponseEntity.ok(Missatges.POBLAT_CONSTRUIT);
	}

	private ResponseEntity<?> construirCiutat(Jugador jugador, Partida partida, int idVertex) {
		Vertex vertex = vertexRepository.findById(idVertex).orElse(null);
		if (vertex == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.VERTEX_NO_EXISTEIX + ": " + idVertex);
		}
		if (!vertex.getPartida().getId().equals(partida.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.VERTEX_ALTRA_PARTIDA);
		}
		Assentament poblat = vertex.getAssentament();
		if (!(poblat instanceof Poblat)
				|| !poblat.getPropietari().getId().equals(jugador.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.NO_POBLAT_PROPI);
		}
		int ciutats = 0;
		for (Construccio c : jugador.getConstruccions()) {
			if (c instanceof Ciutat) {
				ciutats++;
			}
		}
		if (ciutats >= 4) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.LIMIT_CIUTATS);
		}

		RecursJugador blat = null;
		RecursJugador mineral = null;
		for (RecursJugador recurs : jugador.getInventari()) {
			if (recurs.getTipus() == TipusRecurs.BLAT) {
				blat = recurs;
			}
			if (recurs.getTipus() == TipusRecurs.MINERAL) {
				mineral = recurs;
			}
		}
		if (blat == null || blat.getQuantitat() < 2 || mineral == null || mineral.getQuantitat() < 3) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.RECURSOS_INSUFICIENTS_CIUTAT);
		}
		blat.setQuantitat(blat.getQuantitat() - 2);
		mineral.setQuantitat(mineral.getQuantitat() - 3);

		jugador.getConstruccions().remove(poblat);
		vertex.setAssentament(null);
		entityManager.flush();
		Ciutat ciutat = new Ciutat();
		ciutat.setPropietari(jugador);
		ciutat.setVertex(vertex);
		jugador.getConstruccions().add(ciutat);
		entityManager.persist(ciutat);
		vertex.setAssentament(ciutat);
		return ResponseEntity.ok(Missatges.CIUTAT_CONSTRUIDA);
	}
}
