package MADDGOOFAFF.catan.service;

import java.util.ArrayList;
import java.util.Comparator;
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
import MADDGOOFAFF.catan.repository.PartidaRepository;
import MADDGOOFAFF.catan.repository.VertexRepository;
import jakarta.persistence.EntityManager;

@Service
public class JugadorService {

	private final JugadorRepository repository;
	private final ArestaRepository arestaRepository;
	private final VertexRepository vertexRepository;
	private final PartidaRepository partidaRepository;
	private final EntityManager entityManager;

	public JugadorService(JugadorRepository repository, ArestaRepository arestaRepository,
			VertexRepository vertexRepository, PartidaRepository partidaRepository, EntityManager entityManager) {
		this.repository = repository;
		this.arestaRepository = arestaRepository;
		this.vertexRepository = vertexRepository;
		this.partidaRepository = partidaRepository;
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
	public ResponseEntity<String> passarTorn(int idJugador) {
		var resultat = partidaRepository.findByJugadorIdForUpdate(idJugador);
		if (resultat.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Missatges.JUGADOR_NO_EXISTEIX + ": " + idJugador);
		}
		Partida partida = resultat.get();
		if (partida.getEstat() == EstatPartida.FINALITZADA) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.PARTIDA_FINALITZADA);
		}
		if (partida.getActiu() == null || !partida.getActiu().getId().equals(idJugador)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.JUGADOR_NO_ACTIU);
		}
		if (partida.isLladrePendent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.LLADRE_PENDENT);
		}

		List<Jugador> jugadors = new ArrayList<>(partida.getJugadors());
		jugadors.sort(Comparator.comparingInt(Jugador::getOrdreTorn));
		Jugador actiu = jugadors.stream().filter(j -> j.getId().equals(idJugador)).findFirst().orElseThrow();
		int posicio = jugadors.indexOf(actiu);
		if (partida.getEstat() == EstatPartida.PREPARACIO) {
			return passarTornPreparacio(partida, jugadors, posicio);
		}
		if (!partida.isDausTirats()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.DAUS_NO_TIRATS);
		}
		Jugador guanyador = trobarGuanyador(partida, jugadors, actiu);
		if (guanyador != null) {
			partida.setGuanyador(guanyador);
			partida.setEstat(EstatPartida.FINALITZADA);
			return ResponseEntity.ok(Missatges.VICTORIA + guanyador.getNom());
		}
		if (posicio == jugadors.size() - 1) {
			partida.setRonda(partida.getRonda() + 1);
			partida.setActiu(jugadors.getFirst());
		} else {
			partida.setActiu(jugadors.get(posicio + 1));
		}
		partida.setDausTirats(false);
		return ResponseEntity.ok(Missatges.TORN_PASSAT + partida.getActiu().getNom());
	}

	private ResponseEntity<String> passarTornPreparacio(Partida partida, List<Jugador> jugadors, int posicio) {
		int ronda = partida.getRonda();
		if (!teConstruccionsInicials(jugadors.get(posicio), ronda)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Missatges.PREPARACIO_INCOMPLETA);
		}
		partida.setDausTirats(false);
		if (jugadors.stream().allMatch(j -> teConstruccionsInicials(j, 2))) {
			partida.setEstat(EstatPartida.EN_CURS);
			partida.setRonda(1);
			partida.setActiu(jugadors.getFirst());
			return ResponseEntity.ok(Missatges.PREPARACIO_ACABADA + partida.getActiu().getNom());
		}
		if (ronda == 1) {
			if (posicio == jugadors.size() - 1) {
				partida.setRonda(2);
			} else {
				partida.setActiu(jugadors.get(posicio + 1));
			}
		} else if (posicio > 0) {
			partida.setActiu(jugadors.get(posicio - 1));
		}
		return ResponseEntity.ok(Missatges.TORN_PASSAT + partida.getActiu().getNom());
	}

	private boolean teConstruccionsInicials(Jugador jugador, int quantitat) {
		int poblats = 0;
		int carreteres = 0;
		for (Construccio construccio : jugador.getConstruccions()) {
			if (construccio instanceof Poblat) {
				poblats++;
			} else if (construccio instanceof Carretera) {
				carreteres++;
			}
		}
		return poblats >= quantitat && carreteres >= quantitat;
	}

	private Jugador trobarGuanyador(Partida partida, List<Jugador> jugadors, Jugador actiu) {
		Jugador exercitMesGran = exercitMesGran(jugadors);
		Jugador guanyador = null;
		int millorPuntuacio = -1;
		for (Jugador jugador : jugadors) {
			int punts = calcularPunts(jugador, exercitMesGran);
			if (punts < partida.getPuntsPerGuanyar()) {
				continue;
			}
			if (jugador == actiu) {
				return jugador;
			}
			if (punts > millorPuntuacio) {
				millorPuntuacio = punts;
				guanyador = jugador;
			}
		}
		return guanyador;
	}

	private Jugador exercitMesGran(List<Jugador> jugadors) {
		Jugador millor = null;
		int max = 2;
		boolean empat = false;
		for (Jugador jugador : jugadors) {
			int cavallers = jugador.getCavallersJugats();
			if (cavallers > max) {
				max = cavallers;
				millor = jugador;
				empat = false;
			} else if (cavallers == max && millor != null) {
				empat = true;
			}
		}
		return empat ? null : millor;
	}

	private int calcularPunts(Jugador jugador, Jugador exercitMesGran) {
		int punts = jugador == exercitMesGran ? 2 : 0;
		for (Construccio construccio : jugador.getConstruccions()) {
			if (construccio instanceof Poblat) {
				punts++;
			} else if (construccio instanceof Ciutat) {
				punts += 2;
			}
		}
		for (CartaDesenvolupament carta : jugador.getMa()) {
			if (carta.getTipus() == TipusCarta.PUNT_VICTORIA) {
				punts++;
			}
		}
		return punts;
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
