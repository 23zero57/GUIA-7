package com.umb.taller.domain;

public class ApiMetric {
    private String endpoint;
    private String status;

    public ApiMetric(String endpoint, String status) {
        this.endpoint = endpoint;
        this.status = status;
    }

    public String getEndpoint() { return endpoint; }
    public String getStatus() { return status; }
}