package MADDGOOFAFF.catan.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import MADDGOOFAFF.catan.service.PartidaService;


@RestController
@RequestMapping("/partida")
public class PartidaController {

	@Autowired
	private PartidaService service;

	@GetMapping("/{idPartida}/tauler")
	public ResponseEntity<?> tauler(@PathVariable int idPartida) {
		return service.tauler(idPartida);
	}

	@PostMapping("/{idPartida}/daus")
	public ResponseEntity<?> daus(@PathVariable int idPartida){
		return service.daus(idPartida);
	}

}
