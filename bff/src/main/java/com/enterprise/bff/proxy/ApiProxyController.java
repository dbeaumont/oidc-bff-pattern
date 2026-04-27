package com.enterprise.bff.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiProxyController {

    // Headers hop-by-hop (RFC 7230) : ne doivent pas être retransmis par un proxy
    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade"
    );

    private final RestClient apiRestClient;

    public ApiProxyController(RestClient apiRestClient) {
        this.apiRestClient = apiRestClient;
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(
        HttpServletRequest request,
        @RequestBody(required = false) Optional<byte[]> body
    ) {
        String path = request.getRequestURI().replaceFirst("/bff/api", "");
        String query = request.getQueryString();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        var uriSpec = apiRestClient
            .method(method)
            .uri(uriBuilder -> {
                uriBuilder.replacePath(path);
                if (query != null) uriBuilder.replaceQuery(query);
                return uriBuilder.build();
            });

        ResponseEntity<byte[]> apiResponse;
        if (body.isPresent() && body.get().length > 0) {
            apiResponse = uriSpec.body(body.get()).retrieve().toEntity(byte[].class);
        } else {
            apiResponse = uriSpec.retrieve().toEntity(byte[].class);
        }

        HttpHeaders filteredHeaders = new HttpHeaders();
        apiResponse.getHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                filteredHeaders.addAll(name, values);
            }
        });

        return ResponseEntity
            .status(apiResponse.getStatusCode())
            .headers(filteredHeaders)
            .body(apiResponse.getBody());
    }
}
