package com.zapgest.backend.models;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Tabla "referencia_calidad": los 3 precios de una referencia para una calidad.
// Son la "plantilla" de precios que heredaran las variantes (Sprint 2).
@Getter
@Setter
@Entity
@Table(name = "referencia_calidad")
public class ReferenciaCalidad {

    @EmbeddedId
    private ReferenciaCalidadId id = new ReferenciaCalidadId();

    @MapsId("referenciaId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referencia_id")
    private Referencia referencia;

    @MapsId("calidadId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calidad_id")
    private Calidad calidad;

    @Column(name = "costo_adquisicion", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoAdquisicion;

    @Column(name = "precio_minimo", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMinimo;

    @Column(name = "precio_sugerido", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioSugerido;
}
