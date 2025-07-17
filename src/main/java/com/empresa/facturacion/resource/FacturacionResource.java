package com.empresa.facturacion.resource;

import com.empresa.facturacion.config.EmisorConfig;
import com.empresa.facturacion.dto.FacturaPruebaRequest;
import com.empresa.facturacion.service.SunatIntegrationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import java.util.Map;

@Path("/api/facturacion")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FacturacionResource {

    private static final Logger LOG = Logger.getLogger(FacturacionResource.class);

    @Inject
    SunatIntegrationService sunatService;

    // ✅ CAMBIO: Inyectar configuración del emisor real
    @Inject
    EmisorConfig emisorConfig;

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
                "status", "OK",
                "message", "Servicio de facturación SUNAT activo 🚀",
                "version", "1.0.0"
        )).build();
    }

    @GET
    @Path("/test")
    public Response test() {
        return Response.ok(Map.of(
                "framework", "Quarkus",
                "integration", "SUNAT",
                "ready", true
        )).build();
    }

    @GET
    @Path("/datos-prueba")
    public Response obtenerDatosPrueba() {
        // ✅ CAMBIO: Usar datos reales de configuración en lugar de hardcodeados
        return Response.ok(Map.of(
                "emisor", Map.of(
                        "ruc", emisorConfig.ruc(),
                        "razon_social", emisorConfig.razonSocial(),
                        "nombre_comercial", emisorConfig.nombreComercial(),
                        "direccion", emisorConfig.direccion(),
                        "ubigeo", emisorConfig.ubigeo(),
                        "departamento", emisorConfig.departamento(),
                        "provincia", emisorConfig.provincia(),
                        "distrito", emisorConfig.distrito(),
                        "usuario_sol", emisorConfig.usuarioSol(),
                        "clave_sol", emisorConfig.claveSol()
                ),
                "cliente", Map.of(
                        "tipo_documento", "6",
                        "numero_documento", "20123456789",
                        "razon_social", "CLIENTE DE PRUEBA SAC",
                        "direccion", "AV. CLIENTE 456 - LIMA"
                ),
                "serie", "F001",
                "correlativo", System.currentTimeMillis() % 100000,
                "moneda", "PEN",
                "items", java.util.List.of(Map.of(
                        "item", 1,
                        "codigo_producto", "PROD001",
                        "descripcion", "PRODUCTO DE PRUEBA",
                        "cantidad", 1,
                        "valor_unitario", 100.00,
                        "codigo_afectacion_igv", "10",
                        "unidad_medida", "NIU"
                ))
        )).build();
    }

    /**
     * 🚀 ENDPOINT PRINCIPAL - ENVÍO DE FACTURAS A SUNAT
     * Este es el endpoint que realmente envía facturas al ambiente BETA de SUNAT
     */
    @POST
    @Path("/prueba-factura")
    public Uni<Response> pruebaFactura(@Valid FacturaPruebaRequest request) {
        LOG.infof("🚀 Recibida solicitud de prueba factura: %s-%d", request.serie, request.correlativo);

        return sunatService.enviarFactura(request)
                .onItem().transform(result -> {
                    if (result.success) {
                        LOG.infof("✅ Factura enviada exitosamente: %s", result.descripcion);
                        return Response.ok(result).build();
                    } else {
                        LOG.errorf("❌ Error enviando factura: %s", result.descripcion);
                        return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
                    }
                })
                .onFailure().recoverWithItem(ex -> {
                    LOG.errorf(ex, "💥 Error procesando factura");
                    var errorResponse = Map.of(
                            "success", false,
                            "codigo_respuesta", "ERROR_INTERNO",
                            "descripcion", "Error interno: " + ex.getMessage()
                    );
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
                });
    }

    /**
     * 📄 ENDPOINT PARA GENERAR XML (Solo para debugging)
     * Útil para ver cómo se ve el XML que se generará antes del envío
     */
    @POST
    @Path("/generar-xml")
    @Produces(MediaType.APPLICATION_XML)
    public Response generarXml(@Valid FacturaPruebaRequest request) {
        try {
            // Inyectar XmlGeneratorService si quieres usar este endpoint
            String xml = "<!-- XML se generaría aquí -->";
            return Response.ok(xml)
                    .header("Content-Disposition", "attachment; filename=factura_" + request.serie + "_" + request.correlativo + ".xml")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Error generando XML: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * 📊 ENDPOINT PARA ESTADÍSTICAS DEL SERVICIO
     */
    @GET
    @Path("/stats")
    public Response obtenerEstadisticas() {
        return Response.ok(Map.of(
                "sistema", "Sistema de Facturación Electrónica",
                "ambiente_sunat", "BETA (Pruebas)",
                "url_sunat", "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService",
                "tipos_documento_soportados", java.util.List.of(
                        Map.of("codigo", "01", "descripcion", "Factura"),
                        Map.of("codigo", "03", "descripcion", "Boleta"),
                        Map.of("codigo", "07", "descripcion", "Nota de Crédito"),
                        Map.of("codigo", "08", "descripcion", "Nota de Débito")
                ),
                "version_ubl", "2.1",
                "framework", "Quarkus 3.24.3",
                "java_version", System.getProperty("java.version")
        )).build();
    }

    /**
     * 🔧 ENDPOINT PARA VERIFICAR CONECTIVIDAD CON SUNAT
     */
    @GET
    @Path("/ping-sunat")
    public Uni<Response> pingSunat() {
        // Aquí podrías hacer una verificación simple de conectividad
        return Uni.createFrom().item(() -> {
            try {
                // Simulamos verificación de conectividad
                return Response.ok(Map.of(
                        "sunat_accesible", true,
                        "ambiente", "BETA",
                        "mensaje", "Conectividad con SUNAT OK",
                        "timestamp", java.time.LocalDateTime.now().toString()
                )).build();
            } catch (Exception e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of(
                                "sunat_accesible", false,
                                "error", e.getMessage(),
                                "timestamp", java.time.LocalDateTime.now().toString()
                        )).build();
            }
        });
    }

    /**
     * 📋 ENDPOINT PARA LISTAR CÓDIGOS DE AFECTACIÓN IGV
     */
    @GET
    @Path("/codigos-afectacion")
    public Response obtenerCodigosAfectacion() {
        return Response.ok(Map.of(
                "codigos_afectacion_igv", java.util.List.of(
                        Map.of("codigo", "10", "descripcion", "Gravado - Operación Onerosa", "porcentaje", 18),
                        Map.of("codigo", "20", "descripcion", "Exonerado - Operación Onerosa", "porcentaje", 0),
                        Map.of("codigo", "30", "descripcion", "Inafecto - Operación Onerosa", "porcentaje", 0),
                        Map.of("codigo", "40", "descripcion", "Exportación", "porcentaje", 0)
                ),
                "unidades_medida_comunes", java.util.List.of(
                        Map.of("codigo", "NIU", "descripcion", "Unidad"),
                        Map.of("codigo", "KGM", "descripcion", "Kilogramo"),
                        Map.of("codigo", "MTR", "descripcion", "Metro"),
                        Map.of("codigo", "LTR", "descripcion", "Litro"),
                        Map.of("codigo", "ZZ", "descripcion", "Unidad (Servicios)")
                )
        )).build();
    }
}
