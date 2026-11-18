package MADDGOOFAFF.catan.dto;

import java.util.ArrayList;
import java.util.List;

import MADDGOOFAFF.catan.model.ColorJugador;
import MADDGOOFAFF.catan.model.Jugador;
import MADDGOOFAFF.catan.model.RecursJugador;
import MADDGOOFAFF.catan.model.TipusRecurs;

public record JugadorInventari(int id, String nom, ColorJugador color, int ordreTorn, int cavallersJugats, List<Recurs> inventari) {

	public static JugadorInventari de(Jugador jugador) {
		List<Recurs> inventari = new ArrayList<>();
		for (RecursJugador recurs : jugador.getInventari()) {
			inventari.add(new Recurs(recurs.getTipus(), recurs.getQuantitat()));
		}
		return new JugadorInventari(jugador.getId(), jugador.getNom(), jugador.getColor(), jugador.getOrdreTorn(), jugador.getCavallersJugats(), List.copyOf(inventari));
	}

	public record Recurs(TipusRecurs tipus, int quantitat) {
	}
}
