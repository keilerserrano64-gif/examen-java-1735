package com.carmotors.simulacion;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.EvaluacionProveedorDao;
import com.carmotors.dao.JdbcProveedorDao;
import com.carmotors.dao.JdbcRepuestoDao;
import com.carmotors.dao.LoteDao;
import com.carmotors.dao.ReservaRepuestoDao;
import com.carmotors.excepcion.StockInsuficienteException;
import com.carmotors.servicio.InventarioProveedorServicio;
import com.carmotors.servicio.OrdenServicioServicio;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

// Simulación: dos técnicos intentan reservar al mismo tiempo la ÚNICA unidad de un repuesto.
// Se ejecuta sola (no está en el menú del taller).
public class MainReservaConcurrente {

    static final String NOMBRE_REPUESTO = "Filtro de Aceite XY-123";

    // El mismo servicio de reservas que usa el menú de órdenes de la aplicación
    static OrdenServicioServicio ordenes = new OrdenServicioServicio(ConexionFactory.getInstancia()::abrir);

    // Señal de salida: los dos hilos esperan aquí y arrancan al mismo tiempo
    static CountDownLatch salida = new CountDownLatch(1);

    // Aquí cada hilo anota cómo le fue
    static String ganador = "nadie";
    static String rechazado = "nadie";

    public static void main(String[] args) throws Exception {
        // 1. Preparar el escenario: un repuesto con stock 1 y una orden para cada técnico
        int repuesto = crearRepuestoConStock1();
        int ordenA = crearOrden("Técnico A");
        int ordenB = crearOrden("Técnico B");

        System.out.println("Iniciando simulación de reserva concurrente para el repuesto '" + NOMBRE_REPUESTO + "'...");
        System.out.println("Stock inicial: " + ordenes.disponibleDe(repuesto));
        System.out.println();

        // 2. Crear los dos hilos (cada hilo es un técnico) y ponerlos a correr
        Thread tecnicoA = new Thread(() -> reservar("Hilo-Técnico-A", ordenA, repuesto));
        Thread tecnicoB = new Thread(() -> reservar("Hilo-Técnico-B", ordenB, repuesto));
        tecnicoA.start();
        tecnicoB.start();

        Thread.sleep(200);   // un momento para que los dos estén listos
        System.out.println();
        salida.countDown();  // ¡arrancan!

        tecnicoA.join();     // esperar a que los dos terminen
        tecnicoB.join();

        // 3. Mostrar el resultado
        System.out.println();
        System.out.println("Simulación finalizada.");
        System.out.println("Reservó la última unidad: " + ganador);
        System.out.println("Reserva rechazada: " + rechazado);
        System.out.println("Stock final del repuesto: " + ordenes.disponibleDe(repuesto));
    }

    // Lo que hace cada técnico: intentar reservar 1 unidad del repuesto para su orden
    static void reservar(String tecnico, int orden, int repuesto) {
        System.out.println(tecnico + ": Intentando reservar el repuesto...");
        try {
            salida.await();  // esperar la señal de salida

            // Reserva con synchronized dentro del servicio: solo un técnico a la vez
            ordenes.agregarRepuesto(orden, repuesto, 1, new OrdenServicioServicio.ObservadorReserva() {
                @Override
                public void verificando(int disponible) {
                    System.out.println(tecnico + ": Verificando stock. Hay " + disponible + " disponible.");
                }

                @Override
                public void reservado(int disponible) {
                    System.out.println(tecnico + ": ¡Reserva exitosa! Stock actualizado a " + disponible + ".");
                }
            });
            ganador = tecnico;

        } catch (StockInsuficienteException e) {
            System.out.println(tecnico + ": Fallo en la reserva. No hay suficiente stock.");
            rechazado = tecnico;
        } catch (Exception e) {
            System.out.println(tecnico + ": Error inesperado: " + e.getMessage());
        }
    }

    // Registra un repuesto nuevo con exactamente 1 unidad en stock y devuelve su id
    static int crearRepuestoConStock1() throws SQLException {
        InventarioProveedorServicio inventario = new InventarioProveedorServicio(new JdbcProveedorDao(),
                new JdbcRepuestoDao(), new LoteDao(), new EvaluacionProveedorDao(), new ReservaRepuestoDao());
        return inventario.registrarRepuesto(NOMBRE_REPUESTO, "XY", primerId("repuesto_tipos"), primerId("proveedores"),
                new BigDecimal("20000"), new BigDecimal("35000"), 1, 0);
    }

    // Crea una orden de servicio Pendiente para un técnico y devuelve su id
    static int crearOrden(String tecnico) throws SQLException {
        return ordenes.crear(primerId("vehiculos"), primerId("mantenimiento_tipos"), "Cambio de aceite",
                "Simulación de reserva concurrente - " + tecnico, new BigDecimal("30000"));
    }

    // Devuelve el primer id de una tabla (la base debe tener cargados schema.sql y data.sql)
    static int primerId(String tabla) throws SQLException {
        try (Connection c = ConexionFactory.getInstancia().abrir();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT MIN(id) FROM " + tabla)) {
            r.next();
            return r.getInt(1);
        }
    }
}
