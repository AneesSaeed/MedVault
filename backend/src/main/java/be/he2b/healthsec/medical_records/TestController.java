package be.he2b.healthsec.medical_records;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class TestController {
    @GetMapping("/hello")
    public String publicHello() {
        return "Hello from public endpoint!";
    }
}
