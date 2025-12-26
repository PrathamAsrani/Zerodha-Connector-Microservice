package com.zerodha.service.dtos.logging;

import com.zerodha.service.enums.LogEnum;
import lombok.Data;

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
