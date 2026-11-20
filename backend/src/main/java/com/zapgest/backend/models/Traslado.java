package com.zapgest.backend.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Tabla "traslado": mover pares de una variante de una ubicacion (origen) a otra (destino).
// Guarda quien y cuando en cada paso. El stock solo se mueve al despachar (sale del origen)
// y al recibir (entra al destino); cada uno deja su movimiento TRASLADO.
// Las fechas son Instant para que el navegador las muestre en su hora local sin desfases.
@Getter
@Setter
@Entity
@Table(name = "traslado")
public class Traslado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private Ubicacion origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private Ubicacion destino;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTraslado estado = EstadoTraslado.SOLICITADO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por", nullable = false)
    private Usuario solicitadoPor;

    @Column(name = "solicitado_en", nullable = false)
    private Instant solicitadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despachado_por")
    private Usuario despachadoPor;

    @Column(name = "despachado_en")
    private Instant despachadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibido_por")
    private Usuario recibidoPor;

    @Column(name = "recibido_en")
    private Instant recibidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por")
    private Usuario canceladoPor;

    @Column(name = "cancelado_en")
    private Instant canceladoEn;
}
