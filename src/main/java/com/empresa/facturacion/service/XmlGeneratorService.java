package com.empresa.facturacion.service;

import com.empresa.facturacion.dto.FacturaPruebaRequest;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;

@ApplicationScoped
public class XmlGeneratorService {

    public String generarXmlFactura(FacturaPruebaRequest request) {
        String numeroDocumento = request.serie + "-" + request.correlativo;
        String fechaEmision = request.fechaEmision.toString();

        // Calcular totales
        BigDecimal totalGravadas = request.items.stream()
                .filter(item -> "10".equals(item.codigoAfectacionIgv))
                .map(item -> item.valorUnitario.multiply(item.cantidad))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIgv = totalGravadas.multiply(new BigDecimal("0.18"));
        BigDecimal totalDocumento = totalGravadas.add(totalIgv);

        String totalLetras = convertirALetras(totalDocumento);

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" 
                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" 
                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2" 
                     xmlns:ds="http://www.w3.org/2000/09/xmldsig#" 
                     xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
              <ext:UBLExtensions>
                <ext:UBLExtension>
                  <ext:ExtensionContent/>
                </ext:UBLExtension>
              </ext:UBLExtensions>
              <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
              <cbc:CustomizationID>2.0</cbc:CustomizationID>
              <cbc:ID>%s</cbc:ID>
              <cbc:IssueDate>%s</cbc:IssueDate>
              <cbc:IssueTime>00:00:00</cbc:IssueTime>
              <cbc:DueDate>%s</cbc:DueDate>
              <cbc:InvoiceTypeCode listID="0101">01</cbc:InvoiceTypeCode>
              <cbc:Note languageLocaleID="1000"><![CDATA[%s]]></cbc:Note>
              <cbc:DocumentCurrencyCode>%s</cbc:DocumentCurrencyCode>
              %s
              %s
              %s
              %s
              %s
              %s
            </Invoice>
            """,
                numeroDocumento, fechaEmision, fechaEmision, totalLetras, request.moneda,
                generarSeccionFirma(request.emisor),
                generarSeccionEmisor(request.emisor),
                generarSeccionCliente(request.cliente),
                generarSeccionImpuestos(totalGravadas, totalIgv, request.moneda),
                generarSeccionTotales(totalGravadas, totalDocumento, request.moneda),
                generarLineasDetalle(request.items, request.moneda)
        );
    }

    private String generarSeccionFirma(FacturaPruebaRequest.EmisorDto emisor) {
        return String.format("""
            <cac:Signature>
              <cbc:ID>%s</cbc:ID>
              <cbc:Note><![CDATA[%s]]></cbc:Note>
              <cac:SignatoryParty>
                <cac:PartyIdentification>
                  <cbc:ID>%s</cbc:ID>
                </cac:PartyIdentification>
                <cac:PartyName>
                  <cbc:Name><![CDATA[%s]]></cbc:Name>
                </cac:PartyName>
              </cac:SignatoryParty>
              <cac:DigitalSignatureAttachment>
                <cac:ExternalReference>
                  <cbc:URI>#SignatureSP</cbc:URI>
                </cac:ExternalReference>
              </cac:DigitalSignatureAttachment>
            </cac:Signature>
            """, emisor.ruc, emisor.nombreComercial, emisor.ruc, emisor.razonSocial);
    }

    private String generarSeccionEmisor(FacturaPruebaRequest.EmisorDto emisor) {
        return String.format("""
            <cac:AccountingSupplierParty>
              <cac:Party>
                <cac:PartyIdentification>
                  <cbc:ID schemeID="6">%s</cbc:ID>
                </cac:PartyIdentification>
                <cac:PartyName>
                  <cbc:Name><![CDATA[%s]]></cbc:Name>
                </cac:PartyName>
                <cac:PartyLegalEntity>
                  <cbc:RegistrationName><![CDATA[%s]]></cbc:RegistrationName>
                  <cac:RegistrationAddress>
                    <cbc:ID>%s</cbc:ID>
                    <cbc:AddressTypeCode>0000</cbc:AddressTypeCode>
                    <cbc:CitySubdivisionName>NONE</cbc:CitySubdivisionName>
                    <cbc:CityName>%s</cbc:CityName>
                    <cbc:CountrySubentity>%s</cbc:CountrySubentity>
                    <cbc:District>%s</cbc:District>
                    <cac:AddressLine>
                      <cbc:Line><![CDATA[%s]]></cbc:Line>
                    </cac:AddressLine>
                    <cac:Country>
                      <cbc:IdentificationCode>PE</cbc:IdentificationCode>
                    </cac:Country>
                  </cac:RegistrationAddress>
                </cac:PartyLegalEntity>
              </cac:Party>
            </cac:AccountingSupplierParty>
            """, emisor.ruc, emisor.nombreComercial, emisor.razonSocial,
                emisor.ubigeo, emisor.provincia, emisor.departamento, emisor.distrito, emisor.direccion);
    }

    private String generarSeccionCliente(FacturaPruebaRequest.ClienteDto cliente) {
        return String.format("""
            <cac:AccountingCustomerParty>
              <cac:Party>
                <cac:PartyIdentification>
                  <cbc:ID schemeID="%s">%s</cbc:ID>
                </cac:PartyIdentification>
                <cac:PartyLegalEntity>
                  <cbc:RegistrationName><![CDATA[%s]]></cbc:RegistrationName>
                  <cac:RegistrationAddress>
                    <cac:AddressLine>
                      <cbc:Line><![CDATA[%s]]></cbc:Line>
                    </cac:AddressLine>
                    <cac:Country>
                      <cbc:IdentificationCode>PE</cbc:IdentificationCode>
                    </cac:Country>
                  </cac:RegistrationAddress>
                </cac:PartyLegalEntity>
              </cac:Party>
            </cac:AccountingCustomerParty>
            """, cliente.tipoDocumento, cliente.numeroDocumento, cliente.razonSocial, cliente.direccion);
    }

    private String generarSeccionImpuestos(BigDecimal totalGravadas, BigDecimal totalIgv, String moneda) {
        return String.format("""
            <cac:TaxTotal>
              <cbc:TaxAmount currencyID="%s">%s</cbc:TaxAmount>
              <cac:TaxSubtotal>
                <cbc:TaxableAmount currencyID="%s">%s</cbc:TaxableAmount>
                <cbc:TaxAmount currencyID="%s">%s</cbc:TaxAmount>
                <cac:TaxCategory>
                  <cac:TaxScheme>
                    <cbc:ID>1000</cbc:ID>
                    <cbc:Name>IGV</cbc:Name>
                    <cbc:TaxTypeCode>VAT</cbc:TaxTypeCode>
                  </cac:TaxScheme>
                </cac:TaxCategory>
              </cac:TaxSubtotal>
            </cac:TaxTotal>
            """, moneda, totalIgv, moneda, totalGravadas, moneda, totalIgv);
    }

    private String generarSeccionTotales(BigDecimal totalGravadas, BigDecimal totalDocumento, String moneda) {
        return String.format("""
            <cac:LegalMonetaryTotal>
              <cbc:LineExtensionAmount currencyID="%s">%s</cbc:LineExtensionAmount>
              <cbc:TaxInclusiveAmount currencyID="%s">%s</cbc:TaxInclusiveAmount>
              <cbc:PayableAmount currencyID="%s">%s</cbc:PayableAmount>
            </cac:LegalMonetaryTotal>
            """, moneda, totalGravadas, moneda, totalDocumento, moneda, totalDocumento);
    }

    private String generarLineasDetalle(java.util.List<FacturaPruebaRequest.ItemDto> items, String moneda) {
        StringBuilder xml = new StringBuilder();

        for (FacturaPruebaRequest.ItemDto item : items) {
            BigDecimal valorTotal = item.valorUnitario.multiply(item.cantidad);
            BigDecimal igv = "10".equals(item.codigoAfectacionIgv) ?
                    valorTotal.multiply(new BigDecimal("0.18")) : BigDecimal.ZERO;
            BigDecimal precioUnitario = item.valorUnitario.add(
                    igv.divide(item.cantidad, 2, java.math.RoundingMode.HALF_UP));

            xml.append(String.format("""
                <cac:InvoiceLine>
                  <cbc:ID>%d</cbc:ID>
                  <cbc:InvoicedQuantity unitCode="%s">%s</cbc:InvoicedQuantity>
                  <cbc:LineExtensionAmount currencyID="%s">%s</cbc:LineExtensionAmount>
                  <cac:PricingReference>
                    <cac:AlternativeConditionPrice>
                      <cbc:PriceAmount currencyID="%s">%s</cbc:PriceAmount>
                      <cbc:PriceTypeCode>01</cbc:PriceTypeCode>
                    </cac:AlternativeConditionPrice>
                  </cac:PricingReference>
                  <cac:TaxTotal>
                    <cbc:TaxAmount currencyID="%s">%s</cbc:TaxAmount>
                    <cac:TaxSubtotal>
                      <cbc:TaxableAmount currencyID="%s">%s</cbc:TaxableAmount>
                      <cbc:TaxAmount currencyID="%s">%s</cbc:TaxAmount>
                      <cac:TaxCategory>
                        <cbc:Percent>18.00</cbc:Percent>
                        <cbc:TaxExemptionReasonCode>%s</cbc:TaxExemptionReasonCode>
                        <cac:TaxScheme>
                          <cbc:ID>1000</cbc:ID>
                          <cbc:Name>IGV</cbc:Name>
                          <cbc:TaxTypeCode>VAT</cbc:TaxTypeCode>
                        </cac:TaxScheme>
                      </cac:TaxCategory>
                    </cac:TaxSubtotal>
                  </cac:TaxTotal>
                  <cac:Item>
                    <cbc:Description><![CDATA[%s]]></cbc:Description>
                    <cac:SellersItemIdentification>
                      <cbc:ID>%s</cbc:ID>
                    </cac:SellersItemIdentification>
                  </cac:Item>
                  <cac:Price>
                    <cbc:PriceAmount currencyID="%s">%s</cbc:PriceAmount>
                  </cac:Price>
                </cac:InvoiceLine>
                """,
                    item.item, item.unidadMedida, item.cantidad, moneda, valorTotal,
                    moneda, precioUnitario,
                    moneda, igv, moneda, valorTotal, moneda, igv, item.codigoAfectacionIgv,
                    item.descripcion, item.codigoProducto,
                    moneda, item.valorUnitario
            ));
        }

        return xml.toString();
    }

    private String convertirALetras(BigDecimal total) {
        // Implementación simple para prueba
        return "CIENTO DIECIOCHO CON 00/100 SOLES";
    }
}