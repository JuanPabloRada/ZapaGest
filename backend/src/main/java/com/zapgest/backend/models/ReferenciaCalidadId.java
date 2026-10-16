package com.zapgest.backend.models;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Llave compuesta de referencia_calidad: (referencia_id, calidad_id)
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReferenciaCalidadId implements Serializable {

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "calidad_id")
    private Long calidadId;
}
