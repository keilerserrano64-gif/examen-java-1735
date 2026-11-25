package com.carmotors.excepcion;

/**
 * Se lanza cuando se intenta reservar más unidades de un repuesto de las que hay disponibles.
 * Hereda de IllegalArgumentException para que los menús y pruebas que ya la capturan sigan igual.
 */
public class StockInsuficienteException extends IllegalArgumentException {
    private final int disponible;

    public StockInsuficienteException(String mensaje, int disponible) {
        super(mensaje);
        this.disponible = disponible;
    }

    /** Unidades que había disponibles cuando se rechazó la reserva. */
    public int getDisponible() {
        return disponible;
    }
}
