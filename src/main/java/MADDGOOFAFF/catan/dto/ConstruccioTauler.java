package MADDGOOFAFF.catan.dto;

import MADDGOOFAFF.catan.model.Assentament;
import MADDGOOFAFF.catan.model.Ciutat;
import MADDGOOFAFF.catan.model.HexagonVertex;


public record ConstruccioTauler(
		int idVertex,
		String codiVertex,
		int posicio,
		String tipus,
		int idPropietari,
		String propietari) {

	public static ConstruccioTauler de(HexagonVertex hv) {
		Assentament a = hv.getVertex().getAssentament();
		return new ConstruccioTauler(
				hv.getVertex().getId(),
				hv.getVertex().getCodi(),
				hv.getPosicio(),
				a instanceof Ciutat ? "CIUTAT" : "POBLAT",
				a.getPropietari().getId(),
				a.getPropietari().getNom());
	}

}
