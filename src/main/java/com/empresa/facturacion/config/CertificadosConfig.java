
package com.empresa.facturacion.config;

import io.smallrye.config.ConfigMapping;

/**
 * 🔐 CONFIGURACIÓN DEL DIRECTORIO DE CERTIFICADOS
 */
@ConfigMapping(prefix = "certificados")
public interface CertificadosConfig {
    String path();
}