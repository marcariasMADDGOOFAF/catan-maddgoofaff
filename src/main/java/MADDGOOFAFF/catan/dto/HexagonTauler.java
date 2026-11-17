package MADDGOOFAFF.catan.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import MADDGOOFAFF.catan.model.Hexagon;
import MADDGOOFAFF.catan.model.HexagonVertex;
import MADDGOOFAFF.catan.model.TipusTerreny;


public record HexagonTauler(int id,String codi,TipusTerreny terreny,Integer numero,boolean lladre,List<ConstruccioTauler> construccions) {

	public static HexagonTauler de(Hexagon h, Hexagon hexagonLladre) {
		
		List<ConstruccioTauler> construccions = new ArrayList<ConstruccioTauler>();

		for (HexagonVertex hv : h.getHexagonVertices()) {
			if (hv.getVertex().getAssentament() != null) {
				construccions.add(ConstruccioTauler.de(hv));
			}
		}
		construccions.sort(Comparator.comparingInt(ConstruccioTauler::posicio));

		boolean teLladre = hexagonLladre != null && hexagonLladre.getId().equals(h.getId());
		return new HexagonTauler(h.getId(), h.getCodi(), h.getTerreny(), h.getNumero(), teLladre, construccions);
	}

}
