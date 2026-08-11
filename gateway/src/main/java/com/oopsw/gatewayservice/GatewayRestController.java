package com.oopsw.gatewayservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayRestController {
    @GetMapping("/gateway")
    public String gateway() {return "API Gateway Server";}
}
