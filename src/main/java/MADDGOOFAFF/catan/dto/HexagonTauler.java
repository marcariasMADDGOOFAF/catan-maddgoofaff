package MADDGOOFAFF.catan.dto;

import java.util.Comparator;
import java.util.List;

import MADDGOOFAFF.catan.model.Hexagon;
import MADDGOOFAFF.catan.model.TipusTerreny;


public record HexagonTauler(
		int id,
		String codi,
		TipusTerreny terreny,
		Integer numero,
		boolean lladre,
		List<ConstruccioTauler> construccions) {

	public static HexagonTauler de(Hexagon h, Hexagon hexagonLladre) {
		List<ConstruccioTauler> construccions = h.getHexagonVertices().stream()
				.filter(hv -> hv.getVertex().getAssentament() != null)
				.sorted(Comparator.comparingInt(hv -> hv.getPosicio()))
				.map(ConstruccioTauler::de)
				.toList();
		boolean teLladre = hexagonLladre != null && hexagonLladre.getId().equals(h.getId());
		return new HexagonTauler(h.getId(), h.getCodi(), h.getTerreny(), h.getNumero(), teLladre, construccions);
	}

}
