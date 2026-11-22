package MADDGOOFAFF.catan.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import MADDGOOFAFF.catan.service.JugadorService;

@RestController
@RequestMapping("/jugador")
public class JugadorController {

	private final JugadorService service;

	public JugadorController(JugadorService service) {
		this.service = service;
	}

	@GetMapping("/{idJugador}/recursos")
	public ResponseEntity<?> recursos(@PathVariable("idJugador") int idJugador) {
		return service.recursos(idJugador);
	}

	@PostMapping("/{idJugador}/construir")
	public ResponseEntity<?> construir(@PathVariable("idJugador") int idJugador,
			@RequestParam("tipus") String tipus, @RequestParam("posicio") int posicio) {
		return service.construir(idJugador, tipus, posicio);
	}
}
