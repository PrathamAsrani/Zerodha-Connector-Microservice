package com.zerodha.service.dtos.logging;

import lombok.Data;

@Data
public class LogPayLoadDTO {
    private String message;
    private String user;

    public LogPayLoadDTO() {
    }

    public LogPayLoadDTO(String message, String user) {
        this.message = message;
        this.user = user;
    }
}
