package com.empresa.facturacion.service;

import com.empresa.facturacion.dto.FacturaPruebaRequest;
import com.empresa.facturacion.dto.SunatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class SunatIntegrationService {

    private static final Logger LOG = Logger.getLogger(SunatIntegrationService.class);

    @Inject
    @RestClient
    SunatSoapClient sunatClient;

    @Inject
    XmlGeneratorService xmlGenerator;

    @Inject
    DigitalSignatureService signatureService;

    @Retry(maxRetries = 3, delay = 2000)
    @Timeout(value = 120, unit = ChronoUnit.SECONDS)
    public Uni<SunatResponse> enviarFactura(FacturaPruebaRequest request) {
        LOG.infof("🚀 Iniciando envío de factura %s-%d a SUNAT", request.serie, request.correlativo);

        return Uni.createFrom().item(request)
                .onItem().transform(xmlGenerator::generarXmlFactura)
                .onItem().invoke(xml -> LOG.debugf("📄 XML generado (primeros 300 chars): %s",
                        xml.substring(0, Math.min(300, xml.length()))))
                .onItem().transformToUni(this::firmarYComprimir)
                .onItem().transformToUni(zipData -> construirYEnviarSoap(zipData, request))
                .onItem().transform(this::procesarRespuestaSunat)
                .onFailure().recoverWithItem(this::manejarError);
    }

    private Uni<CompressedDocument> firmarYComprimir(String xmlContent) {
        return Uni.createFrom().item(() -> {
            try {
                LOG.info("🔐 Iniciando proceso de firma y compresión");

                // CAMBIO IMPORTANTE: Usar firma digital real
                DigitalSignatureService.SignedDocumentResult signResult =
                        signatureService.firmarXml(xmlContent);

                if (!signResult.success) {
                    LOG.warnf("⚠️ Firma falló, usando firma simulada: %s", signResult.mensaje);
                    // Fallback a firma simulada si falla la real
                    return simularFirmaYComprimir(xmlContent);
                }

                LOG.info("✅ Documento firmado correctamente con certificado real");

                // 🗂️ NUEVO: Crear directorio para almacenar archivos
                String outputDir = "output/facturas";
                File dir = new File(outputDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                    LOG.infof("📁 Directorio creado: %s", outputDir);
                }

                // 🗂️ NUEVO: Generar nombres de archivos con timestamp
                String timestamp = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String xmlFileName = String.format("factura_%s.xml", timestamp);
                String zipFileName = String.format("factura_%s.zip", timestamp);

                // 💾 NUEVO: Guardar XML firmado
                String xmlFilePath = outputDir + "/" + xmlFileName;
                try (FileWriter writer = new FileWriter(xmlFilePath)) {
                    writer.write(signResult.xmlFirmado);
                }
                LOG.infof("💾 XML firmado guardado: %s", xmlFilePath);

                // Comprimir en ZIP (en memoria)
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos);

                String zipEntryName = "documento.xml";
                ZipEntry entry = new ZipEntry(zipEntryName);
                zos.putNextEntry(entry);
                zos.write(signResult.xmlFirmado.getBytes("UTF-8"));
                zos.closeEntry();
                zos.close();

                // 💾 NUEVO: Guardar ZIP también en disco
                String zipFilePath = outputDir + "/" + zipFileName;
                try (FileOutputStream fos = new FileOutputStream(zipFilePath)) {
                    fos.write(baos.toByteArray());
                }
                LOG.infof("💾 ZIP guardado: %s (tamaño: %d bytes)", zipFilePath, baos.size());

                String zipBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                return new CompressedDocument(
                        signResult.xmlFirmado,
                        signResult.hashCpe,
                        zipBase64,
                        zipEntryName,
                        true, // indica que es firma real
                        xmlFilePath, // 🗂️ NUEVO: ruta del XML
                        zipFilePath  // 🗂️ NUEVO: ruta del ZIP
                );

            } catch (Exception e) {
                LOG.errorf(e, "❌ Error en firma real, usando simulada como fallback");
                // Fallback a firma simulada
                return simularFirmaYComprimir(xmlContent);
            }
        });
    }

    private CompressedDocument simularFirmaYComprimir(String xmlContent) {
        try {
            LOG.warn("⚠️ Usando firma SIMULADA - Solo para pruebas");

            String hashCpe = "simulado_hash_" + System.currentTimeMillis();

            // XML "firmado" simulado
            String xmlFirmado = xmlContent.replace(
                    "<ext:ExtensionContent/>",
                    "<ext:ExtensionContent>" + generarEstructuraFirmaSimulada(hashCpe) + "</ext:ExtensionContent>"
            );

            // 🗂️ NUEVO: Crear directorio y guardar archivos simulados también
            String outputDir = "output/facturas";
            File dir = new File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String xmlFileName = String.format("factura_SIMULADA_%s.xml", timestamp);
            String zipFileName = String.format("factura_SIMULADA_%s.zip", timestamp);

            // 💾 Guardar XML simulado
            String xmlFilePath = outputDir + "/" + xmlFileName;
            try (FileWriter writer = new FileWriter(xmlFilePath)) {
                writer.write(xmlFirmado);
            }

            // Comprimir en ZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            String zipEntryName = "documento.xml";
            ZipEntry entry = new ZipEntry(zipEntryName);
            zos.putNextEntry(entry);
            zos.write(xmlFirmado.getBytes("UTF-8"));
            zos.closeEntry();
            zos.close();

            // 💾 Guardar ZIP simulado
            String zipFilePath = outputDir + "/" + zipFileName;
            try (FileOutputStream fos = new FileOutputStream(zipFilePath)) {
                fos.write(baos.toByteArray());
            }
            LOG.infof("💾 Archivos SIMULADOS guardados: XML=%s, ZIP=%s", xmlFilePath, zipFilePath);

            String zipBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            return new CompressedDocument(
                    xmlFirmado,
                    hashCpe,
                    zipBase64,
                    zipEntryName,
                    false, // indica que es firma simulada
                    xmlFilePath,
                    zipFilePath
            );

        } catch (Exception e) {
            throw new RuntimeException("Error procesando documento", e);
        }
    }

    private String generarEstructuraFirmaSimulada(String hashCpe) {
        return String.format("""
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="SignatureSP">
              <ds:SignedInfo>
                <ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
                <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
                <ds:Reference URI="">
                  <ds:Transforms>
                    <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                  </ds:Transforms>
                  <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
                  <ds:DigestValue>%s</ds:DigestValue>
                </ds:Reference>
              </ds:SignedInfo>
              <ds:SignatureValue>SIGNATURE_VALUE_SIMULADO_PARA_PRUEBA</ds:SignatureValue>
              <ds:KeyInfo>
                <ds:X509Data>
                  <ds:X509Certificate>CERTIFICADO_SIMULADO_PARA_PRUEBA</ds:X509Certificate>
                </ds:X509Data>
              </ds:KeyInfo>
            </ds:Signature>
            """, hashCpe);
    }

    private Uni<String> construirYEnviarSoap(CompressedDocument doc, FacturaPruebaRequest request) {
        String numeroDocumento = request.serie + "-" + request.correlativo;
        String fileName = request.emisor.ruc + "-01-" + numeroDocumento + ".ZIP";

        String username = request.emisor.ruc + request.emisor.usuarioSol;
        String password = request.emisor.claveSol;

        // LOG DETALLADO DE CREDENCIALES
        LOG.infof("🔐 Credenciales SOL a enviar:");
        LOG.infof("   RUC: %s", request.emisor.ruc);
        LOG.infof("   Usuario secundario: %s", request.emisor.usuarioSol);
        LOG.infof("   Usuario SOL completo: %s", username);
        LOG.infof("   Clave SOL: %s***", password.substring(0, Math.min(3, password.length())));

        String soapEnvelope = construirSoapEnvelope(username, password, fileName, doc.zipBase64);

        String tipoFirma = doc.esReal ? "REAL" : "SIMULADA";
        LOG.infof("📤 Enviando SOAP a SUNAT: %s (Firma: %s)", fileName, tipoFirma);
        LOG.infof("📂 Archivos locales: XML=%s, ZIP=%s", doc.xmlFilePath, doc.zipFilePath);

        return sunatClient.enviarDocumento(
                "text/xml; charset=utf-8",
                "\"\"",
                "text/xml",
                "Quarkus-SUNAT-Client/1.0",
                soapEnvelope
        ).onFailure().invoke(failure -> {
            LOG.errorf("❌ Error en llamada SOAP: %s", failure.getMessage());

            // NUEVO: Intentar obtener más detalles del error
            if (failure instanceof jakarta.ws.rs.WebApplicationException) {
                jakarta.ws.rs.WebApplicationException webEx = (jakarta.ws.rs.WebApplicationException) failure;

                LOG.errorf("🚨 Detalles del error SUNAT:");
                LOG.errorf("   Status Code: %d", webEx.getResponse().getStatus());

                try {
                    String responseBody = webEx.getResponse().readEntity(String.class);
                    LOG.errorf("   Response Body: %s", responseBody.substring(0, Math.min(500, responseBody.length())));
                } catch (Exception e) {
                    LOG.errorf("   No se pudo leer el cuerpo de la respuesta: %s", e.getMessage());
                }
            }

            LOG.error("🚨 Error 500 detectado - Posibles causas:");
            LOG.error("   1. Servidor SUNAT BETA temporalmente inestable");
            LOG.error("   2. RUC no habilitado para facturación electrónica");
            LOG.error("   3. Certificado no autorizado en SUNAT");
            LOG.error("   4. Formato XML no completamente compatible");
        });
    }

    private String construirSoapEnvelope(String username, String password, String fileName, String zipContent) {
        return String.format("""
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
                              xmlns:ser="http://service.sunat.gob.pe" 
                              xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
              <soapenv:Header>
                <wsse:Security>
                  <wsse:UsernameToken>
                    <wsse:Username>%s</wsse:Username>
                    <wsse:Password>%s</wsse:Password>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <ser:sendBill>
                  <fileName>%s</fileName>
                  <contentFile>%s</contentFile>
                </ser:sendBill>
              </soapenv:Body>
            </soapenv:Envelope>
            """, username, password, fileName, zipContent);
    }

    private SunatResponse procesarRespuestaSunat(String soapResponse) {
        try {
            LOG.infof("📨 Respuesta SUNAT recibida (primeros 200 chars): %s",
                    soapResponse.substring(0, Math.min(200, soapResponse.length())));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(soapResponse.getBytes()));

            // Verificar si hay CDR (respuesta exitosa)
            NodeList applicationResponse = doc.getElementsByTagName("applicationResponse");
            if (applicationResponse.getLength() > 0) {
                String cdrBase64 = applicationResponse.item(0).getTextContent();

                LOG.infof("🎉 ¡ÉXITO! Documento ACEPTADO por SUNAT - CDR recibido");

                return SunatResponse.success(
                        "0",
                        "La Factura ha sido aceptada por SUNAT",
                        "",
                        cdrBase64,
                        "hash_real",
                        "documento_aceptado"
                );
            } else {
                // Verificar errores SOAP
                NodeList faultCode = doc.getElementsByTagName("faultcode");
                NodeList faultString = doc.getElementsByTagName("faultstring");

                if (faultCode.getLength() > 0) {
                    String codigo = faultCode.item(0).getTextContent();
                    String mensaje = faultString.item(0).getTextContent();

                    LOG.errorf("❌ Error SUNAT - Código: %s, Mensaje: %s", codigo, mensaje);

                    return SunatResponse.error(codigo, mensaje);
                }
            }

            throw new RuntimeException("Respuesta SUNAT no reconocida");

        } catch (Exception e) {
            LOG.errorf(e, "❌ Error procesando respuesta SUNAT");
            return SunatResponse.error("PARSE_ERROR", "Error procesando respuesta: " + e.getMessage());
        }
    }

    private SunatResponse manejarError(Throwable throwable) {
        LOG.errorf(throwable, "💥 Error en integración SUNAT");

        String mensaje = throwable.getMessage();

        // Analizar el mensaje de error más detalladamente
        if (mensaje.contains("status code 500")) {

            // Verificar si es error de autenticación dentro del 500
            if (mensaje.contains("Authentication") || mensaje.contains("Usuario") ||
                    mensaje.contains("Password") || mensaje.contains("Unauthorized")) {

                return SunatResponse.error("SUNAT_AUTH_500",
                        "Error 500 - Posible problema de autenticación. Verificar credenciales SOL (RUC + Usuario Secundario)");
            }

            // Verificar si es error de firma
            if (mensaje.contains("Signature") || mensaje.contains("Certificate") ||
                    mensaje.contains("xmldsig")) {

                return SunatResponse.error("SUNAT_SIGNATURE_500",
                        "Error 500 - Problema con firma digital o certificado");
            }

            // Error 500 genérico
            return SunatResponse.error("SUNAT_500",
                    "Error 500 - Verificar: 1) Credenciales SOL correctas 2) Permisos de facturación 3) Firma digital válida");

        } else if (mensaje.contains("status code 401")) {
            return SunatResponse.error("SUNAT_401",
                    "Error 401 - Credenciales SOL incorrectas. Verificar RUC + Usuario Secundario + Clave");
        } else if (mensaje.contains("status code 404")) {
            return SunatResponse.error("SUNAT_404",
                    "Error 404 - Servicio SUNAT no encontrado. Verificar URL del ambiente BETA");
        } else if (mensaje.contains("ConnectException") || mensaje.contains("timeout")) {
            return SunatResponse.error("SUNAT_CONECTIVIDAD",
                    "Error de conectividad - SUNAT temporalmente no disponible");
        } else {
            return SunatResponse.error("ERROR_INTERNO", "Error interno: " + mensaje);
        }
    }

    // 🗂️ NUEVO: Clase auxiliar actualizada con rutas de archivos
    private static class CompressedDocument {
        final String xmlFirmado;
        final String hashCpe;
        final String zipBase64;
        final String fileName;
        final boolean esReal;
        final String xmlFilePath; // 🗂️ NUEVO
        final String zipFilePath; // 🗂️ NUEVO

        CompressedDocument(String xmlFirmado, String hashCpe, String zipBase64, String fileName,
                           boolean esReal, String xmlFilePath, String zipFilePath) {
            this.xmlFirmado = xmlFirmado;
            this.hashCpe = hashCpe;
            this.zipBase64 = zipBase64;
            this.fileName = fileName;
            this.esReal = esReal;
            this.xmlFilePath = xmlFilePath;
            this.zipFilePath = zipFilePath;
        }
    }
}
