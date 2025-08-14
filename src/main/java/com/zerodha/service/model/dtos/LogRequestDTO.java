package com.zerodha.service.model.dtos;

import com.zerodha.service.enums.LogEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class LogRequestDTO {
    private LogPayLoadDTO payload;
    private LogEnum level;

    public LogRequestDTO() {
    }

    public LogRequestDTO(LogPayLoadDTO payload, LogEnum level) {
        this.payload = payload;
        this.level = level;
    }
}
