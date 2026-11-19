package com.zapgest.backend.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

// Tabla "variante" (HU-06): la combinacion exacta de una referencia,
// ej. "AF1-001 / Blanco / 40 / Original". Aqui se guardara el stock (por ubicacion) en HU-07.
// Una misma combinacion solo puede existir una vez (restriccion unica).
@Getter
@Setter
@Entity
@Table(name = "variante", uniqueConstraints = @UniqueConstraint(
        name = "uk_variante_combinacion",
        columnNames = { "referencia_id", "color_id", "talla_id", "calidad_id" }))
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referencia_id", nullable = false)
    private Referencia referencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talla_id", nullable = false)
    private Talla talla;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calidad_id", nullable = false)
    private Calidad calidad;

    // Precios heredados del catalogo (referencia_calidad) al crear la variante.
    // Pueden quedar null si la calidad aun no tiene precios; se completan en HU-12/HU-13.
    @Column(name = "costo_adquisicion", precision = 12, scale = 2)
    private BigDecimal costoAdquisicion;

    @Column(name = "precio_minimo", precision = 12, scale = 2)
    private BigDecimal precioMinimo;

    @Column(name = "precio_sugerido", precision = 12, scale = 2)
    private BigDecimal precioSugerido;

    private Boolean activa = true;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}
