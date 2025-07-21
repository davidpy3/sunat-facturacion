package com.empresa.facturacion.config;

import io.smallrye.config.ConfigMapping;

/**
 * 🏢 CONFIGURACIÓN DEL EMISOR DESDE application.properties
 * Esta clase mapea automáticamente las propiedades empresa.* del archivo de configuración
 */
@ConfigMapping(prefix = "empresa")
public interface EmisorConfig {

    // ✅ DATOS BÁSICOS DE LA EMPRESA
    String ruc();
    String razonSocial();  // Mapea empresa.razon_social
    String nombreComercial(); // Mapea empresa.nombre_comercial
    String direccion();

    // ✅ DATOS DE UBICACIÓN
    String ubigeo();
    String departamento();
    String provincia();
    String distrito();

    // ✅ CREDENCIALES SUNAT
    String usuarioSol(); // Mapea empresa.usuario_sol
    String claveSol();   // Mapea empresa.clave_sol
}
