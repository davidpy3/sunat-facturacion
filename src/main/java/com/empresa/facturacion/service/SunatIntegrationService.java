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

                // Comprimir en ZIP
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos);

                String fileName = "documento.xml";
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(signResult.xmlFirmado.getBytes("UTF-8"));
                zos.closeEntry();
                zos.close();

                String zipBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                return new CompressedDocument(
                        signResult.xmlFirmado,
                        signResult.hashCpe,
                        zipBase64,
                        fileName,
                        true // indica que es firma real
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

            // Comprimir en ZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            String fileName = "documento.xml";
            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(xmlFirmado.getBytes("UTF-8"));
            zos.closeEntry();
            zos.close();

            String zipBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            return new CompressedDocument(
                    xmlFirmado,
                    hashCpe,
                    zipBase64,
                    fileName,
                    false // indica que es firma simulada
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

        String soapEnvelope = construirSoapEnvelope(
                request.emisor.ruc + request.emisor.usuarioSol,
                request.emisor.claveSol,
                fileName,
                doc.zipBase64
        );

        String tipoFirma = doc.esReal ? "REAL" : "SIMULADA";
        LOG.infof("📤 Enviando SOAP a SUNAT: %s (Firma: %s)", fileName, tipoFirma);

        return sunatClient.enviarDocumento(
                "text/xml; charset=utf-8",
                "\"\"",
                "text/xml",
                "Quarkus-SUNAT-Client/1.0",
                soapEnvelope
        ).onFailure().invoke(failure -> {
            LOG.errorf("❌ Error en llamada SOAP: %s", failure.getMessage());
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

        if (mensaje.contains("status code 500")) {
            return SunatResponse.error("SUNAT_500",
                    "Error en servidor SUNAT (500) - Verificar firma digital y formato XML");
        } else if (mensaje.contains("status code 401")) {
            return SunatResponse.error("SUNAT_401",
                    "Error de autenticación - Verificar credenciales SOL");
        } else if (mensaje.contains("status code 404")) {
            return SunatResponse.error("SUNAT_404",
                    "Servicio SUNAT no encontrado - Verificar URL");
        } else if (mensaje.contains("ConnectException") || mensaje.contains("timeout")) {
            return SunatResponse.error("SUNAT_CONECTIVIDAD",
                    "Error de conectividad con SUNAT");
        } else {
            return SunatResponse.error("ERROR_INTERNO", "Error interno: " + mensaje);
        }
    }

    // Clase auxiliar actualizada
    private static class CompressedDocument {
        final String xmlFirmado;
        final String hashCpe;
        final String zipBase64;
        final String fileName;
        final boolean esReal; // nuevo campo

        CompressedDocument(String xmlFirmado, String hashCpe, String zipBase64, String fileName, boolean esReal) {
            this.xmlFirmado = xmlFirmado;
            this.hashCpe = hashCpe;
            this.zipBase64 = zipBase64;
            this.fileName = fileName;
            this.esReal = esReal;
        }
    }
}
