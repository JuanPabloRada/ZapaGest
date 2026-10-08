package com.zapgest.backend.models;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Tabla "talla": catalogo fijo de tallas europeas (34, 34.5, 35 ... 46).
// No se crean tallas nuevas desde la API.
@Getter
@Setter
@Entity
@Table(name = "talla")
public class Talla {

    @Id
    private Long id;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal valor;

    // Devuelve la talla como texto sin ceros de sobra: 38.0 -> "38", 38.5 -> "38.5"
    public String comoTexto() {
        return valor.stripTrailingZeros().toPlainString();
    }
}
