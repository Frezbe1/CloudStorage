package com.example.cloudstorage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import com.example.cloudstorage.dto.UserDTO;
import com.example.cloudstorage.entity.UserEntity;
import com.example.cloudstorage.model.Token;
import com.example.cloudstorage.repository.UsersRepository;
import com.example.cloudstorage.security.JWTToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTToken jwtToken;

    public Token login(UserDTO userDTO) {
        log.info("Ищем пользователя по логину: {}", userDTO.getLogin());
        var userFound = usersRepository.findByLogin(userDTO.getLogin())
                .orElseThrow(() -> new UsernameNotFoundException("Такого пользователя нет в базе данных"));
        log.info("Пользователь {} найден\n Его данные: {}", userDTO.getLogin(), userFound);

        //шифруем пароли для заполнения БД
           encodePasswordFromBD();

        //проверяем закодированный пароль из БД
        if (passwordEncoder.matches(userDTO.getPassword(), userFound.getPassword())) {
            String token = jwtToken.generateToken(userFound);
            return new Token(token);
        } else {
            throw new UsernameNotFoundException("Пользователь с таким паролем не найден");
        }
    }


    public void encodePasswordFromBD() {
        log.info("Шифруем пароли для БД");
        var listPassword = usersRepository.findAllByLogin();
        for (String s : listPassword) {
            var resultEncode = passwordEncoder.encode(s);
            System.out.println(s.length());
            log.info("Исходный пароль: {} и Зашифрованный пароль: {}", s, resultEncode);
        }
    }

    public String logout(String authToken, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity userEntity = usersRepository.findUserEntitiesByLogin(auth.getPrincipal().toString()).orElseThrow(()
                -> new UsernameNotFoundException("Пользователь не найден"));
        log.info("Пользователь выходит из системы: {}", userEntity.getLogin());
        SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
        securityContextLogoutHandler.logout(request, response, auth);
        jwtToken.removeToken(authToken);
        log.info("Токен {} удален из списка активных токенов для пользователя {} .", authToken, userEntity);
        return userEntity.getLogin();
    }
}
