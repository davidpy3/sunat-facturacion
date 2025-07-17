package com.empresa.facturacion.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Base64;

@ApplicationScoped
public class DigitalSignatureService {

    private static final Logger LOG = Logger.getLogger(DigitalSignatureService.class);

    private static final String CERTIFICADO_PATH = "src/main/resources/certificates/certificado_factura.pfx";
    private static final String CERTIFICADO_PASSWORD = "factura2025";

    public SignedDocumentResult firmarXml(String xmlContent) {
        try {
            LOG.info("🔐 Iniciando proceso de firma digital");

            // Verificar que el archivo existe
            File certFile = new File(CERTIFICADO_PATH);
            if (!certFile.exists()) {
                LOG.errorf("❌ Certificado no encontrado en: %s", CERTIFICADO_PATH);
                return new SignedDocumentResult(xmlContent, "", false,
                        "Certificado no encontrado en: " + CERTIFICADO_PATH);
            }

            LOG.infof("📂 Certificado encontrado: %s (tamaño: %d bytes)",
                    CERTIFICADO_PATH, certFile.length());

            // 1. Cargar certificado digital
            CertificateInfo certInfo = cargarCertificado();

            // 2. Parsear XML
            Document doc = parsearXml(xmlContent);

            // 3. Calcular hash del documento
            String hashCpe = calcularHashCpe(xmlContent);

            // 4. Firmar el documento
            Document docFirmado = firmarDocumento(doc, certInfo);

            // 5. Convertir a string
            String xmlFirmado = documentToString(docFirmado);

            LOG.info("✅ Documento firmado exitosamente");

            return new SignedDocumentResult(xmlFirmado, hashCpe, true, "Documento firmado correctamente");

        } catch (Exception e) {
            LOG.errorf(e, "❌ Error firmando documento: %s", e.getMessage());
            return new SignedDocumentResult(xmlContent, "", false, "Error firmando: " + e.getMessage());
        }
    }

    private CertificateInfo cargarCertificado() throws Exception {
        LOG.info("📜 Cargando certificado digital");

        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");

            try (FileInputStream fis = new FileInputStream(CERTIFICADO_PATH)) {
                keyStore.load(fis, CERTIFICADO_PASSWORD.toCharArray());
            }

            // Listar aliases disponibles
            java.util.Enumeration<String> aliases = keyStore.aliases();
            String alias = null;
            while (aliases.hasMoreElements()) {
                alias = aliases.nextElement();
                LOG.infof("📋 Alias encontrado: %s", alias);
            }

            if (alias == null) {
                throw new Exception("No se encontraron aliases en el certificado");
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, CERTIFICADO_PASSWORD.toCharArray());
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

            if (privateKey == null) {
                throw new Exception("No se pudo cargar la clave privada");
            }

            if (certificate == null) {
                throw new Exception("No se pudo cargar el certificado");
            }

            LOG.infof("✅ Certificado cargado exitosamente");
            LOG.infof("📋 Sujeto: %s", certificate.getSubjectDN().getName());
            LOG.infof("📋 Emisor: %s", certificate.getIssuerDN().getName());
            LOG.infof("📋 Válido desde: %s", certificate.getNotBefore());
            LOG.infof("📋 Válido hasta: %s", certificate.getNotAfter());

            return new CertificateInfo(privateKey, certificate);

        } catch (Exception e) {
            LOG.errorf(e, "❌ Error cargando certificado");
            throw e;
        }
    }

    private Document parsearXml(String xmlContent) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));
    }

    private String calcularHashCpe(String xmlContent) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(xmlContent.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(hash);
    }

    private Document firmarDocumento(Document doc, CertificateInfo certInfo) throws Exception {
        LOG.info("🔏 Aplicando firma digital al documento");

        // Configurar XMLSignature factory
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Crear referencia al documento
        Reference ref = fac.newReference(
                "",
                fac.newDigestMethod(DigestMethod.SHA1, null),
                Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                null,
                null
        );

        // Crear SignedInfo
        SignedInfo si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,
                        (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                Collections.singletonList(ref)
        );

        // Crear KeyInfo
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        X509Data x509Data = kif.newX509Data(Collections.singletonList(certInfo.certificate));
        KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509Data));

        // Encontrar el elemento ExtensionContent para insertar la firma
        Element extensionContent = (Element) doc.getElementsByTagName("ext:ExtensionContent").item(0);

        if (extensionContent == null) {
            throw new Exception("No se encontró el elemento ext:ExtensionContent en el XML");
        }

        // Crear XMLSignature
        XMLSignature signature = fac.newXMLSignature(si, ki, null, "SignatureSP", null);

        // Crear contexto de firma
        DOMSignContext dsc = new DOMSignContext(certInfo.privateKey, extensionContent);

        // Firmar el documento
        signature.sign(dsc);

        LOG.info("✅ Firma digital aplicada correctamente");

        return doc;
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    // Clases auxiliares
    public static class SignedDocumentResult {
        public final String xmlFirmado;
        public final String hashCpe;
        public final boolean success;
        public final String mensaje;

        public SignedDocumentResult(String xmlFirmado, String hashCpe, boolean success, String mensaje) {
            this.xmlFirmado = xmlFirmado;
            this.hashCpe = hashCpe;
            this.success = success;
            this.mensaje = mensaje;
        }
    }

    private static class CertificateInfo {
        public final PrivateKey privateKey;
        public final X509Certificate certificate;

        public CertificateInfo(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
