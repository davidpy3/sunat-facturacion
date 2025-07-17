package com.empresa.facturacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FacturaPruebaRequest {

    @NotNull
    @JsonProperty("emisor")
    public EmisorDto emisor;

    @NotNull
    @JsonProperty("cliente")
    public ClienteDto cliente;

    @NotNull
    @JsonProperty("serie")
    public String serie = "F001";

    @NotNull
    @JsonProperty("correlativo")
    public Long correlativo;

    @JsonProperty("fecha_emision")
    public LocalDate fechaEmision = LocalDate.now();

    @JsonProperty("moneda")
    public String moneda = "PEN";

    @NotEmpty
    @JsonProperty("items")
    public List<ItemDto> items;

    public static class EmisorDto {
        public String ruc = "20000000001"; // RUC de prueba SUNAT
        @JsonProperty("razon_social")
        public String razonSocial = "EMPRESA DE PRUEBA SAC";
        @JsonProperty("nombre_comercial")
        public String nombreComercial = "EMPRESA PRUEBA";
        public String direccion = "AV. PRUEBA 123 - LIMA - LIMA - LIMA";
        public String ubigeo = "150101";
        public String departamento = "LIMA";
        public String provincia = "LIMA";
        public String distrito = "LIMA";
        @JsonProperty("usuario_sol")
        public String usuarioSol = "MODDATOS";
        @JsonProperty("clave_sol")
        public String claveSol = "MODDATOS";
    }

    public static class ClienteDto {
        @JsonProperty("tipo_documento")
        public String tipoDocumento = "6"; // RUC
        @JsonProperty("numero_documento")
        public String numeroDocumento = "20123456789";
        @JsonProperty("razon_social")
        public String razonSocial = "CLIENTE DE PRUEBA SAC";
        public String direccion = "AV. CLIENTE 456 - LIMA";
    }

    public static class ItemDto {
        public Integer item = 1;
        @JsonProperty("codigo_producto")
        public String codigoProducto = "PROD001";
        public String descripcion = "PRODUCTO DE PRUEBA";
        public BigDecimal cantidad = new BigDecimal("1");
        @JsonProperty("valor_unitario")
        public BigDecimal valorUnitario = new BigDecimal("100.00");
        @JsonProperty("codigo_afectacion_igv")
        public String codigoAfectacionIgv = "10"; // Gravado
        @JsonProperty("unidad_medida")
        public String unidadMedida = "NIU";
    }
}
