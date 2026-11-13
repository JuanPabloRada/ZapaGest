package com.zapgest.backend.models;

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

// Tabla "inventario" (HU-07 / HU-10): cuantas unidades hay de UNA variante en UNA ubicacion.
// Hay una sola fila por (variante, ubicacion): la restriccion unica tambien la necesita el
// "INSERT ... ON CONFLICT" que suma stock sin pisarse entre ingresos simultaneos.
// La cantidad nunca debe ser negativa (el script SQL debe llevar CHECK (cantidad >= 0)).
@Getter
@Setter
@Entity
@Table(name = "inventario", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventario_variante_ubicacion",
        columnNames = { "variante_id", "ubicacion_id" }))
public class Inventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Column(nullable = false)
    private Integer cantidad = 0;
}
