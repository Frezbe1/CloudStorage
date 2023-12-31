package com.example.cloudstorage.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.cloudstorage.controller.config.AuthenticationConfigConstants;
import com.example.cloudstorage.dto.UserDTO;
import com.example.cloudstorage.model.Token;
import com.example.cloudstorage.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("login")
    public ResponseEntity<Token> login(@RequestBody UserDTO userDTO) {
        log.info("Попытка авторизоваться на сервере");
        Token token = userService.login(userDTO);
        return ResponseEntity.ok(token);
    }

    @PostMapping("logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = AuthenticationConfigConstants.AUTH_TOKEN) String authToken,
                                       HttpServletRequest request, HttpServletResponse response) {
        String userLogout = userService.logout(authToken, request, response);
        if (userLogout == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        log.info("Пользователь: {} успешно вышел из системы. Auth-token: {}", userLogout, authToken);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
