package MADDGOOFAFF.catan.dto;

import java.util.Date;
import java.util.List;

import MADDGOOFAFF.catan.model.ColorJugador;
import MADDGOOFAFF.catan.model.EstatPartida;
import MADDGOOFAFF.catan.model.Jugador;
import MADDGOOFAFF.catan.model.Partida;

public record PartidaCreada(int id, EstatPartida estat, Date dataInici, int ronda, int puntsPerGuanyar, int jugadorActiuId, List<JugadorCreat> jugadors, List<HexagonTauler> tauler) {

	public static PartidaCreada de(Partida partida) {
		return new PartidaCreada(partida.getId(), partida.getEstat(), partida.getDataInici(),
				partida.getRonda(), partida.getPuntsPerGuanyar(), partida.getActiu().getId(),
				partida.getJugadors().stream().map(JugadorCreat::de).toList(),
				partida.getTauler().stream().map(h -> HexagonTauler.de(h, partida.getLladre())).toList());
	}

	public record JugadorCreat(int id, String nom, ColorJugador color, int ordreTorn) {
		public static JugadorCreat de(Jugador jugador) {
			return new JugadorCreat(jugador.getId(), jugador.getNom(), jugador.getColor(), jugador.getOrdreTorn());
		}
	}
}
