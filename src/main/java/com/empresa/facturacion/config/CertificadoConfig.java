package com.empresa.facturacion.config;

import io.smallrye.config.ConfigMapping;

/**
 * 🔐 CONFIGURACIÓN DEL CERTIFICADO DIGITAL
 */
@ConfigMapping(prefix = "certificado")
public interface CertificadoConfig {
    String nombre();
    String password();
}
