package com.zapgest.backend.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Tabla "referencia" (HU-05): un modelo de zapato, ej. "AF1-001 Nike Air Force".
// Guardar una referencia NO crea stock ni variantes (eso es del Sprint 2).
@Getter
@Setter
@Entity
@Table(name = "referencia")
public class Referencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    // En la pantalla se llama "referencia" (ej. AF1-001); en la base, "codigo"
    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    // Columnas nuevas: Rada debe agregarlas al script SQL
    @Column(length = 60)
    private String marca;

    @Column(length = 60)
    private String categoria;

    private Boolean activa = true;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    // Colores que maneja (tabla puente referencia_color)
    @ManyToMany
    @JoinTable(name = "referencia_color",
            joinColumns = @JoinColumn(name = "referencia_id"),
            inverseJoinColumns = @JoinColumn(name = "color_id"))
    private Set<Color> colores = new LinkedHashSet<>();

    // Tallas que maneja (tabla puente referencia_talla)
    @ManyToMany
    @JoinTable(name = "referencia_talla",
            joinColumns = @JoinColumn(name = "referencia_id"),
            inverseJoinColumns = @JoinColumn(name = "talla_id"))
    private Set<Talla> tallas = new LinkedHashSet<>();

    // Precios por calidad (tabla referencia_calidad)
    @OneToMany(mappedBy = "referencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReferenciaCalidad> precios = new ArrayList<>();
}
