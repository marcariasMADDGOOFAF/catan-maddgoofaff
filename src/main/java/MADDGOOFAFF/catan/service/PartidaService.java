package MADDGOOFAFF.catan.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import MADDGOOFAFF.catan.dao.Missatges;
import MADDGOOFAFF.catan.dto.CrearPartida;
import MADDGOOFAFF.catan.dto.HexagonTauler;
import MADDGOOFAFF.catan.dto.PartidaCreada;
import MADDGOOFAFF.catan.model.Hexagon;
import MADDGOOFAFF.catan.model.Partida;
import MADDGOOFAFF.catan.model.*;
import MADDGOOFAFF.catan.repository.HexagonRepository;
import MADDGOOFAFF.catan.repository.PartidaRepository;
import jakarta.persistence.EntityManager;


@Service
public class PartidaService {

	private static final String CARPETA_DADES = "/data/";
	private static final String ARXIU_ARESTES = "arestes.csv";
	private static final String ARXIU_HEXAGON_VERTEX = "hexagon_vertex.csv";
	private static final String ARXIU_HEXAGONS = "hexagons.csv";
	private static final String ARXIU_VERTEXS = "vertexs.csv";

	@Autowired
	private PartidaRepository repository;

	@Autowired
	private HexagonRepository hRepository;

	@Autowired
	private EntityManager entityManager;

	@Transactional
	public PartidaCreada iniciarPartida(CrearPartida peticio) {
		int punts = peticio.puntsPerGuanyar() == null ? 10 : peticio.puntsPerGuanyar();
		Partida partida = new Partida(punts);
		prepararJugadors(partida, peticio.nomsJugadors());
		carregarTopologia(partida);
		prepararPila(partida);
		partida = repository.save(partida);
		// El jugador actiu i el desert ja s’han desat per cascada amb la partida.
		assignarJugadorActiuILladre(partida);
		return PartidaCreada.de(partida);
	}


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

	private void prepararJugadors(Partida partida, List<String> nomsJugadors) {
		List<String> jugadorsBarrejats = new ArrayList<>(nomsJugadors);
		Collections.shuffle(jugadorsBarrejats);
		ColorJugador[] colors = ColorJugador.values();
		for (int i = 0; i < jugadorsBarrejats.size(); i++) {
			Jugador jugador = new Jugador(jugadorsBarrejats.get(i), colors[i], i + 1, partida);
			for (TipusRecurs tipus : TipusRecurs.values()) {
				jugador.getInventari().add(new RecursJugador(tipus, 0, jugador));
			}
			partida.afegirJugador(jugador);
		}
	}

	private void carregarTopologia(Partida partida) {
		Map<String, Hexagon> hexagons = carregarHexagons(partida);
		Map<String, Vertex> vertexs = carregarVertexs(partida);
		carregarHexagonVertexs(hexagons, vertexs);
		carregarArestes(vertexs, partida);
	}

	private void prepararPila(Partida partida) {
		for (TipusCarta tipus : TipusCarta.values()) {
			int quantitat = switch (tipus) {
				case CAVALLER -> 14;
				case PUNT_VICTORIA -> 5;
				case MONOPOLI -> 2;
			};
			for (int i = 0; i < quantitat; i++) {
				CartaDesenvolupament carta = new CartaDesenvolupament(tipus);
				carta.setPartida(partida);
				partida.getPila().add(carta);
			}
		}
	}

	private void assignarJugadorActiuILladre(Partida partida) {
		partida.setActiu(partida.getJugadors().getFirst());
		Hexagon desert = partida.getTauler().stream()
				.filter(h -> h.getTerreny() == TipusTerreny.DESERT)
				.findFirst().orElseThrow(() -> new IllegalStateException("No s'ha generat el desert"));
		partida.setLladre(desert);
	}

	private void assignarTerrenysINumeros(Map<String, Hexagon> hexagons) {
		List<TipusTerreny> terrenys = new ArrayList<>();
		TipusTerreny[] tipusTerreny = {TipusTerreny.BOSC, TipusTerreny.TURONS, TipusTerreny.PASTURES,
				TipusTerreny.CAMPS, TipusTerreny.MUNTANYES};
		int[] quantitats = {4, 3, 4, 4, 3};
		for (int t = 0; t < tipusTerreny.length; t++) {
			for (int i = 0; i < quantitats[t]; i++) {
				terrenys.add(tipusTerreny[t]);
			}
		}
		terrenys.add(TipusTerreny.DESERT);

		List<Hexagon> ordreHexagons = new ArrayList<>(hexagons.values());
		if (ordreHexagons.size() != terrenys.size()) {
			throw new IllegalStateException(Missatges.COUNT_HEXAGONS);
		}
		Collections.shuffle(terrenys);
		for (int i = 0; i < ordreHexagons.size() - 1; i++) {
			for (int j = i + 1; j < ordreHexagons.size(); j++) {
				int codiActual = Integer.parseInt(ordreHexagons.get(i).getCodi());
				int codiSeguent = Integer.parseInt(ordreHexagons.get(j).getCodi());
				if (codiActual > codiSeguent) {
					Hexagon temporal = ordreHexagons.get(i);
					ordreHexagons.set(i, ordreHexagons.get(j));
					ordreHexagons.set(j, temporal);
				}
			}
		}
		List<Integer> numeros = new ArrayList<>(List.of(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12));
		Collections.shuffle(numeros);

		int indexNumero = 0;
		for (int i = 0; i < ordreHexagons.size(); i++) {
			Hexagon hexagon = ordreHexagons.get(i);
			TipusTerreny terreny = terrenys.get(i);
			hexagon.setTerreny(terreny);
			if (terreny == TipusTerreny.DESERT) {
				hexagon.setNumero(null);
			} else {
				hexagon.setNumero(numeros.get(indexNumero++));
			}
		}
	}

	private void carregarArestes(Map<String, Vertex> vertexs, Partida partida) {
		for (String[] c : llegirCsv(ARXIU_ARESTES)) {
			Vertex vertexA = obtenir(vertexs, c[1], ARXIU_ARESTES);
			Vertex vertexB = obtenir(vertexs, c[2], ARXIU_ARESTES);
			Aresta e = new Aresta(c[0], vertexA, vertexB, partida);
			partida.afegirAresta(e);
		}
	}

	private void carregarHexagonVertexs(Map<String, Hexagon> hexagons, Map<String, Vertex> vertexs) {
		for (String[] c : llegirCsv(ARXIU_HEXAGON_VERTEX)) {
			Hexagon hexagon = obtenir(hexagons, c[0], ARXIU_HEXAGON_VERTEX);
			Vertex vertex = obtenir(vertexs, c[1], ARXIU_HEXAGON_VERTEX);
			int posicio = Integer.parseInt(c[2]);

			HexagonVertex hexagonVertex = new HexagonVertex(hexagon, vertex, posicio);
			hexagon.afegirVertex(hexagonVertex);
			vertex.afegirHexagon(hexagonVertex);
		}
	}

	private Map<String, Hexagon> carregarHexagons(Partida partida) {
		Map<String, Hexagon> hexagons = new HashMap<>();
		for (String[] c : llegirCsv(ARXIU_HEXAGONS)) {
			Hexagon e = new Hexagon(c[0], Integer.parseInt(c[1]), Integer.parseInt(c[2]), partida);
			partida.afegirHexagon(e);
			hexagons.put(c[0], e);
		}

		assignarTerrenysINumeros(hexagons);
		return hexagons;
	}

	private Map<String, Vertex> carregarVertexs(Partida partida) {
		Map<String, Vertex> vertexs = new HashMap<>();
		for (String[] c : llegirCsv(ARXIU_VERTEXS)) {
			Vertex e = new Vertex(c[0], Double.parseDouble(c[1]), Double.parseDouble(c[2]), partida);
			partida.afegirVertex(e);
			vertexs.put(c[0], e);
		}
		return vertexs;
	}

	private List<String[]> llegirCsv(String fitxer) {
		InputStream is = PartidaService.class.getResourceAsStream(CARPETA_DADES + fitxer);
		if (is == null) {
			throw new IllegalStateException("No es troba el fitxer " + CARPETA_DADES + fitxer);
		}
		List<String[]> files = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			br.readLine();
			String linia;
			while ((linia = br.readLine()) != null) {
				if (linia.isBlank()) {
					continue;
				}
				String[] camps = linia.split(",", -1);
				for (int i = 0; i < camps.length; i++) {
					camps[i] = camps[i].trim();
				}
				files.add(camps);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Error llegint " + fitxer, e);
		}
		return files;
	}

	private <E> E obtenir(Map<String, E> mapa, String clau, String fitxer) {
		E e = mapa.get(clau);
		if (e == null) {
			throw new IllegalStateException(fitxer + ": no existeix '" + clau + "'");
		}
		return e;
	}
}
