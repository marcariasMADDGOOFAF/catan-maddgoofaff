package MADDGOOFAFF.catan.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearPartida(
		@NotNull(message = "La llista de jugadors és obligatòria")
		@Size(min = 2, max = 4, message = "Calen entre 2 i 4 jugadors")
		List<@NotBlank(message = "El nom és obligatori")
				@Size(max = 100, message = "El nom no pot superar els 100 caràcters") String> nomsJugadors,
		@Min(value = 1, message = "Els punts per guanyar han de ser positius")
		Integer puntsPerGuanyar) {
}
