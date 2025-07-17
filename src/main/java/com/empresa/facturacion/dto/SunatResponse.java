package com.empresa.facturacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SunatResponse {
    public boolean success;
    @JsonProperty("codigo_respuesta")
    public String codigoRespuesta;
    public String descripcion;
    @JsonProperty("xml_firmado")
    public String xmlFirmado;
    @JsonProperty("cdr_sunat")
    public String cdrSunat;
    @JsonProperty("hash_cpe")
    public String hashCpe;
    @JsonProperty("numero_documento")
    public String numeroDocumento;

    public static SunatResponse success(String codigoRespuesta, String descripcion,
                                        String xmlFirmado, String cdrSunat,
                                        String hashCpe, String numeroDocumento) {
        SunatResponse response = new SunatResponse();
        response.success = true;
        response.codigoRespuesta = codigoRespuesta;
        response.descripcion = descripcion;
        response.xmlFirmado = xmlFirmado;
        response.cdrSunat = cdrSunat;
        response.hashCpe = hashCpe;
        response.numeroDocumento = numeroDocumento;
        return response;
    }

    public static SunatResponse error(String codigoError, String mensaje) {
        SunatResponse response = new SunatResponse();
        response.success = false;
        response.codigoRespuesta = codigoError;
        response.descripcion = mensaje;
        return response;
    }
}
