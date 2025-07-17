package com.empresa.facturacion.service;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "sunat-api")
public interface SunatSoapClient {

    @POST
    @Path("/billService")
    @Consumes("text/xml; charset=utf-8")
    @Produces("text/xml; charset=utf-8")
    Uni<String> enviarDocumento(
            @HeaderParam("Content-Type") String contentType,
            @HeaderParam("SOAPAction") String soapAction,
            @HeaderParam("Accept") String accept,
            @HeaderParam("User-Agent") String userAgent,
            String soapEnvelope
    );
}
