package com.zerodha.service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
