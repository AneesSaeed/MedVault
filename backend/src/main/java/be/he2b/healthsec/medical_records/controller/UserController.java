package be.he2b.healthsec.medical_records.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.service.UserService;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/patient")
    public ResponseEntity<String> createPatient(
            @RequestBody User user,
            @RequestParam String dateOfBirthEncBase64) {
        try {
            String message = userService.createPatient(user, dateOfBirthEncBase64);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/doctor")
    public ResponseEntity<String> createDoctor(
            @RequestBody User user,
            @RequestParam String medicalOrganizationEncBase64) {
        try {
            String message = userService.createDoctor(user, medicalOrganizationEncBase64);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
