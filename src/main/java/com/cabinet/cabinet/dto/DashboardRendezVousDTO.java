package com.cabinet.cabinet.dto;

import java.util.List;
import java.util.Map;

public record DashboardRendezVousDTO(Map<String, Long> stats, List<RendezVousResponseDTO> rendezVous) { }
